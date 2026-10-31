package app.kasane.core

import java.time.LocalDate

/** Open tasks in the three groups the drawer shows, in Reminder order. */
class DrawerGroups(val overdue: List<Task>, val today: List<Task>, val tomorrow: List<Task>) {
    val all: List<Task> get() = overdue + today + tomorrow
    val isEmpty: Boolean get() = overdue.isEmpty() && today.isEmpty() && tomorrow.isEmpty()
}

/** What the Now-first screen, its widgets and the status provider say about tasks. No statistics. */
data class NextUpSummary(
    val next: Task?,
    /** Minute of day the next task is due, or -1 when it has no time. */
    val nextAtMinute: Int,
    val openCount: Int,
    val doneCount: Int,
    val overdueCount: Int,
)

object NextUp {
    private fun order(today: LocalDate) = compareBy<Task> { it.nextDate(today) ?: LocalDate.MAX }.thenBy { it.remindAt ?: -1 }

    /** Tasks that still need doing today or earlier, oldest first; untimed before timed. */
    fun pending(tasks: List<Task>, today: LocalDate): List<Task> =
        tasks.filter { it.open && !it.doneOn(today) && (it.nextDate(today) ?: LocalDate.MAX) <= today }.sortedWith(order(today))

    fun groups(tasks: List<Task>, today: LocalDate): DrawerGroups {
        val live = tasks.filter { it.open && !it.doneOn(today) && it.nextDate(today) != null }.sortedWith(order(today))
        fun bucket(t: Task) = TaskBucket.of(t.nextDate(today)!!, today)
        return DrawerGroups(
            live.filter { bucket(it) == TaskBucket.OVERDUE },
            live.filter { bucket(it) == TaskBucket.TODAY },
            live.filter { bucket(it) == TaskBucket.TOMORROW },
        )
    }

    /** The next task to do: the oldest overdue or due-today one. Null when today is clear. */
    fun pick(tasks: List<Task>, today: LocalDate): Task? = pending(tasks, today).firstOrNull()

    fun summary(tasks: List<Task>, today: LocalDate): NextUpSummary {
        val pending = pending(tasks, today)
        val next = pending.firstOrNull()
        val doneToday = tasks.count { it.doneOn(today) && (it.repeats || it.date == today.toString()) }
        return NextUpSummary(
            next = next,
            nextAtMinute = next?.remindAt ?: -1,
            openCount = pending.size,
            doneCount = doneToday,
            overdueCount = pending.count { (it.nextDate(today) ?: today).isBefore(today) },
        )
    }
}
