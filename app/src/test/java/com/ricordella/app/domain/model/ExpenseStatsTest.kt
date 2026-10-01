package com.ricordella.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ExpenseStatsTest {

    private fun record(date: LocalDate, cents: Long, kind: ExpenseKind = ExpenseKind.SERVICE, km: Int? = null, liters: Double? = null) =
        MaintenanceRecord(itemId = "car", title = "x", date = date, odometerKm = km, costCents = cents, createdAt = Instant.EPOCH, kind = kind, liters = liters)

    @Test
    fun totalsByYearAndKind() {
        val stats = ExpenseStats.of(
            listOf(
                record(LocalDate.of(2026, 3, 1), 30_000),
                record(LocalDate.of(2026, 5, 1), 6_000, ExpenseKind.FUEL),
                record(LocalDate.of(2025, 6, 1), 20_000),
            ),
            2026,
        )
        assertEquals(36_000L, stats.totalCents)
        assertEquals(20_000L, stats.previousYearCents)
        assertEquals(mapOf(ExpenseKind.SERVICE to 30_000L, ExpenseKind.FUEL to 6_000L), stats.byKind)
    }

    @Test
    fun consumptionFromFullTankFills() {
        val stats = ExpenseStats.of(
            listOf(
                record(LocalDate.of(2026, 1, 1), 7_000, ExpenseKind.FUEL, km = 10_000, liters = 40.0),
                record(LocalDate.of(2026, 1, 20), 6_000, ExpenseKind.FUEL, km = 10_600, liters = 30.0),
                record(LocalDate.of(2026, 2, 10), 6_000, ExpenseKind.FUEL, km = 11_200, liters = 30.0),
            ),
            2026,
        )
        assertEquals(20.0, stats.kmPerLiter!!, 0.001)
    }

    @Test
    fun noConsumptionWithoutKilometres() {
        assertNull(ExpenseStats.of(listOf(record(LocalDate.of(2026, 1, 1), 5_000, ExpenseKind.FUEL, liters = 30.0)), 2026).kmPerLiter)
    }
}
