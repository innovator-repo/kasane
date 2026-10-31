package app.kasane.widget

import app.kasane.core.Block
import app.kasane.core.Entry
import app.kasane.core.Item
import app.kasane.core.Journal
import app.kasane.core.Kind
import app.kasane.core.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HorizonRowsTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val noon = LocalTime.of(12, 0)

    private fun list(journal: Journal) = horizonList(journal, today, noon, emptyMap(), false)

    @Test fun pendingRunsDueTasksThenHabitsThenLaterTasks() {
        val journal = Journal(
            items = listOf(Item("h", "Read", Kind.CHECK, Block.DAY)),
            tasks = listOf(
                Task("later", "Later", "2026-10-30"),
                Task("late", "Overdue", "2026-10-20"),
                Task("now", "Today", "2026-10-24"),
            ),
        )
        assertEquals(listOf("task:late", "task:now", "item:h", "task:later"), list(journal).pending.map { it.key })
    }

    @Test fun finishedHabitsAndTodaysTasksMoveToCompleted() {
        val journal = Journal(
            items = listOf(Item("h", "Read", Kind.CHECK, Block.DAY), Item("s", "Squats", Kind.COUNT, Block.DAY, target = 10)),
            days = mapOf(today.toString() to mapOf("h" to Entry(done = true), "s" to Entry(count = 10))),
            tasks = listOf(Task("t", "Call", "2026-10-24", done = true), Task("old", "Old", "2026-10-01", done = true)),
        )
        val l = list(journal)
        assertTrue(l.pending.isEmpty())
        assertEquals(setOf("item:h", "item:s", "task:t"), l.completed.map { it.key }.toSet())
        assertTrue(l.completed.all { it.done })
    }

    @Test fun countAndMinutesRowsCarryProgress() {
        val journal = Journal(
            items = listOf(Item("m", "Walk", Kind.TIMED, Block.DAY, target = 30)),
            days = mapOf(today.toString() to mapOf("m" to Entry(minutes = 20))),
        )
        val row = list(journal).pending.single() as HorizonRow.Amount
        assertEquals(20, row.value)
        assertTrue(row.minutes)
        assertEquals(20f / 30f, row.fraction, 0.001f)
    }

    @Test fun repeatingTaskTickedTodayIsCompleted() {
        val journal = Journal(tasks = listOf(Task("r", "Stretch", "2026-10-01", repeat = listOf(1, 2, 3, 4, 5, 6, 7), doneThrough = today.toString())))
        assertEquals(listOf("task:r"), list(journal).completed.map { it.key })
        assertTrue(list(journal).pending.isEmpty())
    }

    @Test fun tasksFallIntoOverdueTodayAndUpcomingBuckets() {
        val journal = Journal(
            items = listOf(Item("h", "Read", Kind.CHECK, Block.DAY)),
            tasks = listOf(Task("later", "Later", "2026-10-30"), Task("late", "Overdue", "2026-10-20"), Task("now", "Today", "2026-10-24")),
        )
        val buckets = list(journal).pending.associate { it.key to it.bucket }
        assertEquals(Bucket.OVERDUE, buckets["task:late"])
        assertEquals(Bucket.TODAY, buckets["task:now"])
        assertEquals(Bucket.TODAY, buckets["item:h"])
        assertEquals(Bucket.WEEK, buckets["task:later"])
    }

    @Test fun filterKeepsOnlyHabitsOrOnlyTasks() {
        val journal = Journal(
            items = listOf(Item("h", "Read", Kind.CHECK, Block.DAY)),
            days = mapOf(),
            tasks = listOf(Task("t", "Call", "2026-10-24"), Task("d", "Done", "2026-10-24", done = true)),
        )
        val habits = horizonList(journal, today, noon, emptyMap(), false, HorizonFilter.HABITS)
        val tasks = horizonList(journal, today, noon, emptyMap(), false, HorizonFilter.TASKS)
        assertEquals(listOf("item:h"), habits.pending.map { it.key })
        assertTrue(habits.completed.isEmpty())
        assertEquals(listOf("task:t"), tasks.pending.map { it.key })
        assertEquals(listOf("task:d"), tasks.completed.map { it.key })
    }

    @Test fun onlyOneOffOpenTasksCanBeSnoozed() {
        val journal = Journal(tasks = listOf(
            Task("once", "Once", "2026-10-24"),
            Task("weekly", "Weekly", "2026-10-24", repeat = listOf(6)),
        ))
        val rows = list(journal).pending.filterIsInstance<HorizonRow.Tick>().associateBy { it.key }
        assertTrue(rows.getValue("task:once").snoozable)
        assertTrue(!rows.getValue("task:weekly").snoozable)
    }

    @Test fun fractionIsDoneOverAll() {
        val journal = Journal(tasks = listOf(Task("a", "A", "2026-10-24"), Task("b", "B", "2026-10-24", done = true)))
        val l = list(journal)
        assertEquals(2, l.total)
        assertEquals(0.5f, l.fraction, 0.001f)
    }

    @Test fun followsSamsungReminderOrder() {
        val journal = Journal(tasks = listOf(
            Task("far", "Far", "2026-12-01"), Task("week", "Week", "2026-10-28"), Task("tom", "Tomorrow", "2026-10-25"),
            Task("t18", "Evening", "2026-10-24", remindAt = 18 * 60), Task("t09", "Morning", "2026-10-24", remindAt = 9 * 60),
            Task("none", "No time", "2026-10-24"), Task("old", "Old", "2026-10-20", remindAt = 8 * 60),
        ))
        assertEquals(listOf("task:old", "task:none", "task:t09", "task:t18", "task:tom", "task:week", "task:far"),
            list(journal).pending.map { it.key })
        val buckets = list(journal).pending.map { it.bucket }
        assertEquals(listOf(Bucket.OVERDUE, Bucket.TODAY, Bucket.TODAY, Bucket.TODAY, Bucket.TOMORROW, Bucket.WEEK, Bucket.UPCOMING), buckets)
    }

    @Test fun listFilterKeepsOneListOrTheDefault() {
        val journal = Journal(tasks = listOf(
            Task("a", "A", "2026-10-24", listId = "work"), Task("b", "B", "2026-10-24"), Task("c", "C", "2026-10-24", listId = "home")))
        fun keys(f: String?) = horizonList(journal, today, noon, emptyMap(), false, HorizonFilter.TASKS, f).pending.map { it.key }.sorted()
        assertEquals(listOf("task:a", "task:b", "task:c"), keys(null))
        assertEquals(listOf("task:a"), keys("work"))
        assertEquals(listOf("task:b"), keys(""))
    }

    @Test fun amountRowsKnowTheirHabit() {
        val journal = Journal(items = listOf(Item("s", "Squats", Kind.COUNT, Block.DAY, target = 10)))
        assertEquals("s", (list(journal).pending.single() as HorizonRow.Amount).itemId)
    }
}
