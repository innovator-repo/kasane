package app.kasane.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.res.ResourcesCompat
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.size
import app.kasane.designsystem.LumeType
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.kasane.LumeCore
import app.kasane.MainActivity
import app.kasane.R
import app.kasane.core.Analysis
import app.kasane.core.Item
import app.kasane.core.Journal
import app.kasane.core.Kind
import app.kasane.core.NowPick
import app.kasane.core.Orchestrator
import app.kasane.core.Phase
import app.kasane.designsystem.LumePalette
import app.kasane.ipc.Ecosystem
import app.kasane.ipc.KatachiStatus
import app.kasane.ipc.RenStatus
import app.kasane.ipc.SleepLockStore
import app.kasane.ipc.VowStatus
import app.kasane.store
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

/** What the other apps said when the widget last drew. */
private class Peers(val ren: RenStatus?, val katachi: KatachiStatus?, val vow: VowStatus?, val renLink: Intent?, val katachiLink: Intent?, val sigilLink: Intent?)

/**
 * The Lume Horizon: the circadian block and momentum on top (with Vow's reclaimed time when it is
 * installed), the one habit to do now in the middle with a one-tap log, and quick launch pills
 * for Ren, Katachi and Sigil below. The border turns amber after 21:00 while Japanese is still open.
 */
class HorizonWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = context.store
        val peers = Peers(
            Ecosystem.renStatus(context), Ecosystem.katachiStatus(context), Ecosystem.vowStatus(context),
            Ecosystem.renIntent(context, true), Ecosystem.katachiIntent(context), Ecosystem.sigilIntent(context),
        )
        provideContent {
            val journal by store.journal.collectAsState()
            KasaneWidgetTheme { HorizonContent(journal, store.floorToday(), peers) }
        }
    }
}

class HorizonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = HorizonWidget()
}

@Composable
private fun HorizonContent(journal: Journal, floor: Boolean, peers: Peers) {
    val context = LocalContext.current
    val now = LocalDateTime.now()
    val today = LocalDate.now()
    val phase = Phase.surfaceOf(now.toLocalTime(), SleepLockStore.active(context))
    val momentum = remember(journal) {
        Analysis(journal, today, LumeCore.active).momentum().map { it.value }.average()
            .takeIf { !it.isNaN() }?.times(100)?.roundToInt() ?: 0
    }
    val item = NowPick.next(journal, today, now.toLocalTime(), floorMode = floor)
    val urgent = Orchestrator.urgent(journal, now, floor)
    val rules = journal.items.filter { it.kind == Kind.RULE && it.dueOn(today) }
    val emblem = remember { emblemBitmap(context) }
    val kept = rules.count { it.finishedFor(journal.entry(today, it.id)) }

    // The border is a ring of colour around the card; amber when Japanese is running out of day.
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(22.dp)
            .background(if (urgent) ColorProvider(LumePalette.Urgent) else ColorProvider(Color.Transparent))
            .padding(if (urgent) 2.dp else 0.dp),
    ) {
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface).cornerRadius(20.dp).padding(12.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))),
        ) {
            // Top band
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(emblem), contentDescription = null, modifier = GlanceModifier.size(20.dp))
                Spacer(GlanceModifier.width(6.dp))
                Text(phaseLabel(context, phase), style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp,
                    fontWeight = FontWeight.Medium), modifier = GlanceModifier.defaultWeight())
                Text(context.getString(R.string.widget_horizon_momentum, momentum), style = headlineStyle(14))
            }
            val vow = peers.vow
            val line = if (vow != null)
                context.getString(R.string.widget_horizon_reclaimed, vow.reclaimedMinutes / 60) + " · " +
                    context.getString(R.string.widget_horizon_rules, vow.rulesKept, vow.rulesTotal)
            else if (rules.isNotEmpty()) context.getString(R.string.widget_horizon_rules, kept, rules.size) else null
            if (line != null) Text(line, style = labelStyle(), maxLines = 1)
            Spacer(GlanceModifier.height(8.dp))

            // Centre: the one thing to do now
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                if (item == null) {
                    Text(context.getString(R.string.widget_horizon_nothing), style = bodyStyle())
                } else {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(item.title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp,
                            fontWeight = FontWeight.Medium), maxLines = 2)
                        val entry = journal.entry(today, item.id)
                        when (item.kind) {
                            Kind.COUNT -> Text(context.getString(R.string.count_of, entry.count, item.target), style = labelStyle())
                            Kind.TIMED -> Text(context.getString(R.string.minutes_of, entry.minutes, item.target), style = labelStyle())
                            else -> {}
                        }
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    Pill(nowActionLabel(context, item),
                        actionRunCallback<LogItemAction>(actionParametersOf(ItemIdKey to item.id)), primary = true)
                }
            }

            // Bottom: quick launch
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                peers.renLink?.let {
                    Pill("Ren " + (peers.ren?.let { s -> context.getString(R.string.widget_horizon_ren, s.due) } ?: ""),
                        actionStartActivity(it), tint = Color(0xFFE03E36))
                    Spacer(GlanceModifier.width(6.dp))
                }
                peers.katachiLink?.let {
                    Pill("Katachi " + (peers.katachi?.let { s -> context.getString(R.string.widget_horizon_katachi, s.solved, s.target) } ?: ""),
                        actionStartActivity(it), tint = Color(0xFF1E7D68))
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

private fun phaseLabel(context: Context, phase: Phase): String = when (phase) {
    Phase.MORNING -> context.getString(R.string.block_morning)
    Phase.DAY -> context.getString(R.string.block_day)
    Phase.EVENING -> context.getString(R.string.block_night)
    Phase.SANCTUARY -> context.getString(R.string.widget_horizon_sleep_lock)
}.uppercase()

private fun nowActionLabel(context: Context, item: Item): String =
    if (item.kind == Kind.CHECK) context.getString(R.string.widget_done) else "+${quickStep(item)}"

/** 重ね in Noto Serif JP, drawn to a small bitmap because a widget cannot load app fonts. */
private fun emblemBitmap(context: Context): Bitmap {
    val px = (40 * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6B7BD6.toInt()
        textSize = px * 0.48f
        typeface = ResourcesCompat.getFont(context, R.font.noto_serif_jp_semibold)
    }
    val text = LumeType.EMBLEM
    val canvas = Canvas(bitmap)
    canvas.drawText(text, (px - paint.measureText(text)) / 2f, px * 0.64f, paint)
    return bitmap
}
