package com.ricordella.app.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeeklySummaryTest {

    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun sundayEveningSummarisesTheNextWeek() = assertEquals(monday, WeeklySummary.weekAfter(LocalDate.of(2026, 11, 1)))

    @Test
    fun alarmLateOnMondayStillSummarisesThisWeek() = assertEquals(monday, WeeklySummary.weekAfter(monday))
}
