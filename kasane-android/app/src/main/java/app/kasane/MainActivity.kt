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
import app.kasane.ui.AddFlow
import app.kasane.designsystem.rememberLumeHaptics
import app.kasane.ui.KasaneTheme
import app.kasane.ui.MomentumScreen
import app.kasane.ui.TasksScreen
import app.kasane.ui.TodayScreen
import app.kasane.ui.WeekScreen

class MainActivity : ComponentActivity() {
    private val tab = mutableIntStateOf(0)
    private val addChooser = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab.intValue = savedInstanceState?.getInt(EXTRA_TAB) ?: intent.getIntExtra(EXTRA_TAB, 0)
        addChooser.value = savedInstanceState == null && wantsAddChooser(intent)
        enableEdgeToEdge()
        setContent { KasaneTheme { Kasane(tab, addChooser) } }
    }

    // A widget tap while the app is open switches to the widget's tab.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(EXTRA_TAB)) tab.intValue = intent.getIntExtra(EXTRA_TAB, 0)
        if (wantsAddChooser(intent)) addChooser.value = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_TAB, tab.intValue)
    }

    /** A widget's + button asks which of habit, task or rule to add, once per tap. */
    private fun wantsAddChooser(intent: Intent): Boolean {
        val wants = intent.action == ACTION_ADD_FROM_WIDGET || intent.getBooleanExtra(EXTRA_PROMPT_ADD_TYPE, false)
        intent.removeExtra(EXTRA_PROMPT_ADD_TYPE)
        if (intent.action == ACTION_ADD_FROM_WIDGET) intent.action = null
        return wants
    }

    companion object {
        /** Which tab to open on, from a widget: 0 Today, 1 Momentum, 2 Week, 3 Tasks. */
        const val EXTRA_TAB = "tab"

        const val ACTION_ADD_FROM_WIDGET = "app.kasane.action.ADD_FROM_WIDGET"

        /** Asks for a habit, task or rule before the creation form, from a widget's add button. */
        const val EXTRA_PROMPT_ADD_TYPE = "extra_prompt_add_type"
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
private fun Kasane(selected: MutableIntState, addChooser: MutableState<Boolean>) {
    var tab by selected
    val openAdd = { addChooser.value = true }
    val haptics = rememberLumeHaptics()
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { if (tab != index) haptics.tick(); tab = index },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            0 -> TodayScreen(modifier, onAdd = openAdd)
            1 -> MomentumScreen(modifier)
            2 -> WeekScreen(modifier)
            else -> TasksScreen(modifier, onAdd = openAdd)
        }
    }
    if (addChooser.value) AddFlow(onDismiss = { addChooser.value = false })
}
