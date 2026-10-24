@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.kasane.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kasane.R
import app.kasane.designsystem.Lume
import app.kasane.core.SlipLink
import kotlin.math.roundToInt

@Composable
fun WeekScreen(modifier: Modifier = Modifier) {
    val insights by rememberInsights()
    val data = insights
    // Completion across all habits per week, oldest first. Lists are aligned on the latest week.
    val series: List<Pair<java.time.LocalDate, Float>> = remember(data) {
        val lists = data?.weekly?.values?.filter { it.isNotEmpty() } ?: emptyList()
        val n = lists.maxOfOrNull { it.size } ?: 0
        (0 until n).map { i ->
            val rates = lists.mapNotNull { l -> l.getOrNull(l.size - n + i) }
            val start = rates.firstOrNull()?.start ?: java.time.LocalDate.now()
            start to (rates.map { it.rate }.average().takeIf { !it.isNaN() } ?: 0.0).toFloat()
        }
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
                Text(stringResource(R.string.tab_week), style = MaterialTheme.typography.headlineMedium)
            }
        }
        if (series.isNotEmpty()) {
            val thisWeek = series.last().second
            val lastWeek = series.getOrNull(series.size - 2)?.second
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("${(thisWeek * 100).roundToInt()}%", stringResource(R.string.stat_this_week), Modifier.weight(1f), Lume.colors.accent)
                        val delta = lastWeek?.let { ((thisWeek - it) * 100).roundToInt() }
                        StatTile(delta?.let { (if (it >= 0) "+" else "") + it } ?: "-", stringResource(R.string.stat_vs_last), Modifier.weight(1f),
                            if (delta != null && delta < 0) Lume.colors.slip else Lume.colors.target)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("${(series.maxOf { it.second } * 100).roundToInt()}%", stringResource(R.string.stat_best_week), Modifier.weight(1f))
                        StatTile("${(series.map { it.second }.average() * 100).roundToInt()}%", stringResource(R.string.stat_weekly_avg), Modifier.weight(1f))
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    StatCard {
                        Column {
                            Text(stringResource(R.string.stat_trend), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                            StatColumns(series.map { it.second }, series.map { it.first.dayOfMonth.toString() })
                        }
                    }
                }
            }
        }
        item { SectionLabel(stringResource(R.string.week_before_slip)) }
        item { Links(data?.lead, R.string.week_before_slip_line) }
        item { SectionLabel(stringResource(R.string.week_after_slip)) }
        item { Links(data?.knockOn, R.string.week_after_slip_line) }
        item { SectionLabel(stringResource(R.string.week_last_8)) }
        items(data?.weekly?.entries?.toList() ?: emptyList(), key = { it.key.id }) { (item, rates) ->
            Box(Modifier.animateItemPlacement().padding(horizontal = 20.dp, vertical = 4.dp)) {
                StatCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            // One square per week, oldest on the left; darker means more days done.
                            for (rate in rates) {
                                val alpha = 0.12f + 0.88f * rate.rate.toFloat().coerceIn(0f, 1f)
                                Box(Modifier.size(14.dp).background(Lume.colors.accent.copy(alpha = alpha), RoundedCornerShape(3.dp)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Links(links: List<SlipLink>?, line: Int) {
    val text = when {
        links == null -> stringResource(R.string.loading)
        links.isEmpty() -> stringResource(R.string.week_not_enough)
        else -> null
    }
    Column(Modifier.padding(horizontal = 20.dp)) {
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            for (link in links.orEmpty()) {
                Text(stringResource(line, link.item.title, (link.lift * 100).roundToInt()),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}
