package app.kasane.core

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)
    private fun dates(r: Recurrence, first: LocalDateTime, n: Int) = r.occurrences(first).take(n).map { it.toLocalDate().toString() }.toList()

    @Test fun everyTwoHoursSteps() {
        val r = Recurrence(RepeatUnit.HOUR, 2)
        assertEquals(at(2026, 10, 24, 16), r.firstAtOrAfter(at(2026, 10, 24, 12), at(2026, 10, 24, 15)))
        assertEquals(at(2026, 10, 24, 12), r.firstAtOrAfter(at(2026, 10, 24, 12), at(2026, 10, 24, 12)))
    }

    @Test fun everyNDaysSkipsAhead() {
        val r = Recurrence(RepeatUnit.DAY, 3)
        assertEquals(at(2026, 11, 2), r.firstAtOrAfter(at(2026, 10, 24), at(2026, 10, 31)))
    }

    @Test fun weeklyOnTuesdayAndSaturday() {
        val r = Recurrence(RepeatUnit.WEEK, 1, listOf(2, 6))
        // 24 Oct 2026 is a Saturday.
        assertEquals(listOf("2026-10-24", "2026-10-27", "2026-10-31", "2026-11-03"), dates(r, at(2026, 10, 24), 4))
        assertEquals("Every week on Tue, Sat", r.summary(LocalDate.of(2026, 10, 24)))
    }

    @Test fun everyOtherWeekAlternates() {
        val r = Recurrence(RepeatUnit.WEEK, 2, listOf(6))
        assertEquals(listOf("2026-10-24", "2026-11-07", "2026-11-21"), dates(r, at(2026, 10, 24), 3))
    }

    @Test fun monthlyOnChosenDatesClampsShortMonths() {
        val r = Recurrence(RepeatUnit.MONTH, 1, monthMode = MonthMode.DATES, monthDates = listOf(15, 30))
        assertEquals(listOf("2026-10-15", "2026-10-30", "2026-11-15", "2026-11-30", "2026-12-15"), dates(r, at(2026, 10, 1), 5).let { it })
        val feb = r.occurrences(at(2027, 2, 1)).take(2).map { it.toLocalDate().toString() }.toList()
        assertEquals(listOf("2027-02-15", "2027-02-28"), feb)
        assertEquals("Every month on the 15th, 30th", r.summary(LocalDate.of(2026, 10, 1)))
    }

    @Test fun monthlyOnTheFourthSaturday() {
        val r = Recurrence(RepeatUnit.MONTH, 1, monthMode = MonthMode.NTH_WEEKDAY)
        assertEquals(listOf("2026-10-24", "2026-11-28", "2026-12-26"), dates(r, at(2026, 10, 24), 3))
        assertEquals("Every month on the 4th Saturday", r.summary(LocalDate.of(2026, 10, 24)))
    }

    @Test fun monthlyOnThe31stUsesLastDay() {
        val r = Recurrence(RepeatUnit.MONTH)
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31"), dates(r, at(2026, 1, 31), 3))
    }

    @Test fun yearlyInChosenMonths() {
        val r = Recurrence(RepeatUnit.YEAR, 1, months = listOf(4, 10))
        assertEquals(listOf("2026-10-24", "2027-04-24", "2027-10-24"), dates(r, at(2026, 10, 24), 3))
    }

    @Test fun yearlyOnTheFourthSaturdayOfOctober() {
        val r = Recurrence(RepeatUnit.YEAR, 1, monthMode = MonthMode.NTH_WEEKDAY)
        assertEquals(listOf("2026-10-24", "2027-10-23", "2028-10-28"), dates(r, at(2026, 10, 24), 3))
        assertEquals("Every year on the 4th Saturday of Oct", r.summary(LocalDate.of(2026, 10, 24)))
    }

    @Test fun endsAfterCountOrUntilDate() {
        val count = Recurrence(RepeatUnit.DAY, end = RepeatEnd.COUNT, count = 3)
        assertEquals(3, count.occurrences(at(2026, 10, 24)).count())
        assertNull(count.firstAtOrAfter(at(2026, 10, 24), at(2026, 10, 27)))
        val until = Recurrence(RepeatUnit.DAY, end = RepeatEnd.UNTIL, until = "2026-10-26")
        assertEquals(3, until.occurrences(at(2026, 10, 24)).count())
        assertNull(until.firstAtOrAfter(at(2026, 10, 24), at(2026, 10, 27)))
        assertEquals("Every day (3 times)", count.summary(LocalDate.of(2026, 10, 24)))
        assertEquals("Every 2 hours (until 26 Oct 2026)", Recurrence(RepeatUnit.HOUR, 2, end = RepeatEnd.UNTIL, until = "2026-10-26").summary(LocalDate.of(2026, 10, 24)))
    }

    @Test fun oldWeekdayTasksStillWork() {
        val today = LocalDate.of(2026, 10, 20) // Tuesday
        val weekly = Task("d", "Gym", "2026-10-01", repeat = listOf(2))
        assertTrue(weekly.repeats && weekly.dueOn(today) && !weekly.dueOn(today.plusDays(1)))
        assertEquals(today, weekly.nextDate(today))
        assertEquals(today.plusDays(7), weekly.finish(today, true).nextDate(today))
    }

    @Test fun finishingMovesToTheNextOccurrenceAndEndsLast() {
        val today = LocalDate.of(2026, 10, 24)
        val task = Task("t", "Pay", "2026-10-24", remindAt = 540, recurrence = Recurrence(RepeatUnit.DAY, end = RepeatEnd.COUNT, count = 2))
        val one = task.finish(today, true)
        assertTrue(one.open && one.nextDate(today) == today.plusDays(1))
        val two = one.finish(today.plusDays(1), true)
        assertFalse(two.open)
    }

    @Test fun hourlyReminderComesBackWithinTheDay() {
        val task = Task("t", "Water", "2026-10-24", remindAt = 9 * 60, recurrence = Recurrence(RepeatUnit.HOUR, 2))
        assertEquals(at(2026, 10, 24, 13), task.nextOccurrence(at(2026, 10, 24, 11, 30)))
        val ticked = task.finish(LocalDate.of(2026, 10, 24), true)
        assertEquals(at(2026, 10, 25, 1), ticked.nextOccurrence(at(2026, 10, 24, 11, 30)))
    }

    @Test fun taskRemindersIgnoreTheSleepLock() {
        val task = Task("t", "Night", "2026-10-24", remindAt = 23 * 60, recurrence = Recurrence(RepeatUnit.DAY))
        val oneOff = Task("o", "Late", "2026-10-24", remindAt = 23 * 60 + 30)
        val out = Reminders.upcoming(Journal(tasks = listOf(task, oneOff)), at(2026, 10, 24, 10), SleepLock(22 * 60, 8 * 60))
        assertEquals(listOf("task:t", "task:o"), out.map { it.key })
    }

    @Test fun recurrenceSurvivesStorage() {
        val j = Journal(tasks = listOf(Task("t", "x", "2026-10-24", recurrence = Recurrence(RepeatUnit.MONTH, 2, monthMode = MonthMode.DATES, monthDates = listOf(1, 15)))))
        assertEquals(j, Codec.decode(Codec.encode(j)))
    }
}
