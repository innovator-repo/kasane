package app.kasane.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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
import app.kasane.core.Task
import app.kasane.store
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The next open tasks, such as exams, with how many days are left. */
class TasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = context.store
        provideContent {
            val journal by store.journal.collectAsState()
            KasaneWidgetTheme { TasksContent(journal.tasks) }
        }
    }
}

/** Open tasks by the date they are next due. */
internal fun openTasks(tasks: List<Task>, today: LocalDate): List<Task> =
    tasks.filter { it.open && it.nextDate(today) != null }.sortedBy { it.nextDate(today) }

@Composable
private fun TasksContent(tasks: List<Task>) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val open = openTasks(tasks, today)
    WidgetCard(tab = 3) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(context.getString(R.string.widget_tasks_name), style = headlineStyle(18), modifier = GlanceModifier.defaultWeight())
            AddTaskButton()
        }
        Spacer(GlanceModifier.height(8.dp))
        if (open.isEmpty()) {
            Text(context.getString(R.string.widget_no_tasks), style = bodyStyle())
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(open, itemId = { it.id.hashCode().toLong() }) { task -> TaskRow(task, today) }
            }
        }
    }
}

@Composable
internal fun TaskRow(task: Task, today: LocalDate) {
    val context = LocalContext.current
    val date = task.nextDate(today) ?: return
    val days = ChronoUnit.DAYS.between(today, date)
    val whenText = when {
        days < 0 -> context.getString(R.string.widget_overdue)
        days == 0L -> context.getString(R.string.widget_today)
        days == 1L -> context.getString(R.string.widget_tomorrow)
        else -> context.resources.getQuantityString(R.plurals.widget_in_days, days.toInt(), days.toInt())
    }
    val urgent = days <= 1
    Column(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            GlanceModifier.fillMaxWidth().background(GlanceTheme.colors.surfaceVariant).cornerRadius(10.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Text(task.title, style = bodyStyle(), maxLines = 1)
                Text("$whenText · ${date.format(DateTimeFormatter.ofPattern("EEE d MMM"))}",
                    style = TextStyle(fontSize = 12.sp, fontWeight = if (urgent) FontWeight.Medium else FontWeight.Normal,
                        color = if (days < 0) GlanceTheme.colors.error
                        else if (urgent) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant))
            }
            Spacer(GlanceModifier.width(8.dp))
            Box(
                GlanceModifier.background(GlanceTheme.colors.primaryContainer).cornerRadius(14.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable(actionRunCallback<FinishTaskAction>(actionParametersOf(TaskIdKey to task.id))),
            ) {
                Text(context.getString(R.string.widget_done), style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}
