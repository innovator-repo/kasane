package app.kasane.core

import java.time.LocalTime

/** The four surfaces of the day, which the Lume apps tint themselves to. */
enum class Phase {
    /** 06:00-11:59, warm washi. */
    MORNING,

    /** 12:00-19:59, plain paper. */
    DAY,

    /** 20:00-21:59, warm graphite. */
    EVENING,

    /** 22:00-05:59, quiet obsidian while the sleep lock holds. */
    SANCTUARY;

    /** The Today block that is open in this phase. */
    val block: Block get() = when (this) {
        MORNING -> Block.MORNING
        DAY -> Block.DAY
        EVENING, SANCTUARY -> Block.NIGHT
    }

    companion object {
        /**
         * The surface to draw. Activity prompts follow [of], so Morning starts at 06:00, but while the
         * sleep lock holds (22:00-08:00 by default) the surface stays Sanctuary. With the lock off, it is [of].
         */
        fun surfaceOf(time: LocalTime, lock: SleepLock?): Phase =
            if (lock != null && lock.isLocked(time)) SANCTUARY else of(time)

        fun of(time: LocalTime): Phase {
            val minute = time.hour * 60 + time.minute
            return when {
                minute in 6 * 60 until 12 * 60 -> MORNING
                minute in 12 * 60 until 20 * 60 -> DAY
                minute in 20 * 60 until 22 * 60 -> EVENING
                else -> SANCTUARY
            }
        }
    }
}
