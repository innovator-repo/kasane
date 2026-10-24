package app.kasane.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kasane.R
import app.kasane.core.Entry
import app.kasane.core.ExceptionState
import app.kasane.core.Item
import app.kasane.core.Kind
import app.kasane.core.Task
import app.kasane.designsystem.GroupedInset
import app.kasane.designsystem.HapticToken
import app.kasane.designsystem.Lume
import app.kasane.designsystem.LumeButton
import app.kasane.designsystem.LumeCheck
import app.kasane.designsystem.LumeMotion
import app.kasane.designsystem.LumeType
import app.kasane.designsystem.TierPill
import app.kasane.designsystem.pressable
import app.kasane.designsystem.rememberLumeHaptics
import app.kasane.store

@Composable
private fun Detail(text: String, color: Color = Lume.colors.textSecondary) {
    Text(text, color = color, fontSize = 13.sp)
}

/** One habit or rule inside a grouped list. Long press edits or removes it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitRow(item: Item, entry: Entry, floorMode: Boolean, onEdit: () -> Unit, onChange: (Entry) -> Unit) {
    val c = Lume.colors
    val context = LocalContext.current
    val finished = item.finishedFor(entry, floorMode)
    Column(
        Modifier.fillMaxWidth().pressable()
            .combinedClickable(onLongClick = onEdit) { if (item.kind == Kind.CHECK) onChange(entry.copy(done = !entry.done)) }
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (item.kind) {
                Kind.CHECK -> LumeCheck(entry.done, { onChange(entry.copy(done = it)) })
                Kind.COUNT, Kind.TIMED -> LumeCheck(finished && !item.floorKept(entry), {
                    // Ticking is the whole target; unticking clears the day's amount.
                    onChange(if (it) full(item, entry) else clear(item, entry))
                }, color = if (item.floorKept(entry)) c.floor else c.target)
                Kind.RULE -> Icon(Icons.Outlined.Shield, null, Modifier.padding(start = 10.dp, end = 10.dp).padding(2.dp),
                    tint = when {
                        entry.slipped -> c.slip
                        entry.exception == ExceptionState.PLANNED -> c.seal
                        entry.done -> c.target
                        else -> c.textSecondary
                    })
            }
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(item.title, color = if (finished && item.kind != Kind.RULE) c.textSecondary else c.text, fontSize = 16.sp,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                when (item.kind) {
                    Kind.COUNT -> Detail(stringResource(R.string.count_of, entry.count, item.target))
                    Kind.TIMED -> Detail(stringResource(R.string.minutes_of, entry.minutes, item.target))
                    Kind.RULE -> when {
                        entry.exception == ExceptionState.PLANNED -> Detail(stringResource(R.string.rule_exception_planned), c.seal)
                        entry.slipped -> Detail(stringResource(R.string.rule_slipped), c.slip)
                        else -> Detail(stringResource(R.string.rule_kept), c.textSecondary)
                    }
                    Kind.CHECK -> {}
                }
            }
            item.opens?.let { target -> LumeButton(stringResource(R.string.open), { open(context, target) }) }
            when (item.kind) {
                Kind.COUNT -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LumeButton("−", { onChange(entry.copy(count = (entry.count - 1).coerceAtLeast(0))) }, haptic = HapticToken.TICK)
                    LumeButton("+10", { onChange(entry.copy(count = entry.count + 10)) })
                }
                Kind.TIMED -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LumeButton("−", { onChange(entry.copy(minutes = (entry.minutes - 5).coerceAtLeast(0))) }, haptic = HapticToken.TICK)
                    LumeButton("+5", { onChange(entry.copy(minutes = entry.minutes + 5)) })
                }
                Kind.RULE -> RuleControls(item, entry, onChange)
                Kind.CHECK -> {}
            }
        }
        if (item.kind == Kind.COUNT || item.kind == Kind.TIMED) {
            Column(Modifier.padding(start = 44.dp, top = 6.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TierPill(item.amount(entry), item.target, item.floorTarget)
                if (item.floorKept(entry)) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.floor_kept), color = c.floor, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (!finished) LumeButton(stringResource(R.string.floor_park), { onChange(entry.copy(floorParked = true)) },
                        tint = c.floor, haptic = HapticToken.CHECK_COMPLETE)
                }
            }
        }
        if (item.kind == Kind.RULE) ExceptionLine(item, entry, onChange)
    }
}

private fun full(item: Item, entry: Entry): Entry = when (item.kind) {
    Kind.COUNT -> entry.copy(count = maxOf(entry.count, item.target))
    Kind.TIMED -> entry.copy(minutes = maxOf(entry.minutes, item.target))
    else -> entry
}

private fun clear(item: Item, entry: Entry): Entry = when (item.kind) {
    Kind.COUNT -> entry.copy(count = 0, floorParked = false)
    Kind.TIMED -> entry.copy(minutes = 0, floorParked = false)
    else -> entry
}

@Composable
private fun RuleControls(item: Item, entry: Entry, onChange: (Entry) -> Unit) {
    val c = Lume.colors
    val haptics = rememberLumeHaptics()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (entry.done && !entry.slipped) {
            LumeButton(stringResource(R.string.rule_undo), { onChange(entry.copy(done = false, exception = ExceptionState.NONE)) })
        } else {
            if (!entry.slipped) LumeButton(stringResource(R.string.rule_mark_kept), {
                haptics.play(HapticToken.SEAL_LOCK)
                onChange(entry.copy(done = true))
            }, tint = c.target, haptic = HapticToken.TICK)
            // A slip is calm terracotta, never red.
            LumeButton(stringResource(if (entry.slipped) R.string.rule_undo else R.string.rule_mark_slip),
                { onChange(entry.copy(slipped = !entry.slipped, done = false, exception = ExceptionState.NONE)) },
                tint = if (entry.slipped) null else c.slip, haptic = HapticToken.GENTLE_NUDGE)
        }
    }
}

/** Setting a rule aside for today: allowed before 08:00, or after a 24-hour cooling off. */
@Composable
private fun ExceptionLine(item: Item, entry: Entry, onChange: (Entry) -> Unit) {
    val c = Lume.colors
    val store = LocalContext.current.store
    if (entry.slipped || entry.exception == ExceptionState.PLANNED) return
    var message by remember(item.id) { mutableStateOf<Int?>(null) }
    Column(Modifier.padding(start = 44.dp)) {
        Text(stringResource(R.string.rule_exception), color = c.seal, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.pressable().clickable {
                if (store.declareException(java.time.LocalDate.now(), item.id)) {
                    message = null
                } else {
                    store.requestException(item.id)
                    message = R.string.rule_exception_cooling
                }
            }.padding(vertical = 4.dp))
        message?.let { Detail(stringResource(it)) }
    }
}

/** A repeating or dated task, in a grouped list. */
@Composable
fun TaskRow(task: Task, onDone: () -> Unit) {
    val c = Lume.colors
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LumeCheck(false, { onDone() })
        Column(Modifier.weight(1f)) {
            Text(task.title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            task.remindAt?.let { Detail("%02d:%02d".format(it / 60, it % 60)) }
        }
        KindBadge(stringResource(R.string.add_badge_task))
    }
}

@Composable
fun UpcomingRow(title: String, badge: Int, remindAt: Int?) {
    val c = Lume.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.textSecondary, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            remindAt?.let { Detail("%02d:%02d".format(it / 60, it % 60)) }
        }
        KindBadge(stringResource(badge))
    }
}

/** A quiet one-line pill for a closed block. Tapping opens it with a spring. */
@Composable
fun DrawerPill(summary: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Lume.colors
    val haptics = rememberLumeHaptics()
    Column(modifier.fillMaxWidth().animateContentSize(LumeMotion.spatial())) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().pressable()
                .clip(RoundedCornerShape(14.dp)).background(c.grouped)
                .clickable { haptics.tick(); onToggle() }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(summary, color = c.textSecondary, fontSize = 14.sp, fontFamily = LumeType.Geist, modifier = Modifier.weight(1f))
            Text(if (expanded) "−" else "+", color = c.accent, fontSize = 18.sp)
        }
        if (expanded) content()
    }
}
