package app.kasane.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasane.R
import app.kasane.core.Block
import app.kasane.core.Item
import app.kasane.core.Kind
import app.kasane.core.NowPick
import app.kasane.core.Phase
import app.kasane.designsystem.GroupedList
import app.kasane.designsystem.Lume
import app.kasane.designsystem.LumeChip
import app.kasane.store
import java.time.LocalDate
import java.time.LocalTime

/** Which kind of item the chips on Today show. */
private enum class Filter(val label: Int) {
    ALL(R.string.filter_all), HABITS(R.string.add_type_habit), TASKS(R.string.today_tasks), RULES(R.string.add_type_rule)
}

@Composable
fun TodayScreen(modifier: Modifier = Modifier, onAdd: () -> Unit = {}) {
    val context = LocalContext.current
    val store = context.store
    val journal by store.journal.collectAsStateWithLifecycle()
    val floorFlag by store.floorMode.collectAsStateWithLifecycle()
    val floor = remember(floorFlag, journal) { store.floorToday() }
    val today = remember(journal) { LocalDate.now() }
    val tomorrow = today.plusDays(1)
    val now = remember(journal) { LocalTime.now() }
    val phase = Phase.of(now)
    val insights by rememberInsights()
    val momentum = remember(insights) { insights?.momentum?.associate { it.item.id to it.value } ?: emptyMap() }

    val due = journal.items.filter { it.dueOn(today) }
    val counted = due.count { it.finishedFor(journal.entry(today, it.id), floor) }

    var editing by remember { mutableStateOf<Item?>(null) }
    var removing by remember { mutableStateOf<Item?>(null) }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var showCompleted by remember { mutableStateOf(false) }
    // Only the block the clock is in starts open; the others wait behind a one-line pill.
    var opened by remember { mutableStateOf(setOf(phase.block)) }
    fun toggle(block: Block) { opened = if (block in opened) opened - block else opened + block }

    // Finished items leave Today at once; "Show completed" brings them back to undo.
    val pending = due.filter { !it.finishedFor(journal.entry(today, it.id), floor) }
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

    val nowItem = NowPick.next(journal, today, now, momentum, floor)
    val rules = due.filter { it.kind == Kind.RULE }
    val rulesKept = rules.count { it.finishedFor(journal.entry(today, it.id)) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item(key = "now") {
                NowRitualCard(nowItem, nowItem?.let { journal.entry(today, it.id) } ?: app.kasane.core.Entry(), counted, due.size,
                    today, floor, blockLabel(phase.block)) { changed -> nowItem?.let { store.setEntry(today, it.id, changed) } }
            }
            item(key = "chips") {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    LumeChip(stringResource(if (floor) R.string.floor_chip_on else R.string.floor_chip), floor,
                        { store.setFloorMode(!floor) }, tint = Lume.colors.floor)
                }
            }
            item(key = "setup") { SetupCard() }
            item(key = "filters") {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Filter.entries.forEach { LumeChip(stringResource(it.label), filter == it, { filter = it }) }
                }
            }
            if (journal.items.isEmpty() && journal.tasks.isEmpty()) {
                item { EmptyNote(R.string.today_nothing, R.string.today_nothing_hint) }
            } else if (pending.isEmpty() && tasksToday.isEmpty() && due.isNotEmpty() && filter == Filter.ALL) {
                item { EmptyNote(R.string.today_all_done, R.string.habit_hint) }
            }
            if (showTasks && tasksToday.isNotEmpty()) {
                item(key = "h-tasks") { SectionLabel(stringResource(R.string.today_tasks)) }
                item(key = "tasks") {
                    GroupedList(tasksToday) { task -> TaskRow(task) { store.setTaskDone(task.id, true) } }
                }
            }
            for (block in blockOrder(now).filter { it != Block.ALL_DAY }) {
                val items = pending.filter { it.block == block && visible(it) }
                if (items.isEmpty()) continue
                if (block == phase.block) {
                    item(key = "h-$block") { SectionLabel(blockLabel(block)) }
                    item(key = "b-$block") { HabitGroup(items, today, floor, { editing = it }) }
                } else item(key = "b-$block") {
                    DrawerPill(stringResource(R.string.drawer_summary, blockLabel(block),
                        due.count { it.block == block && it.id !in items.map { p -> p.id } }, due.count { it.block == block }),
                        block in opened, { toggle(block) }) { HabitGroup(items, today, floor, { editing = it }) }
                }
            }
            if (showRules && rules.isNotEmpty()) {
                item(key = "b-rules") {
                    DrawerPill(stringResource(R.string.drawer_rules_summary, rulesKept, rules.size),
                        Block.ALL_DAY in opened, { toggle(Block.ALL_DAY) }) { HabitGroup(rules, today, floor, { editing = it }) }
                }
            }
            val doneShown = finished.filter { visible(it) && it.kind != Kind.RULE }
            if (doneShown.isNotEmpty()) {
                item(key = "toggle-completed") {
                    TextButton(onClick = { showCompleted = !showCompleted }, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
                        Text(if (showCompleted) stringResource(R.string.today_hide_completed)
                        else stringResource(R.string.today_show_completed, doneShown.size))
                    }
                }
                if (showCompleted) item(key = "completed") { HabitGroup(doneShown, today, floor, { editing = it }) }
            }
            val nextItems = tomorrowItems.filter { visible(it) }
            val nextTasks = if (showTasks) tomorrowTasks else emptyList()
            if (nextItems.isNotEmpty() || nextTasks.isNotEmpty()) {
                item(key = "h-tomorrow") { SectionLabel(stringResource(R.string.today_tomorrow)) }
                item(key = "tomorrow") {
                    GroupedList(nextTasks.map { Upcoming(it.title, R.string.add_badge_task, it.remindAt) } +
                        nextItems.map { Upcoming(it.title, if (it.kind == Kind.RULE) R.string.add_badge_rule else R.string.add_badge_habit, it.remindAt) }) {
                        UpcomingRow(it.title, it.badge, it.remindAt)
                    }
                }
            }
        }
        FloatingActionButton(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = Lume.colors.accent, contentColor = Lume.colors.onAccent) {
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

private data class Upcoming(val title: String, val badge: Int, val remindAt: Int?)

@Composable
private fun HabitGroup(items: List<Item>, today: LocalDate, floor: Boolean, onEdit: (Item) -> Unit) {
    val store = LocalContext.current.store
    val journal by store.journal.collectAsStateWithLifecycle()
    GroupedList(items) { item ->
        HabitRow(item, journal.entry(today, item.id), floor, { onEdit(item) }) { store.setEntry(today, item.id, it) }
    }
}

@Composable
private fun EmptyNote(title: Int, hint: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), color = Lume.colors.text, fontSize = 16.sp)
        Text(stringResource(hint), color = Lume.colors.textSecondary, fontSize = 13.sp)
    }
}

/** The block for the time of day comes first; the rest follow in order. */
internal fun blockOrder(now: LocalTime): List<Block> {
    val current = Phase.of(now).block
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
    Text(text.uppercase(), fontSize = 12.sp, color = Lume.colors.textSecondary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp))
}

/** Opens an app by package, or a site in Chrome. Missing apps open their Play Store page. */
internal fun open(context: Context, target: String) {
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
