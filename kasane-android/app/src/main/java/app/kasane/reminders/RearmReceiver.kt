package app.kasane.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms reminders after a reboot, an app update or a change of time or time zone. */
class RearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AlarmScheduler.rearm(context)
}
