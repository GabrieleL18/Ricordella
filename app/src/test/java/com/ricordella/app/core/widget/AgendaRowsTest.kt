package com.ricordella.app.core.widget

import com.ricordella.app.domain.date.RecurrenceCalculator
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderDraft
import com.ricordella.app.domain.usecase.CompleteReminderUseCase
import com.ricordella.app.domain.usecase.FakeReminderRepository
import com.ricordella.app.domain.usecase.FakeScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AgendaRowsTest {

    private val zone = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 10, 1)
    private val time = TimeSource(Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), zone)) { zone }
    private val repository = FakeReminderRepository()
    private val calculator = RecurrenceCalculator()

    private fun items(rows: List<AgendaRow>) = rows.filterIsInstance<AgendaRow.Item>()

    @Test
    fun `il ricorrente fatto oggi resta spuntato oggi e domani si potrà spuntare`() = runTest {
        val pill = Reminder(title = "Pillola", dueDate = today, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        repository.save(ReminderDraft(pill, RecurrenceRule(frequency = RecurrenceFrequency.DAILY, startDate = today), emptySet(), emptySet()))
        CompleteReminderUseCase(repository, FakeScheduler(), calculator, time)(pill.id)

        val rows = agendaRows(repository, calculator, today)
        assertEquals(listOf(AgendaRow.Day(today)), rows.take(1))
        val (done, tomorrow) = items(rows)
        assertEquals(today to true, done.occurrence to done.done)
        assertEquals(today.plusDays(1) to false, tomorrow.occurrence to tomorrow.done)
        // Domani è ora l'occorrenza corrente: si può spuntare.
        assertEquals(true, tomorrow.toggleable)
    }

    @Test
    fun `un'occorrenza futura non si spunta e i giorni vuoti lo dicono`() = runTest {
        val gym = Reminder(title = "Palestra", dueDate = today, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        repository.save(ReminderDraft(gym, RecurrenceRule(frequency = RecurrenceFrequency.DAILY, startDate = today), emptySet(), emptySet()))
        val (current, future) = items(agendaRows(repository, calculator, today))
        assertEquals(true, current.toggleable)
        assertEquals(false, future.toggleable)

        val empty = agendaRows(FakeReminderRepository(), calculator, today)
        assertEquals(listOf(AgendaRow.Day(today), AgendaRow.Empty(today), AgendaRow.Day(today.plusDays(1)), AgendaRow.Empty(today.plusDays(1))), empty)
    }
}
