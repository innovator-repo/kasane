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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(FlowPreview::class)
object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pings = MutableSharedFlow<Context>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        // A burst of ticks redraws once, a moment after the last one, instead of once per tick.
        scope.launch {
            pings.debounce(60).collect { app ->
                HorizonWidget().updateAll(app)
                NextUpWidget().updateAll(app)
                UniversalTodayWidget().updateAll(app)
                DockWidget().updateAll(app)
            }
        }
    }

    /** Redraws every Kasane widget on the home screen, after a change or when the day turns over. */
    fun refresh(context: Context) {
        pings.tryEmit(context.applicationContext)
    }
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

/** The Horizon checkbox on a tick habit: ticks it off, or takes the tick back. */
class ToggleItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ItemIdKey] ?: return
        val store = context.store
        val journal = store.journal.value
        if (journal.items.none { it.id == id }) return
        val today = LocalDate.now()
        val entry = journal.entry(today, id)
        store.setEntry(today, id, entry.copy(done = !entry.done))
    }
}

/** The Horizon checkbox on a task: ticks off today's occurrence, or takes the tick back. */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        val store = context.store
        val task = store.journal.value.tasks.firstOrNull { it.id == id } ?: return
        store.setTaskDone(id, !task.doneOn(LocalDate.now()))
    }
}

internal val FilterKey = ActionParameters.Key<String>("filter")

/** A chip at the top of the Horizon: show everything, only habits, or only tasks. */
class SetFilterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val filter = parameters[FilterKey]?.let { runCatching { HorizonFilter.valueOf(it) }.getOrNull() } ?: return
        HorizonPrefs.get(context).setFilter(filter)
        HorizonWidget().update(context, glanceId)
    }
}

/** The Completed header folds the finished rows away and back, as in the Reminder list. */
class ToggleCompletedAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        HorizonPrefs.get(context).toggleCompleted()
        HorizonWidget().update(context, glanceId)
    }
}

/** The "tomorrow" button on a one-off task: snoozes it a day. */
class SnoozeTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        parameters[TaskIdKey]?.let { context.store.snoozeTask(it) }
    }
}

/** The list chip: tap to step through All, My tasks and each named list. */
class CycleListAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        HorizonPrefs.get(context).cycleList(context.store.journal.value.lists.map { it.id })
        HorizonWidget().update(context, glanceId)
    }
}
