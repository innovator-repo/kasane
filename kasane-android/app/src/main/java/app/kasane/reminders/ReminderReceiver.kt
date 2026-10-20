package app.kasane.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.kasane.core.Alert
import app.kasane.widget.Widgets

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val alert = intent.getStringExtra(EXTRA_ALERT)?.let { runCatching { Alert.valueOf(it) }.getOrNull() } ?: Alert.MEDIUM
        Notifier.show(context, key, title, alert)
        AlarmScheduler.rearm(context)
        Widgets.refresh(context)
    }

    companion object {
        const val EXTRA_KEY = "key"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ALERT = "alert"
    }
}
