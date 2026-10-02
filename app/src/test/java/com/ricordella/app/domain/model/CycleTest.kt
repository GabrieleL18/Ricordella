package com.ricordella.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CycleTest {
    private val profile = CycleProfile("anna", periodDays = 5, cycleDays = 28)
    private val d = { day: Int -> LocalDate.of(2026, 10, day) }

    @Test
    fun predictsNextStartAndLateDays() {
        val log = listOf(CycleEntry("anna", d(1)))
        assertEquals(LocalDate.of(2026, 10, 29), CycleCalendar.nextStart(profile, log))
        assertEquals(0, CycleCalendar.lateDays(profile, log, d(29)))
        assertEquals(3, CycleCalendar.lateDays(profile, log, LocalDate.of(2026, 11, 1)))
        assertNull(CycleCalendar.nextStart(profile, emptyList()))
    }

    @Test
    fun daysMixRealAndPredicted() {
        val log = listOf(CycleEntry("anna", d(1)), CycleEntry("bea", d(2)))
        val days = CycleCalendar.days(profile, log, d(1), LocalDate.of(2026, 11, 5))
        assertFalse(days.getValue(d(5)).predicted)
        assertNull(days[d(6)])
        assertTrue(days.getValue(d(29)).predicted)
        assertTrue(days.getValue(LocalDate.of(2026, 11, 2)).predicted)
        assertNull(days[LocalDate.of(2026, 11, 3)])
        assertTrue(days.values.all { it.personId == "anna" })
    }

    @Test
    fun startEndAndHistory() {
        var log = CycleCalendar.withStart(emptyList(), profile, d(1))
        log = CycleCalendar.withStart(log, profile, d(3)) // già dentro la mestruazione: nessun doppione
        assertEquals(1, log.size)
        log = CycleCalendar.withEnd(log, "anna", d(4))
        assertEquals(d(4), log.single().end)
        log = CycleCalendar.withStart(log, profile, LocalDate.of(2026, 10, 30))
        val rows = CycleCalendar.history(profile, log, LocalDate.of(2026, 11, 1))
        assertEquals(listOf(null, 29), rows.map { it.cycleLength }.reversed())
        assertEquals(4, rows.last().days)
    }

    @Test
    fun oldEntriesDropAfterTwoYears() {
        val old = CycleEntry("anna", LocalDate.of(2024, 1, 1))
        val log = CycleCalendar.withStart(listOf(old), profile, LocalDate.of(2026, 10, 1))
        assertEquals(1, log.size)
    }
}
