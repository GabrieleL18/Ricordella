package com.ricordella.app.domain.model

import com.ricordella.app.domain.date.RecurrenceCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MoneyLedgerTest {
    private val now = Instant.EPOCH
    private val anna = Person(id = "a", name = "Anna", createdAt = now, updatedAt = now)
    private val marco = Person(id = "m", name = "Marco", createdAt = now, updatedAt = now)
    private val year = LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31)

    private fun payment(
        money: MoneyInfo? = null,
        plan: InstallmentPlan? = null,
        rule: RecurrenceRule? = null,
        due: LocalDate = LocalDate.of(2026, 1, 5),
        people: List<Person> = listOf(anna, marco),
    ) = ReminderWithLinks(
        Reminder(id = "r", title = "Affitto", type = ReminderType.PAYMENT, dueDate = due, money = money, plan = plan, createdAt = now, updatedAt = now),
        people, emptyList(), rule,
    )

    private fun entries(vararg r: ReminderWithLinks) = MoneyLedger.entries(r.toList(), year.start, year.endInclusive, RecurrenceCalculator())

    @Test
    fun equalSplitGivesLeftoverCentsToTheFirst() {
        val parts = MoneyLedger.split(MoneyInfo(), 1000, listOf("a", "m", "x"))
        assertEquals(mapOf("a" to 334L, "m" to 333L, "x" to 333L), parts)
    }

    @Test
    fun customSharesAreProportions() {
        val parts = MoneyLedger.split(MoneyInfo(shares = mapOf("a" to 60L, "m" to 40L)), 2000, listOf("a", "m"))
        assertEquals(mapOf("a" to 1200L, "m" to 800L), parts)
    }

    @Test
    fun subscriptionCountsEveryMonthOfTheYear() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, startDate = LocalDate.of(2026, 1, 5))
        val recap = MoneyLedger.recap(entries(payment(MoneyInfo(MoneyKind.SUBSCRIPTION, 1000), rule = rule)))
        assertEquals(2, recap.size)
        assertEquals(12 * 500L, recap.first { it.personId == "a" }.spentCents)
        assertEquals(12, recap.first().lines.single().count)
    }

    @Test
    fun installmentsFallingInTheYearAreCounted() {
        // 4 rate dal 15 novembre 2025: nel 2026 cadono a gennaio, febbraio e marzo.
        val plan = InstallmentPlan.create(LocalDate.of(2025, 11, 15), 5, 3000, 1, false, false)
        val all = entries(payment(plan = plan, people = listOf(anna)))
        assertEquals(3, all.size)
        assertEquals(9000L, MoneyLedger.recap(all).single().spentCents)
    }

    @Test
    fun incomeIsReceivedNotSpent() {
        val recap = MoneyLedger.recap(entries(payment(MoneyInfo(MoneyKind.INCOME, 5000), people = listOf(anna))))
        assertEquals(0L, recap.single().spentCents)
        assertEquals(5000L, recap.single().receivedCents)
    }

    @Test
    fun aPaymentWithoutAnAmountIsIgnored() {
        assertEquals(emptyList<MoneyEntry>(), entries(payment(MoneyInfo(MoneyKind.EXPENSE, 0))))
    }
}
