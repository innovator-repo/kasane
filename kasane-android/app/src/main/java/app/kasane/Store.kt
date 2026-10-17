package app.kasane

import android.content.Context
import app.kasane.core.Codec
import app.kasane.core.Entry
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

    fun setEntry(date: LocalDate, itemId: String, entry: Entry) = update { it.withEntry(date, itemId, entry) }

    fun addItem(item: Item) = update { it.copy(items = it.items + item) }

    /** Takes an item off Today and out of reminders, keeping its history for the analysis. */
    fun pauseItem(id: String) = update { journal ->
        journal.copy(items = journal.items.map { if (it.id == id) it.copy(paused = true) else it })
    }

    fun addTask(title: String, date: LocalDate, remindAt: Int?) = update {
        it.copy(tasks = it.tasks + Task(UUID.randomUUID().toString(), title.trim(), date.toString(), remindAt))
    }

    fun setTaskDone(id: String, done: Boolean) = update { journal ->
        journal.copy(tasks = journal.tasks.map { if (it.id == id) it.copy(done = done) else it })
    }

    fun deleteTask(id: String) = update { journal -> journal.copy(tasks = journal.tasks.filterNot { it.id == id }) }
}

val Context.store: Store get() = (applicationContext as KasaneApp).store
