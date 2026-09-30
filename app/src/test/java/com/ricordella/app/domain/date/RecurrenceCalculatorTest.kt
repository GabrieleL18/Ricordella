package com.ricordella.app.domain.date

import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class RecurrenceCalculatorTest {

    private val calculator = RecurrenceCalculator()

    private fun rule(
        frequency: RecurrenceFrequency,
        interval: Int = 1,
        start: LocalDate = LocalDate.of(2026, 1, 15),
        end: LocalDate? = null,
        days: Set<DayOfWeek> = emptySet(),
    ) = RecurrenceRule(frequency = frequency, interval = interval, startDate = start, endDate = end, daysOfWeek = days)

    private fun next(rule: RecurrenceRule, after: LocalDate) = calculator.nextOccurrenceAfter(rule, after)

    @Test
    fun `ogni giorno`() {
        assertEquals(LocalDate.of(2026, 1, 16), next(rule(RecurrenceFrequency.DAILY), LocalDate.of(2026, 1, 15)))
    }

    @Test
    fun `ogni 15 giorni rispetta l'ancoraggio alla data di inizio`() {
        val r = rule(RecurrenceFrequency.DAILY, interval = 15)
        assertEquals(LocalDate.of(2026, 1, 30), next(r, LocalDate.of(2026, 1, 15)))
        assertEquals(LocalDate.of(2026, 2, 14), next(r, LocalDate.of(2026, 1, 31)))
    }

    @Test
    fun `ogni settimana e ogni 2 settimane`() {
        assertEquals(LocalDate.of(2026, 1, 22), next(rule(RecurrenceFrequency.WEEKLY), LocalDate.of(2026, 1, 15)))
        assertEquals(LocalDate.of(2026, 1, 29), next(rule(RecurrenceFrequency.WEEKLY, 2), LocalDate.of(2026, 1, 15)))
        assertEquals(LocalDate.of(2026, 2, 12), next(rule(RecurrenceFrequency.WEEKLY, 2), LocalDate.of(2026, 1, 29)))
    }

    @Test
    fun `settimanale su giorni specifici`() {
        // 15/01/2026 è giovedì.
        val r = rule(RecurrenceFrequency.WEEKLY, days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        assertEquals(LocalDate.of(2026, 1, 19), next(r, LocalDate.of(2026, 1, 15)))
        assertEquals(LocalDate.of(2026, 1, 22), next(r, LocalDate.of(2026, 1, 19)))
    }

    @Test
    fun `ogni mese, 3 mesi e 6 mesi`() {
        assertEquals(LocalDate.of(2026, 2, 15), next(rule(RecurrenceFrequency.MONTHLY), LocalDate.of(2026, 1, 15)))
        assertEquals(LocalDate.of(2026, 4, 15), next(rule(RecurrenceFrequency.MONTHLY, 3), LocalDate.of(2026, 1, 15)))
        assertEquals(LocalDate.of(2026, 7, 15), next(rule(RecurrenceFrequency.MONTHLY, 6), LocalDate.of(2026, 1, 15)))
        assertEquals(LocalDate.of(2027, 1, 15), next(rule(RecurrenceFrequency.MONTHLY, 6), LocalDate.of(2026, 7, 15)))
    }

    @Test
    fun `fine mese viene adattato senza perdere il giorno originale`() {
        val r = rule(RecurrenceFrequency.MONTHLY, start = LocalDate.of(2026, 1, 31))
        assertEquals(LocalDate.of(2026, 2, 28), next(r, LocalDate.of(2026, 1, 31)))
        assertEquals(LocalDate.of(2026, 3, 31), next(r, LocalDate.of(2026, 2, 28)))
        assertEquals(LocalDate.of(2026, 4, 30), next(r, LocalDate.of(2026, 3, 31)))
    }

    @Test
    fun `ogni anno, anche a cavallo di fine anno`() {
        val r = rule(RecurrenceFrequency.YEARLY, start = LocalDate.of(2025, 11, 18))
        assertEquals(LocalDate.of(2026, 11, 18), next(r, LocalDate.of(2025, 12, 31)))
        assertEquals(LocalDate.of(2027, 11, 18), next(r, LocalDate.of(2026, 11, 18)))
    }

    @Test
    fun `anno bisestile - il 29 febbraio diventa 28 negli anni non bisestili`() {
        val r = rule(RecurrenceFrequency.YEARLY, start = LocalDate.of(2024, 2, 29))
        assertEquals(LocalDate.of(2025, 2, 28), next(r, LocalDate.of(2024, 2, 29)))
        assertEquals(LocalDate.of(2028, 2, 29), next(r, LocalDate.of(2027, 12, 31)))
    }

    @Test
    fun `intervallo personalizzato in anni`() {
        val r = rule(RecurrenceFrequency.YEARLY, interval = 2, start = LocalDate.of(2026, 5, 5))
        assertEquals(LocalDate.of(2028, 5, 5), next(r, LocalDate.of(2026, 5, 5)))
    }

    @Test
    fun `fine ricorrenza - nessuna occorrenza dopo la data di fine`() {
        val r = rule(RecurrenceFrequency.MONTHLY, end = LocalDate.of(2026, 3, 1))
        assertEquals(LocalDate.of(2026, 2, 15), next(r, LocalDate.of(2026, 1, 15)))
        assertNull(next(r, LocalDate.of(2026, 2, 15)))
    }

    @Test
    fun `date passate - la successiva a una data lontana è calcolata direttamente`() {
        val r = rule(RecurrenceFrequency.DAILY, start = LocalDate.of(2000, 1, 1))
        assertEquals(LocalDate.of(2026, 6, 2), next(r, LocalDate.of(2026, 6, 1)))
    }

    @Test
    fun `occorrenze nel periodo`() {
        val r = rule(RecurrenceFrequency.WEEKLY, start = LocalDate.of(2026, 9, 1))
        val dates = calculator.occurrencesBetween(r, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        assertEquals(listOf(1, 8, 15, 22, 29), dates.map { it.dayOfMonth })
    }

    @Test
    fun `occorrenze di un promemoria - completato non proietta date future`() {
        val r = rule(RecurrenceFrequency.WEEKLY, start = LocalDate.of(2026, 9, 1))
        val reminder = Reminder(title = "Test", dueDate = LocalDate.of(2026, 9, 8), createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val from = LocalDate.of(2026, 9, 1)
        val to = LocalDate.of(2026, 9, 30)
        assertEquals(listOf(8, 15, 22, 29), calculator.occurrencesInRange(reminder, r, from, to).map { it.dayOfMonth })
        val completed = reminder.copy(status = ReminderStatus.COMPLETED)
        assertEquals(listOf(8), calculator.occurrencesInRange(completed, r, from, to).map { it.dayOfMonth })
    }
}
