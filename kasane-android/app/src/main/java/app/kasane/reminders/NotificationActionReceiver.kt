package app.kasane.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.kasane.core.Alert
import app.kasane.store
import java.time.LocalDate

/** The buttons on a Kasane notification that act without opening the app. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val action = intent.action.orEmpty()
        when {
            action.startsWith(FLOOR) -> {
                val id = intent.getStringExtra(EXTRA_ITEM) ?: return
                val store = context.store
                val item = store.journal.value.items.firstOrNull { it.id == id } ?: return
                val today = LocalDate.now()
                store.setEntry(today, id, Notifier.floorEntry(item, store.journal.value.entry(today, id)))
            }
            action.startsWith(SNOOZE) -> {
                val title = intent.getStringExtra(ReminderReceiver.EXTRA_TITLE) ?: return
                val alert = intent.getStringExtra(ReminderReceiver.EXTRA_ALERT)
                    ?.let { runCatching { Alert.valueOf(it) }.getOrNull() } ?: Alert.MEDIUM
                Notifier.snooze(context, key, title, alert)
            }
            else -> return
        }
        NotificationManagerCompat.from(context).cancel(key.hashCode())
    }

    companion object {
        const val FLOOR = "app.kasane.FLOOR"
        const val SNOOZE = "app.kasane.SNOOZE_ACTION"
        const val EXTRA_ITEM = "item"
        const val EXTRA_KEY = "notification_key"
    }
}
