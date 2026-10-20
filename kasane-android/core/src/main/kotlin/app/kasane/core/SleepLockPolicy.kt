package app.kasane.core

import java.time.LocalDateTime
import java.time.LocalTime

/** A loosening that is waiting out its cooling-off. */
data class PendingLock(val to: SleepLock, val toOn: Boolean, val requestedAt: LocalDateTime)

/** The sleep lock as stored: its schedule, whether it is on, a sealed flag, and a pending loosening. */
data class SleepLockState(
    val lock: SleepLock = SleepLock(),
    val on: Boolean = true,
    val sealed: Boolean = false,
    val pending: PendingLock? = null,
)

/** What happened to a requested change. */
enum class LockOutcome { APPLIED, PENDING, ALREADY_PENDING, UNCHANGED, REFUSED_SEALED, INVALID }

/**
 * The one set of rules for changing the sleep lock, used for local edits and for changes
 * Vow sends. Tightening applies at once. Loosening, which includes turning the lock off, waits
 * 24 hours and then needs a confirm between 08:00 and 20:00. A sealed lock refuses everything.
 * Asking again for a change already waiting does not restart its timer.
 */
object SleepLockPolicy {
    fun valid(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): Boolean =
        startHour in 0..23 && endHour in 0..23 && startMinute in 0..59 && endMinute in 0..59

    fun request(state: SleepLockState, to: SleepLock, toOn: Boolean, now: LocalDateTime): Pair<SleepLockState, LockOutcome> {
        if (state.sealed) return state to LockOutcome.REFUSED_SEALED
        if (to == state.lock && toOn == state.on) {
            // Asking for what is already in force cancels nothing; any pending loosening stays.
            return state to LockOutcome.UNCHANGED
        }
        if (tightens(state, to, toOn)) return state.copy(lock = to, on = toOn, pending = null) to LockOutcome.APPLIED
        val waiting = state.pending
        if (waiting != null && waiting.to == to && waiting.toOn == toOn) return state to LockOutcome.ALREADY_PENDING
        return state.copy(pending = PendingLock(to, toOn, now)) to LockOutcome.PENDING
    }

    private fun tightens(state: SleepLockState, to: SleepLock, toOn: Boolean): Boolean = when {
        !toOn -> false
        !state.on -> true
        else -> state.lock.isTightening(to)
    }

    /** Applies a waiting loosening once its cooling-off has passed and it is inside the confirm window. */
    fun confirmDue(state: SleepLockState, now: LocalDateTime): Pair<SleepLockState, Boolean> {
        val p = state.pending ?: return state to false
        if (state.sealed || !SleepLock.canConfirmLoosening(p.requestedAt, now)) return state to false
        return state.copy(lock = p.to, on = p.toOn, pending = null) to true
    }

    /** The lock in force at [time], or null when it is off. */
    fun active(state: SleepLockState): SleepLock? = state.lock.takeIf { state.on }
}
