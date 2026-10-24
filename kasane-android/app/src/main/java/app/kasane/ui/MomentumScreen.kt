@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.kasane.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import app.kasane.designsystem.Lume
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
    val sorted = data?.momentum?.sortedBy { it.value } ?: emptyList()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
                Text(stringResource(R.string.tab_momentum), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.momentum_explain), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (data != null && sorted.isNotEmpty()) {
            val overall = sorted.map { it.value }.average()
            item {
                StatCard(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        StatRing(overall.toFloat(), "${(overall * 100).roundToInt()}")
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.momentum_overall, (overall * 100).roundToInt()),
                                style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(if (data.engineIsLume) R.string.engine_lume else R.string.engine_kotlin),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            item {
                val best = sorted.last()
                val worst = sorted.first()
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("${(best.value * 100).roundToInt()}", stringResource(R.string.stat_strongest) + ": " + best.item.title,
                            Modifier.weight(1f), Lume.colors.target)
                        StatTile("${(worst.value * 100).roundToInt()}", stringResource(R.string.stat_weakest) + ": " + worst.item.title,
                            Modifier.weight(1f), Lume.colors.slip)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("${sorted.size}", stringResource(R.string.stat_active), Modifier.weight(1f))
                        StatTile("${sorted.count { it.value >= 0.7 }}", stringResource(R.string.stat_steady), Modifier.weight(1f), Lume.colors.accent)
                    }
                }
            }
        }
        item { EcoSection() }
        item { SectionLabel(stringResource(R.string.stat_by_habit)) }
        // Weakest first: that is where today's effort pays most.
        items(sorted, key = { it.item.id }) { m ->
            Box(Modifier.animateItemPlacement().padding(horizontal = 20.dp, vertical = 4.dp)) {
                StatCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${(m.value * 100).roundToInt()}", style = MaterialTheme.typography.titleMedium)
                        }
                        StatBar(m.value.toFloat(), Modifier.fillMaxWidth().padding(top = 8.dp),
                            if (m.value >= 0.7) Lume.colors.target else if (m.value >= 0.4) Lume.colors.accent else Lume.colors.slip)
                    }
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
