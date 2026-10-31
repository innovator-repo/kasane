package app.kasane.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.kasane.MainActivity
import app.kasane.R
import app.kasane.core.NextUp
import app.kasane.designsystem.LumeTokens
import app.kasane.ipc.AppFact
import app.kasane.ipc.Companion
import app.kasane.ipc.Ecosystem
import app.kasane.ipc.LumeApp
import app.kasane.ipc.readFacts
import app.kasane.store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Kasane's own facts for the universal widgets, built the same way a companion's row is. */
internal fun kasaneFact(context: Context, today: LocalDate = LocalDate.now()): AppFact {
    val s = NextUp.summary(context.store.journal.value.tasks, today)
    return AppFact(LumeApp.KASANE, count = s.openCount, total = s.openCount + s.doneCount, text = s.next?.title,
        minute = s.nextAtMinute.takeIf { it >= 0 })
}

private fun tint(app: LumeApp): Color = when (app) {
    LumeApp.KASANE -> LumeTokens.Accent.KasaneSumi
    LumeApp.SIGIL -> LumeTokens.Accent.SigilSapphire
    LumeApp.VOW -> LumeTokens.Accent.VowTeal
    LumeApp.REN -> LumeTokens.Accent.RenAkane
    LumeApp.KATACHI -> LumeTokens.Accent.KatachiJade
}

private fun glyph(app: LumeApp) = when (app) {
    LumeApp.KASANE -> "重"; LumeApp.SIGIL -> "印"; LumeApp.VOW -> "誓"; LumeApp.REN -> "練"; LumeApp.KATACHI -> "形"
}

private fun appName(context: Context, app: LumeApp) = context.getString(when (app) {
    LumeApp.KASANE -> R.string.app_kasane; LumeApp.SIGIL -> R.string.app_sigil; LumeApp.VOW -> R.string.app_vow
    LumeApp.REN -> R.string.app_ren; LumeApp.KATACHI -> R.string.app_katachi
})

private fun openApp(context: Context, app: LumeApp): Intent? =
    if (app == LumeApp.KASANE) openKasane(context) else Ecosystem.launchIntent(context, app.pkg)

/** One short line for an app's fact, or null when there is nothing to say. */
internal fun lineText(context: Context, fact: AppFact): String? = when (fact.app) {
    LumeApp.KASANE -> fact.text?.let { t -> fact.minute?.let { context.getString(R.string.line_kasane_at, t, "%02d:%02d".format(it / 60, it % 60)) } ?: t }
        ?: context.getString(R.string.now_clear)
    LumeApp.REN -> fact.count?.let { context.getString(R.string.line_ren, it) }
    LumeApp.KATACHI -> fact.count?.let { context.getString(R.string.line_katachi, it) }
    LumeApp.VOW -> fact.flag?.let { context.getString(if (it) R.string.line_vow_on else R.string.line_vow_off) +
        (fact.text?.takeIf { _ -> it }?.let { n -> " · $n" } ?: "") }
    LumeApp.SIGIL -> fact.text
}

private suspend fun facts(context: Context): List<AppFact> =
    listOf(kasaneFact(context)) + withContext(Dispatchers.IO) { readFacts(context) }

@Composable
private fun Card(modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    Box(GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(22.dp).background(GlanceTheme.colors.surface).padding(12.dp)) {
        Box(modifier.fillMaxSize()) { content() }
    }
}

// ---- Today: one line per installed app ----

class UniversalTodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val peers = withContext(Dispatchers.IO) { readFacts(context) }
        provideContent {
            val journal by context.store.journal.collectAsState()
            val lines = (listOf(kasaneFact(context)) + peers).mapNotNull { f -> lineText(context, f)?.let { f to it } }
            KasaneWidgetTheme {
                Card {
                    Column(GlanceModifier.fillMaxSize()) {
                        lines.forEach { (fact, text) ->
                            Row(GlanceModifier.fillMaxWidth().padding(vertical = 5.dp)
                                .let { m -> openApp(context, fact.app)?.let { m.clickable(actionStartActivity(it)) } ?: m },
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(ColorProvider(tint(fact.app)))) {}
                                Spacer(GlanceModifier.width(8.dp))
                                Text(appName(context, fact.app), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium), modifier = GlanceModifier.width(58.dp), maxLines = 1)
                                Text(text, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp), maxLines = 1,
                                    modifier = GlanceModifier.defaultWeight())
                            }
                        }
                    }
                }
            }
            @Suppress("UNUSED_EXPRESSION") journal
        }
    }
}

class UniversalTodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = UniversalTodayWidget()
}

// ---- Dock: launch tiles with a live badge ----

class DockWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val peers = withContext(Dispatchers.IO) { readFacts(context) }
        provideContent {
            val journal by context.store.journal.collectAsState()
            val all = listOf(kasaneFact(context)) + peers
            KasaneWidgetTheme {
                Card {
                    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        all.forEach { fact ->
                            val intent = openApp(context, fact.app) ?: return@forEach
                            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(intent)), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(GlanceModifier.size(36.dp).cornerRadius(12.dp).background(ColorProvider(tint(fact.app))), contentAlignment = Alignment.Center) {
                                    Text(glyph(fact.app), style = TextStyle(color = ColorProvider(Color.White), fontSize = 16.sp, fontWeight = FontWeight.Medium))
                                }
                                Text(listOfNotNull(appName(context, fact.app), fact.badge()).joinToString(" "),
                                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center), maxLines = 1)
                            }
                        }
                    }
                }
            }
            @Suppress("UNUSED_EXPRESSION") journal
        }
    }
}

class DockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = DockWidget()
}

// ---- Quick add: one button per installed app ----

class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val buttons = withContext(Dispatchers.IO) {
            buildList {
                add(R.string.qa_task to Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_ADD_FROM_WIDGET)
                    .putExtra(MainActivity.EXTRA_PROMPT_ADD_TYPE, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                fun maybe(label: Int, app: LumeApp, link: Intent?) { if (link != null && Companion.available(context, app.pkg)) add(label to link) }
                maybe(R.string.qa_note, LumeApp.SIGIL, Ecosystem.sigilIntent(context))
                maybe(R.string.qa_review, LumeApp.REN, Ecosystem.renIntent(context, true))
                maybe(R.string.qa_puzzle, LumeApp.KATACHI, Ecosystem.katachiIntent(context))
                maybe(R.string.qa_focus, LumeApp.VOW, Ecosystem.vowIntent(context))
            }
        }
        provideContent {
            KasaneWidgetTheme {
                Card {
                    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        buttons.forEachIndexed { i, (label, intent) ->
                            if (i > 0) Spacer(GlanceModifier.width(6.dp))
                            Box(GlanceModifier.defaultWeight().background(GlanceTheme.colors.primaryContainer).cornerRadius(16.dp)
                                .padding(horizontal = 4.dp, vertical = 10.dp).clickable(actionStartActivity(intent)), contentAlignment = Alignment.Center) {
                                Text(context.getString(label), style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium, textAlign = TextAlign.Center), maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = QuickAddWidget()
}
