package com.dialogs.familycountdown

import com.dialogs.familycountdown.model.CountdownEngine
import com.dialogs.familycountdown.model.CountdownEvent
import com.dialogs.familycountdown.model.EventStore
import com.dialogs.familycountdown.model.HolidayProvider
import com.dialogs.familycountdown.model.Iso8601
import com.dialogs.familycountdown.model.SettingsStore
import com.dialogs.familycountdown.model.TimeRemaining
import com.dialogs.familycountdown.model.TimeShift
import com.dialogs.familycountdown.model.TrainSchedule
import com.dialogs.familycountdown.ui.board.DazzleRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

class CountdownTests {

    private val ny: ZoneId = ZoneId.of("America/New_York")

    private fun date(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Instant =
        LocalDateTime.of(y, m, d, h, min).atZone(ny).toInstant()

    private fun local(i: Instant) = i.atZone(ny).toLocalDateTime()

    // MARK: - Holiday math

    @Test fun easter2026() = assertEquals(LocalDate.of(2026, 4, 5), HolidayProvider.easterDate(2026))
    @Test fun easter2027() = assertEquals(LocalDate.of(2027, 3, 28), HolidayProvider.easterDate(2027))
    @Test fun thanksgiving2026() = assertEquals(LocalDate.of(2026, 11, 26), HolidayProvider.thanksgiving(2026))

    @Test fun holidaysAtLocalMidnightForTwoYears() {
        val list = HolidayProvider.holidays(date(2026, 6, 1, 12), ny)
        assertEquals(10, list.size)
        assertTrue(list.all { it.isGenerated })
        val christmas = list.first { it.label == "Christmas" }
        assertEquals(LocalDateTime.of(2026, 12, 25, 0, 0), local(christmas.targetDate))
    }

    // MARK: - TimeRemaining

    @Test fun timeRemainingBreakdown() {
        val now = date(2026, 1, 1, 0, 0)
        val target = now.plusSeconds(2 * 86_400 + 3 * 3_600 + 4 * 60)
        val r = TimeRemaining.between(now, target)
        assertEquals(2, r.days); assertEquals(3, r.hours); assertEquals(4, r.minutes); assertEquals(0, r.seconds)
    }

    @Test fun timeRemainingClampsToZero() {
        assertTrue(TimeRemaining.between(date(2026, 6, 1), date(2026, 5, 1)).isZero)
    }

    @Test fun timeRemainingCapsDays() {
        assertEquals(9999, TimeRemaining.between(date(2026, 1, 1), date(2100, 1, 1)).days)
    }

    // MARK: - Roll-forward & board

    @Test fun repeatingEventRollsToNextYear() {
        // Birthday is June 1. "Today" is 2026-06-02 — the day after — so it rolls to 2027-06-01.
        val event = CountdownEvent(label = "B-day", targetDate = date(2025, 6, 1), repeats = true)
        val now = date(2026, 6, 2, 9, 0)
        val occ = CountdownEngine.occurrence(event, now.atZone(ny).toLocalDate(), ny)
        assertEquals(LocalDateTime.of(2027, 6, 1, 0, 0), local(occ))
    }

    @Test fun repeatingEventStaysOnTheDay() {
        val event = CountdownEvent(label = "B-day", targetDate = date(2025, 6, 1), repeats = true)
        val now = date(2026, 6, 1, 9, 0)
        val occ = CountdownEngine.occurrence(event, now.atZone(ny).toLocalDate(), ny)
        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 0), local(occ))
    }

    @Test fun arrivedShowsOnTheDay() {
        val event = CountdownEvent(label = "Today", targetDate = date(2026, 6, 1, 0, 0))
        val board = CountdownEngine.board(listOf(event), date(2026, 6, 1, 10, 0), ny)
        assertEquals(1, board.size)
        assertTrue(board[0].arrived)
        assertTrue(board[0].remaining.isZero)
    }

    @Test fun notArrivedBeforeItsInstant() {
        val event = CountdownEvent(label = "Tonight", targetDate = date(2026, 6, 1, 20, 0))
        val board = CountdownEngine.board(listOf(event), date(2026, 6, 1, 10, 0), ny)
        assertFalse(board[0].arrived)
        assertEquals(10, board[0].remaining.hours)
    }

    @Test fun nonRepeatingDropsOffDayAfter() {
        val event = CountdownEvent(label = "Gone", targetDate = date(2026, 6, 1, 0, 0))
        val board = CountdownEngine.board(listOf(event), date(2026, 6, 2, 0, 1), ny)
        assertTrue(board.isEmpty())
    }

    @Test fun pinnedSortsToTop() {
        val laterPinned = CountdownEvent(label = "Pinned", targetDate = date(2026, 6, 10), pinned = true)
        val soonNormal = CountdownEvent(label = "Normal", targetDate = date(2026, 6, 5))
        val board = CountdownEngine.board(listOf(soonNormal, laterPinned), date(2026, 6, 1), ny)
        assertEquals("Pinned", board.first().label)
        assertEquals("Normal", board.last().label)
    }

    // MARK: - Store persistence (the editor's data path)

    @Test fun storeSeedsWhenFileMissing() {
        val file = tempFile()
        try {
            val seed = """[{"label":"Seeded","targetDate":"2027-01-01T00:00:00-05:00","pinned":true,"repeats":false}]"""
            val store = EventStore(file) { seed }
            assertEquals(1, store.events.value.size)
            assertTrue(file.exists())
            assertEquals("Seeded", EventStore(file) { null }.events.value.single().label)
        } finally { file.delete() }
    }

    @Test fun storeRoundTrips() {
        val file = tempFile()
        try {
            val store = EventStore(file) { "[]" }
            assertEquals(0, store.events.value.size)
            val ev = CountdownEvent(label = "Round Trip", targetDate = date(2027, 3, 3), pinned = true)
            store.add(ev)

            val reopened = EventStore(file) { null }
            assertEquals(1, reopened.events.value.size)
            val saved = reopened.events.value.single()
            assertTrue(saved.label == "Round Trip" && saved.pinned && !saved.repeats)
            assertEquals(ev.targetDate, saved.targetDate)

            reopened.update(saved.copy(label = "Edited"))
            val afterEdit = EventStore(file) { null }
            assertTrue(afterEdit.events.value.any { it.label == "Edited" })

            afterEdit.delete(afterEdit.events.value.first { it.label == "Edited" })
            assertTrue(EventStore(file) { null }.events.value.isEmpty())
        } finally { file.delete() }
    }

    @Test fun jsonMatchesWebFormat() {
        val ev = CountdownEvent(label = "X", targetDate = Iso8601.parse("2026-11-20T00:00:00-05:00")!!)
        val json = ev.toJson()
        assertEquals(setOf("label", "targetDate", "pinned", "repeats"), json.keys().asSequence().toSet())
        assertEquals(ev.targetDate, Iso8601.parse(json.getString("targetDate")))
    }

    @Test fun dateFormatRoundTrips() {
        val d = Iso8601.parse("2026-11-20T00:00:00-05:00")
        assertNotNull(d)
        assertEquals(d, Iso8601.parse(Iso8601.format(d!!)))
        assertEquals(d, Iso8601.parse("2026-11-20T05:00:00Z"))
        assertEquals(d, Iso8601.parse("2026-11-20T00:00:00.000-05:00"))
    }

    @Test fun formatUsesRequestedZoneOffset() {
        val d = Iso8601.parse("2026-11-20T00:00:00-05:00")!!
        assertEquals("2026-11-20T00:00:00-05:00", Iso8601.format(d, ny))
        assertEquals("2026-11-19T23:00:00-06:00", Iso8601.format(d, ZoneId.of("America/Chicago")))
    }

    // MARK: - Time shift (reinterpret wall-clock across zones)

    @Test fun shiftEasternToCentralPreservesWallClock() {
        val central = ZoneId.of("America/Chicago")
        val stored = Iso8601.parse("2026-11-20T00:00:00-05:00")!!
        val shifted = TimeShift.reinterpret(stored, ny, central)
        assertEquals(LocalDateTime.of(2026, 11, 20, 0, 0), shifted.atZone(central).toLocalDateTime())
        assertEquals(3600L, shifted.epochSecond - stored.epochSecond)
    }

    @Test fun shiftSameZoneIsNoOp() {
        val tz = ZoneId.of("America/Chicago")
        val d = date(2027, 5, 1, 9, 30)
        assertEquals(d, TimeShift.reinterpret(d, tz, tz))
    }

    // MARK: - Night blackout

    @Test fun blackoutHourBoundaries() {
        assertFalse(SettingsStore.isBlackoutHour(date(2026, 3, 10, 20, 59), ny))
        assertTrue(SettingsStore.isBlackoutHour(date(2026, 3, 10, 21, 0), ny))
        assertTrue(SettingsStore.isBlackoutHour(date(2026, 3, 11, 0, 0), ny))
        assertTrue(SettingsStore.isBlackoutHour(date(2026, 3, 11, 5, 59), ny))
        assertFalse(SettingsStore.isBlackoutHour(date(2026, 3, 11, 6, 0), ny))
        assertFalse(SettingsStore.isBlackoutHour(date(2026, 3, 11, 12, 0), ny))
    }

    @Test fun blackoutUsesDisplayZone() {
        val tenPmNewYork = date(2026, 3, 10, 22, 0)
        assertTrue(SettingsStore.isBlackoutHour(tenPmNewYork, ny))
        // Same instant is 7 PM in Los Angeles: not blackout there.
        assertFalse(SettingsStore.isBlackoutHour(tenPmNewYork, ZoneId.of("America/Los_Angeles")))
    }

    // MARK: - Razzle dazzle

    @Test fun dazzleReturnsRealCharAtRestAndAfterSettle() {
        val run = DazzleRun(seed = 42L)
        assertEquals('D', run.charFor(0, 0, 'D'))             // tick 0: at rest
        run.tick = DazzleRun.TICKS + 1
        assertEquals('D', run.charFor(0, 0, 'D'))             // settle tick: back to real
        assertEquals('7', run.charFor(3, 13, '7'))
    }

    @Test fun dazzleScramblesFromAlphabetDeterministically() {
        val a = DazzleRun(seed = 42L)
        val b = DazzleRun(seed = 42L)
        for (t in 1..DazzleRun.TICKS) {
            a.tick = t; b.tick = t
            for (row in 0 until 8) for (col in 0 until 40) {
                val ch = a.charFor(row, col, ' ')
                assertTrue("'$ch' not in alphabet", ch in DazzleRun.ALPHABET)
                assertEquals(ch, b.charFor(row, col, ' '))
            }
        }
        // Different tiles on the same tick don't all show the same character, across columns and rows.
        a.tick = 1
        val acrossColumns = (0 until 40).map { a.charFor(0, it, ' ') }.toSet()
        assertTrue("expected variety across columns, got $acrossColumns", acrossColumns.size > 5)
        val acrossRows = (0 until 8).map { a.charFor(it, 0, ' ') }.toSet()
        assertTrue("expected variety across rows, got $acrossRows", acrossRows.size > 2)
    }

    // MARK: - Arrival train

    @Test fun trainGapWithinBounds() {
        val random = Random(7)
        val gaps = (1..1000).map { TrainSchedule.randomGapMs(random) }
        gaps.forEach { assertTrue("gap $it out of range", it in TrainSchedule.MIN_GAP_MS..TrainSchedule.MAX_GAP_MS) }
        assertTrue("gaps should vary", gaps.toSet().size > 100)
        assertTrue(TrainSchedule.MIN_GAP_MS >= 2 * 60_000L && TrainSchedule.MAX_GAP_MS <= 5 * 60_000L)
    }

    @Test fun trainActiveOnlyWhenArrivedEnabledAndNotBlackout() {
        assertTrue(TrainSchedule.isActive(enabled = true, blackout = false, anyArrived = true))
        assertFalse(TrainSchedule.isActive(enabled = false, blackout = false, anyArrived = true))
        assertFalse(TrainSchedule.isActive(enabled = true, blackout = true, anyArrived = true))
        assertFalse(TrainSchedule.isActive(enabled = true, blackout = false, anyArrived = false))
    }

    private fun tempFile() = File(System.getProperty("java.io.tmpdir"), "fc-test-${UUID.randomUUID()}.json")
}
