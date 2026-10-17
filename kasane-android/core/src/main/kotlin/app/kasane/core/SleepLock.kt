package app.kasane.core

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The 22:00-08:00 sleep lock. Loosening follows Vow's rule: a request waits
 * 24 hours and can only be confirmed between 08:00 and 20:00. Tightening is
 * immediate.
 */
data class SleepLock(
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 8 * 60,
) {
    fun isLocked(time: LocalTime): Boolean {
        val minute = time.hour * 60 + time.minute
        return if (startMinute > endMinute) minute >= startMinute || minute < endMinute
        else minute in startMinute until endMinute
    }

    /** Whether this change makes the lock stricter (longer), so it applies at once. */
    fun isTightening(to: SleepLock): Boolean = length(to) >= length(this) && covers(to)

    private fun length(lock: SleepLock): Int = ((lock.endMinute - lock.startMinute) + 24 * 60) % (24 * 60)

    private fun covers(to: SleepLock): Boolean {
        var minute = startMinute
        while (minute != endMinute) {
            if (!to.isLocked(LocalTime.of(minute / 60, minute % 60))) return false
            minute = (minute + 1) % (24 * 60)
        }
        return true
    }

    companion object {
        const val COOLING_OFF_HOURS = 24L
        val CONFIRM_FROM: LocalTime = LocalTime.of(8, 0)
        val CONFIRM_UNTIL: LocalTime = LocalTime.of(20, 0)

        /** A loosening asked for at `requested` may be confirmed at `now`. */
        fun canConfirmLoosening(requested: LocalDateTime, now: LocalDateTime): Boolean =
            !now.isBefore(requested.plusHours(COOLING_OFF_HOURS)) &&
                !now.toLocalTime().isBefore(CONFIRM_FROM) && now.toLocalTime().isBefore(CONFIRM_UNTIL)
    }
}
