package app.kasane.core

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate

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

    /**
     * Finished for the day, so Today hides it. A rule counts once it is marked kept;
     * a slipped rule stays visible so the slip can be undone.
     */
    fun finishedFor(entry: Entry): Boolean = when (kind) {
        Kind.CHECK -> entry.done
        Kind.RULE -> entry.done && !entry.slipped
        Kind.COUNT -> target > 0 && entry.count >= target
        Kind.TIMED -> target > 0 && entry.minutes >= target
    }
}

/** What was logged for one item on one day. */
@Serializable
data class Entry(
    val done: Boolean = false,
    val count: Int = 0,
    val minutes: Int = 0,
    val slipped: Boolean = false,
)

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
    /** Days of the week it repeats on, 1 = Monday to 7 = Sunday. Empty for a one-off task. */
    val repeat: List<Int> = emptyList(),
    /** For a repeating task, the ISO date of the latest occurrence ticked off. */
    val doneThrough: String? = null,
    /** How the reminder sounds. */
    val alert: Alert = Alert.MEDIUM,
) {
    val repeats: Boolean get() = repeat.isNotEmpty()

    /** Whether it still belongs in the open list. A repeating task never runs out. */
    val open: Boolean get() = repeats || !done

    /** Ticked off for today, or for good if it is a one-off. */
    fun doneOn(today: LocalDate): Boolean = if (repeats) doneThrough == today.toString() else done

    /**
     * The date it is next due: a one-off task's own date, or a repeating task's
     * first chosen day from today that has not been ticked off. Missed days do not pile up.
     */
    fun nextDate(today: LocalDate): LocalDate? {
        val start = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        if (!repeats) return start
        var from = maxOf(today, start)
        val ticked = doneThrough?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (ticked != null && !ticked.isBefore(from)) from = ticked.plusDays(1)
        return (0L..6L).map { from.plusDays(it) }.firstOrNull { it.dayOfWeek.value in repeat }
    }

    /** Whether it belongs on `date`'s list: a one-off on its day (or overdue), a repeating one on its next chosen day. */
    fun dueOn(date: LocalDate): Boolean {
        if (!repeats) return open && runCatching { !LocalDate.parse(this.date).isAfter(date) }.getOrDefault(false)
        return date.dayOfWeek.value in repeat && !runCatching { LocalDate.parse(this.date).isAfter(date) }.getOrDefault(false)
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
) {
    fun entry(date: LocalDate, itemId: String): Entry =
        days[date.toString()]?.get(itemId) ?: Entry()

    fun withEntry(date: LocalDate, itemId: String, entry: Entry): Journal {
        val key = date.toString()
        val day = (days[key] ?: emptyMap()) + (itemId to entry)
        return copy(days = days + (key to day))
    }
}
