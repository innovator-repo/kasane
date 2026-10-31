package app.kasane.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.kasane.LumeCore
import app.kasane.MainActivity
import app.kasane.R
import app.kasane.core.Analysis
import app.kasane.core.Journal
import app.kasane.core.Kind
import app.kasane.core.Orchestrator
import app.kasane.core.Phase
import app.kasane.designsystem.LumePalette
import app.kasane.designsystem.LumeTokens
import app.kasane.designsystem.LumeType
import app.kasane.ipc.Companion as Peer
import app.kasane.ipc.Ecosystem
import app.kasane.ipc.KatachiStatus
import app.kasane.ipc.RenStatus
import app.kasane.ipc.SleepLockStore
import app.kasane.ipc.VowStatus
import app.kasane.store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** What the other apps said when the widget last drew. */
private class Peers(val ren: RenStatus?, val katachi: KatachiStatus?, val vow: VowStatus?, val renLink: Intent?, val katachiLink: Intent?, val sigilLink: Intent?)

/** Everything one draw needs, worked out off the main thread so recomposing is only layout. */
private class HorizonState(
    /** The task list the widget is showing, or null for every list. */
    val listName: String?,
    val phase: Phase,
    val list: HorizonList,
    val urgent: Boolean,
    val rulesKept: Int,
    val rulesTotal: Int,
)

/**
 * The Lume Horizon, in the shape of a task list: the circadian block on top, a
 * progress bar for the day, All / Habits / Tasks chips, then Overdue, Today and Upcoming groups
 * with the time each task is due, a checkbox on every tick habit and task, a "tomorrow" snooze on
 * one-off tasks, a Completed group that folds away, and quick launch pills for Ren, Katachi and
 * Sigil below. The border turns amber after 21:00 while Japanese is still open.
 *
 * Smoothness: sizes are a fixed set (no redraw per pixel of resize), the analysis runs on a
 * background dispatcher, the status of the other apps is fetched in parallel before the first
 * frame, the emblem is drawn once, and bursts of changes are merged into a single redraw.
 */
class HorizonWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = context.store
        val prefs = HorizonPrefs.get(context)
        val peers = coroutineScope {
            val ren = async(Dispatchers.IO) { Ecosystem.renStatus(context) }
            val katachi = async(Dispatchers.IO) { Ecosystem.katachiStatus(context) }
            val vow = async(Dispatchers.IO) { Ecosystem.vowStatus(context) }
            val renOn = async(Dispatchers.IO) { Peer.available(context, Ecosystem.REN) }
            val katachiOn = async(Dispatchers.IO) { Peer.available(context, Ecosystem.KATACHI) }
            val sigilOn = async(Dispatchers.IO) { Peer.available(context, Ecosystem.SIGIL) }
            val vowOn = async(Dispatchers.IO) { Peer.available(context, Ecosystem.VOW) }
            // A companion counts only when installed AND its provider answers.
            Peers(if (renOn.await()) ren.await() else null, if (katachiOn.await()) katachi.await() else null,
                if (vowOn.await()) vow.await() else null,
                Ecosystem.renIntent(context, true).takeIf { renOn.await() },
                Ecosystem.katachiIntent(context).takeIf { katachiOn.await() },
                Ecosystem.sigilIntent(context).takeIf { sigilOn.await() })
        }
        val emblem = emblemBitmap(context)
        val first = withContext(Dispatchers.Default) { buildState(context, store.journal.value, store.floorToday(), prefs.filter.value, prefs.list.value) }
        provideContent {
            val state by produceState(first) {
                combine(store.journal, prefs.filter, prefs.list) { journal, filter, list -> buildState(context, journal, store.floorToday(), filter, list) }
                    .flowOn(Dispatchers.Default)
                    .collect { value = it }
            }
            val filter by prefs.filter.collectAsState()
            val completedOpen by prefs.completedOpen.collectAsState()
            KasaneWidgetTheme { HorizonContent(state, filter, completedOpen, peers, emblem) }
        }
    }

    companion object {
        private val COMPACT = DpSize(180.dp, 110.dp)
        private val MEDIUM = DpSize(250.dp, 200.dp)
        private val LARGE = DpSize(300.dp, 330.dp)
    }
}

class HorizonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = HorizonWidget()
}

private fun buildState(context: Context, journal: Journal, floor: Boolean, filter: HorizonFilter, listFilter: String?): HorizonState {
    val now = LocalDateTime.now()
    val today = now.toLocalDate()
    val values = Analysis(journal, today, LumeCore.active).momentum()
    val rules = journal.items.filter { it.kind == Kind.RULE && it.dueOn(today) }
    return HorizonState(
        listName = when (listFilter) { null -> null; "" -> context.getString(R.string.lists_default)
            else -> journal.lists.firstOrNull { it.id == listFilter }?.name },
        phase = Phase.surfaceOf(now.toLocalTime(), SleepLockStore.active(context)),
        list = horizonList(journal, today, now.toLocalTime(), values.associate { it.item.id to it.value }, floor, filter, listFilter),
        urgent = Orchestrator.urgent(journal, now, floor),
        rulesKept = rules.count { it.finishedFor(journal.entry(today, it.id)) },
        rulesTotal = rules.size,
    )
}

@Composable
private fun HorizonContent(state: HorizonState, filter: HorizonFilter, completedOpen: Boolean, peers: Peers, emblem: Bitmap) {
    val context = LocalContext.current
    val size = LocalSize.current
    val compact = size.height < 160.dp
    val roomy = size.height >= 280.dp
    val list = state.list

    // The border is a ring of colour around the card; amber when Japanese is running out of day.
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(22.dp)
            .background(if (state.urgent) ColorProvider(LumePalette.Urgent) else ColorProvider(Color.Transparent))
            .padding(if (state.urgent) 2.dp else 0.dp),
    ) {
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface).cornerRadius(20.dp).padding(12.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))),
        ) {
            // Top band: block, then one line of context
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(emblem), contentDescription = null, modifier = GlanceModifier.size(20.dp))
                Spacer(GlanceModifier.width(6.dp))
                Text(phaseLabel(context, state.phase), style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp,
                    fontWeight = FontWeight.Medium), modifier = GlanceModifier.defaultWeight())
            }
            val vow = peers.vow
            val line = if (vow != null)
                context.getString(R.string.widget_horizon_reclaimed, vow.reclaimedMinutes / 60) + " · " +
                    context.getString(R.string.widget_horizon_rules, vow.rulesKept, vow.rulesTotal)
            else if (state.rulesTotal > 0) context.getString(R.string.widget_horizon_rules, state.rulesKept, state.rulesTotal) else null
            if (line != null) Text(line, style = labelStyle(), maxLines = 1)

            // Day progress, as the Reminder list shows how many are done
            if (!compact && list.total > 0) {
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = list.fraction, modifier = GlanceModifier.defaultWeight().height(4.dp),
                        color = GlanceTheme.colors.primary, backgroundColor = GlanceTheme.colors.surfaceVariant,
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(context.getString(R.string.today_progress, list.completed.size, list.total), style = labelStyle(), maxLines = 1)
                }
            }

            // Chips and add, once there is room for them
            if (roomy) {
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Chip(context.getString(R.string.widget_horizon_filter_all), filter == HorizonFilter.ALL, HorizonFilter.ALL)
                    Spacer(GlanceModifier.width(6.dp))
                    Chip(context.getString(R.string.widget_habits), filter == HorizonFilter.HABITS, HorizonFilter.HABITS)
                    Spacer(GlanceModifier.width(6.dp))
                    Chip(context.getString(R.string.widget_tasks), filter == HorizonFilter.TASKS, HorizonFilter.TASKS)
                    Spacer(GlanceModifier.width(6.dp))
                    ListChip(state.listName ?: context.getString(R.string.lists_chip), state.listName != null)
                    Spacer(GlanceModifier.defaultWeight())
                    AddButton()
                }
            }
            Spacer(GlanceModifier.height(6.dp))

            // Centre: groups scrolling together, or just the next row when small
            when {
                list.total == 0 -> Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.CenterStart) {
                    Text(context.getString(R.string.widget_horizon_nothing), style = bodyStyle())
                }
                compact -> Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.CenterStart) {
                    val next = list.pending.firstOrNull()
                    if (next != null) RowFor(next, snooze = false) else Text(context.getString(R.string.widget_all_done), style = bodyStyle())
                }
                else -> LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    list.pending.groupBy { it.bucket }.forEach { (bucket, rows) ->
                        item(itemId = ("section:" + bucket.name).hashCode().toLong()) {
                            SectionLabel(bucketLabel(context, bucket), rows.size, urgent = bucket == Bucket.OVERDUE)
                        }
                        items(rows, itemId = { it.key.hashCode().toLong() }) { RowFor(it, snooze = true) }
                    }
                    if (list.completed.isNotEmpty()) {
                        item(itemId = "section:completed".hashCode().toLong()) { CompletedHeader(list.completed.size, completedOpen) }
                        if (completedOpen) items(list.completed, itemId = { it.key.hashCode().toLong() }) { RowFor(it, snooze = false) }
                    }
                }
            }
            Spacer(GlanceModifier.height(6.dp))

            // Bottom: quick launch
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                peers.renLink?.let {
                    Pill("Ren " + (peers.ren?.let { s -> context.getString(R.string.widget_horizon_ren, s.due) } ?: ""),
                        actionStartActivity(it), tint = LumeTokens.Accent.RenAkane)
                    Spacer(GlanceModifier.width(6.dp))
                }
                peers.katachiLink?.let {
                    Pill("Katachi " + (peers.katachi?.let { s -> context.getString(R.string.widget_horizon_katachi, s.solved, s.target) } ?: ""),
                        actionStartActivity(it), tint = LumeTokens.Accent.KatachiJade)
                    Spacer(GlanceModifier.width(6.dp))
                }
                peers.sigilLink?.let {
                    Pill("Sigil " + context.getString(R.string.widget_horizon_sigil), actionStartActivity(it), tint = LumePalette.SealLight)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, count: Int, urgent: Boolean) {
    Text("$text · $count", style = TextStyle(color = if (urgent) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
        fontSize = 12.sp, fontWeight = FontWeight.Medium), modifier = GlanceModifier.padding(top = 4.dp, bottom = 2.dp))
}

/** Tapping it folds the finished rows away or back. */
@Composable
private fun CompletedHeader(count: Int, open: Boolean) {
    val context = LocalContext.current
    Text((if (open) "▾ " else "▸ ") + context.getString(R.string.widget_horizon_completed_header, count), style = labelStyle(),
        modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp).clickable(actionRunCallback<ToggleCompletedAction>()))
}

@Composable
private fun Chip(label: String, selected: Boolean, filter: HorizonFilter) {
    Box(
        GlanceModifier.background(if (selected) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surfaceVariant)
            .cornerRadius(14.dp).padding(horizontal = 10.dp, vertical = 4.dp)
            .clickable(actionRunCallback<SetFilterAction>(actionParametersOf(FilterKey to filter.name))),
    ) {
        Text(label, style = TextStyle(
            color = if (selected) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant,
            fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

/** Steps through the task lists, like picking a list in the Reminder widget's settings. */
@Composable
private fun ListChip(label: String, selected: Boolean) {
    Box(
        GlanceModifier.background(if (selected) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surfaceVariant)
            .cornerRadius(14.dp).padding(horizontal = 10.dp, vertical = 4.dp).clickable(actionRunCallback<CycleListAction>()),
    ) {
        Text(label + " ▾", style = TextStyle(
            color = if (selected) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant,
            fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

@Composable
private fun RowFor(row: HorizonRow, snooze: Boolean) {
    when (row) {
        is HorizonRow.Tick -> TickRow(row, snooze)
        is HorizonRow.Amount -> AmountRow(row)
    }
}

@Composable
private fun rowTitleStyle(done: Boolean) = TextStyle(
    color = if (done) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface, fontSize = 14.sp,
    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
)

/**
 * What a tap on a row does, as in the Reminder widget: open that task or habit for editing. The data
 * link makes each row's intent distinct, so one row's tap never opens another's.
 */
private fun openRow(context: Context, taskId: String?, itemId: String?): Intent =
    Intent(context, MainActivity::class.java)
        .setData(android.net.Uri.parse(if (taskId != null) "kasane://task/$taskId" else "kasane://item/${itemId.orEmpty()}"))
        .apply {
            if (taskId != null) putExtra(MainActivity.EXTRA_TASK_ID, taskId)
            else if (itemId != null) putExtra(MainActivity.EXTRA_ITEM_ID, itemId)
        }
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

/** When a task falls: "Overdue · Fri 23 Oct", "Today · 18:30", "Tomorrow", "Sat 31 Oct". */
private fun whenText(context: Context, row: HorizonRow.Tick, today: LocalDate): String? {
    val date = row.date ?: return null
    val day = when {
        date.isBefore(today) -> context.getString(R.string.widget_overdue) + " · " + date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
        date == today -> context.getString(R.string.widget_today)
        date == today.plusDays(1) -> context.getString(R.string.widget_tomorrow)
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
    val at = row.remindAt?.let { " · %02d:%02d".format(it / 60, it % 60) }.orEmpty()
    return if (date.isBefore(today)) day else day + at
}

/** A checkbox and a title: tick it off right here. Tasks say when they fall and can be snoozed a day. */
@Composable
private fun TickRow(row: HorizonRow.Tick, snooze: Boolean) {
    val context = LocalContext.current
    val toggle = if (row.taskId != null) actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskIdKey to row.taskId))
    else actionRunCallback<ToggleItemAction>(actionParametersOf(ItemIdKey to row.itemId.orEmpty()))
    val subtitle = if (row.taskId != null && !row.done) whenText(context, row, LocalDate.now()) else null
    val overdue = row.bucket == Bucket.OVERDUE && !row.done
    val open = openRow(context, row.taskId, row.itemId)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CheckBox(
            checked = row.done, onCheckedChange = toggle,
            colors = CheckboxDefaults.colors(checkedColor = GlanceTheme.colors.primary, uncheckedColor = GlanceTheme.colors.onSurfaceVariant),
        )
        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(open))) {
            Text(row.title, style = rowTitleStyle(row.done), maxLines = 1)
            if (subtitle != null) Text(subtitle, maxLines = 1, style = TextStyle(fontSize = 11.sp,
                color = if (overdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant))
        }
        if (snooze && row.snoozable && (row.bucket == Bucket.OVERDUE || row.bucket == Bucket.TODAY)) {
            Box(
                GlanceModifier.background(GlanceTheme.colors.surfaceVariant).cornerRadius(12.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .clickable(actionRunCallback<SnoozeTaskAction>(actionParametersOf(TaskIdKey to row.taskId.orEmpty()))),
            ) {
                Text(context.getString(R.string.widget_horizon_snooze), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            }
        }
    }
}

/** Reps or minutes so far with a thin bar. Logging happens in the app, so a tap opens it. */
@Composable
private fun AmountRow(row: HorizonRow.Amount) {
    val context = LocalContext.current
    val open = openRow(context, null, row.itemId)
    val progress = context.getString(if (row.minutes) R.string.minutes_of else R.string.count_of, row.value, row.target)
    Column(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 4.dp).clickable(actionStartActivity(open))) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(row.title, style = rowTitleStyle(row.done), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            Text(progress, style = labelStyle(), maxLines = 1)
        }
        Spacer(GlanceModifier.height(3.dp))
        LinearProgressIndicator(
            progress = row.fraction, modifier = GlanceModifier.fillMaxWidth().height(3.dp),
            color = GlanceTheme.colors.primary, backgroundColor = GlanceTheme.colors.surfaceVariant,
        )
    }
}

@Composable
private fun Pill(label: String, action: Action, primary: Boolean = false, tint: Color? = null) {
    val bg = when {
        tint != null -> ColorProvider(tint)
        primary -> GlanceTheme.colors.primary
        else -> GlanceTheme.colors.primaryContainer
    }
    val fg = if (tint != null || primary) ColorProvider(Color.White) else GlanceTheme.colors.onPrimaryContainer
    Box(GlanceModifier.background(bg).cornerRadius(14.dp).padding(horizontal = 10.dp, vertical = 5.dp).clickable(action)) {
        Text(label.trim(), style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

private fun bucketLabel(context: Context, bucket: Bucket): String = when (bucket) {
    Bucket.OVERDUE -> context.getString(R.string.widget_overdue)
    Bucket.TODAY -> context.getString(R.string.widget_today)
    Bucket.TOMORROW -> context.getString(R.string.widget_tomorrow)
    Bucket.WEEK -> context.getString(R.string.widget_horizon_week)
    Bucket.UPCOMING -> context.getString(R.string.widget_horizon_upcoming)
}

private fun phaseLabel(context: Context, phase: Phase): String = when (phase) {
    Phase.MORNING -> context.getString(R.string.block_morning)
    Phase.DAY -> context.getString(R.string.block_day)
    Phase.EVENING -> context.getString(R.string.block_night)
    Phase.SANCTUARY -> context.getString(R.string.widget_horizon_sleep_lock)
}.uppercase()

private var emblemCache: Pair<Float, Bitmap>? = null

/** 重ね in Noto Serif JP, drawn to a small bitmap because a widget cannot load app fonts. Drawn once per density. */
@Synchronized
private fun emblemBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    emblemCache?.let { if (it.first == density) return it.second }
    val px = (40 * density).toInt()
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6B7BD6.toInt()
        textSize = px * 0.48f
        typeface = ResourcesCompat.getFont(context, R.font.noto_serif_jp_semibold)
    }
    val text = LumeType.EMBLEM
    val canvas = Canvas(bitmap)
    canvas.drawText(text, (px - paint.measureText(text)) / 2f, px * 0.64f, paint)
    emblemCache = density to bitmap
    return bitmap
}
