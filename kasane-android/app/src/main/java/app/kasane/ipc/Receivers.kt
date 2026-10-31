package app.kasane.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.kasane.core.ExceptionGate
import app.kasane.core.ExceptionState
import app.kasane.core.Kind
import app.kasane.reminders.Notifier
import app.kasane.store
import java.time.LocalDate
import java.time.LocalTime

/**
 * Another app reports that a tick-box item is done (Ren after a session, Vow after Duolingo).
 * Only plain CHECK items are touched. Duolingo done opens the hand-off to Ren.
 */
class LogCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!LumeTrust.fromPeer(intent)) return
        if (intent.action != Ecosystem.ACTION_LOG_CHECK) return
        val id = intent.getStringExtra("item") ?: return
        val done = intent.getBooleanExtra("done", true)
        val store = context.store
        val item = store.journal.value.items.firstOrNull { it.id == id && !it.paused } ?: return
        if (item.kind != Kind.CHECK) return
        val today = LocalDate.now()
        val entry = store.journal.value.entry(today, id)
        if (entry.done == done) return
        store.setEntry(today, id, entry.copy(done = done))
        if (done) Notifier.onChecked(context, id)
    }
}

/** Ren, Katachi and Vow send a small JSON summary, kept on the device. */
class EcoMetricsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!LumeTrust.fromPeer(intent)) return
        if (intent.action != Ecosystem.ACTION_ECO_METRICS) return
        val source = intent.getStringExtra("source") ?: return
        val payload = intent.getStringExtra("payload") ?: return
        EcoMetrics.save(context, source, payload)
    }
}

/**
 * Vow reports a rule slipped, kept or set aside. A slip is always taken. Kept never overrides a slip.
 * An exception is only taken when the calm-moment gate allows it, so a hot-state broadcast cannot
 * quietly turn a slip into a loophole.
 */
class RuleEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!LumeTrust.fromPeer(intent)) return
        if (intent.action != Ecosystem.ACTION_RULE_EVENT) return
        val id = intent.getStringExtra("rule") ?: return
        val kind = intent.getStringExtra("kind") ?: return
        val store = context.store
        store.journal.value.items.firstOrNull { it.id == id && it.kind == Kind.RULE && !it.paused } ?: return
        val today = LocalDate.now()
        val entry = store.journal.value.entry(today, id)
        when (kind) {
            "slip" -> if (!entry.slipped) store.setEntry(today, id,
                entry.copy(slipped = true, done = false, exception = ExceptionState.NONE))
            "kept" -> if (!entry.slipped && !entry.done) store.setEntry(today, id, entry.copy(done = true))
            "exception" -> if (!entry.slipped && entry.exception == ExceptionState.NONE &&
                ExceptionGate.canDeclare(LocalTime.now(), null, System.currentTimeMillis())) {
                store.setEntry(today, id, entry.copy(done = true, exception = ExceptionState.PLANNED))
            }
        }
    }
}

/**
 * Vow's sleep lock changed. The values are checked and put through the same rules as a local edit:
 * tightening applies at once, loosening waits out its 24 hours, a sealed lock refuses. Not echoed back.
 */
class SleepLockSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!LumeTrust.fromPeer(intent)) return
        if (intent.action != SleepLockStore.ACTION_SYNC || !intent.hasExtra("on")) return
        SleepLockStore.applyRemote(
            context,
            intent.getIntExtra("startHour", -1), intent.getIntExtra("startMinute", -1),
            intent.getIntExtra("endHour", -1), intent.getIntExtra("endMinute", -1),
            intent.getBooleanExtra("on", true),
        )
        app.kasane.reminders.AlarmScheduler.rearm(context)
    }
}
