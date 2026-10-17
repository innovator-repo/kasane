package app.kasane.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasane.R
import app.kasane.core.Block
import app.kasane.core.Entry
import app.kasane.core.Item
import app.kasane.core.Kind
import app.kasane.core.Tier
import app.kasane.store
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = context.store
    val journal by store.journal.collectAsStateWithLifecycle()
    val today = remember(journal) { LocalDate.now() }
    val due = journal.items.filter { it.dueOn(today) }
    val counted = due.count { Tier.of(it, journal.entry(today, it.id)) >= Tier.TARGET }
    val order = blockOrder(LocalTime.now())

    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Item?>(null) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
            item {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
                    Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.today_progress, counted, due.size),
                        style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.habit_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { SetupCard() }
            for (block in order) {
                val items = due.filter { it.block == block }
                if (items.isEmpty()) continue
                item(key = "h-$block") { SectionLabel(blockLabel(block)) }
                items(items, key = { it.id }) { item ->
                    ItemRow(item, journal.entry(today, item.id), onRemove = { removing = item }) {
                        store.setEntry(today, item.id, it)
                    }
                }
            }
        }
        FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.habit_add))
        }
    }
    if (adding) AddHabitDialog(onDismiss = { adding = false }) { item ->
        store.addItem(item)
        adding = false
    }
    removing?.let { item ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(item.title) },
            text = { Text(stringResource(R.string.habit_remove_explain)) },
            confirmButton = {
                TextButton(onClick = { store.pauseItem(item.id); removing = null }) { Text(stringResource(R.string.habit_remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The block for the time of day comes first; the rest follow in order. */
internal fun blockOrder(now: LocalTime): List<Block> {
    val current = when {
        now.isBefore(LocalTime.NOON) -> Block.MORNING
        now.isBefore(LocalTime.of(20, 0)) -> Block.DAY
        else -> Block.NIGHT
    }
    return listOf(current) + listOf(Block.MORNING, Block.DAY, Block.NIGHT).filter { it != current } + Block.ALL_DAY
}

@Composable
private fun blockLabel(block: Block): String = stringResource(
    when (block) {
        Block.MORNING -> R.string.block_morning
        Block.DAY -> R.string.block_day
        Block.NIGHT -> R.string.block_night
        Block.ALL_DAY -> R.string.block_rules
    },
)

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemRow(item: Item, entry: Entry, onRemove: () -> Unit, onChange: (Entry) -> Unit) {
    val context = LocalContext.current
    val counted = Tier.of(item, entry) >= Tier.TARGET
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            // Long press takes an item off Today.
            .combinedClickable(onLongClick = onRemove) { if (item.kind == Kind.CHECK) onChange(entry.copy(done = !entry.done)) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (counted && item.kind != Kind.RULE) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface)
            when (item.kind) {
                Kind.COUNT -> Detail(stringResource(R.string.count_of, entry.count, item.target))
                Kind.TIMED -> Detail(stringResource(R.string.minutes_of, entry.minutes, item.target))
                Kind.RULE -> Detail(stringResource(if (entry.slipped) R.string.rule_slipped else R.string.rule_kept),
                    if (entry.slipped) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Kind.CHECK -> {}
            }
        }
        item.opens?.let { target ->
            TextButton(onClick = { open(context, target) }) { Text(stringResource(R.string.open)) }
        }
        when (item.kind) {
            Kind.CHECK -> Checkbox(checked = entry.done, onCheckedChange = { onChange(entry.copy(done = it)) })
            Kind.COUNT -> Stepper(
                minus = { onChange(entry.copy(count = (entry.count - 1).coerceAtLeast(0))) },
                plus = { onChange(entry.copy(count = entry.count + 1)) },
                big = "+10" to { onChange(entry.copy(count = entry.count + 10)) },
            )
            Kind.TIMED -> Stepper(
                minus = { onChange(entry.copy(minutes = (entry.minutes - 5).coerceAtLeast(0))) },
                plus = { onChange(entry.copy(minutes = entry.minutes + 5)) },
                plusLabel = "+5",
            )
            Kind.RULE -> TextButton(onClick = { onChange(entry.copy(slipped = !entry.slipped)) }) {
                Text(stringResource(if (entry.slipped) R.string.rule_undo else R.string.rule_mark_slip))
            }
        }
    }
}

@Composable
private fun Detail(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun Stepper(minus: () -> Unit, plus: () -> Unit, plusLabel: String = "+1", big: Pair<String, () -> Unit>? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = minus) { Text("−") }
        FilledTonalButton(onClick = plus) { Text(plusLabel) }
        if (big != null) {
            Spacer(Modifier.width(2.dp))
            FilledTonalButton(onClick = big.second) { Text(big.first) }
        }
    }
}

/** Opens an app by package, or a site in Chrome. Missing apps open their Play Store page. */
private fun open(context: Context, target: String) {
    val intent = if (target.startsWith("http")) {
        Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
            if (context.packageManager.getLaunchIntentForPackage("com.android.chrome") != null) setPackage("com.android.chrome")
        }
    } else {
        context.packageManager.getLaunchIntentForPackage(target)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$target"))
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}
