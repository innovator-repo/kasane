package app.kasane.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberLumeHaptics(): LumeHaptics = LocalLumeHaptics.current ?: LumeHaptics.get(LocalContext.current)

/** A round tick box. Fills with the target colour and plays CHECK_COMPLETE when ticked on. */
@Composable
fun LumeCheck(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, color: Color = Lume.colors.target) {
    val haptics = rememberLumeHaptics()
    val fill by animateColorAsState(if (checked) color else Color.Transparent, LumeMotion.effects(), label = "check")
    val c = Lume.colors
    Box(
        modifier.sizeIn(minWidth = LumeTokens.Control.MinTouch, minHeight = LumeTokens.Control.MinTouch)
            .clickable(role = Role.Checkbox, indication = null,
                interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                if (!checked) haptics.play(HapticToken.CHECK_COMPLETE) else haptics.tick()
                onChange(!checked)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(fill)
            .border(1.5.dp, if (checked) color else c.textSecondary.copy(alpha = 0.5f), CircleShape))
        if (checked) Canvas(Modifier.size(24.dp)) {
            val p = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.68f)
                lineTo(size.width * 0.74f, size.height * 0.34f)
            }
            drawPath(p, Color.White, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** A small pill button. [primary] fills with the accent; otherwise it sits on a soft tint. */
@Composable
fun LumeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    tint: Color? = null,
    haptic: HapticToken = HapticToken.CLICK,
) {
    val c = Lume.colors
    val haptics = rememberLumeHaptics()
    val bg = when {
        tint != null -> tint.copy(alpha = 0.16f)
        primary -> c.accent
        else -> c.accent.copy(alpha = 0.12f)
    }
    val fg = when {
        tint != null -> tint
        primary -> c.onAccent
        else -> c.accent
    }
    Box(
        modifier.pressable().sizeIn(minHeight = LumeTokens.Control.ButtonHeight, minWidth = LumeTokens.Control.MinTouch).clip(RoundedCornerShape(LumeTokens.Shape.L)).background(bg)
            .clickable(role = Role.Button) { haptics.play(haptic); onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** A selectable pill, in place of the stock filter chip. */
@Composable
fun LumeChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color? = null) {
    val c = Lume.colors
    val base = tint ?: c.accent
    val bg by animateColorAsState(if (selected) base.copy(alpha = 0.18f) else c.grouped, LumeMotion.effects(), label = "chip")
    Box(
        modifier.pressable().sizeIn(minHeight = LumeTokens.Control.ChipHeight).clip(RoundedCornerShape(LumeTokens.Shape.M)).background(bg)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) base else c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/**
 * Three concentric arcs for a count or a timer: the outer arc is stretch (150% of target, ochre),
 * the middle is the target (sage), the inner is the floor (20%, slate blue). They fill with an
 * effects spring, so there is no overshoot.
 */
@Composable
fun TierRings(value: Int, target: Int, floor: Int, size: Dp = 72.dp, modifier: Modifier = Modifier) {
    val c = Lume.colors
    fun frac(v: Int, of: Float) = if (of <= 0f) 0f else (v / of).coerceIn(0f, 1f)
    val stretch by animateFloatAsState(frac(value, target * 1.5f), LumeMotion.effects(), label = "stretch")
    val tgt by animateFloatAsState(frac(value, target.toFloat()), LumeMotion.effects(), label = "target")
    val flo by animateFloatAsState(frac(value, floor.toFloat()), LumeMotion.effects(), label = "floor")
    Canvas(modifier.size(size)) {
        val stroke = size.toPx() * 0.075f
        val gap = stroke * 1.5f
        fun ring(inset: Float, fraction: Float, color: Color) {
            val d = this.size.minDimension - inset * 2
            val topLeft = Offset(inset, inset)
            val arc = Size(d, d)
            drawArc(color.copy(alpha = 0.16f), -90f, 360f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            if (fraction > 0f) drawArc(color, -90f, 360f * fraction, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        ring(stroke / 2, stretch, c.stretch)
        ring(stroke / 2 + gap, tgt, c.target)
        ring(stroke / 2 + gap * 2, flo, c.floor)
    }
}

/** The same three stages as a flat pill: floor, then target, then stretch, filling left to right. */
@Composable
fun TierPill(value: Int, target: Int, floor: Int, modifier: Modifier = Modifier) {
    val c = Lume.colors
    if (target <= 0) return
    val total = target * 1.5f
    fun seg(from: Float, to: Float) = ((value - from) / (to - from)).coerceIn(0f, 1f)
    val a by animateFloatAsState(seg(0f, floor.toFloat()), LumeMotion.effects(), label = "a")
    val b by animateFloatAsState(seg(floor.toFloat(), target.toFloat()), LumeMotion.effects(), label = "b")
    val d by animateFloatAsState(seg(target.toFloat(), total), LumeMotion.effects(), label = "d")
    Row(modifier.fillMaxWidth().height(5.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
        Segment(floor / total, a, c.floor)
        Segment((target - floor) / total, b, c.target)
        Segment((total - target) / total, d, c.stretch)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Segment(weight: Float, fill: Float, color: Color) {
    Box(Modifier.weight(weight.coerceAtLeast(0.01f)).fillMaxHeight().clip(RoundedCornerShape(LumeTokens.Shape.XXS)).background(color.copy(alpha = 0.16f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(color))
    }
}
