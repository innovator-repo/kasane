package app.kasane

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.kasane.ui.KasaneTheme
import app.kasane.ui.MomentumScreen
import app.kasane.ui.TasksScreen
import app.kasane.ui.TodayScreen
import app.kasane.ui.WeekScreen

class MainActivity : ComponentActivity() {
    private val tab = mutableIntStateOf(0)
    private val addTask = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab.intValue = savedInstanceState?.getInt(EXTRA_TAB) ?: intent.getIntExtra(EXTRA_TAB, 0)
        addTask.value = savedInstanceState == null && intent.getBooleanExtra(EXTRA_ADD_TASK, false)
        enableEdgeToEdge()
        setContent { KasaneTheme { Kasane(tab, addTask) } }
    }

    // A widget tap while the app is open switches to the widget's tab.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(EXTRA_TAB)) tab.intValue = intent.getIntExtra(EXTRA_TAB, 0)
        if (intent.getBooleanExtra(EXTRA_ADD_TASK, false)) addTask.value = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_TAB, tab.intValue)
    }

    companion object {
        /** Which tab to open on, from a widget: 0 Today, 1 Momentum, 2 Week, 3 Tasks. */
        const val EXTRA_TAB = "tab"

        /** Opens the add-task form on the Tasks tab, from a widget's add button. */
        const val EXTRA_ADD_TASK = "add_task"
    }
}

private data class Tab(val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(R.string.tab_today, Icons.Outlined.CheckCircle),
    Tab(R.string.tab_momentum, Icons.Outlined.ShowChart),
    Tab(R.string.tab_week, Icons.Outlined.Insights),
    Tab(R.string.tab_tasks, Icons.Outlined.CalendarMonth),
)

@Composable
private fun Kasane(selected: MutableIntState, addTask: MutableState<Boolean>) {
    var tab by selected
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            0 -> TodayScreen(modifier)
            1 -> MomentumScreen(modifier)
            2 -> WeekScreen(modifier)
            else -> TasksScreen(modifier, addRequested = addTask.value, onAddShown = { addTask.value = false })
        }
    }
}
