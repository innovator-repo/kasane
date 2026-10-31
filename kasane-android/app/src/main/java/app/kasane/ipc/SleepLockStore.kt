package app.kasane.ipc

import android.content.Context
import android.content.Intent
import app.kasane.core.LockOutcome
import app.kasane.core.PendingLock
import app.kasane.core.SleepLock
import app.kasane.core.SleepLockPolicy
import app.kasane.core.SleepLockState
import java.time.LocalDateTime

/**
 * The sleep lock on the device, shared with Vow. Every change goes through [SleepLockPolicy], whether it
 * is typed here or arrives from Vow. Only local changes are sent to Vow, so the two never echo.
 */
object SleepLockStore {
    const val ACTION_SYNC = "app.lume.action.SLEEP_LOCK_SYNC"

    private fun prefs(context: Context) = context.getSharedPreferences("sleep_lock", Context.MODE_PRIVATE)

    @Synchronized
    fun state(context: Context): SleepLockState {
        val p = prefs(context)
        val d = SleepLock()
        val pending = p.getString("pending_at", null)?.let { at ->
            runCatching { LocalDateTime.parse(at) }.getOrNull()?.let {
                PendingLock(SleepLock(p.getInt("pending_start", d.startMinute), p.getInt("pending_end", d.endMinute)),
                    p.getBoolean("pending_on", true), it)
            }
        }
        return SleepLockState(SleepLock(p.getInt("start", d.startMinute), p.getInt("end", d.endMinute)),
            p.getBoolean("on", true), p.getBoolean("sealed", false), pending)
    }

    /** The lock in force now, or null when it is off. */
    fun active(context: Context): SleepLock? = SleepLockPolicy.active(state(context))

    @Synchronized
    private fun save(context: Context, s: SleepLockState) {
        prefs(context).edit().apply {
            putInt("start", s.lock.startMinute).putInt("end", s.lock.endMinute)
            putBoolean("on", s.on).putBoolean("sealed", s.sealed)
            val p = s.pending
            if (p == null) remove("pending_at").remove("pending_start").remove("pending_end").remove("pending_on")
            else putString("pending_at", p.requestedAt.toString()).putInt("pending_start", p.to.startMinute)
                .putInt("pending_end", p.to.endMinute).putBoolean("pending_on", p.toOn)
        }.apply()
    }

    /** A change made in Kasane. Sent to Vow when it takes effect. */
    fun requestLocal(context: Context, to: SleepLock, on: Boolean, now: LocalDateTime = LocalDateTime.now()): LockOutcome =
        change(context, to, on, now, notifyVow = true)

    /** A change Vow sent: validated, put through the same rules, and never sent back. */
    fun applyRemote(context: Context, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int, on: Boolean,
        now: LocalDateTime = LocalDateTime.now()): LockOutcome {
        if (!SleepLockPolicy.valid(startHour, startMinute, endHour, endMinute)) return LockOutcome.INVALID
        return change(context, SleepLock(startHour * 60 + startMinute, endHour * 60 + endMinute), on, now, notifyVow = false)
    }

    @Synchronized
    private fun change(context: Context, to: SleepLock, on: Boolean, now: LocalDateTime, notifyVow: Boolean): LockOutcome {
        val (next, outcome) = SleepLockPolicy.request(state(context), to, on, now)
        if (next != state(context)) save(context, next)
        if (outcome == LockOutcome.APPLIED && notifyVow) sendToVow(context, next)
        return outcome
    }

    /** Applies a waiting loosening once it may be confirmed. A local effect, so Vow is told. */
    @Synchronized
    fun confirmDue(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val (next, applied) = SleepLockPolicy.confirmDue(state(context), now)
        if (!applied) return
        save(context, next)
        sendToVow(context, next)
    }

    private fun sendToVow(context: Context, s: SleepLockState) {
        if (!Ecosystem.installed(context, Ecosystem.VOW)) return
        val intent = Intent(ACTION_SYNC).setPackage(Ecosystem.VOW)
            .putExtra("on", s.on)
            .putExtra("startHour", s.lock.startMinute / 60).putExtra("startMinute", s.lock.startMinute % 60)
            .putExtra("endHour", s.lock.endMinute / 60).putExtra("endMinute", s.lock.endMinute % 60)
        runCatching { context.sendBroadcast(LumeTrust.stamp(context, intent)) }
    }
}
