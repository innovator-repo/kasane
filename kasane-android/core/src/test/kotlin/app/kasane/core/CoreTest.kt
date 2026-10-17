package app.kasane.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.exp

class CoreTest {
    private val H = MathCore.HABITS
    private val D = MathCore.DAYS

    // The same cases as lume/host_check.c, so the Kotlin fallback and the
    // compiled Lume library are held to the same numbers.
    @Test fun momentumStepMatchesLumeHostCheck() {
        val m = DoubleArray(H) { 0.5 }.also { it[2] = 0.95 }
        val decay = DoubleArray(H) { exp(-0.08) }
        val delta = DoubleArray(H).also { it[1] = 0.18; it[2] = 0.28 }
        val sched = DoubleArray(H) { 1.0 }.also { it[3] = 0.0 }
        val out = KotlinCore.momentumStep(m, decay, delta, sched)
        assertEquals(0.5 * exp(-0.08), out[0], 1e-12)
        assertEquals(0.5 * exp(-0.08) + 0.18, out[1], 1e-12)
        assertEquals(1.0, out[2], 1e-12)
        assertEquals(0.5, out[3], 1e-12)
    }

    @Test fun periodRatesMatchLumeHostCheck() {
        val group = DoubleArray(MathCore.PERIODS * D)
        val done = DoubleArray(D * H)
        val due = DoubleArray(D * H)
        for (d in 0 until 7) { group[d] = 1.0; due[d * H] = 1.0; if (d < 5) done[d * H] = 1.0 }
        val rates = KotlinCore.periodRates(group, done, due)
        assertEquals(5.0 / (7.0 + 1e-6), rates[0], 1e-12)
        assertEquals(0.0, rates[1], 1e-12)
    }

    @Test fun slipLiftMatchesLumeHostCheck() {
        val miss = DoubleArray(D * H)
        val kept = DoubleArray(D * H)
        val slip = DoubleArray(D)
        for (d in 0 until 4) { miss[d * H] = 1.0; slip[d] = if (d < 3) 1.0 else 0.0 }
        for (d in 4 until 10) { kept[d * H] = 1.0; slip[d] = if (d == 4) 1.0 else 0.0 }
        val lift = KotlinCore.slipLift(miss, kept, slip)
        assertEquals(3.0 / (4 + 1e-6) - 1.0 / (6 + 1e-6), lift[0], 1e-12)
        assertEquals(0.0, lift[5], 1e-12)
    }

    @Test fun tiers() {
        val squats = Catalog.defaults.first { it.id == "squats" } // target 50
        assertEquals(Tier.MISSED, Tier.of(squats, Entry(count = 0)))
        assertEquals(Tier.FLOOR, Tier.of(squats, Entry(count = 20)))
        assertEquals(Tier.TARGET, Tier.of(squats, Entry(count = 50)))
        assertEquals(Tier.STRETCH, Tier.of(squats, Entry(count = 75)))
        val rule = Catalog.defaults.first { it.isRelapse }
        assertEquals(Tier.TARGET, Tier.of(rule, Entry()))
        assertEquals(Tier.MISSED, Tier.of(rule, Entry(slipped = true)))
    }

    @Test fun thirtyGoodDaysSaturateAndOneMissIsNotAReset() {
        val today = LocalDate.of(2026, 10, 17)
        var journal = Journal(items = listOf(Catalog.defaults.first { it.id == "yoga" }))
        for (back in 30 downTo 1) journal = journal.withEntry(today.minusDays(back.toLong()), "yoga", Entry(done = true))
        val full = Analysis(journal, today, KotlinCore).momentum().single().value
        assertEquals(1.0, full, 1e-9)
        // Today not done yet: no drop while the day is still going.
        assertEquals(full, Analysis(journal, today, KotlinCore).momentum().single().value, 1e-12)
        // Tomorrow, with today missed: one day of decay, not zero.
        val next = Analysis(journal, today.plusDays(1), KotlinCore).momentum().single().value
        assertEquals(exp(-0.08), next, 1e-9)
    }

    @Test fun weeklyRateCountsOnlyDueDays() {
        val today = LocalDate.of(2026, 10, 19) // Monday
        val office = Catalog.defaults.first { it.id == "office_site" }
        var journal = Journal(items = listOf(office))
        // Last week, Mon 12 to Sun 18 October: done Monday to Thursday, missed Friday.
        for (day in 12..15) journal = journal.withEntry(LocalDate.of(2026, 10, day), office.id, Entry(done = true))
        val weeks = Analysis(journal, today, KotlinCore).weeklyRates().getValue(office)
        assertEquals(LocalDate.of(2026, 10, 12), weeks.last().start)
        assertEquals(4.0 / (5.0 + 1e-6), weeks.last().rate, 1e-9)
    }

    @Test fun officeItemsAreNotDueAtWeekends() {
        val office = Catalog.defaults.first { it.id == "office_site" }
        assertTrue(office.dueOn(LocalDate.of(2026, 10, 16))) // Friday
        assertFalse(office.dueOn(LocalDate.of(2026, 10, 17))) // Saturday
    }

    @Test fun chosenWeekdaysOverrideTheDaysSetting() {
        val gym = Item("gym", "Gym", Kind.CHECK, Block.DAY, weekdays = listOf(2, 4)) // Tuesday, Thursday
        assertTrue(gym.dueOn(LocalDate.of(2026, 10, 15))) // Thursday
        assertFalse(gym.dueOn(LocalDate.of(2026, 10, 16))) // Friday
    }

    @Test fun repeatingTaskMovesToItsNextDayWhenTickedOff() {
        val friday = LocalDate.of(2026, 10, 16)
        val task = Task("t", "Bins", "2026-10-01", remindAt = 18 * 60, repeat = listOf(1, 5)) // Monday, Friday
        assertEquals(friday, task.nextDate(friday))
        val ticked = task.finish(friday, true)
        assertTrue(ticked.doneOn(friday))
        assertTrue(ticked.open)
        assertEquals(LocalDate.of(2026, 10, 19), ticked.nextDate(friday))
        assertFalse(ticked.finish(friday, false).doneOn(friday))
        // Missed days do not pile up: on Saturday the next one is Monday.
        assertEquals(LocalDate.of(2026, 10, 19), task.nextDate(friday.plusDays(1)))
        // Nothing before its start date.
        assertEquals(LocalDate.of(2026, 10, 23), task.copy(date = "2026-10-20").nextDate(friday))
    }

    @Test fun repeatingTaskRemindsAtItsNextOpenTime() {
        val task = Task("t", "Bins", "2026-10-01", remindAt = 18 * 60, repeat = listOf(1, 5))
        val journal = Journal(items = emptyList(), tasks = listOf(task))
        val morning = LocalDateTime.of(2026, 10, 16, 9, 0) // Friday
        assertEquals(LocalDateTime.of(2026, 10, 16, 18, 0), Reminders.upcoming(journal, morning).single().at)
        val evening = morning.withHour(19)
        assertEquals(LocalDateTime.of(2026, 10, 19, 18, 0), Reminders.upcoming(journal, evening).single().at)
        val ticked = journal.copy(tasks = listOf(task.finish(morning.toLocalDate(), true)))
        assertEquals(LocalDateTime.of(2026, 10, 19, 18, 0), Reminders.upcoming(ticked, morning).single().at)
    }

    @Test fun sleepLockCoversTenToEight() {
        val lock = SleepLock()
        assertTrue(lock.isLocked(LocalTime.of(22, 0)))
        assertTrue(lock.isLocked(LocalTime.of(3, 0)))
        assertTrue(lock.isLocked(LocalTime.of(7, 59)))
        assertFalse(lock.isLocked(LocalTime.of(8, 0)))
        assertFalse(lock.isLocked(LocalTime.of(21, 59)))
        assertTrue(lock.isTightening(SleepLock(21 * 60 + 30, 8 * 60)))
        assertFalse(lock.isTightening(SleepLock(23 * 60, 8 * 60)))
    }

    @Test fun looseningWaitsADayAndOnlyInDaylight() {
        val asked = LocalDateTime.of(2026, 10, 16, 23, 40)
        assertFalse(SleepLock.canConfirmLoosening(asked, asked.plusHours(2)))
        assertFalse(SleepLock.canConfirmLoosening(asked, asked.plusHours(24))) // 23:40, night
        assertTrue(SleepLock.canConfirmLoosening(asked, LocalDateTime.of(2026, 10, 18, 9, 0)))
    }

    @Test fun remindersSkipDoneItemsAndTheLock() {
        val now = LocalDateTime.of(2026, 10, 16, 8, 0) // Friday
        val journal = Journal().withEntry(now.toLocalDate(), "yoga", Entry(done = true))
        val reminders = Reminders.upcoming(journal, now)
        val yoga = reminders.first { it.key == "item:yoga" }
        assertEquals(LocalDateTime.of(2026, 10, 17, 8, 15), yoga.at) // tomorrow, today is done
        assertEquals(LocalDateTime.of(2026, 10, 16, 8, 30), reminders.first { it.key == "item:exercise" }.at)
        assertTrue(reminders.none { SleepLock().isLocked(it.at.toLocalTime()) })
        // Office reminder on Friday morning, then not again until Monday.
        assertEquals(LocalDateTime.of(2026, 10, 16, 9, 30), reminders.first { it.key == "item:office_site" }.at)
    }

    @Test fun codecRoundTripsAndAddsNewCatalogItems() {
        val journal = Journal(items = Catalog.defaults.take(3)).withEntry(LocalDate.of(2026, 10, 16), "yoga", Entry(done = true))
        val back = Codec.decode(Codec.encode(journal))
        assertEquals(Catalog.defaults.size, back.items.size)
        assertTrue(back.entry(LocalDate.of(2026, 10, 16), "yoga").done)
    }

    @Test fun missingDiaryBeforeSlipsShowsUpAsALead() {
        val today = LocalDate.of(2026, 10, 17)
        val items = Catalog.defaults.filter { it.id == "diary" || it.id == "yoga" || it.isRelapse }
        var journal = Journal(items = items)
        // 60 days: diary missed every third day, and the next day is always a slip.
        for (back in 60 downTo 1) {
            val date = today.minusDays(back.toLong())
            val skipDiary = back % 3 == 0
            journal = journal.withEntry(date, "diary", Entry(done = !skipDiary))
            journal = journal.withEntry(date, "yoga", Entry(done = true))
            journal = journal.withEntry(date, Catalog.RELAPSE_ID, Entry(slipped = (back + 1) % 3 == 0))
        }
        val (lead, _) = Analysis(journal, today, KotlinCore).slipLinks()
        assertEquals("diary", lead.first().item.id)
        assertTrue(lead.first().lift > 0.9)
    }
}
