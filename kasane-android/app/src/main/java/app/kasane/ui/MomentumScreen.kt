package app.kasane.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.kasane.designsystem.GroupedList
import app.kasane.ipc.EcoMetrics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kasane.R
import kotlin.math.roundToInt

@Composable
fun MomentumScreen(modifier: Modifier = Modifier) {
    val insights by rememberInsights()
    val data = insights
    LazyColumn(modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
                Text(stringResource(R.string.tab_momentum), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.momentum_explain), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                if (data != null) {
                    val overall = data.momentum.map { it.value }.average().takeIf { !it.isNaN() } ?: 0.0
                    Text(stringResource(R.string.momentum_overall, (overall * 100).roundToInt()),
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                    Text(stringResource(if (data.engineIsLume) R.string.engine_lume else R.string.engine_kotlin),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { EcoSection() }
        // Weakest first: that is where today's effort pays most.
        items(data?.momentum?.sortedBy { it.value } ?: emptyList(), key = { it.item.id }) { m ->
 androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItem()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(Modifier.width(120.dp).height(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(m.value.toFloat().coerceIn(0f, 1f))
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                }
                Text("${(m.value * 100).roundToInt()}", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(36.dp).padding(start = 8.dp))
            }
        }
}
    }
}

/** The latest summaries Vow, Ren and Katachi sent over the on-device bus. Shows nothing until one arrives. */
@Composable
private fun EcoSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lines = remember {
        listOfNotNull(
            EcoMetrics.read(context, "vow")?.let {
                context.getString(R.string.eco_vow, it.optInt("reclaimedMinutes"), it.optInt("shortVideoBlockAttempts"))
            },
            EcoMetrics.read(context, "ren")?.let {
                context.getString(R.string.eco_ren, (it.optDouble("recallAccuracy") * 100).roundToInt().coerceAtMost(100),
                    it.optInt("meanLatencyMs"), it.optInt("dueCount"))
            },
            EcoMetrics.read(context, "katachi")?.let {
                context.getString(R.string.eco_katachi, it.optInt("solved"), it.optInt("target"))
            },
        )
    }
    if (lines.isEmpty()) return
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.eco_title).uppercase(), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        GroupedList(lines, Modifier.padding(top = 6.dp).padding(horizontal = 0.dp)) {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
}
