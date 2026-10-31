package app.kasane

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasane.core.Task
import app.kasane.core.Kind
import app.kasane.designsystem.Lume
import app.kasane.ui.AddHabitDialog
import app.kasane.ui.KasaneTheme
import app.kasane.ui.NowScreen
import app.kasane.ui.SettingsScreen
import app.kasane.ui.TaskDialog
import app.kasane.ui.TasksScreen

class MainActivity : ComponentActivity() {
    private val screen = mutableIntStateOf(SCREEN_NOW)
    private val addTask = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen.intValue = savedInstanceState?.getInt(EXTRA_SCREEN) ?: SCREEN_NOW
        addTask.value = savedInstanceState == null && wantsAdd(intent)
        if (savedInstanceState == null) readOpenTarget(intent)
        enableEdgeToEdge()
        setContent { KasaneTheme { Kasane(screen, addTask) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (wantsAdd(intent)) addTask.value = true
        readOpenTarget(intent)
    }

    /** A tap on a row in a widget opens that task or habit for editing. */
    private fun readOpenTarget(intent: Intent) {
        intent.getStringExtra(EXTRA_TASK_ID)?.let { taskToOpen.value = it; screen.intValue = SCREEN_NOW }
        intent.getStringExtra(EXTRA_ITEM_ID)?.let { itemToOpen.value = it; screen.intValue = SCREEN_NOW }
        intent.removeExtra(EXTRA_TASK_ID)
        intent.removeExtra(EXTRA_ITEM_ID)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_SCREEN, screen.intValue)
    }

    /** A widget's + or New task button opens the new-task form, once per tap. */
    private fun wantsAdd(intent: Intent): Boolean {
        val wants = intent.action == ACTION_ADD_FROM_WIDGET || intent.getBooleanExtra(EXTRA_PROMPT_ADD_TYPE, false)
        intent.removeExtra(EXTRA_PROMPT_ADD_TYPE)
        if (intent.action == ACTION_ADD_FROM_WIDGET) intent.action = null
        return wants
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_NOW = 0
        const val SCREEN_LISTS = 1
        const val SCREEN_SETTINGS = 2

        /** The task or habit a widget row asks to open for editing. */
        const val EXTRA_TASK_ID = "open_task"
        const val EXTRA_ITEM_ID = "open_item"

        val taskToOpen = mutableStateOf<String?>(null)
        val itemToOpen = mutableStateOf<String?>(null)

        const val ACTION_ADD_FROM_WIDGET = "app.kasane.action.ADD_FROM_WIDGET"
        const val EXTRA_PROMPT_ADD_TYPE = "extra_prompt_add_type"
    }
}

@Composable
private fun Kasane(selected: MutableIntState, addTask: MutableState<Boolean>) {
    var screen by selected
    val store = LocalContext.current.store
    val journal by store.journal.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Task?>(null) }
    val openAdd = { addTask.value = true }

    // A row tapped in a widget opens its edit form straight away.
    val requestedTask by MainActivity.taskToOpen
    LaunchedEffect(requestedTask, journal.tasks) {
        val id = requestedTask ?: return@LaunchedEffect
        journal.tasks.firstOrNull { it.id == id }?.let { editing = it }
        MainActivity.taskToOpen.value = null
    }
    val requestedItem by MainActivity.itemToOpen
    val habit = requestedItem?.let { id -> journal.items.firstOrNull { it.id == id } }

    BackHandler(enabled = screen != MainActivity.SCREEN_NOW) { screen = MainActivity.SCREEN_NOW }
    Box(Modifier.fillMaxSize().background(Lume.colors.canvas)) {
        when (screen) {
            MainActivity.SCREEN_LISTS -> TasksScreen(onBack = { screen = MainActivity.SCREEN_NOW }, onAdd = openAdd)
            MainActivity.SCREEN_SETTINGS -> SettingsScreen(onBack = { screen = MainActivity.SCREEN_NOW })
            else -> NowScreen(
                onLists = { screen = MainActivity.SCREEN_LISTS },
                onAdd = openAdd,
                onSettings = { screen = MainActivity.SCREEN_SETTINGS },
                onEdit = { editing = it },
            )
        }
    }
    if (addTask.value) {
        TaskDialog(existing = null, onDismiss = { addTask.value = false }) { title, date, minute, recurrence, alert, listId ->
            store.addTask(title, date, minute, recurrence, alert, listId)
            addTask.value = false
        }
    }
    editing?.let { task ->
        TaskDialog(existing = task, onDismiss = { editing = null }) { title, date, minute, recurrence, alert, listId ->
            store.editTask(task.id, title, date, minute, recurrence, alert, listId)
            editing = null
        }
    }
    habit?.let { item ->
        AddHabitDialog(onDismiss = { MainActivity.itemToOpen.value = null }, existing = item,
            onRemove = { MainActivity.itemToOpen.value = null; store.pauseItem(item.id) }) { changed ->
            store.updateItem(changed)
            MainActivity.itemToOpen.value = null
        }
    }
}
