package app.kasane.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kasane.designsystem.Lume
import app.kasane.designsystem.LumeMotion

/** A rounded surface that holds one group of statistics. */
@Composable
fun StatCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().background(Lume.colors.grouped, RoundedCornerShape(16.dp)).padding(16.dp)) { content() }
}

/** One big number with a small label under it. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, tint: Color = Lume.colors.text) {
    Column(modifier.background(Lume.colors.grouped, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = tint, maxLines = 1)
        Text(label, fontSize = 12.sp, color = Lume.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
    }
}

/** A ring that fills to [fraction] with the Lume effects spring, with [label] in the middle. */
@Composable
fun StatRing(fraction: Float, label: String, modifier: Modifier = Modifier, tint: Color = Lume.colors.accent) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), LumeMotion.effects(), label = "ring")
    val track = Lume.colors.hairline
    Box(modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(120.dp)) {
            val w = 12.dp.toPx()
            val inset = w / 2
            val arc = Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            drawArc(tint, -90f, 360f * shown, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(label, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Lume.colors.text)
    }
}

/** A horizontal bar for a 0..1 [fraction], filled with the effects spring. */
@Composable
fun StatBar(fraction: Float, modifier: Modifier = Modifier, tint: Color = Lume.colors.accent) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), LumeMotion.effects(), label = "bar")
    Box(modifier.height(8.dp).background(Lume.colors.hairline, RoundedCornerShape(4.dp))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(tint, RoundedCornerShape(4.dp)))
    }
}

/** Vertical columns, one per value in 0..1, with a small caption under each. */
@Composable
fun StatColumns(values: List<Float>, captions: List<String>, modifier: Modifier = Modifier, tint: Color = Lume.colors.accent) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val shown by animateFloatAsState(v.coerceIn(0f, 1f), LumeMotion.spatial(), label = "col$i")
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(80.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().height((80f * shown).coerceAtLeast(4f).dp)
                        .background(tint.copy(alpha = 0.35f + 0.65f * shown), RoundedCornerShape(6.dp)))
                }
                Text(captions.getOrElse(i) { "" }, fontSize = 10.sp, color = Lume.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
