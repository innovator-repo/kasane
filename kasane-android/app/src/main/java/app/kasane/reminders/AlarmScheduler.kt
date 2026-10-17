package app.kasane.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.kasane.MainActivity
import app.kasane.core.Reminders
import app.kasane.store
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Arms the next reminders as alarm clocks. Samsung's battery saver delays
 * ordinary alarms for sleeping apps, which is why the Habits app's
 * notifications arrive late or not at all; `setAlarmClock` is delivered on
 * time even in Doze. Re-armed after every change, reboot, time change and update.
 */
object AlarmScheduler {
    /** Alarms armed at once. Each one re-arms the rest when it fires. */
    private const val SLOTS = 24

    fun rearm(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val upcoming = Reminders.upcoming(context.store.journal.value, LocalDateTime.now()).take(SLOTS)
        for (slot in 0 until SLOTS) alarms.cancel(pending(context, slot, null, null))
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        upcoming.forEachIndexed { slot, reminder ->
            val millis = reminder.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val fire = pending(context, slot, reminder.key, reminder.title)
            if (exact) {
                val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE)
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), fire)
            } else {
                // Without the exact-alarm permission the reminder can be a few minutes late.
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, fire)
            }
        }
    }

    private fun pending(context: Context, slot: Int, key: String?, title: String?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction("app.kasane.REMIND.$slot")
            .putExtra(ReminderReceiver.EXTRA_KEY, key)
            .putExtra(ReminderReceiver.EXTRA_TITLE, title)
        return PendingIntent.getBroadcast(context, slot, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
