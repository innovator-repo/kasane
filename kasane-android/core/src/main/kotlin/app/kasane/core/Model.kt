package app.kasane.core

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** How an item is logged, which decides its tier for the day. */
@Serializable
enum class Kind {
    /** Done or not done. */
    CHECK,

    /** A number, such as squats. `target` is the day's goal. */
    COUNT,

    /** Minutes, from the timer. `target` is the day's goal in minutes. */
    TIMED,

    /** A rule for the whole day. Kept unless a slip is marked. */
    RULE,
}

/** Where an item sits on the Today screen. */
@Serializable
enum class Block { MORNING, DAY, NIGHT, ALL_DAY }

/** How strongly a reminder gets your attention, like Samsung Reminder's alert levels. */
@Serializable
enum class Alert {
    /** A short buzz, no sound. */
    LIGHT,

    /** The notification sound and a buzz. */
    MEDIUM,

    /** The alarm sound, ringing until the reminder is opened or swiped away. */
    STRONG,
}

/** Which days an item is due. */
@Serializable
enum class Days {
    EVERY_DAY,

    /** Monday to Friday. */
    OFFICE_DAYS;

    fun includes(date: LocalDate): Boolean = when (this) {
        EVERY_DAY -> true
        OFFICE_DAYS -> date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
    }
}

@Serializable
data class Item(
    val id: String,
    val title: String,
    val kind: Kind,
    val block: Block,
    val days: Days = Days.EVERY_DAY,
    /** Goal for COUNT (reps) and TIMED (minutes). Ignored otherwise. */
    val target: Int = 0,
    /** Minutes after midnight for the reminder, or null for none. */
    val remindAt: Int? = null,
    /** A paused item stays in history but is not due. */
    val paused: Boolean = false,
    /** Package or URL the item's shortcut opens, if any. */
    val opens: String? = null,
    /** The item a slip on this rule counts as, for the slip analysis. */
    val isRelapse: Boolean = false,
    /** Chosen days of the week, 1 = Monday to 7 = Sunday. When set, used instead of `days`. */
    val weekdays: List<Int> = emptyList(),
    /** How the reminder sounds. */
    val alert: Alert = Alert.MEDIUM,
) {
    fun dueOn(date: LocalDate): Boolean =
        !paused && if (weekdays.isEmpty()) days.includes(date) else date.dayOfWeek.value in weekdays

    /** Reps or minutes that keep the habit alive on a low-energy day: 20% of the target, at least 1. */
    val floorTarget: Int get() = if (target <= 0) 0 else maxOf(1, (target * FLOOR_SHARE + 0.999).toInt())

    /** The amount logged for COUNT and TIMED items. */
    fun amount(entry: Entry): Int = when (kind) {
        Kind.COUNT -> entry.count
        Kind.TIMED -> entry.minutes
        else -> 0
    }

    /**
     * Finished for the day, so Today hides it. A rule counts once it is marked kept;
     * a slipped rule stays visible so the slip can be undone. A planned exception counts as kept.
     * On a Floor Mode day, or once the floor is parked, a COUNT or TIMED item is finished at its floor.
     */
    fun finishedFor(entry: Entry, floorMode: Boolean = false): Boolean {
        if (entry.exception == ExceptionState.PLANNED) return true
        return when (kind) {
            Kind.CHECK -> entry.done
            Kind.RULE -> entry.done && !entry.slipped
            Kind.COUNT, Kind.TIMED -> {
                val value = amount(entry)
                target > 0 && (value >= target || ((floorMode || entry.floorParked) && value >= floorTarget))
            }
        }
    }

    /** At the floor but short of the target: momentum is protected, the full goal is still open. */
    fun floorKept(entry: Entry): Boolean =
        (kind == Kind.COUNT || kind == Kind.TIMED) && target > 0 && amount(entry) in floorTarget until target

    companion object {
        const val FLOOR_SHARE = 0.2
    }
}

/** Whether a rule was set aside on purpose, planned in a calm moment, rather than slipped. */
@Serializable
enum class ExceptionState { NONE, PLANNED }

/** What was logged for one item on one day. */
@Serializable
data class Entry(
    val done: Boolean = false,
    val count: Int = 0,
    val minutes: Int = 0,
    val slipped: Boolean = false,
    /** A planned exception counts as done, never as a slip, and leaves momentum untouched. */
    val exception: ExceptionState = ExceptionState.NONE,
    /** The floor was accepted for today, so the item rests in the completed tray. */
    val floorParked: Boolean = false,
    /** Minutes after midnight when the item was first finished today, for the notification anchors. */
    val doneAt: Int? = null,
) {
    companion object {
        fun planned(): Entry = Entry(done = true, slipped = false, exception = ExceptionState.PLANNED)
    }
}

/** A named group of tasks, such as Work or Exams. */
@Serializable
data class TaskList(val id: String, val name: String)

/** The time groups a task falls into, in the order Samsung Reminder lists them. */
enum class TaskBucket {
    OVERDUE, TODAY, TOMORROW, WEEK, LATER;

    companion object {
        fun of(date: LocalDate, today: LocalDate): TaskBucket = when {
            date.isBefore(today) -> OVERDUE
            date == today -> TODAY
            date == today.plusDays(1) -> TOMORROW
            date.isBefore(today.plusDays(7)) -> WEEK
            else -> LATER
        }
    }
}

/** A task on a date, such as an exam, or one that repeats on chosen days of the week. */
@Serializable
data class Task(
    val id: String,
    val title: String,
    /** ISO date, yyyy-mm-dd. For a repeating task, the first day it can fall on. */
    val date: String,
    /** Minutes after midnight for the reminder, or null for none. */
    val remindAt: Int? = null,
    val done: Boolean = false,
    /** Days of the week it repeats on, 1 = Monday to 7 = Sunday. Only for tasks saved before [recurrence] existed. */
    val repeat: List<Int> = emptyList(),
    /** For a repeating task, the ISO date of the latest occurrence ticked off. */
    val doneThrough: String? = null,
    /** How the reminder sounds. */
    val alert: Alert = Alert.MEDIUM,
    /** How it repeats, or null for a one-off task. */
    val recurrence: Recurrence? = null,
    /** The list it belongs to, or null for the default list. */
    val listId: String? = null,
) {
    /** The repeat in force: the stored one, or the old weekday list read as weekly. */
    val rule: Recurrence? get() = recurrence ?: if (repeat.isEmpty()) null else Recurrence.weekly(repeat)

    val repeats: Boolean get() = rule != null

    /** Whether it still belongs in the open list. A repeating task stays until its repeat ends. */
    val open: Boolean get() = if (repeats) !ended else !done

    private val startDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()

    private val firstAt: LocalDateTime?
        get() = startDate?.let { LocalDateTime.of(it, LocalTime.of((remindAt ?: 0) / 60, (remindAt ?: 0) % 60)) }

    private val tickedDate: LocalDate? get() = doneThrough?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** A repeat with an end whose last occurrence has been ticked off. */
    val ended: Boolean get() {
        val r = rule ?: return false
        val first = firstAt ?: return false
        val ticked = tickedDate ?: return false
        return r.end != RepeatEnd.FOREVER && r.firstAtOrAfter(first, ticked.plusDays(1).atStartOfDay()) == null
    }

    /** Ticked off for today, or for good if it is a one-off. */
    fun doneOn(today: LocalDate): Boolean = if (repeats) doneThrough == today.toString() else done

    /**
     * The date it is next due: a one-off task's own date, or a repeating task's
     * first occurrence from today that has not been ticked off. Missed days do not pile up.
     */
    fun nextDate(today: LocalDate): LocalDate? {
        val start = startDate ?: return null
        val r = rule ?: return start
        var from = maxOf(today, start)
        val ticked = tickedDate
        if (ticked != null && !ticked.isBefore(from)) from = ticked.plusDays(1)
        return r.firstAtOrAfter(firstAt ?: return null, from.atStartOfDay())?.toLocalDate()
    }

    /** The next occurrence strictly after `after`, skipping days already ticked off. Null for a one-off or when it has ended. */
    fun nextOccurrence(after: LocalDateTime): LocalDateTime? {
        val r = rule ?: return null
        val first = firstAt ?: return null
        val ticked = tickedDate
        var from = after.plusMinutes(1).withSecond(0).withNano(0)
        if (ticked != null && !ticked.isBefore(from.toLocalDate())) from = ticked.plusDays(1).atStartOfDay()
        return r.firstAtOrAfter(first, from)
    }

    /** Whether it belongs on `date`'s list: a one-off on its day (or overdue), a repeating one on an occurrence day. */
    fun dueOn(date: LocalDate): Boolean {
        val r = rule
        if (r == null) return open && startDate?.let { !it.isAfter(date) } == true
        val first = firstAt ?: return false
        if (first.toLocalDate().isAfter(date)) return false
        return r.firstAtOrAfter(first, date.atStartOfDay())?.toLocalDate() == date
    }

    /** Ticks it off, or back on. For a repeating task that is the next occurrence only. */
    fun finish(today: LocalDate, finished: Boolean): Task = when {
        !repeats -> copy(done = finished)
        finished -> copy(doneThrough = nextDate(today)?.toString() ?: doneThrough)
        doneThrough == today.toString() -> copy(doneThrough = null)
        else -> this
    }
}

/** Everything the app stores. Days are keyed by ISO date. */
@Serializable
data class Journal(
    val items: List<Item> = emptyList(),
    val days: Map<String, Map<String, Entry>> = emptyMap(),
    val tasks: List<Task> = emptyList(),
    /** Named lists that group tasks, as in a task manager. Tasks with no list sit in the default one. */
    val lists: List<TaskList> = emptyList(),
) {
    fun entry(date: LocalDate, itemId: String): Entry =
        days[date.toString()]?.get(itemId) ?: Entry()

    fun withEntry(date: LocalDate, itemId: String, entry: Entry): Journal {
        val key = date.toString()
        val day = (days[key] ?: emptyMap()) + (itemId to entry)
        return copy(days = days + (key to day))
    }
}
