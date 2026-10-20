package app.kasane.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LumeTest {
    private fun item(id: String) = Catalog.defaults.first { it.id == id }
    private val today = LocalDate.of(2026, 3, 18)

    @Test fun phasesFollowTheClock() {
        assertEquals(Phase.SANCTUARY, Phase.of(LocalTime.of(5, 59)))
        assertEquals(Phase.MORNING, Phase.of(LocalTime.of(6, 0)))
        assertEquals(Phase.MORNING, Phase.of(LocalTime.of(11, 59)))
        assertEquals(Phase.DAY, Phase.of(LocalTime.NOON))
        assertEquals(Phase.EVENING, Phase.of(LocalTime.of(20, 0)))
        assertEquals(Phase.SANCTUARY, Phase.of(LocalTime.of(22, 0)))
        assertEquals(Block.NIGHT, Phase.SANCTUARY.block)
    }

    @Test fun floorIsAFifthOfTheTarget() {
        assertEquals(10, item("squats").floorTarget)
        assertEquals(4, item("maths").floorTarget)
        assertEquals(2, item("meditation").floorTarget)
        assertEquals(1, Item("x", "x", Kind.COUNT, Block.DAY, target = 3).floorTarget)
    }

    @Test fun floorFinishesAnItemOnlyOnAFloorDayOrOnceParked() {
        val squats = item("squats")
        val atFloor = Entry(count = 10)
        assertFalse(squats.finishedFor(atFloor))
        assertTrue(squats.floorKept(atFloor))
        assertTrue(squats.finishedFor(atFloor, floorMode = true))
        assertTrue(squats.finishedFor(atFloor.copy(floorParked = true)))
        assertFalse(squats.finishedFor(Entry(count = 9), floorMode = true))
        assertTrue(squats.finishedFor(Entry(count = 50)))
        assertFalse(squats.floorKept(Entry(count = 50)))
    }

    @Test fun plannedExceptionCountsAsKeptAndFreezesMomentum() {
        val rule = item("no_sweets")
        assertTrue(rule.finishedFor(Entry.planned()))
        assertFalse(rule.finishedFor(Entry(slipped = true)))
        fun journal(day3: Entry) = Journal(items = listOf(rule), days = (0..5).associate { back ->
            today.minusDays(back.toLong()).toString() to mapOf(rule.id to if (back == 3) day3 else Entry(done = true))
        })
        val planned = Analysis(journal(Entry.planned()), today, KotlinCore).momentum().single().value
        val missed = Analysis(journal(Entry(slipped = true)), today, KotlinCore).momentum().single().value
        // A planned day is skipped, so it costs no decay; a slip does.
        assertTrue(planned > missed)
    }

    @Test fun plannedExceptionIsNotARelapse() {
        val shit = item("no_shit")
        val days = (0..20).associate { back ->
            today.minusDays(back.toLong()).toString() to mapOf(shit.id to Entry.planned())
        }
        val a = Analysis(Journal(items = listOf(shit), days = days), today, KotlinCore)
        assertTrue(a.disciplined(today))
    }

    @Test fun codecKeepsExceptionAndReadsOldEntries() {
        val journal = Journal(items = listOf(item("no_sweets")),
            days = mapOf(today.toString() to mapOf("no_sweets" to Entry.planned().copy(doneAt = 600))))
        val back = Codec.decode(Codec.encode(journal))
        assertEquals(ExceptionState.PLANNED, back.entry(today, "no_sweets").exception)
        val old = Codec.decode("""{"days":{"2026-03-18":{"squats":{"count":12}}}}""")
        assertEquals(ExceptionState.NONE, old.entry(today, "squats").exception)
    }

    @Test fun anchorIsTheMedianMinusFifteenOnceThreeDaysAreKnown() {
        val ren = item("ren")
        fun journal(vararg times: Int) = Journal(items = listOf(ren), days = times.withIndex().associate { (i, t) ->
            today.minusDays(i + 1L).toString() to mapOf("ren" to Entry(done = true, doneAt = t))
        })
        assertNull(Orchestrator.anchorMinute(ren, journal(1200, 1230), today))
        assertEquals(1215 - 15 + 0, Orchestrator.anchorMinute(ren, journal(1200, 1230, 1215), today) )
        // Held inside 08:00..21:45
        assertEquals(Orchestrator.WINDOW_END, Orchestrator.anchorMinute(ren, journal(1400, 1400, 1400), today))
        assertEquals(Orchestrator.WINDOW_START, Orchestrator.anchorMinute(ren, journal(60, 70, 80), today))
    }

    @Test fun anchorIgnoresDaysOlderThanAWeekAndPlannedDays() {
        val ren = item("ren")
        val days = mapOf(
            today.minusDays(1).toString() to mapOf("ren" to Entry(done = true, doneAt = 1100)),
            today.minusDays(2).toString() to mapOf("ren" to Entry(done = true, doneAt = 1100)),
            today.minusDays(8).toString() to mapOf("ren" to Entry(done = true, doneAt = 1100)),
            today.minusDays(3).toString() to mapOf("ren" to Entry(done = true, doneAt = 1100, exception = ExceptionState.PLANNED)),
        )
        assertNull(Orchestrator.anchorMinute(ren, Journal(items = listOf(ren), days = days), today))
    }

    @Test fun medianOfEvenListAverages() {
        assertEquals(15, Orchestrator.median(listOf(10, 20)))
        assertNull(Orchestrator.median(emptyList()))
    }

    @Test fun remindersUseTheLearnedAnchor() {
        val ren = item("ren") // 19:15
        val days = (1..4).associate { back ->
            today.minusDays(back.toLong()).toString() to mapOf("ren" to Entry(done = true, doneAt = 18 * 60 + 30))
        }
        val journal = Journal(items = listOf(ren), days = days)
        val r = Reminders.upcoming(journal, LocalDateTime.of(today, LocalTime.of(8, 0))).first { it.key == "item:ren" }
        assertEquals(LocalDateTime.of(today, LocalTime.of(18, 15)), r.at)
    }

    @Test fun duolingoAndRenGoOutAsOneRitual() {
        val journal = Journal(items = listOf(item("duolingo"), item("ren")))
        val keys = Reminders.upcoming(journal, LocalDateTime.of(today, LocalTime.of(8, 0))).map { it.key }
        assertTrue("item:duolingo" in keys)
        assertFalse("item:ren" in keys)
        val done = journal.withEntry(today, "duolingo", Entry(done = true))
        val after = Reminders.upcoming(done, LocalDateTime.of(today, LocalTime.of(8, 0))).map { it.key }
        assertTrue("item:ren" in after)
    }

    @Test fun savesFireOnlyWhileSomethingIsOpen() {
        val journal = Journal(items = listOf(item("duolingo"), item("ren"), item("diary")))
        val at8 = LocalDateTime.of(today, LocalTime.of(8, 0))
        val all = Orchestrator.upcomingSaves(journal, at8).map { it.first }
        assertEquals(listOf(Orchestrator.Save.WARN_2030, Orchestrator.Save.SHIELD_2100, Orchestrator.Save.FINAL_2130), all)
        assertEquals(Alert.STRONG, Orchestrator.saveAlert(Orchestrator.Save.FINAL_2130))
        assertEquals(Alert.MEDIUM, Orchestrator.saveAlert(Orchestrator.Save.WARN_2030))
        // Japanese done, diary open: 21:00 shield has nothing to guard.
        val j2 = journal.withEntry(today, "duolingo", Entry(done = true)).withEntry(today, "ren", Entry(done = true))
        assertEquals(listOf(Orchestrator.Save.WARN_2030, Orchestrator.Save.FINAL_2130),
            Orchestrator.upcomingSaves(j2, at8).map { it.first })
        val j3 = j2.withEntry(today, "diary", Entry(done = true))
        assertTrue(Orchestrator.upcomingSaves(j3, at8).isEmpty())
        // Past 21:00 only the final save remains.
        assertEquals(listOf(Orchestrator.Save.FINAL_2130),
            Orchestrator.upcomingSaves(journal, LocalDateTime.of(today, LocalTime.of(21, 5))).map { it.first })
    }

    @Test fun handOffFollowsDuolingoToRen() {
        val journal = Journal(items = listOf(item("duolingo"), item("ren")))
        assertEquals(Orchestrator.HandOff.FULL, Orchestrator.handOff(journal, today, LocalTime.of(19, 5)))
        assertEquals(Orchestrator.HandOff.QUICK, Orchestrator.handOff(journal, today, LocalTime.of(21, 5)))
        val done = journal.withEntry(today, "ren", Entry(done = true))
        assertEquals(Orchestrator.HandOff.NONE, Orchestrator.handOff(done, today, LocalTime.of(19, 5)))
        assertEquals(Orchestrator.HandOff.NONE, Orchestrator.handOff(Journal(), today, LocalTime.of(19, 5)))
    }

    @Test fun urgencyShowsBetweenNineAndTenWhileJapaneseIsOpen() {
        val journal = Journal(items = listOf(item("ren")))
        assertTrue(Orchestrator.urgent(journal, LocalDateTime.of(today, LocalTime.of(21, 30))))
        assertFalse(Orchestrator.urgent(journal, LocalDateTime.of(today, LocalTime.of(20, 59))))
        assertFalse(Orchestrator.urgent(journal, LocalDateTime.of(today, LocalTime.of(22, 0))))
    }

    @Test fun nowCardPicksNearestReminderInTheCurrentBlock() {
        val journal = Journal(items = listOf(item("yoga"), item("exercise"), item("maths"), item("no_sweets")))
        assertEquals("yoga", NowPick.next(journal, today, LocalTime.of(8, 10))?.id)
        assertEquals("exercise", NowPick.next(journal, today, LocalTime.of(8, 28))?.id)
        assertEquals("maths", NowPick.next(journal, today, LocalTime.of(14, 0))?.id)
        // Nothing open in the night block: falls back to any open habit, nearest reminder first.
        assertEquals("maths", NowPick.next(journal, today, LocalTime.of(21, 0))?.id)
    }

    @Test fun nowCardBreaksTiesByWeakestMomentum() {
        val journal = Journal(items = listOf(item("squats"), item("pushups")))
        val pick = NowPick.next(journal, today, LocalTime.of(9, 0), mapOf("squats" to 0.8, "pushups" to 0.2))
        assertEquals("pushups", pick?.id)
        assertNull(NowPick.next(Journal(), today, LocalTime.of(9, 0)))
    }

    @Test fun exceptionsOnlyInCalmMoments() {
        val now = 1_000_000_000_000L
        assertTrue(ExceptionGate.canDeclare(LocalTime.of(7, 59), null, now))
        assertFalse(ExceptionGate.canDeclare(LocalTime.of(23, 45), null, now))
        assertFalse(ExceptionGate.canDeclare(LocalTime.of(23, 45), now - ExceptionGate.COOLING_MILLIS + 1, now))
        assertTrue(ExceptionGate.canDeclare(LocalTime.of(23, 45), now - ExceptionGate.COOLING_MILLIS, now))
    }
}

class SleepLockPolicyTest {
    private val t0 = LocalDateTime.of(2026, 3, 18, 10, 0)
    private val base = SleepLockState() // 22:00-08:00, on
    private fun lock(sh: Int, eh: Int) = SleepLock(sh * 60, eh * 60)

    @Test fun validatesRanges() {
        assertTrue(SleepLockPolicy.valid(0, 0, 23, 59))
        assertFalse(SleepLockPolicy.valid(24, 0, 8, 0))
        assertFalse(SleepLockPolicy.valid(22, 60, 8, 0))
        assertFalse(SleepLockPolicy.valid(22, 0, -1, 0))
    }

    @Test fun tighteningAppliesAtOnce() {
        val (s, o) = SleepLockPolicy.request(base, lock(21, 9), true, t0)
        assertEquals(LockOutcome.APPLIED, o)
        assertEquals(lock(21, 9), s.lock)
    }

    @Test fun looseningWaitsAndRepeatDoesNotRestartTheTimer() {
        val (s1, o1) = SleepLockPolicy.request(base, lock(23, 7), true, t0)
        assertEquals(LockOutcome.PENDING, o1)
        assertEquals(base.lock, s1.lock)
        val (s2, o2) = SleepLockPolicy.request(s1, lock(23, 7), true, t0.plusHours(5))
        assertEquals(LockOutcome.ALREADY_PENDING, o2)
        assertEquals(t0, s2.pending?.requestedAt)
    }

    @Test fun turningOffIsALooseningAndTurningOnIsImmediate() {
        val (off, o) = SleepLockPolicy.request(base, base.lock, false, t0)
        assertEquals(LockOutcome.PENDING, o)
        assertTrue(off.on)
        val offNow = base.copy(on = false)
        val (on, o2) = SleepLockPolicy.request(offNow, base.lock, true, t0)
        assertEquals(LockOutcome.APPLIED, o2)
        assertTrue(on.on)
    }

    @Test fun sealedLockRefuses() {
        val (s, o) = SleepLockPolicy.request(base.copy(sealed = true), lock(21, 9), true, t0)
        assertEquals(LockOutcome.REFUSED_SEALED, o)
        assertEquals(base.lock, s.lock)
    }

    @Test fun sameAsCurrentIsUnchanged() {
        assertEquals(LockOutcome.UNCHANGED, SleepLockPolicy.request(base, base.lock, true, t0).second)
    }

    @Test fun confirmsOnlyAfterCoolingAndInDaylight() {
        val (pending, _) = SleepLockPolicy.request(base, lock(23, 7), true, t0)
        assertFalse(SleepLockPolicy.confirmDue(pending, t0.plusHours(23)).second)
        assertFalse(SleepLockPolicy.confirmDue(pending, t0.plusHours(24).withHour(21)).second)
        val (done, ok) = SleepLockPolicy.confirmDue(pending, t0.plusHours(25))
        assertTrue(ok)
        assertEquals(lock(23, 7), done.lock)
        assertNull(done.pending)
    }

    @Test fun surfaceStaysSanctuaryUntilEightOnlyWhileTheLockIsOn() {
        val seven = LocalTime.of(7, 0)
        assertEquals(Phase.SANCTUARY, Phase.surfaceOf(seven, SleepLock()))
        assertEquals(Phase.MORNING, Phase.surfaceOf(seven, null))
        assertEquals(Phase.MORNING, Phase.of(seven))
        assertEquals(Phase.MORNING, Phase.surfaceOf(LocalTime.of(8, 0), SleepLock()))
    }
}
