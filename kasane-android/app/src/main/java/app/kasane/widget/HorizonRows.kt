package app.kasane.widget

import app.kasane.core.Item
import app.kasane.core.Journal
import app.kasane.core.Kind
import app.kasane.core.NowPick
import app.kasane.core.Task
import java.time.LocalDate
import java.time.LocalTime

/** Which slice of the list the Horizon shows, switched from the chips in the widget. */
internal enum class HorizonFilter { ALL, HABITS, TASKS }

/** The heading a pending row sits under, in the order Samsung Reminder lists its time groups. */
internal enum class Bucket { OVERDUE, TODAY, TOMORROW, WEEK, UPCOMING }

/** One line of the Horizon list. Habits and tasks share the shape so the widget draws them alike. */
internal sealed interface HorizonRow {
    val key: String
    val title: String
    val done: Boolean
    val bucket: Bucket

    /** A habit or task with nothing to count: a checkbox ticks it off in place. */
    data class Tick(override val key: String, override val title: String, override val done: Boolean,
                    val itemId: String?, val taskId: String?, val note: String? = null,
                    override val bucket: Bucket = Bucket.TODAY,
                    /** For a task: the day it falls on and the reminder minute, so the row can say when. */
                    val date: LocalDate? = null, val remindAt: Int? = null, val repeats: Boolean = false) : HorizonRow {
        /** A one-off task that can be pushed to tomorrow from the widget. */
        val snoozable: Boolean get() = taskId != null && !done && !repeats
    }

    /** A count or minutes habit: the widget shows progress, the app does the logging. */
    data class Amount(override val key: String, override val title: String, override val done: Boolean,
                      val value: Int, val target: Int, val minutes: Boolean,
                      override val bucket: Bucket = Bucket.TODAY, val itemId: String? = null) : HorizonRow {
        val fraction: Float get() = if (target <= 0) 0f else (value.toFloat() / target).coerceIn(0f, 1f)
    }
}

internal class HorizonList(val pending: List<HorizonRow>, val completed: List<HorizonRow>) {
    val total: Int get() = pending.size + completed.size
    val fraction: Float get() = if (total == 0) 0f else completed.size.toFloat() / total
}

/**
 * What the Horizon shows. Pending comes in priority order: tasks that are overdue or due today,
 * then habits in the order the app would nudge them, then tasks further out by date. Finished
 * habits and tasks ticked off today follow, so a tap on a checkbox moves a row down, not away.
 * Rules are left out; the header keeps their tally.
 */
internal fun horizonList(
    journal: Journal,
    today: LocalDate,
    time: LocalTime,
    momentum: Map<String, Double>,
    floor: Boolean,
    filter: HorizonFilter = HorizonFilter.ALL,
    /** Which task list to show: null for every list, "" for the default list, or a list id. */
    listFilter: String? = null,
): HorizonList {
    val queue = NowPick.queue(journal, today, time, momentum, floor)
    val dueItems = journal.items.filter { it.kind != Kind.RULE && it.dueOn(today) }
    val finished = dueItems.filter { it.finishedFor(journal.entry(today, it.id), floor) }

    fun inList(t: Task) = listFilter == null || (if (listFilter == "") t.listId == null else t.listId == listFilter)
    val doneTasks = journal.tasks.filter { inList(it) && it.doneOn(today) && (it.repeats || sameDay(it, today)) }
    val live = journal.tasks.filter { inList(it) && it.open && !it.doneOn(today) && it.nextDate(today) != null }
        // Samsung Reminder sorts by due time, oldest first, and a task with no time sorts as the start of its day.
        .sortedWith(compareBy<Task> { it.nextDate(today) }.thenBy { it.remindAt ?: -1 })
    val overdue = live.filter { it.nextDate(today)!!.isBefore(today) }
    val todayTasks = live.filter { it.nextDate(today) == today }
    val (untimed, timed) = todayTasks.partition { it.remindAt == null }
    val later = live.filter { it.nextDate(today)!!.isAfter(today) }

    val habits = filter != HorizonFilter.TASKS
    val tasks = filter != HorizonFilter.HABITS
    // Overdue, then today (untimed first, then by time), tomorrow, the week, later; habits count as untimed today.
    val pending = (if (tasks) (overdue + untimed).map { taskRow(it, false, today) } else emptyList()) +
        (if (habits) queue.map { habitRow(it, journal, today, floor) } else emptyList()) +
        (if (tasks) (timed + later).map { taskRow(it, false, today) } else emptyList())
    val completed = (if (habits) finished.map { habitRow(it, journal, today, floor) } else emptyList()) +
        (if (tasks) doneTasks.map { taskRow(it, true, today) } else emptyList())
    return HorizonList(pending, completed)
}

/** A one-off task is only kept in today's tray if it was due today; older ones have left the list. */
private fun sameDay(task: Task, today: LocalDate) = runCatching { LocalDate.parse(task.date) == today }.getOrDefault(false)

private fun taskRow(task: Task, done: Boolean, today: LocalDate): HorizonRow.Tick {
    val date = task.nextDate(today) ?: runCatching { LocalDate.parse(task.date) }.getOrNull()
    val bucket = when {
        done || date == null -> Bucket.TODAY
        date.isBefore(today) -> Bucket.OVERDUE
        date == today.plusDays(1) -> Bucket.TOMORROW
        date.isBefore(today.plusDays(7)) && date.isAfter(today) -> Bucket.WEEK
        date.isAfter(today) -> Bucket.UPCOMING
        else -> Bucket.TODAY
    }
    return HorizonRow.Tick("task:" + task.id, task.title, done, itemId = null, taskId = task.id,
        bucket = bucket, date = date, remindAt = task.remindAt, repeats = task.repeats)
}

private fun habitRow(item: Item, journal: Journal, today: LocalDate, floor: Boolean): HorizonRow {
    val entry = journal.entry(today, item.id)
    val done = item.finishedFor(entry, floor)
    return when (item.kind) {
        Kind.COUNT, Kind.TIMED ->
            HorizonRow.Amount("item:" + item.id, item.title, done, item.amount(entry), item.target, item.kind == Kind.TIMED, itemId = item.id)
        else -> HorizonRow.Tick("item:" + item.id, item.title, done, itemId = item.id, taskId = null)
    }
}
