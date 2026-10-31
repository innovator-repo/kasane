package app.kasane.core

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Serializable
enum class RepeatUnit { MINUTE, HOUR, DAY, WEEK, MONTH, YEAR }

/** Which day a month (or a year's chosen months) repeats on. */
@Serializable
enum class MonthMode {
    /** The same day number as the first occurrence, e.g. the 24th. */
    DAY_OF_MONTH,

    /** The same weekday slot as the first occurrence, e.g. the 4th Saturday. */
    NTH_WEEKDAY,

    /** Chosen day numbers, e.g. the 15th and 30th. Monthly only. */
    DATES,
}

@Serializable
enum class RepeatEnd { FOREVER, COUNT, UNTIL }

/**
 * How a task repeats, modelled on the Samsung Reminder app: every N minutes, hours,
 * days, weeks (on chosen weekdays), months or years, ending never, after a number
 * of times, or on a date. The first occurrence is the task's own date and time.
 */
@Serializable
data class Recurrence(
    val unit: RepeatUnit,
    val interval: Int = 1,
    /** Weekly: days of the week, 1 = Monday to 7 = Sunday. Empty means the first occurrence's weekday. */
    val weekdays: List<Int> = emptyList(),
    /** Monthly and yearly: which day of the month. */
    val monthMode: MonthMode = MonthMode.DAY_OF_MONTH,
    /** Monthly with [MonthMode.DATES]: day numbers, 1 to 31. A short month uses its last day. */
    val monthDates: List<Int> = emptyList(),
    /** Yearly: months to repeat in, 1 to 12. Empty means the first occurrence's month. */
    val months: List<Int> = emptyList(),
    val end: RepeatEnd = RepeatEnd.FOREVER,
    /** For [RepeatEnd.COUNT], the total number of occurrences, the first included. */
    val count: Int = 1,
    /** For [RepeatEnd.UNTIL], the ISO date of the last day it may fall on. */
    val until: String? = null,
) {
    private val step: Long get() = interval.coerceAtLeast(1).toLong()

    /** Every occurrence in order, from `first`, up to the end condition. */
    fun occurrences(first: LocalDateTime): Sequence<LocalDateTime> {
        val untilDate = if (end == RepeatEnd.UNTIL) until?.let { runCatching { LocalDate.parse(it) }.getOrNull() } else null
        var seq = raw(first).filter { !it.isBefore(first) }
        if (untilDate != null) seq = seq.takeWhile { !it.toLocalDate().isAfter(untilDate) }
        if (end == RepeatEnd.COUNT) seq = seq.take(count.coerceAtLeast(1))
        return seq
    }

    /** The first occurrence at or after `from`, or null once the repeat has ended. */
    fun firstAtOrAfter(first: LocalDateTime, from: LocalDateTime): LocalDateTime? {
        if (end != RepeatEnd.COUNT && (unit == RepeatUnit.MINUTE || unit == RepeatUnit.HOUR || unit == RepeatUnit.DAY)) {
            // Fixed steps: jump straight to the right index instead of walking.
            val unitMinutes = when (unit) { RepeatUnit.MINUTE -> 1L; RepeatUnit.HOUR -> 60L; else -> 1440L }
            val stepMinutes = unitMinutes * step
            val gap = ChronoUnit.MINUTES.between(first, from) + if (from.second > 0 || from.nano > 0) 1 else 0
            val k = if (gap <= 0) 0 else (gap + stepMinutes - 1) / stepMinutes
            val at = first.plusMinutes(k * stepMinutes)
            val untilDate = if (end == RepeatEnd.UNTIL) until?.let { runCatching { LocalDate.parse(it) }.getOrNull() } else null
            return at.takeIf { untilDate == null || !it.toLocalDate().isAfter(untilDate) }
        }
        return occurrences(first).dropWhile { it.isBefore(from) }.firstOrNull()
    }

    /** How the repeat reads, e.g. "Every Tue, Sat" or "Every month on the 15th, 30th (10 times)". */
    fun summary(first: LocalDate): String {
        val every = if (interval <= 1) "Every" else "Every $interval"
        val plural = if (interval <= 1) "" else "s"
        val base = when (unit) {
            RepeatUnit.MINUTE -> "$every minute$plural"
            RepeatUnit.HOUR -> "$every hour$plural"
            RepeatUnit.DAY -> "$every day$plural"
            RepeatUnit.WEEK -> {
                val days = effectiveWeekdays(first).joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
                "$every week$plural on $days"
            }
            RepeatUnit.MONTH -> "$every month$plural on " + when (monthMode) {
                MonthMode.DAY_OF_MONTH -> "the ${ordinal(first.dayOfMonth)}"
                MonthMode.NTH_WEEKDAY -> "the ${nthLabel(first)}"
                MonthMode.DATES -> "the " + effectiveDates(first).joinToString(", ") { ordinal(it) }
            }
            RepeatUnit.YEAR -> {
                val months = effectiveMonths(first).joinToString(", ") { java.time.Month.of(it).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
                "$every year$plural on " + if (monthMode == MonthMode.NTH_WEEKDAY) "the ${nthLabel(first)} of $months" else "the ${ordinal(first.dayOfMonth)} of $months"
            }
        }
        val tail = when (end) {
            RepeatEnd.FOREVER -> ""
            RepeatEnd.COUNT -> " ($count ${if (count == 1) "time" else "times"})"
            RepeatEnd.UNTIL -> until?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.let { " (until ${it.dayOfMonth} ${java.time.Month.of(it.monthValue).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${it.year})" } ?: ""
        }
        return base + tail
    }

    private fun effectiveWeekdays(first: LocalDate) = weekdays.ifEmpty { listOf(first.dayOfWeek.value) }.sorted()
    private fun effectiveDates(first: LocalDate) = monthDates.ifEmpty { listOf(first.dayOfMonth) }.sorted()
    private fun effectiveMonths(first: LocalDate) = months.ifEmpty { listOf(first.monthValue) }.sorted()

    /** Candidates in order, which may start before `first`. */
    private fun raw(first: LocalDateTime): Sequence<LocalDateTime> {
        val time = first.toLocalTime()
        val date = first.toLocalDate()
        return when (unit) {
            RepeatUnit.MINUTE -> generateSequence(0L) { it + 1 }.map { first.plusMinutes(it * step) }
            RepeatUnit.HOUR -> generateSequence(0L) { it + 1 }.map { first.plusHours(it * step) }
            RepeatUnit.DAY -> generateSequence(0L) { it + 1 }.map { first.plusDays(it * step) }
            RepeatUnit.WEEK -> {
                val days = effectiveWeekdays(date)
                val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                generateSequence(0L) { it + 1 }.flatMap { w ->
                    val weekStart = monday.plusWeeks(w * step)
                    days.asSequence().map { LocalDateTime.of(weekStart.plusDays((it - 1).toLong()), time) }
                }
            }
            RepeatUnit.MONTH -> {
                val start = YearMonth.from(date)
                generateSequence(0L) { it + 1 }.flatMap { m ->
                    daysIn(start.plusMonths(m * step), date, monthMode, monthDates).map { LocalDateTime.of(it, time) }
                }
            }
            RepeatUnit.YEAR -> {
                val months = effectiveMonths(date)
                generateSequence(0L) { it + 1 }.flatMap { y ->
                    months.asSequence().flatMap { m ->
                        daysIn(YearMonth.of(date.year + (y * step).toInt(), m), date, monthMode, emptyList())
                    }.map { LocalDateTime.of(it, time) }
                }
            }
        }
    }

    private fun daysIn(month: YearMonth, first: LocalDate, mode: MonthMode, dates: List<Int>): Sequence<LocalDate> = when (mode) {
        MonthMode.DAY_OF_MONTH -> sequenceOf(month.atDay(minOf(first.dayOfMonth, month.lengthOfMonth())))
        MonthMode.DATES -> (dates.ifEmpty { listOf(first.dayOfMonth) }).sorted()
            .map { month.atDay(minOf(it, month.lengthOfMonth())) }.distinct().asSequence()
        MonthMode.NTH_WEEKDAY -> {
            val nth = (first.dayOfMonth - 1) / 7 + 1
            val day = month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(nth, first.dayOfWeek))
            // A 5th weekday that the month lacks falls back to its last one.
            sequenceOf(if (YearMonth.from(day) == month) day else month.atEndOfMonth().with(TemporalAdjusters.lastInMonth(first.dayOfWeek)))
        }
    }

    companion object {
        fun ordinal(n: Int): String {
            val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
            return "$n$suffix"
        }

        fun nthLabel(date: LocalDate): String =
            "${ordinal((date.dayOfMonth - 1) / 7 + 1)} ${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"

        /** What a task saved before this existed means: weekly on its chosen days. */
        fun weekly(days: List<Int>) = Recurrence(RepeatUnit.WEEK, 1, days.sorted())
    }
}
