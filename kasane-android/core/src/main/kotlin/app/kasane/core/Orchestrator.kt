package app.kasane.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * When Kasane nudges, learned from when each habit was actually done over the last
 * seven days, and the evening save pushes that guard Duolingo, Ren and the night items.
 */
object Orchestrator {
    const val DUOLINGO = "duolingo"
    const val REN = "ren"

    /** Anchor pushes stay inside 08:00 to 21:45, clear of the sleep lock. */
    const val WINDOW_START = 8 * 60
    const val WINDOW_END = 21 * 60 + 45
    private const val LEAD = 15
    private const val DAYS = 7L
    private const val MIN_SAMPLES = 3

    fun median(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }

    /**
     * Minute of day for the item's push: the median of the last seven days' completion
     * times minus 15 minutes, held to the window. Null until three days are on record,
     * when the reminder time the user set stays.
     */
    fun anchorMinute(item: Item, journal: Journal, today: LocalDate): Int? {
        if (item.kind == Kind.RULE) return null
        val times = (1L..DAYS).mapNotNull { back ->
            val e = journal.entry(today.minusDays(back), item.id)
            e.doneAt.takeIf { e.exception == ExceptionState.NONE }
        }
        if (times.size < MIN_SAMPLES) return null
        return (median(times)!! - LEAD).coerceIn(WINDOW_START, WINDOW_END)
    }

    /** The minute a reminder should fire: the learned anchor if there is one, else the item's own time. */
    fun reminderMinute(item: Item, journal: Journal, today: LocalDate): Int? =
        item.remindAt?.let { anchorMinute(item, journal, today) ?: it }

    enum class Save { WARN_2030, SHIELD_2100, FINAL_2130 }

    /** When each save fires, and how loud. */
    fun saveTime(save: Save): LocalTime = when (save) {
        Save.WARN_2030 -> LocalTime.of(20, 30)
        Save.SHIELD_2100 -> LocalTime.of(21, 0)
        Save.FINAL_2130 -> LocalTime.of(21, 30)
    }

    fun saveAlert(save: Save): Alert = if (save == Save.FINAL_2130) Alert.STRONG else Alert.MEDIUM

    /** Duolingo and Ren, if they are on the list and still open today. */
    fun japanesePending(journal: Journal, date: LocalDate, floorMode: Boolean = false): List<Item> =
        journal.items.filter { it.id == DUOLINGO || it.id == REN }
            .filter { it.dueOn(date) && !it.finishedFor(journal.entry(date, it.id), floorMode) }

    /** What the save at [save] guards: the Japanese pair, and for 20:30 and 21:30 the night items too. */
    fun pending(journal: Journal, date: LocalDate, save: Save, floorMode: Boolean = false): List<Item> {
        val japanese = japanesePending(journal, date, floorMode)
        if (save == Save.SHIELD_2100) return japanese
        val night = journal.items.filter {
            it.block == Block.NIGHT && it.kind != Kind.RULE && it.dueOn(date) &&
                !it.finishedFor(journal.entry(date, it.id), floorMode)
        }
        return (japanese + night).distinctBy { it.id }
    }

    /** The saves still ahead of [now] today that have something to guard. */
    fun upcomingSaves(journal: Journal, now: LocalDateTime, floorMode: Boolean = false): List<Pair<Save, LocalDateTime>> =
        Save.entries.mapNotNull { save ->
            val at = LocalDateTime.of(now.toLocalDate(), saveTime(save))
            if (at.isAfter(now) && pending(journal, now.toLocalDate(), save, floorMode).isNotEmpty()) save to at else null
        }

    /**
     * The hand-off after Duolingo is logged: Ren is the next step if it is on the list and open.
     * Past 21:00 the banner offers the 5-card quick round instead of a full session.
     */
    enum class HandOff { NONE, FULL, QUICK }

    fun handOff(journal: Journal, date: LocalDate, time: LocalTime): HandOff {
        val ren = journal.items.firstOrNull { it.id == REN } ?: return HandOff.NONE
        if (!ren.dueOn(date) || ren.finishedFor(journal.entry(date, REN))) return HandOff.NONE
        return if (time >= LocalTime.of(21, 0)) HandOff.QUICK else HandOff.FULL
    }

    /** Whether the amber border on the widget and notification should show: past 21:00 with Japanese still open. */
    fun urgent(journal: Journal, now: LocalDateTime, floorMode: Boolean = false): Boolean =
        now.toLocalTime() >= LocalTime.of(21, 0) && now.toLocalTime() < LocalTime.of(22, 0) &&
            japanesePending(journal, now.toLocalDate(), floorMode).isNotEmpty()
}

/** Chooses the single habit the Now card shows. */
object NowPick {
    /**
     * The next open habit in the block the clock is in: the one whose reminder is nearest the
     * current time, else the one with the lowest momentum. Falls back to any open habit.
     */
    fun next(
        journal: Journal,
        date: LocalDate,
        time: LocalTime,
        momentum: Map<String, Double> = emptyMap(),
        floorMode: Boolean = false,
    ): Item? {
        val open = journal.items.filter {
            it.kind != Kind.RULE && it.dueOn(date) && !it.finishedFor(journal.entry(date, it.id), floorMode)
        }
        val block = Phase.of(time).block
        val pool = open.filter { it.block == block }.ifEmpty { open }
        val now = time.hour * 60 + time.minute
        return pool.sortedWith(
            compareBy<Item> { it.remindAt?.let { r -> kotlin.math.abs(r - now) } ?: Int.MAX_VALUE }
                .thenBy { momentum[it.id] ?: 0.0 },
        ).firstOrNull()
    }
}

/** A planned exception may only be declared in a calm moment, never in the late hot hours. */
object ExceptionGate {
    const val COOLING_MILLIS = 24L * 60 * 60 * 1000
    private val COLD_UNTIL = LocalTime.of(8, 0)

    /** Before 08:00 anything goes; otherwise the request must have cooled for 24 hours. */
    fun canDeclare(time: LocalTime, requestedAtMillis: Long?, nowMillis: Long): Boolean =
        time.isBefore(COLD_UNTIL) ||
            (requestedAtMillis != null && nowMillis - requestedAtMillis >= COOLING_MILLIS)
}
