package app.kasane

import android.content.Context
import app.kasane.core.Alert
import app.kasane.core.Codec
import app.kasane.core.Entry
import app.kasane.core.ExceptionGate
import app.kasane.core.ExceptionState
import app.kasane.ipc.Ecosystem
import app.kasane.core.Item
import app.kasane.core.Journal
import app.kasane.core.Task
import app.kasane.reminders.AlarmScheduler
import app.kasane.widget.Widgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * The journal, kept in one JSON file and written atomically on every change.
 * Small enough (a few hundred KB after a year) to load whole.
 */
class Store(private val context: Context) {
    private val file = File(context.filesDir, "journal.json")
    private val state = MutableStateFlow(load())
    val journal: StateFlow<Journal> = state.asStateFlow()

    private fun load(): Journal = runCatching { Codec.decode(file.readText()) }.getOrElse { Journal() }

    @Synchronized
    private fun update(change: (Journal) -> Journal) {
        val next = change(state.value)
        val temp = File(file.parentFile, "journal.json.tmp")
        temp.writeText(Codec.encode(next))
        temp.renameTo(file)
        state.value = next
        AlarmScheduler.rearm(context)
        Widgets.refresh(context)
    }

    /** Stamps the minute an item is first finished, which the notification anchors learn from. */
    fun setEntry(date: LocalDate, itemId: String, entry: Entry) = update { journal ->
        val item = journal.items.firstOrNull { it.id == itemId }
        val finished = item?.finishedFor(entry, floorToday() && date == LocalDate.now()) == true
        val now = LocalTime.now()
        val stamped = when {
            !finished -> entry.copy(doneAt = null)
            entry.doneAt == null && date == LocalDate.now() -> entry.copy(doneAt = now.hour * 60 + now.minute)
            else -> entry
        }
        journal.withEntry(date, itemId, stamped)
    }

    private val prefs = context.getSharedPreferences("kasane_state", Context.MODE_PRIVATE)
    private val floor = MutableStateFlow(prefs.getString(FLOOR_DATE, null) == LocalDate.now().toString())

    /** Low-Energy Floor Mode for today: every count and timer finishes at its floor. Resets with the day. */
    val floorMode: StateFlow<Boolean> = floor.asStateFlow()

    /** Whether today is a Floor Mode day, dropping a flag left over from an earlier day. */
    fun floorToday(): Boolean {
        refreshFloor()
        return floor.value
    }

    private fun refreshFloor() {
        val on = prefs.getString(FLOOR_DATE, null) == LocalDate.now().toString()
        if (floor.value != on) floor.value = on
    }

    fun setFloorMode(on: Boolean) {
        if (on) prefs.edit().putString(FLOOR_DATE, LocalDate.now().toString()).apply()
        else prefs.edit().remove(FLOOR_DATE).apply()
        floor.value = on
        Ecosystem.sendFloorMode(context, on)
        AlarmScheduler.rearm(context)
        Widgets.refresh(context)
    }

    /** When the user asked to set this rule aside, so a late-night request has to cool for 24 hours first. */
    fun exceptionRequestedAt(itemId: String): Long? =
        prefs.getLong(REQUEST + itemId, 0L).takeIf { it > 0L }

    fun requestException(itemId: String) {
        if (exceptionRequestedAt(itemId) == null) prefs.edit().putLong(REQUEST + itemId, System.currentTimeMillis()).apply()
    }

    fun canDeclareException(itemId: String): Boolean =
        ExceptionGate.canDeclare(LocalTime.now(), exceptionRequestedAt(itemId), System.currentTimeMillis())

    /** Sets a rule aside for today as planned, if the calm-moment gate allows. Returns whether it did. */
    fun declareException(date: LocalDate, itemId: String): Boolean {
        if (!canDeclareException(itemId)) return false
        val entry = journal.value.entry(date, itemId)
        setEntry(date, itemId, entry.copy(done = true, slipped = false, exception = ExceptionState.PLANNED))
        prefs.edit().remove(REQUEST + itemId).apply()
        return true
    }

    fun addItem(item: Item) = update { it.copy(items = it.items + item) }

    fun updateItem(item: Item) = update { journal ->
        journal.copy(items = journal.items.map { if (it.id == item.id) item else it })
    }

    /** Takes an item off Today and out of reminders, keeping its history for the analysis. */
    fun pauseItem(id: String) = update { journal ->
        journal.copy(items = journal.items.map { if (it.id == id) it.copy(paused = true) else it })
    }

    /** `repeat` holds days of the week, 1 = Monday; empty for a one-off task on `date`. */
    fun addTask(title: String, date: LocalDate, remindAt: Int?, repeat: List<Int> = emptyList(), alert: Alert = Alert.MEDIUM) = update {
        it.copy(tasks = it.tasks + Task(UUID.randomUUID().toString(), title.trim(), date.toString(), remindAt,
            repeat = repeat.sorted(), alert = alert))
    }

    /** For a repeating task this ticks off only the next occurrence. */
    fun setTaskDone(id: String, done: Boolean) = update { journal ->
        val today = LocalDate.now()
        journal.copy(tasks = journal.tasks.map { if (it.id == id) it.finish(today, done) else it })
    }

    fun deleteTask(id: String) = update { journal -> journal.copy(tasks = journal.tasks.filterNot { it.id == id }) }

    private companion object {
        const val FLOOR_DATE = "floor_date"
        const val REQUEST = "exception_request_"
    }
}

val Context.store: Store get() = (applicationContext as KasaneApp).store
