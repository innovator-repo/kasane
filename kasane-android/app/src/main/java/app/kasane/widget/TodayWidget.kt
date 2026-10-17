package app.kasane.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.kasane.R
import app.kasane.core.Item
import app.kasane.core.Journal
import app.kasane.core.Kind
import app.kasane.core.Tier
import app.kasane.store
import app.kasane.ui.blockOrder
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Today at a glance: how much is done, and what is still open, current block
 * first. Each open item logs in one tap, so most days the app needn't be opened.
 */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LIST))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = context.store
        provideContent {
            val journal by store.journal.collectAsState()
            KasaneWidgetTheme { TodayContent(journal) }
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 80.dp)
        private val LIST = DpSize(250.dp, 180.dp)
    }
}

@Composable
private fun TodayContent(journal: Journal) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val due = journal.items.filter { it.dueOn(today) }
    val done = due.count { Tier.of(it, journal.entry(today, it.id)) >= Tier.TARGET }
    // Rules count as kept until a slip is marked, so only things to do are listed.
    val open = blockOrder(LocalTime.now()).flatMap { block ->
        due.filter { it.block == block && it.kind != Kind.RULE && Tier.of(it, journal.entry(today, it.id)) < Tier.TARGET }
    }
    val roomy = LocalSize.current.height >= 160.dp

    WidgetCard(tab = 0) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(today.format(DateTimeFormatter.ofPattern("EEE d MMM")), style = labelStyle(), modifier = GlanceModifier.defaultWeight())
            AddTaskButton()
        }
        Text(context.getString(R.string.today_progress, done, due.size), style = headlineStyle(if (roomy) 22 else 18))
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = if (due.isEmpty()) 0f else done.toFloat() / due.size,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
        )
        Spacer(GlanceModifier.height(8.dp))
        when {
            open.isEmpty() -> Text(context.getString(R.string.widget_all_done), style = bodyStyle())
            !roomy -> Text(context.getString(R.string.widget_next, open.first().title), style = bodyStyle(), maxLines = 2)
            else -> LazyColumn(GlanceModifier.fillMaxSize()) {
                items(open, itemId = { it.id.hashCode().toLong() }) { item ->
                    OpenItemRow(item, journal, today)
                }
            }
        }
    }
}

@Composable
private fun OpenItemRow(item: Item, journal: Journal, today: LocalDate) {
    val context = LocalContext.current
    val entry = journal.entry(today, item.id)
    val detail = when (item.kind) {
        Kind.COUNT -> context.getString(R.string.count_of, entry.count, item.target)
        Kind.TIMED -> context.getString(R.string.minutes_of, entry.minutes, item.target)
        else -> null
    }
    val action = if (item.kind == Kind.CHECK) context.getString(R.string.widget_done) else "+${quickStep(item)}"
    Column(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            GlanceModifier.fillMaxWidth().background(GlanceTheme.colors.surfaceVariant).cornerRadius(10.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Text(item.title, style = bodyStyle(), maxLines = 1)
                if (detail != null) Text(detail, style = labelStyle())
            }
            Spacer(GlanceModifier.width(8.dp))
            Box(
                GlanceModifier.background(GlanceTheme.colors.primaryContainer).cornerRadius(14.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable(actionRunCallback<LogItemAction>(actionParametersOf(ItemIdKey to item.id))),
            ) {
                Text(action, style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium))
            }
        }
    }
}
