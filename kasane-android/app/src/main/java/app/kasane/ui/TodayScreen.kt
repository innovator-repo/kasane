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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ExperimentalMaterial3Api
import app.kasane.core.Task
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

/** Which kind of item the chips on Today show. */
private enum class Filter(val label: Int) {
    ALL(R.string.filter_all), HABITS(R.string.add_type_habit), TASKS(R.string.today_tasks), RULES(R.string.add_type_rule)
}

@Composable
fun TodayScreen(modifier: Modifier = Modifier, onAdd: () -> Unit = {}) {
    val context = LocalContext.current
    val store = context.store
    val journal by store.journal.collectAsStateWithLifecycle()
    val today = remember(journal) { LocalDate.now() }
    val tomorrow = today.plusDays(1)
    val due = journal.items.filter { it.dueOn(today) }
    val counted = due.count { it.finishedFor(journal.entry(today, it.id)) }
    val order = blockOrder(LocalTime.now())

    var editing by remember { mutableStateOf<Item?>(null) }
    var removing by remember { mutableStateOf<Item?>(null) }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var showCompleted by remember { mutableStateOf(false) }

    // Finished items leave Today at once; "Show completed" brings them back to undo.
    val pending = due.filter { !it.finishedFor(journal.entry(today, it.id)) }
    val finished = due - pending.toSet()
    val tasksToday = journal.tasks.filter { it.dueOn(today) && !it.doneOn(today) }
    val tomorrowItems = journal.items.filter { it.dueOn(tomorrow) }
    val tomorrowTasks = journal.tasks.filter {
        if (it.repeats) it.dueOn(tomorrow) else it.open && it.date == tomorrow.toString()
    }
    val showHabits = filter == Filter.ALL || filter == Filter.HABITS
    val showRules = filter == Filter.ALL || filter == Filter.RULES
    val showTasks = filter == Filter.ALL || filter == Filter.TASKS
    fun visible(item: Item) = if (item.kind == Kind.RULE) showRules else showHabits

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
            item { ProgressCard(today, counted, due.size, tasksToday.size) }
            item { SetupCard() }
            item { FilterChips(filter) { filter = it } }
            if (journal.items.isEmpty() && journal.tasks.isEmpty()) {
                item { EmptyNote(R.string.today_nothing, R.string.today_nothing_hint) }
            } else if (pending.isEmpty() && tasksToday.isEmpty() && due.isNotEmpty() && filter == Filter.ALL) {
                item { EmptyNote(R.string.today_all_done, R.string.habit_hint) }
            }
            if (showTasks && tasksToday.isNotEmpty()) {
                item(key = "h-tasks") { SectionLabel(stringResource(R.string.today_tasks)) }
                items(tasksToday, key = { "t-" + it.id }) { task ->
                    TaskRow(task) { store.setTaskDone(task.id, true) }
                }
            }
            for (block in order) {
                val items = pending.filter { it.block == block && visible(it) }
                if (items.isEmpty()) continue
                item(key = "h-$block") { SectionLabel(blockLabel(block)) }
                items(items, key = { it.id }) { item ->
                    ItemRow(item, journal.entry(today, item.id), onRemove = { editing = item }) {
                        store.setEntry(today, item.id, it)
                    }
                }
            }
            val doneShown = finished.filter { visible(it) }
            if (doneShown.isNotEmpty()) {
                item(key = "toggle-completed") {
                    TextButton(onClick = { showCompleted = !showCompleted }, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
                        Text(if (showCompleted) stringResource(R.string.today_hide_completed)
                        else stringResource(R.string.today_show_completed, doneShown.size))
                    }
                }
                if (showCompleted) items(doneShown, key = { "d-" + it.id }) { item ->
                    ItemRow(item, journal.entry(today, item.id), onRemove = { editing = item }) {
                        store.setEntry(today, item.id, it)
                    }
                }
            }
            val nextItems = tomorrowItems.filter { visible(it) }
            val nextTasks = if (showTasks) tomorrowTasks else emptyList()
            if (nextItems.isNotEmpty() || nextTasks.isNotEmpty()) {
                item(key = "h-tomorrow") { SectionLabel(stringResource(R.string.today_tomorrow)) }
                items(nextTasks, key = { "nt-" + it.id }) { UpcomingRow(it.title, R.string.add_badge_task, it.remindAt) }
                items(nextItems, key = { "ni-" + it.id }) { item ->
                    UpcomingRow(item.title, if (item.kind == Kind.RULE) R.string.add_badge_rule else R.string.add_badge_habit, item.remindAt)
                }
            }
        }
        FloatingActionButton(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.habit_add))
        }
    }
    editing?.let { item ->
        AddHabitDialog(onDismiss = { editing = null }, existing = item,
            onRemove = { editing = null; removing = item }) { changed ->
            store.updateItem(changed)
            editing = null
        }
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

@Composable
private fun ProgressCard(today: LocalDate, counted: Int, total: Int, openTasks: Int) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            Text(stringResource(R.string.today_progress, counted, total), style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            LinearProgressIndicator(
                progress = if (total == 0) 0f else counted.toFloat() / total,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
            )
            if (openTasks > 0) {
                Text(stringResource(if (openTasks == 1) R.string.today_task_open else R.string.today_tasks_open, openTasks),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChips(selected: Filter, onSelect: (Filter) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Filter.entries.forEach {
            FilterChip(selected = selected == it, onClick = { onSelect(it) }, label = { Text(stringResource(it.label)) })
        }
    }
}

@Composable
private fun EmptyNote(title: Int, hint: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TaskRow(task: Task, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = false, onCheckedChange = { onDone() })
        Column(Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            task.remindAt?.let { Detail("%02d:%02d".format(it / 60, it % 60)) }
        }
        KindBadge(stringResource(R.string.add_badge_task))
    }
}

@Composable
private fun UpcomingRow(title: String, badge: Int, remindAt: Int?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            remindAt?.let { Detail("%02d:%02d".format(it / 60, it % 60)) }
        }
        KindBadge(stringResource(badge))
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
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            // Long press takes an item off Today.
            .combinedClickable(onLongClick = onRemove) { if (item.kind == Kind.CHECK) onChange(entry.copy(done = !entry.done)) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
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
            Kind.RULE -> Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.done && !entry.slipped) {
                    TextButton(onClick = { onChange(entry.copy(done = false)) }) { Text(stringResource(R.string.rule_undo)) }
                } else {
                    if (!entry.slipped) FilledTonalButton(onClick = { onChange(entry.copy(done = true)) }) {
                        Text(stringResource(R.string.rule_mark_kept))
                    }
                    TextButton(onClick = { onChange(entry.copy(slipped = !entry.slipped, done = false)) }) {
                        Text(stringResource(if (entry.slipped) R.string.rule_undo else R.string.rule_mark_slip))
                    }
                }
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
