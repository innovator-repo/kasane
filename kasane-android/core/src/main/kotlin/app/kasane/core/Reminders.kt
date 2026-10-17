package app.kasane.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One reminder to arm with the alarm clock. */
data class Reminder(val key: String, val title: String, val at: LocalDateTime)

object Reminders {
    /**
     * The next reminder for every item and task, from `now`, skipping items
     * already done today, days an item is not due, and anything inside the
     * sleep lock.
     */
    fun upcoming(journal: Journal, now: LocalDateTime, lock: SleepLock = SleepLock()): List<Reminder> {
        val out = mutableListOf<Reminder>()
        for (item in journal.items) {
            val minute = item.remindAt ?: continue
            val time = LocalTime.of(minute / 60, minute % 60)
            if (lock.isLocked(time)) continue
            for (offset in 0L..7L) {
                val date = now.toLocalDate().plusDays(offset)
                val at = LocalDateTime.of(date, time)
                if (!at.isAfter(now) || !item.dueOn(date)) continue
                if (offset == 0L && Tier.of(item, journal.entry(date, item.id)) >= Tier.TARGET) continue
                out.add(Reminder("item:${item.id}", item.title, at))
                break
            }
        }
        for (task in journal.tasks) {
            val minute = task.remindAt ?: continue
            if (task.done) continue
            val date = runCatching { LocalDate.parse(task.date) }.getOrNull() ?: continue
            val at = LocalDateTime.of(date, LocalTime.of(minute / 60, minute % 60))
            if (at.isAfter(now)) out.add(Reminder("task:${task.id}", task.title, at))
        }
        return out.sortedBy { it.at }
    }
}
