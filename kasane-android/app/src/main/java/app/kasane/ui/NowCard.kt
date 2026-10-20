package app.kasane.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kasane.R
import app.kasane.core.Entry
import app.kasane.core.Item
import app.kasane.core.Kind
import app.kasane.core.Orchestrator
import app.kasane.designsystem.HapticToken
import app.kasane.designsystem.Lume
import app.kasane.designsystem.LumeButton
import app.kasane.designsystem.LumeMotion
import app.kasane.designsystem.LumeType
import app.kasane.designsystem.TierRings
import app.kasane.designsystem.rememberLumeHaptics
import app.kasane.ipc.Ecosystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The one raised surface on Today: the single habit to do next, with its control right on the card.
 * Counts get +10 and +25 on three rings, timers get a paced timer, Ren and Duolingo get the quick round.
 */
@Composable
fun NowRitualCard(
    item: Item?,
    entry: Entry,
    doneCount: Int,
    total: Int,
    today: LocalDate,
    floorMode: Boolean,
    blockLabel: String,
    onChange: (Entry) -> Unit,
) {
    val c = Lume.colors
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp).fillMaxWidth()
            .shadow(if (c.dark) 0.dp else 8.dp, shape, ambientColor = c.accent.copy(alpha = 0.25f), spotColor = c.accent.copy(alpha = 0.25f))
            .background(if (c.dark) c.grouped else c.canvas, shape)
            .border(1.dp, c.accent.copy(alpha = 0.35f), shape)
            .clip(shape),
    ) {
      Text(LumeType.EMBLEM, style = LumeType.Kanji, fontSize = 96.sp, color = c.accent.copy(alpha = 0.07f),
          modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 4.dp))
      Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.now_label).uppercase() + " · " + blockLabel.uppercase(),
                color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(today.format(DateTimeFormatter.ofPattern("EEE d MMM")), color = c.textSecondary, fontSize = 12.sp)
        }
        if (item == null) {
            Text(stringResource(R.string.now_all_done), color = c.text, fontFamily = LumeType.Newsreader,
                fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.now_all_done_hint), color = c.textSecondary, fontSize = 14.sp)
        } else {
            NowItem(item, entry, floorMode, onChange)
        }
        Text(stringResource(R.string.today_progress, doneCount, total), color = c.textSecondary, fontSize = 12.sp,
            fontFamily = LumeType.GeistMono)
      }
    }
}

@Composable
private fun NowItem(item: Item, entry: Entry, floorMode: Boolean, onChange: (Entry) -> Unit) {
    val c = Lume.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    Text(item.title, color = c.text, fontFamily = LumeType.Newsreader, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
    // Ren's own numbers, read from its status provider when it is installed.
    if (item.id == Orchestrator.REN) {
        val status by produceState<app.kasane.ipc.RenStatus?>(null) {
            value = withContext(Dispatchers.IO) { Ecosystem.renStatus(context) }
        }
        status?.let {
            Text(stringResource(R.string.now_ren_live, it.due, it.retention), color = c.textSecondary, fontSize = 13.sp,
                fontFamily = LumeType.GeistMono)
        }
    }
    when (item.kind) {
        Kind.COUNT -> CountControls(item, entry, floorMode, onChange)
        Kind.TIMED -> TimerControls(item, entry, onChange)
        Kind.CHECK -> CheckControls(item, entry, onChange)
        Kind.RULE -> Unit
    }
}

@Composable
private fun CountControls(item: Item, entry: Entry, floorMode: Boolean, onChange: (Entry) -> Unit) {
    val c = Lume.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TierRings(entry.count, item.target, item.floorTarget)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.count_of, entry.count, item.target), color = c.text, fontFamily = LumeType.GeistMono, fontSize = 18.sp)
            if (item.floorKept(entry) || (floorMode && entry.count >= item.floorTarget)) {
                Text(stringResource(R.string.floor_kept), color = c.floor, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LumeButton("+10", { onChange(entry.copy(count = entry.count + 10)) }, primary = true)
                LumeButton("+25", { onChange(entry.copy(count = entry.count + 25)) })
            }
        }
    }
}

@Composable
private fun CheckControls(item: Item, entry: Entry, onChange: (Entry) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val japanese = item.id == Orchestrator.REN || item.id == Orchestrator.DUOLINGO
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (japanese) {
            // Ren's 5-card round is the floor; Duolingo's row offers it as the follow-up.
            if (item.id == Orchestrator.DUOLINGO) {
                LumeButton(stringResource(R.string.open), { open(context, Ecosystem.DUOLINGO) }, primary = true)
                LumeButton(stringResource(R.string.now_quick_round), { launchRen(context, true) })
            } else {
                LumeButton(stringResource(R.string.now_quick_round), { launchRen(context, true) }, primary = true)
                LumeButton(stringResource(R.string.now_full_session), { launchRen(context, false) })
            }
        }
        LumeButton(stringResource(R.string.now_mark_done), { onChange(entry.copy(done = true)) },
            primary = !japanese, haptic = HapticToken.CHECK_COMPLETE)
    }
}

/** Opens Ren's session by deep link, or its page if it is not installed. */
internal fun launchRen(context: Context, quick: Boolean) {
    val intent = Ecosystem.renIntent(context, quick)
    if (intent != null) runCatching { context.startActivity(intent) } else open(context, Ecosystem.REN)
}

@Composable
private fun TimerControls(item: Item, entry: Entry, onChange: (Entry) -> Unit) {
    val c = Lume.colors
    val haptics = rememberLumeHaptics()
    val breathing = item.id.startsWith("meditation")
    var startedAt by remember(item.id) { mutableLongStateOf(0L) }
    var elapsedSec by remember(item.id) { mutableStateOf(0) }
    val running = startedAt > 0L
    val goalSec = (item.target.coerceAtLeast(1) - entry.minutes).coerceAtLeast(1) * 60

    // One tick a second off the wall clock, so a slow frame never loses time. A breath is 4s in, 4s out.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var lastPhase = -1
        while (true) {
            elapsedSec = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
            val phase = elapsedSec / 4
            if (breathing && phase != lastPhase) {
                lastPhase = phase
                haptics.play(HapticToken.BREATH_WAVE)
            }
            if (elapsedSec >= goalSec) break
            delay(250)
        }
        // Done: log the whole minutes sat and stop.
        onChange(entry.copy(minutes = entry.minutes + elapsedSec / 60))
        haptics.play(HapticToken.CHECK_COMPLETE)
        startedAt = 0L
        elapsedSec = 0
    }

    val transition = rememberInfiniteTransition(label = "breath")
    val breath by transition.animateFloat(
        initialValue = 0.82f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LumeMotion.BREATH_MS, easing = LumeMotion.EaseInOutSine), RepeatMode.Reverse),
        label = "scale",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (running && breathing) {
            Box(Modifier.size(72.dp).scale(breath).background(c.accent.copy(alpha = 0.18f), CircleShape)
                .border(2.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) {
                Text(if ((elapsedSec / 4) % 2 == 0) stringResource(R.string.now_breathe_in) else stringResource(R.string.now_breathe_out),
                    color = c.accent, fontSize = 11.sp)
            }
        } else {
            TierRings(entry.minutes, item.target, item.floorTarget)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val left = (goalSec - elapsedSec).coerceAtLeast(0)
            Text(if (running) "%d:%02d".format(left / 60, left % 60) else stringResource(R.string.minutes_of, entry.minutes, item.target),
                color = c.text, fontFamily = LumeType.GeistMono, fontSize = 18.sp)
            if (item.floorKept(entry)) Text(stringResource(R.string.floor_kept), color = c.floor, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!running) {
                    LumeButton(stringResource(R.string.now_start_timer, (item.target - entry.minutes).coerceAtLeast(1)),
                        { startedAt = System.currentTimeMillis(); elapsedSec = 0 }, primary = true)
                    LumeButton("+5", { onChange(entry.copy(minutes = entry.minutes + 5)) })
                } else {
                    // Stopping early keeps the whole minutes already sat.
                    LumeButton(stringResource(R.string.now_pause), {
                        onChange(entry.copy(minutes = entry.minutes + elapsedSec / 60))
                        startedAt = 0L
                        elapsedSec = 0
                    })
                }
            }
        }
    }
}
