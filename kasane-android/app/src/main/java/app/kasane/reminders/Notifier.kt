package app.kasane.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
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
import app.kasane.core.Entry
import app.kasane.core.Item
import app.kasane.core.Kind
import app.kasane.core.Orchestrator
import androidx.compose.ui.graphics.toArgb
import app.kasane.designsystem.LumePalette
import app.kasane.ipc.Ecosystem
import app.kasane.store
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The master notification orchestrator. Every push is built from today's live state at the moment
 * it fires, so a habit finished early never gets a late nudge, and Duolingo and Ren arrive as one pass.
 */
object Notifier {
    private const val HANDOFF_ID = 9101
    private const val SNOOZE_MINUTES = 45L

    fun show(context: Context, key: String, title: String, alert: Alert) {
        if (!canNotify(context)) return
        val today = LocalDate.now()
        val store = context.store
        val journal = store.journal.value
        val floor = store.floorToday()
        when {
            key.startsWith("save:") -> {
                val save = runCatching { Orchestrator.Save.valueOf(key.removePrefix("save:")) }.getOrNull() ?: return
                val pending = Orchestrator.pending(journal, today, save, floor)
                if (pending.isEmpty()) return
                notify(context, key, saveNotification(context, save, pending, alert), alert)
            }
            key == "item:${Orchestrator.DUOLINGO}" && Orchestrator.japanesePending(journal, today, floor).size == 2 ->
                notify(context, key, ritualNotification(context, alert), alert)
            key.startsWith("item:") -> {
                val item = journal.items.firstOrNull { "item:${it.id}" == key } ?: return
                if (item.finishedFor(journal.entry(today, item.id), floor)) return
                notify(context, key, anchorNotification(context, item, alert), alert)
            }
            else -> notify(context, key, plain(context, key, title, alert).build(), alert)
        }
    }

    /** Duolingo just got logged: if Ren is still open, offer it while the phone is in hand. */
    @SuppressLint("MissingPermission") // canNotify() checks POST_NOTIFICATIONS
    fun onChecked(context: Context, itemId: String) {
        if (itemId != Orchestrator.DUOLINGO || !canNotify(context)) return
        val handOff = Orchestrator.handOff(context.store.journal.value, LocalDate.now(), LocalTime.now())
        if (handOff == Orchestrator.HandOff.NONE) return
        val quick = handOff == Orchestrator.HandOff.QUICK
        val n = builder(context, KasaneApp.CHANNEL_HANDOFF, context.getString(R.string.handoff_title),
            context.getString(if (quick) R.string.handoff_text_quick else R.string.handoff_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(renOrApp(context, quick, HANDOFF_ID))
            .setTimeoutAfter(30 * 60_000L)
            .build()
        NotificationManagerCompat.from(context).notify(HANDOFF_ID, n)
    }

    private fun ritualNotification(context: Context, alert: Alert): Notification {
        val status = Ecosystem.renStatus(context)
        val body = if (status != null && status.due > 0)
            context.getString(R.string.ritual_text_live, status.due, status.retention)
        else context.getString(R.string.ritual_text)
        val b = plain(context, "item:ritual", context.getString(R.string.ritual_title), alert, body)
        b.addAction(0, context.getString(R.string.action_open_duolingo), openApp(context, Ecosystem.DUOLINGO, 1))
        b.addAction(0, context.getString(R.string.action_quick_ren), renOrApp(context, true, 2))
        return b.build()
    }

    private fun anchorNotification(context: Context, item: Item, alert: Alert): Notification {
        val body = if (item.id == Orchestrator.REN) {
            Ecosystem.renStatus(context)?.takeIf { it.due > 0 }
                ?.let { context.getString(R.string.anchor_ren_live, it.due, it.retention) }
        } else null
        val b = plain(context, "item:${item.id}", item.title, alert, body ?: context.getString(R.string.reminder_text))
        val start = if (item.id == Orchestrator.REN) renOrApp(context, false, 3) else openKasane(context, item.id.hashCode())
        b.addAction(0, context.getString(R.string.action_start_now), start)
        b.addAction(0, context.getString(R.string.action_floor), action(context, NotificationActionReceiver.FLOOR, "item:${item.id}", item.id, item.title, alert))
        b.addAction(0, context.getString(R.string.action_snooze), action(context, NotificationActionReceiver.SNOOZE, "item:${item.id}", item.id, item.title, alert))
        return b.build()
    }

    private fun saveNotification(context: Context, save: Orchestrator.Save, pending: List<Item>, alert: Alert): Notification {
        val status = Ecosystem.renStatus(context)
        val names = pending.joinToString(" · ") { it.title }
        val (title, body) = when (save) {
            Orchestrator.Save.SHIELD_2100 -> context.getString(R.string.shield_title) to
                context.getString(R.string.shield_text, names)
            else -> context.getString(R.string.save_title) to
                if (status != null && status.retention > 0)
                    context.getString(R.string.save_text_live, status.retention, status.streak, names)
                else context.getString(R.string.save_text, names)
        }
        val b = plain(context, "save:${save.name}", title, alert, body)
        if (save != Orchestrator.Save.WARN_2030) b.setColor(LumePalette.Urgent.toArgb())
        val ren = pending.any { it.id == Orchestrator.REN }
        if (ren) b.addAction(0, context.getString(if (save == Orchestrator.Save.SHIELD_2100) R.string.action_shield else R.string.action_quick_save),
            renOrApp(context, true, 4))
        if (save != Orchestrator.Save.SHIELD_2100) {
            b.addAction(0, context.getString(R.string.action_declare_exception), openKasane(context, 5))
        }
        return b.build()
    }

    private fun plain(context: Context, key: String, title: String, alert: Alert, text: String? = null): NotificationCompat.Builder =
        builder(context, KasaneApp.channelFor(alert), title, text ?: context.getString(R.string.reminder_text))
            .setCategory(if (alert == Alert.STRONG) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openKasane(context, key.hashCode()))

    private fun builder(context: Context, channel: String, title: String, text: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)

    @SuppressLint("MissingPermission") // callers check canNotify() first
    private fun notify(context: Context, key: String, n: Notification, alert: Alert) {
        // Strong keeps ringing until the reminder is opened or swiped away.
        if (alert == Alert.STRONG) n.flags = n.flags or Notification.FLAG_INSISTENT
        NotificationManagerCompat.from(context).notify(key.hashCode(), n)
    }

    private fun canNotify(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openKasane(context: Context, code: Int): PendingIntent = PendingIntent.getActivity(context, code,
        Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun openApp(context: Context, pkg: String, code: Int): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context, 100 + code, launch, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun renOrApp(context: Context, quick: Boolean, code: Int): PendingIntent {
        val intent = Ecosystem.renIntent(context, quick) ?: Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context, 200 + code, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(context: Context, name: String, notificationKey: String, itemId: String, title: String, alert: Alert): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).setAction("$name.$notificationKey")
            .putExtra(NotificationActionReceiver.EXTRA_ITEM, itemId)
            .putExtra(NotificationActionReceiver.EXTRA_KEY, notificationKey)
            .putExtra(ReminderReceiver.EXTRA_TITLE, title)
            .putExtra(ReminderReceiver.EXTRA_ALERT, alert.name)
        return PendingIntent.getBroadcast(context, "$name.$notificationKey".hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Re-arms [key] to ring again after the snooze. */
    fun snooze(context: Context, key: String, title: String, alert: Alert) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = LocalDateTime.now().plusMinutes(SNOOZE_MINUTES)
        val millis = at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, ReminderReceiver::class.java).setAction("app.kasane.SNOOZE.$key")
            .putExtra(ReminderReceiver.EXTRA_KEY, key)
            .putExtra(ReminderReceiver.EXTRA_TITLE, title)
            .putExtra(ReminderReceiver.EXTRA_ALERT, alert.name)
        val pending = PendingIntent.getBroadcast(context, "snooze.$key".hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
    }

    /** What the "2-Min Floor" button logs: the floor amount, or a tick. */
    fun floorEntry(item: Item, entry: Entry): Entry = when (item.kind) {
        Kind.CHECK -> entry.copy(done = true)
        Kind.COUNT -> entry.copy(count = maxOf(entry.count, item.floorTarget), floorParked = true)
        Kind.TIMED -> entry.copy(minutes = maxOf(entry.minutes, item.floorTarget), floorParked = true)
        Kind.RULE -> entry
    }
}
