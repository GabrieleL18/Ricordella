package com.ricordella.app.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InstallmentPlanTest {
    private val plan = InstallmentPlan.create(LocalDate.of(2026, 1, 31), count = 4, cents = 10_000, everyMonths = 1, variable = false, deleteAfter = false)

    @Test
    fun endOfMonthDatesClampAndComeBack() {
        assertEquals(LocalDate.of(2026, 2, 28), plan.dueDate(1))
        assertEquals(LocalDate.of(2026, 3, 31), plan.dueDate(2))
        assertEquals(LocalDate.of(2026, 4, 30), plan.dueDate(3))
        assertEquals(LocalDate.of(2026, 4, 30), plan.lastDate)
    }

    @Test
    fun payingAdvancesAndFinishes() {
        var p = plan
        assertEquals(0, p.nextUnpaid())
        p = p.pay(0, LocalDate.of(2026, 2, 2), 10_500)
        assertEquals(1, p.nextUnpaid())
        assertEquals(10_500L, p.cents(0))
        p = p.pay(1, LocalDate.of(2026, 3, 1)).pay(2, LocalDate.of(2026, 3, 31)).pay(3, LocalDate.of(2026, 4, 30))
        assertNull(p.nextUnpaid())
        assertTrue(p.finished)
        assertEquals(40_500L, p.totalCents)
        assertEquals(3, p.unpay(3).nextUnpaid())
    }

    @Test
    fun variableAmountAppliesOnlyToUnpaidFollowing() {
        val p = plan.pay(0, LocalDate.of(2026, 2, 1)).withAmount(1, 12_000, following = true)
        assertEquals(listOf(10_000L, 12_000L, 12_000L, 12_000L), p.amounts)
        val only = plan.withAmount(2, 9_000, following = false)
        assertEquals(listOf(10_000L, 10_000L, 9_000L, 10_000L), only.amounts)
    }

    @Test
    fun resizeKeepsPaidAndRoundTripsAsJson() {
        val p = plan.pay(0, LocalDate.of(2026, 2, 1), 11_000).resized(6, 8_000)
        assertEquals(6, p.count)
        assertEquals(10_000L, p.amounts[0])
        assertEquals(8_000L, p.amounts[5])
        val json = Json { encodeDefaults = false }
        assertEquals(p, json.decodeFromString(InstallmentPlan.serializer(), json.encodeToString(InstallmentPlan.serializer(), p)))
    }
}
