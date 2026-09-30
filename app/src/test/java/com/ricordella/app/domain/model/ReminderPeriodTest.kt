package com.ricordella.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReminderPeriodTest {

    private val anchor = LocalDate.of(2026, 2, 14)

    @Test
    fun monthAndYearCoverTheWholePeriod() {
        assertNull(ReminderPeriod(PeriodKind.ALL, anchor).range)
        assertEquals(LocalDate.of(2026, 2, 1)..LocalDate.of(2026, 2, 28), ReminderPeriod(PeriodKind.MONTH, anchor).range)
        assertEquals(LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31), ReminderPeriod(PeriodKind.YEAR, anchor).range)
    }

    @Test
    fun shiftMovesByMonthOrYear() {
        assertEquals(LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 31), ReminderPeriod(PeriodKind.MONTH, anchor).shift(1).range)
        assertEquals(LocalDate.of(2025, 1, 1)..LocalDate.of(2025, 12, 31), ReminderPeriod(PeriodKind.YEAR, anchor).shift(-1).range)
    }
}
