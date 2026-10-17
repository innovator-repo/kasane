package app.kasane.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import app.kasane.core.Entry
import app.kasane.core.Item
import app.kasane.core.Kind
import app.kasane.store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Redraws every Kasane widget on the home screen, after a change or when the day turns over. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch {
            TodayWidget().updateAll(app)
            TasksWidget().updateAll(app)
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TodayWidget()
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TasksWidget()
}

internal val ItemIdKey = ActionParameters.Key<String>("item")
internal val TaskIdKey = ActionParameters.Key<String>("task")

/** What one tap on the widget logs: done, a set of reps, or five minutes. */
internal fun quickStep(item: Item): Int = when (item.kind) {
    Kind.COUNT -> if (item.target >= 20) 10 else 1
    Kind.TIMED -> 5
    else -> 0
}

internal fun quickLog(item: Item, entry: Entry): Entry = when (item.kind) {
    Kind.CHECK -> entry.copy(done = true)
    Kind.COUNT -> entry.copy(count = entry.count + quickStep(item))
    Kind.TIMED -> entry.copy(minutes = entry.minutes + quickStep(item))
    Kind.RULE -> entry
}

class LogItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ItemIdKey] ?: return
        val store = context.store
        val journal = store.journal.value
        val item = journal.items.firstOrNull { it.id == id } ?: return
        val today = LocalDate.now()
        store.setEntry(today, id, quickLog(item, journal.entry(today, id)))
    }
}

class FinishTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        parameters[TaskIdKey]?.let { context.store.setTaskDone(it, true) }
    }
}
