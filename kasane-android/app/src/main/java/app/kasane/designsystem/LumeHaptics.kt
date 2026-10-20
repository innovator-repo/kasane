package app.kasane.designsystem

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf

/** The Lume haptic vocabulary, shared by every app in the set. */
enum class HapticToken { TICK, CLICK, CHECK_COMPLETE, SEAL_LOCK, REVEAL_SWELL, BREATH_WAVE, GENTLE_NUDGE }

/**
 * Plays [HapticToken]s as composed vibration primitives where the actuator has them
 * (Android 11+), and as a predefined effect or a short waveform where it does not.
 */
class LumeHaptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.applicationContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val cache = HashMap<HapticToken, VibrationEffect?>()

    /** Switch off to silence every token, for example from a settings toggle. */
    @Volatile var enabled: Boolean = true

    fun play(token: HapticToken) {
        val v = vibrator ?: return
        if (!enabled || !v.hasVibrator()) return
        val effect = cache.getOrPut(token) { build(token, v) } ?: return
        runCatching { v.vibrate(effect) }
    }

    fun tick() = play(HapticToken.TICK)
    fun click() = play(HapticToken.CLICK)
    fun checkComplete() = play(HapticToken.CHECK_COMPLETE)

    private fun build(token: HapticToken, v: Vibrator): VibrationEffect? {
        if (Build.VERSION.SDK_INT >= 30) composed(token, v)?.let { return it }
        return fallback(token)
    }

    // (primitive, scale, delay ms) per token. BREATH_WAVE is a single exhale-sized swell; the timer repeats it.
    private fun steps(token: HapticToken): List<Triple<Int, Float, Int>>? {
        if (Build.VERSION.SDK_INT < 30) return null
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        val slowRise = VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
        val quickRise = VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
        val quickFall = VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
        val lowTick = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_LOW_TICK else tick
        return when (token) {
            HapticToken.TICK -> listOf(Triple(tick, 0.5f, 0))
            HapticToken.CLICK -> listOf(Triple(click, 0.65f, 0))
            HapticToken.CHECK_COMPLETE -> listOf(Triple(click, 0.9f, 0), Triple(tick, 0.35f, 50))
            HapticToken.SEAL_LOCK -> listOf(Triple(quickRise, 0.55f, 0), Triple(click, 1f, 30), Triple(tick, 0.4f, 70))
            HapticToken.REVEAL_SWELL -> listOf(Triple(slowRise, 0.6f, 0), Triple(tick, 0.5f, 20))
            HapticToken.BREATH_WAVE -> listOf(Triple(slowRise, 0.7f, 0), Triple(tick, 0.4f, 600), Triple(quickFall, 0.7f, 600))
            HapticToken.GENTLE_NUDGE -> listOf(Triple(lowTick, 0.75f, 0), Triple(lowTick, 0.75f, 100))
        }
    }

    @androidx.annotation.RequiresApi(30)
    private fun composed(token: HapticToken, v: Vibrator): VibrationEffect? {
        val steps = steps(token) ?: return null
        if (!v.areAllPrimitivesSupported(*steps.map { it.first }.distinct().toIntArray())) return null
        val c = VibrationEffect.startComposition()
        for ((p, scale, delay) in steps) c.addPrimitive(p, scale, delay)
        return c.compose()
    }

    private fun fallback(token: HapticToken): VibrationEffect = when (token) {
        HapticToken.TICK -> predefinedOr(VibrationEffect.EFFECT_TICK) { VibrationEffect.createOneShot(8, 90) }
        HapticToken.CLICK -> predefinedOr(VibrationEffect.EFFECT_CLICK) { VibrationEffect.createOneShot(14, 160) }
        HapticToken.CHECK_COMPLETE -> VibrationEffect.createWaveform(longArrayOf(0, 16, 50, 8), intArrayOf(0, 220, 0, 90), -1)
        HapticToken.SEAL_LOCK -> VibrationEffect.createWaveform(longArrayOf(0, 10, 30, 22, 40, 8), intArrayOf(0, 140, 0, 255, 0, 100), -1)
        HapticToken.REVEAL_SWELL -> VibrationEffect.createWaveform(longArrayOf(0, 40, 20, 10), intArrayOf(0, 150, 0, 120), -1)
        HapticToken.BREATH_WAVE -> VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 200), intArrayOf(0, 90, 0, 170), -1)
        HapticToken.GENTLE_NUDGE -> VibrationEffect.createWaveform(longArrayOf(0, 16, 100, 16), intArrayOf(0, 190, 0, 190), -1)
    }

    private fun predefinedOr(id: Int, plain: () -> VibrationEffect): VibrationEffect =
        if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(id) else plain()

    companion object {
        @Volatile private var instance: LumeHaptics? = null
        fun get(context: Context): LumeHaptics =
            instance ?: synchronized(this) { instance ?: LumeHaptics(context).also { instance = it } }
    }
}

/** Composables read the shared engine from here; it is a no-op until the theme provides one. */
val LocalLumeHaptics = staticCompositionLocalOf<LumeHaptics?> { null }
