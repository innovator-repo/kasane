package app.kasane.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasane.R
import app.kasane.core.NextUp
import app.kasane.core.Phase
import app.kasane.core.Task
import app.kasane.designsystem.Lume
import app.kasane.designsystem.LumeCheck
import app.kasane.designsystem.LumeChip
import app.kasane.designsystem.LumeTokens
import app.kasane.designsystem.LumeType
import app.kasane.designsystem.pressable
import app.kasane.designsystem.rememberLumeHaptics
import app.kasane.store
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

/**
 * The one screen: a small header, the hero (NEXT UP and its check button), and a drawer that pulls up
 * to the full Overdue / Today / Tomorrow list. The chips open Lists and Add.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowScreen(onLists: () -> Unit, onAdd: () -> Unit, onSettings: () -> Unit, onEdit: (Task) -> Unit) {
    val context = LocalContext.current
    val store = context.store
    val journal by store.journal.collectAsStateWithLifecycle()
    val c = Lume.colors
    val haptics = rememberLumeHaptics()
    val today = remember(journal) { LocalDate.now() }
    val summary = remember(journal) { NextUp.summary(journal.tasks, today) }
    val groups = remember(journal) { NextUp.groups(journal.tasks, today) }
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = true)
    val scaffold = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val scope = rememberCoroutineScope()
    val phaseWord = when (Phase.of(LocalTime.now())) {
        Phase.MORNING -> R.string.now_morning
        Phase.DAY -> R.string.now_day
        Phase.EVENING -> R.string.now_evening
        Phase.SANCTUARY -> R.string.now_night
    }

    BottomSheetScaffold(
        scaffoldState = scaffold,
        sheetPeekHeight = 176.dp,
        containerColor = c.canvas,
        sheetContainerColor = c.grouped,
        sheetShape = RoundedCornerShape(topStart = LumeTokens.Shape.L, topEnd = LumeTokens.Shape.L),
        sheetShadowElevation = 8.dp,
        sheetDragHandle = {
            Box(Modifier.fillMaxWidth().height(24.dp).clickable {
                scope.launch { if (sheetState.currentValue == SheetValue.Expanded) sheetState.partialExpand() else sheetState.expand() }
            }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(c.hairline))
            }
        },
        sheetContent = {
            Drawer(summary.overdueCount, summary.openCount, groups, today, onLists = onLists, onAdd = onAdd, onEdit = onEdit,
                onDone = { store.setTaskDone(it.id, true) })
        },
    ) { _ ->
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(c.accent), contentAlignment = Alignment.Center) {
                    Text("重", color = c.onAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(stringResource(R.string.app_name).uppercase() + " · " + stringResource(phaseWord).uppercase(),
                    color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f).padding(start = 10.dp))
                IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.now_settings), tint = c.textSecondary)
                }
            }
            // The hero stays clear of the drawer peek (176 dp) on a 360x780 dp screen.
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 176.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val next = summary.next
                if (next != null) {
                    Text(if (summary.nextAtMinute >= 0) stringResource(R.string.now_next_up_at, clock(summary.nextAtMinute)).uppercase()
                    else stringResource(R.string.now_next_up).uppercase(),
                        color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(next.title, color = c.text, fontSize = 36.sp, fontWeight = FontWeight.SemiBold, fontFamily = LumeType.Geist,
                        textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(stringResource(R.string.now_clear), color = c.text, fontSize = 36.sp, fontWeight = FontWeight.SemiBold,
                        fontFamily = LumeType.Geist, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.now_clear_hint), color = c.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
                val total = summary.doneCount + summary.openCount
                if (total > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.now_done_today, summary.doneCount, total), color = c.textSecondary, fontSize = 14.sp)
                }
                if (next != null) {
                    Spacer(Modifier.height(24.dp))
                    val checkLabel = stringResource(R.string.now_check)
                    Box(Modifier.size(72.dp).pressable().clip(CircleShape).background(c.accent)
                        .clickable(onClickLabel = checkLabel) { haptics.tick(); store.setTaskDone(next.id, true) },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Check, contentDescription = checkLabel, tint = c.onAccent, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.now_tap_to_finish), color = c.textSecondary, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun Drawer(overdue: Int, open: Int, groups: app.kasane.core.DrawerGroups, today: LocalDate,
                   onLists: () -> Unit, onAdd: () -> Unit, onEdit: (Task) -> Unit, onDone: (Task) -> Unit) {
    val c = Lume.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.drawer_title), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            val summary = listOfNotNull(stringResource(R.string.drawer_tasks, open),
                if (overdue > 0) stringResource(R.string.drawer_overdue_count, overdue) else null).joinToString(" · ")
            Text(summary, color = c.textSecondary, fontSize = 12.sp)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LumeChip(stringResource(R.string.chip_lists), false, onLists, modifier = Modifier.height(48.dp))
            LumeChip("+ " + stringResource(R.string.chip_add), false, onAdd, modifier = Modifier.height(48.dp))
        }
        if (groups.isEmpty) {
            Text(stringResource(R.string.drawer_empty), color = c.textSecondary, fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp))
        } else {
            LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
                group(R.string.widget_overdue, groups.overdue, today, late = true, onEdit, onDone)
                group(R.string.widget_today, groups.today, today, late = false, onEdit, onDone)
                group(R.string.widget_tomorrow, groups.tomorrow, today, late = false, onEdit, onDone)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.group(label: Int, tasks: List<Task>, today: LocalDate, late: Boolean,
                                                                   onEdit: (Task) -> Unit, onDone: (Task) -> Unit) {
    if (tasks.isEmpty()) return
    item(key = "h-$label") { SectionLabel(stringResource(label)) }
    items(tasks, key = { "t-" + it.id }) { task ->
        Row(Modifier.fillMaxWidth().pressable().clickable { onEdit(task) }, verticalAlignment = Alignment.CenterVertically) {
            LumeCheck(false, { onDone(task) })
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(task.title, color = Lume.colors.text, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val time = task.remindAt?.let { clock(it) }
                val sub = if (late) listOfNotNull(stringResource(R.string.widget_overdue),
                    task.nextDate(today)?.format(DateTimeFormatter.ofPattern("EEE d MMM"))).joinToString(" · ") + (time?.let { " $it" } ?: "")
                else time
                if (sub != null) Text(sub, color = if (late) Lume.colors.slip else Lume.colors.textSecondary, fontSize = 14.sp)
            }
        }
    }
}
