package app.kasane

import android.util.Log
import app.kasane.core.KotlinCore
import app.kasane.core.MathCore

/**
 * The compiled Lume core (lume/core.lm), loaded from liblumecore.so.
 * Each native call copies the arrays in, runs inside a Lume arena and copies
 * the result out, so nothing Lume allocates outlives the call.
 */
object LumeCore : MathCore {
    private const val TAG = "LumeCore"

    /** True when liblumecore.so loaded and agreed with Kotlin on a known case. */
    val available: Boolean = runCatching {
        System.loadLibrary("lumecore")
        selfCheck()
    }.onFailure { Log.i(TAG, "Lume core not loaded, using Kotlin: ${it.message}") }.getOrDefault(false)

    /** The core in use: compiled Lume when available, Kotlin otherwise. */
    val active: MathCore get() = if (available) this else KotlinCore

    private external fun nativeMomentumStep(m: DoubleArray, decay: DoubleArray, delta: DoubleArray, sched: DoubleArray): DoubleArray
    private external fun nativePeriodRates(group: DoubleArray, done: DoubleArray, sched: DoubleArray): DoubleArray
    private external fun nativeSlipLift(miss: DoubleArray, kept: DoubleArray, slip: DoubleArray): DoubleArray

    override fun momentumStep(m: DoubleArray, decay: DoubleArray, delta: DoubleArray, sched: DoubleArray) =
        nativeMomentumStep(m, decay, delta, sched)

    override fun periodRates(group: DoubleArray, done: DoubleArray, sched: DoubleArray) =
        nativePeriodRates(group, done, sched)

    override fun slipLift(miss: DoubleArray, kept: DoubleArray, slip: DoubleArray) =
        nativeSlipLift(miss, kept, slip)

    private fun selfCheck(): Boolean {
        val h = MathCore.HABITS
        val m = DoubleArray(h) { 0.5 }
        val decay = DoubleArray(h) { MathCore.DECAY_DAY }
        val delta = DoubleArray(h) { if (it % 2 == 0) 0.18 else 0.0 }
        val sched = DoubleArray(h) { 1.0 }
        val lume = nativeMomentumStep(m, decay, delta, sched)
        val kotlin = KotlinCore.momentumStep(m, decay, delta, sched)
        return lume.indices.all { close(lume[it], kotlin[it]) }
    }

    private fun close(a: Double, b: Double) = kotlin.math.abs(a - b) < 1e-9
}
