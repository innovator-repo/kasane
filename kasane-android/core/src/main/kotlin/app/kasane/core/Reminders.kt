package app.kasane.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One reminder to arm with the alarm clock. */
data class Reminder(val key: String, val title: String, val at: LocalDateTime, val alert: Alert = Alert.MEDIUM)

object Reminders {
    /**
     * The next reminder for every item and task, from `now`, skipping items
     * already done today, days an item is not due, and items and repeating
     * tasks inside the sleep lock.
     */
    fun upcoming(journal: Journal, now: LocalDateTime, lock: SleepLock = SleepLock()): List<Reminder> {
        val out = mutableListOf<Reminder>()
        val today = now.toLocalDate()
        // Duolingo and Ren go out as one Japanese evening pass at Duolingo's time.
        val ritual = journal.items.any { it.id == Orchestrator.DUOLINGO && it.remindAt != null } &&
            Orchestrator.japanesePending(journal, today).size == 2
        for (item in journal.items) {
            if (ritual && item.id == Orchestrator.REN) continue
            val minute = Orchestrator.reminderMinute(item, journal, today) ?: continue
            val time = LocalTime.of(minute / 60, minute % 60)
            if (lock.isLocked(time)) continue
            for (offset in 0L..7L) {
                val date = now.toLocalDate().plusDays(offset)
                val at = LocalDateTime.of(date, time)
                if (!at.isAfter(now) || !item.dueOn(date)) continue
                if (offset == 0L && Tier.of(item, journal.entry(date, item.id)) >= Tier.TARGET) continue
                out.add(Reminder("item:${item.id}", item.title, at, item.alert))
                break
            }
        }
        for (task in journal.tasks) {
            val minute = task.remindAt ?: continue
            if (task.repeats) {
                val time = LocalTime.of(minute / 60, minute % 60)
                if (lock.isLocked(time)) continue
                // The next open occurrence still ahead of now: today's if its time has not passed, else the one after.
                val first = task.nextDate(now.toLocalDate()) ?: continue
                val at = LocalDateTime.of(first, time).takeIf { it.isAfter(now) }
                    ?: task.nextDate(first.plusDays(1))?.let { LocalDateTime.of(it, time) }?.takeIf { it.isAfter(now) }
                    ?: continue
                out.add(Reminder("task:${task.id}", task.title, at, task.alert))
                continue
            }
            if (task.done) continue
            val date = runCatching { LocalDate.parse(task.date) }.getOrNull() ?: continue
            val at = LocalDateTime.of(date, LocalTime.of(minute / 60, minute % 60))
            if (at.isAfter(now)) out.add(Reminder("task:${task.id}", task.title, at, task.alert))
        }
        for ((save, at) in Orchestrator.upcomingSaves(journal, now)) {
            out.add(Reminder("save:${save.name}", save.name, at, Orchestrator.saveAlert(save)))
        }
        return out.sortedBy { it.at }
    }
}
