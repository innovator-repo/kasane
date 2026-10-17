package app.kasane.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 0 missed, 1 floor, 2 target, 3 stretch. */
object Tier {
    const val MISSED = 0
    const val FLOOR = 1
    const val TARGET = 2
    const val STRETCH = 3

    fun of(item: Item, entry: Entry): Int = when (item.kind) {
        Kind.CHECK -> if (entry.done) TARGET else MISSED
        Kind.RULE -> if (entry.slipped) MISSED else TARGET
        Kind.COUNT -> amount(entry.count, item.target)
        Kind.TIMED -> amount(entry.minutes, item.target)
    }

    private fun amount(value: Int, target: Int): Int = when {
        value <= 0 -> MISSED
        target <= 0 -> TARGET
        value * 2 >= target * 3 -> STRETCH
        value >= target -> TARGET
        else -> FLOOR
    }
}

/** One habit's momentum, 0..1. */
data class Momentum(val item: Item, val value: Double)

/** A habit and how strongly it is tied to slips, as a change in probability. */
data class SlipLink(val item: Item, val lift: Double, val daysSeen: Int)

data class PeriodRate(val start: LocalDate, val rate: Double)

/**
 * Turns the journal into the grids `core.lm` works on and reads the answers
 * back. Day 0 of every grid is [MathCore.DAYS] - 1 days before `today`.
 */
class Analysis(private val journal: Journal, private val today: LocalDate, private val core: MathCore) {
    private val h = MathCore.HABITS
    private val d = MathCore.DAYS
    private val habits: List<Item> = journal.items.filter { !it.paused }.take(h)
    private val firstDay: LocalDate = today.minusDays((d - 1).toLong())

    private fun day(index: Int): LocalDate = firstDay.plusDays(index.toLong())

    /** The first day anything was logged, so days before the app was installed count for nothing. */
    private val startIndex: Int = journal.days.keys
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .minOrNull()
        ?.let { ChronoUnit.DAYS.between(firstDay, it).toInt().coerceIn(0, d - 1) }
        ?: (d - 1)

    private fun due(item: Item, index: Int): Boolean = index >= startIndex && item.dueOn(day(index))

    private fun tier(item: Item, index: Int): Int = Tier.of(item, journal.entry(day(index), item.id))

    /**
     * Momentum for every habit as of now. Each finished day decays and adds;
     * today only adds once something counts, so the morning never shows a drop
     * for a day that is still going.
     */
    fun momentum(): List<Momentum> {
        var m = DoubleArray(h)
        val decay = DoubleArray(h) { MathCore.DECAY_DAY }
        for (index in startIndex until d) {
            val isToday = index == d - 1
            val delta = DoubleArray(h)
            val sched = DoubleArray(h)
            habits.forEachIndexed { i, item ->
                if (due(item, index)) {
                    val t = tier(item, index)
                    if (!isToday || t > Tier.MISSED) {
                        sched[i] = 1.0
                        delta[i] = MathCore.DELTA[t]
                    }
                }
            }
            m = core.momentumStep(m, decay, delta, sched)
        }
        return habits.mapIndexed { i, item -> Momentum(item, m[i]) }
    }

    private fun grids(): Pair<DoubleArray, DoubleArray> {
        val done = DoubleArray(d * h)
        val sched = DoubleArray(d * h)
        for (index in 0 until d) {
            habits.forEachIndexed { i, item ->
                if (due(item, index)) {
                    sched[index * h + i] = 1.0
                    if (tier(item, index) > Tier.MISSED) done[index * h + i] = 1.0
                }
            }
        }
        return done to sched
    }

    /** Completion rate per week (Monday start) for every habit, oldest first, finished days only. */
    fun weeklyRates(): Map<Item, List<PeriodRate>> {
        val (done, sched) = grids()
        val group = DoubleArray(MathCore.PERIODS * d)
        val starts = mutableListOf<LocalDate>()
        for (index in startIndex until d - 1) {
            val date = day(index)
            val weekStart = date.minusDays((date.dayOfWeek.value - 1).toLong())
            if (starts.lastOrNull() != weekStart) starts.add(weekStart)
            val p = starts.size - 1
            if (p < MathCore.PERIODS) group[p * d + index] = 1.0
        }
        val rates = core.periodRates(group, done, sched)
        val weeks = starts.take(MathCore.PERIODS)
        return habits.withIndex().associate { (i, item) ->
            item to weeks.mapIndexed { p, start -> PeriodRate(start, rates[p * h + i]) }
        }
    }

    /**
     * Which habits, when missed one day, come before a slip on the next day
     * (lead), and which ones a slip knocks over the next day (knockOn).
     * Sorted strongest first; habits seen on fewer than 7 days are left out.
     */
    fun slipLinks(): Pair<List<SlipLink>, List<SlipLink>> {
        val relapse = habits.indexOfFirst { it.isRelapse }
        if (relapse < 0) return emptyList<SlipLink>() to emptyList()
        val (done, sched) = grids()
        fun slipOn(index: Int): Double =
            if (sched[index * h + relapse] == 1.0 && done[index * h + relapse] == 0.0) 1.0 else 0.0

        // Row r pairs cause day r with effect day r + 1; the last row stays empty.
        val leadMiss = DoubleArray(d * h)
        val leadKept = DoubleArray(d * h)
        val leadSlip = DoubleArray(d)
        val knockMiss = DoubleArray(d * h)
        val knockKept = DoubleArray(d * h)
        val knockSlip = DoubleArray(d)
        val seen = IntArray(h)
        for (r in 0 until d - 2) {
            val cause = r
            val effect = r + 1 // stops at yesterday: today is not finished
            leadSlip[r] = slipOn(effect)
            knockSlip[r] = slipOn(cause)
            for (i in 0 until h) {
                if (i == relapse) continue
                if (sched[cause * h + i] == 1.0) {
                    if (done[cause * h + i] == 1.0) leadKept[r * h + i] = 1.0 else leadMiss[r * h + i] = 1.0
                }
                // Knock-on swaps the roles: the habit is read on the effect
                // day and the slip on the cause day, so a positive lift means
                // the habit tends to be missed the day after a slip.
                if (sched[effect * h + i] == 1.0) {
                    seen[i]++
                    if (done[effect * h + i] == 1.0) knockKept[r * h + i] = 1.0 else knockMiss[r * h + i] = 1.0
                }
            }
        }
        val lead = core.slipLift(leadMiss, leadKept, leadSlip)
        val knock = core.slipLift(knockMiss, knockKept, knockSlip)
        fun rank(lifts: DoubleArray) = habits.indices
            .filter { it != relapse && seen[it] >= 7 && lifts[it] > 0.0 }
            .map { SlipLink(habits[it], lifts[it], seen[it]) }
            .sortedByDescending { it.lift }
        return rank(lead) to rank(knock)
    }

    /** True when every due rule was kept on that day. */
    fun disciplined(date: LocalDate): Boolean = journal.items
        .filter { it.kind == Kind.RULE && it.dueOn(date) }
        .none { journal.entry(date, it.id).slipped }
}
