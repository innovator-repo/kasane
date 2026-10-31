package app.kasane.ipc

import app.kasane.core.NextUpSummary
import app.kasane.core.Task
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CompanionFactsTest {
    @Test fun absentRowMakesNoFact() {
        for (app in LumeApp.entries) assertNull(AppFact.from(app, null))
    }

    @Test fun missingColumnsAreAbsentNotZero() {
        assertNull(AppFact.from(LumeApp.REN, mapOf("retention" to 90L)))
        assertNull(AppFact.from(LumeApp.KATACHI, mapOf("solved" to 3L)))
        assertNull(AppFact.from(LumeApp.VOW, mapOf("reclaimedMinutes" to 30L)))
        assertNull(AppFact.from(LumeApp.SIGIL, mapOf("sealedCount" to 2L)))
    }

    @Test fun renAndKatachiReadCounts() {
        assertEquals(12, AppFact.from(LumeApp.REN, mapOf("due" to 12L, "newToday" to 5L))?.count)
        val k = AppFact.from(LumeApp.KATACHI, mapOf("solved" to 6L, "target" to 10L))!!
        assertEquals(4, k.count)
        assertEquals("4", k.badge())
        assertEquals(0, AppFact.from(LumeApp.KATACHI, mapOf("solved" to 12L, "target" to 10L))?.count)
        assertNull(AppFact.from(LumeApp.KATACHI, mapOf("solved" to 10L, "target" to 10L))?.badge())
    }

    @Test fun vowFocusFlag() {
        val on = AppFact.from(LumeApp.VOW, mapOf("focusOn" to 1L, "profileName" to "Deep work"))!!
        assertEquals(true, on.flag); assertEquals("Deep work", on.text); assertEquals("on", on.badge())
        assertEquals("off", AppFact.from(LumeApp.VOW, mapOf("focusOn" to 0L))?.badge())
    }

    @Test fun sigilNotesAndLastTitle() {
        val s = AppFact.from(LumeApp.SIGIL, mapOf("noteCount" to 12L, "lastTitle" to "Passport"))!!
        assertEquals(12, s.count); assertEquals("Passport", s.text)
        assertEquals("Passport", AppFact.from(LumeApp.SIGIL, mapOf("lastTitle" to "Passport"))?.text)
        assertNull(AppFact.from(LumeApp.SIGIL, mapOf("lastTitle" to "  "))?.text)
    }

    @Test fun kasaneRowUsesMinuteMinusOneAsNoTime() {
        val f = AppFact.from(LumeApp.KASANE, mapOf("openCount" to 3L, "doneCount" to 4L, "nextTitle" to "Dentist", "nextAtMinute" to -1L))!!
        assertNull(f.minute); assertEquals(7, f.total); assertEquals("Dentist", f.text)
        assertEquals(1110, AppFact.from(LumeApp.KASANE, mapOf("openCount" to 1L, "nextAtMinute" to 1110L))?.minute)
    }

    @Test fun statusRowHasNoMomentumAndMatchesColumns() {
        assertFalse(KasaneStatusRow.COLUMNS.any { it.contains("momentum", ignoreCase = true) })
        val summary = NextUpSummary(Task("1", "Dentist", "2026-10-31", 1110), 1110, 3, 4, 1)
        val values = KasaneStatusRow.values(summary, "DAY", 2, 3)
        assertEquals(KasaneStatusRow.COLUMNS.size, values.size)
        assertEquals("Dentist", values[KasaneStatusRow.COLUMNS.indexOf("nextTitle")])
        assertEquals(1110, values[KasaneStatusRow.COLUMNS.indexOf("nextAtMinute")])
        assertEquals(3, values[KasaneStatusRow.COLUMNS.indexOf("openCount")])
        assertEquals(4, values[KasaneStatusRow.COLUMNS.indexOf("doneCount")])
        val empty = KasaneStatusRow.values(NextUpSummary(null, -1, 0, 0, 0), "DAY", 0, 0)
        assertEquals("", empty[KasaneStatusRow.COLUMNS.indexOf("nextTitle")])
        assertEquals(-1, empty[KasaneStatusRow.COLUMNS.indexOf("nextAtMinute")])
    }
}
