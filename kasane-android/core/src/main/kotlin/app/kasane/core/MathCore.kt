package app.kasane.core

import kotlin.math.exp
import kotlin.math.max

/**
 * The math Kasane runs on, with the exact shapes of `lume/core.lm`.
 * Arrays are row-major doubles, the same memory the compiled Lume functions
 * read, so the JNI shim passes them through without conversion.
 */
interface MathCore {
    /** All arrays have [HABITS] elements. */
    fun momentumStep(m: DoubleArray, decay: DoubleArray, delta: DoubleArray, sched: DoubleArray): DoubleArray

    /** group is [PERIODS] x [DAYS]; done and sched are [DAYS] x [HABITS]. Returns [PERIODS] x [HABITS]. */
    fun periodRates(group: DoubleArray, done: DoubleArray, sched: DoubleArray): DoubleArray

    /** miss and kept are [DAYS] x [HABITS]; slip is [DAYS]. Returns [HABITS]. */
    fun slipLift(miss: DoubleArray, kept: DoubleArray, slip: DoubleArray): DoubleArray

    companion object {
        const val HABITS = 40
        const val DAYS = 366
        const val PERIODS = 53

        /** Keel's constants. */
        val DECAY_DAY = exp(-0.08)
        val DECAY_GRACE = exp(-0.015)
        val DELTA = doubleArrayOf(0.0, 0.08, 0.18, 0.28)

        /** Matches the 0.000001 the Lume source adds before dividing. */
        const val EPSILON = 0.000001
    }
}

/** The same computations in Kotlin, used where the Lume library is not available and to check it. */
object KotlinCore : MathCore {
    private const val H = MathCore.HABITS
    private const val D = MathCore.DAYS
    private const val P = MathCore.PERIODS
    private const val EPS = MathCore.EPSILON

    override fun momentumStep(m: DoubleArray, decay: DoubleArray, delta: DoubleArray, sched: DoubleArray): DoubleArray =
        DoubleArray(H) { h ->
            val grown = m[h] * decay[h] + delta[h]
            val capped = 1.0 - max(0.0, 1.0 - grown)
            sched[h] * capped + (1.0 - sched[h]) * m[h]
        }

    override fun periodRates(group: DoubleArray, done: DoubleArray, sched: DoubleArray): DoubleArray {
        val out = DoubleArray(P * H)
        for (p in 0 until P) {
            for (h in 0 until H) {
                var hits = 0.0
                var due = 0.0
                for (d in 0 until D) {
                    val g = group[p * D + d]
                    if (g != 0.0) {
                        hits += g * done[d * H + h]
                        due += g * sched[d * H + h]
                    }
                }
                out[p * H + h] = hits / (due + EPS)
            }
        }
        return out
    }

    override fun slipLift(miss: DoubleArray, kept: DoubleArray, slip: DoubleArray): DoubleArray =
        DoubleArray(H) { h ->
            var slipMissed = 0.0
            var nMissed = 0.0
            var slipKept = 0.0
            var nKept = 0.0
            for (d in 0 until D) {
                slipMissed += miss[d * H + h] * slip[d]
                nMissed += miss[d * H + h]
                slipKept += kept[d * H + h] * slip[d]
                nKept += kept[d * H + h]
            }
            slipMissed / (nMissed + EPS) - slipKept / (nKept + EPS)
        }
}
