package app.kasane.reminders

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.kasane.KasaneApp
import app.kasane.MainActivity
import app.kasane.R
import app.kasane.core.Alert
import app.kasane.widget.Widgets

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val alert = intent.getStringExtra(EXTRA_ALERT)?.let { runCatching { Alert.valueOf(it) }.getOrNull() } ?: Alert.MEDIUM
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            val open = PendingIntent.getActivity(context, key.hashCode(),
                Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, KasaneApp.channelFor(alert))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.reminder_text))
                .setCategory(if (alert == Alert.STRONG) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            // Strong keeps ringing until the reminder is opened or swiped away.
            if (alert == Alert.STRONG) notification.flags = notification.flags or Notification.FLAG_INSISTENT
            NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
        }
        AlarmScheduler.rearm(context)
        Widgets.refresh(context)
    }

    companion object {
        const val EXTRA_KEY = "key"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ALERT = "alert"
    }
}
