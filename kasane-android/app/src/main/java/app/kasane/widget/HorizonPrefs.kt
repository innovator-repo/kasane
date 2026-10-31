package app.kasane.widget

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the Horizon remembers between draws: the chip that is selected and whether the Completed
 * group is folded open. Kept as flows so a tap redraws at once, and in preferences so it survives
 * the process being stopped.
 */
internal class HorizonPrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("kasane_widget", Context.MODE_PRIVATE)

    private val filterState = MutableStateFlow(
        prefs.getString(FILTER, null)?.let { runCatching { HorizonFilter.valueOf(it) }.getOrNull() } ?: HorizonFilter.ALL)
    // null = every list, "" = the default list, otherwise a list id
    private val listState = MutableStateFlow(prefs.getString(LIST, null))
    private val completedState = MutableStateFlow(prefs.getBoolean(COMPLETED_OPEN, true))

    val filter: StateFlow<HorizonFilter> = filterState.asStateFlow()
    val list: StateFlow<String?> = listState.asStateFlow()
    val completedOpen: StateFlow<Boolean> = completedState.asStateFlow()

    fun setFilter(filter: HorizonFilter) {
        prefs.edit().putString(FILTER, filter.name).apply()
        filterState.value = filter
    }

    /** Steps through every list, the default list, then each named list, and round again. */
    fun cycleList(listIds: List<String>) {
        val order: List<String?> = listOf(null, "") + listIds
        val next = order[(order.indexOf(listState.value).coerceAtLeast(0) + 1) % order.size]
        prefs.edit().apply { if (next == null) remove(LIST) else putString(LIST, next) }.apply()
        listState.value = next
    }

    fun toggleCompleted() {
        val next = !completedState.value
        prefs.edit().putBoolean(COMPLETED_OPEN, next).apply()
        completedState.value = next
    }

    companion object {
        private const val FILTER = "horizon_filter"
        private const val LIST = "horizon_list"
        private const val COMPLETED_OPEN = "horizon_completed_open"

        @Volatile private var instance: HorizonPrefs? = null

        fun get(context: Context): HorizonPrefs =
            instance ?: synchronized(this) { instance ?: HorizonPrefs(context.applicationContext).also { instance = it } }
    }
}
