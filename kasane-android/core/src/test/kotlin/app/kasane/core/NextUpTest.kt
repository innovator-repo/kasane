package app.kasane.core

import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class NextUpTest {
    private val today = LocalDate.of(2026, 10, 31)
    private fun task(id: String, date: LocalDate, at: Int? = null, done: Boolean = false) =
        Task(id, id, date.toString(), at, done = done)

    @Test fun groupsFollowReminderOrder() {
        val tasks = listOf(
            task("pay", today.plusDays(1), 540), task("late", today.minusDays(1), 600), task("b", today, 1110),
            task("a", today), task("far", today.plusDays(9)), task("done", today, done = true),
        )
        val g = NextUp.groups(tasks, today)
        assertEquals(listOf("late"), g.overdue.map { it.id })
        assertEquals(listOf("a", "b"), g.today.map { it.id })
        assertEquals(listOf("pay"), g.tomorrow.map { it.id })
    }

    @Test fun pickIsOldestOverdueThenEarliestToday() {
        val tasks = listOf(task("b", today, 1110), task("late", today.minusDays(2), 900), task("a", today, 600))
        assertEquals("late", NextUp.pick(tasks, today)?.id)
        assertEquals("a", NextUp.pick(tasks.filter { it.id != "late" }, today)?.id)
    }

    @Test fun futureTasksAreNotNext() {
        assertNull(NextUp.pick(listOf(task("t", today.plusDays(1))), today))
    }

    @Test fun summaryCountsAndUntimedIsMinusOne() {
        val tasks = listOf(task("a", today), task("b", today, 1110), task("late", today.minusDays(1)),
            task("d", today, done = true), task("old", today.minusDays(3), done = true))
        val s = NextUp.summary(tasks, today)
        assertEquals("late", s.next?.id)
        assertEquals(-1, s.nextAtMinute)
        assertEquals(3, s.openCount)
        assertEquals(1, s.doneCount)
        assertEquals(1, s.overdueCount)
    }

    @Test fun emptySummary() {
        val s = NextUp.summary(emptyList(), today)
        assertNull(s.next); assertEquals(-1, s.nextAtMinute); assertEquals(0, s.openCount)
    }
}
