package app.kasane.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/** Lume motion tokens. Springs for movement and scale, critically damped springs for colour and progress. */
object LumeMotion {
    const val PRESSED_SCALE = LumeTokens.Motion.PRESSED_SCALE
    const val BREATH_MS = LumeTokens.Motion.BREATH_MS

    /** Movement, scale, sheets and card flips: a little overshoot. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = LumeTokens.Motion.SPATIAL_DAMPING, stiffness = LumeTokens.Motion.SPATIAL_STIFFNESS)

    /** Colour, progress fills and opacity: no overshoot, so nothing flickers. */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = LumeTokens.Motion.EFFECTS_DAMPING, stiffness = LumeTokens.Motion.EFFECTS_STIFFNESS)

    /** EaseInOutSine, for the breathing pace. */
    val EaseInOutSine: Easing = Easing { x -> (-(Math.cos(Math.PI * x) - 1) / 2).toFloat() }
}

/**
 * Scales to 0.97 on touch with a TICK and springs back with a little overshoot.
 * Observes the pointer without consuming it, so clickable and scrolling still work.
 */
fun Modifier.pressable(): Modifier = composed {
    val haptics = LocalLumeHaptics.current ?: LumeHaptics.get(LocalContext.current)
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                haptics.tick()
                scope.launch { scale.animateTo(LumeMotion.PRESSED_SCALE, LumeMotion.effects()) }
                waitForUpOrCancellation(PointerEventPass.Initial)
                scope.launch { scale.animateTo(1f, LumeMotion.spatial()) }
            }
        }
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
