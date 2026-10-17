package app.kasane.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import app.kasane.R
import app.kasane.core.Journal
import app.kasane.core.Tier
import app.kasane.store
import java.time.LocalDate

/** Habits still open today and the tasks coming up, in one list, each done in one tap. */
class AllWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = context.store
        provideContent {
            val journal by store.journal.collectAsState()
            KasaneWidgetTheme { AllContent(journal) }
        }
    }
}

@Composable
private fun AllContent(journal: Journal) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val due = journal.items.filter { it.dueOn(today) }
    val done = due.count { Tier.of(it, journal.entry(today, it.id)) >= Tier.TARGET }
    val habits = openItems(journal, today)
    val tasks = openTasks(journal.tasks, today)

    WidgetCard(tab = 0) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(context.getString(R.string.today_progress, done, due.size), style = headlineStyle(18),
                modifier = GlanceModifier.defaultWeight())
            AddTaskButton()
        }
        Spacer(GlanceModifier.height(6.dp))
        LazyColumn(GlanceModifier.fillMaxSize()) {
            item { Section(context.getString(R.string.widget_habits)) }
            if (habits.isEmpty()) {
                item { Text(context.getString(R.string.widget_all_done), style = bodyStyle()) }
            } else {
                items(habits, itemId = { ("item:" + it.id).hashCode().toLong() }) { OpenItemRow(it, journal, today) }
            }
            item { Section(context.getString(R.string.widget_tasks)) }
            if (tasks.isEmpty()) {
                item { Text(context.getString(R.string.widget_no_tasks), style = bodyStyle()) }
            } else {
                items(tasks, itemId = { ("task:" + it.id).hashCode().toLong() }) { TaskRow(it, today) }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = labelStyle(), modifier = GlanceModifier.padding(top = 6.dp, bottom = 2.dp))
}
