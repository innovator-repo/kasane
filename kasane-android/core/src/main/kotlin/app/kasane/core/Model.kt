package app.kasane.core

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate

/** How an item is logged, which decides its tier for the day. */
@Serializable
enum class Kind {
    /** Done or not done. */
    CHECK,

    /** A number, such as squats. `target` is the day's goal. */
    COUNT,

    /** Minutes, from the timer. `target` is the day's goal in minutes. */
    TIMED,

    /** A rule for the whole day. Kept unless a slip is marked. */
    RULE,
}

/** Where an item sits on the Today screen. */
@Serializable
enum class Block { MORNING, DAY, NIGHT, ALL_DAY }

/** Which days an item is due. */
@Serializable
enum class Days {
    EVERY_DAY,

    /** Monday to Friday. */
    OFFICE_DAYS;

    fun includes(date: LocalDate): Boolean = when (this) {
        EVERY_DAY -> true
        OFFICE_DAYS -> date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
    }
}

@Serializable
data class Item(
    val id: String,
    val title: String,
    val kind: Kind,
    val block: Block,
    val days: Days = Days.EVERY_DAY,
    /** Goal for COUNT (reps) and TIMED (minutes). Ignored otherwise. */
    val target: Int = 0,
    /** Minutes after midnight for the reminder, or null for none. */
    val remindAt: Int? = null,
    /** A paused item stays in history but is not due. */
    val paused: Boolean = false,
    /** Package or URL the item's shortcut opens, if any. */
    val opens: String? = null,
    /** The item a slip on this rule counts as, for the slip analysis. */
    val isRelapse: Boolean = false,
) {
    fun dueOn(date: LocalDate): Boolean = !paused && days.includes(date)
}

/** What was logged for one item on one day. */
@Serializable
data class Entry(
    val done: Boolean = false,
    val count: Int = 0,
    val minutes: Int = 0,
    val slipped: Boolean = false,
)

/** A one-off task on a date, such as an exam. */
@Serializable
data class Task(
    val id: String,
    val title: String,
    /** ISO date, yyyy-mm-dd. */
    val date: String,
    /** Minutes after midnight for the reminder, or null for none. */
    val remindAt: Int? = null,
    val done: Boolean = false,
)

/** Everything the app stores. Days are keyed by ISO date. */
@Serializable
data class Journal(
    val items: List<Item> = Catalog.defaults,
    val days: Map<String, Map<String, Entry>> = emptyMap(),
    val tasks: List<Task> = emptyList(),
) {
    fun entry(date: LocalDate, itemId: String): Entry =
        days[date.toString()]?.get(itemId) ?: Entry()

    fun withEntry(date: LocalDate, itemId: String, entry: Entry): Journal {
        val key = date.toString()
        val day = (days[key] ?: emptyMap()) + (itemId to entry)
        return copy(days = days + (key to day))
    }
}
