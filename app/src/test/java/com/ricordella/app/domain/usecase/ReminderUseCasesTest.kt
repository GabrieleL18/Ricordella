package com.ricordella.app.domain.usecase

import com.ricordella.app.domain.date.RecurrenceCalculator
import com.ricordella.app.domain.date.ReminderAlarmPlanner
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.Item
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderDraft
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.SnoozeOption
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderUseCasesTest {

    private val zone = ZoneId.of("Europe/Rome")
    /** "Adesso" = 29/09/2026 12:00 ora di Roma. */
    private val now = Instant.parse("2026-09-29T10:00:00Z")
    private val today = LocalDate.of(2026, 9, 29)
    private val time = TimeSource(Clock.fixed(now, zone)) { zone }

    private val repository = FakeReminderRepository()
    private val scheduler = FakeScheduler()
    private val settings = FakeSettingsRepository()
    private val planner = ReminderAlarmPlanner()

    private val save = SaveReminderUseCase(repository, settings, scheduler, planner, time)
    private val complete = CompleteReminderUseCase(repository, scheduler, RecurrenceCalculator(), time)
    private val snooze = SnoozeReminderUseCase(repository, settings, scheduler, time)
    private val delete = DeleteReminderUseCase(repository, scheduler)
    private val reopen = ReopenReminderUseCase(repository, scheduler, time)

    private fun reminder(date: LocalDate, time: LocalTime? = null) =
        Reminder(title = " Tagliando ", dueDate = date, dueTime = time, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private suspend fun saveDraft(reminder: Reminder, rule: RecurrenceRule? = null, people: Set<String> = emptySet(), items: Set<String> = emptySet()) {
        save(ReminderDraft(reminder, rule, people, items))
    }

    @Test
    fun `creazione - salva, normalizza il titolo e aggiorna le notifiche`() = runTest {
        val r = reminder(today.plusDays(3))
        saveDraft(r)
        val saved = repository.reminders.value.getValue(r.id)
        assertEquals("Tagliando", saved.title)
        assertEquals(now, saved.createdAt)
        assertNull(saved.lastNotifiedAt)
        assertEquals(1, scheduler.refreshCount)
    }

    @Test
    fun `creazione nel passato - non genera una notifica immediata`() = runTest {
        val r = reminder(today, LocalTime.of(8, 0))
        saveDraft(r)
        assertEquals(now, repository.reminders.value.getValue(r.id).lastNotifiedAt)
    }

    @Test
    fun `modifica della data - annulla il rinvio e mantiene la data di creazione`() = runTest {
        val r = reminder(today.plusDays(3))
        saveDraft(r)
        snooze(r.id, SnoozeOption.ONE_HOUR)
        assertNotNull(repository.reminders.value.getValue(r.id).snoozedUntil)

        val edited = repository.reminders.value.getValue(r.id).copy(dueDate = today.plusDays(10), createdAt = Instant.EPOCH)
        saveDraft(edited)
        val saved = repository.reminders.value.getValue(r.id)
        assertNull(saved.snoozedUntil)
        assertEquals(now, saved.createdAt)
        assertEquals(today.plusDays(10), saved.dueDate)
    }

    @Test
    fun `relazioni - promemoria collegato a persona e cosa`() = runTest {
        val person = Person(name = "Gabriele", createdAt = now, updatedAt = now)
        val car = Item(name = "Fiat Panda", createdAt = now, updatedAt = now)
        repository.people[person.id] = person
        repository.items[car.id] = car
        val r = reminder(today.plusDays(24))
        saveDraft(r, people = setOf(person.id), items = setOf(car.id))
        val loaded = repository.getReminder(r.id)!!
        assertEquals(listOf("Gabriele"), loaded.people.map { it.name })
        assertEquals(listOf("Fiat Panda"), loaded.items.map { it.name })
    }

    @Test
    fun `completamento semplice - passa a completato e annulla la notifica`() = runTest {
        val r = reminder(today)
        saveDraft(r)
        complete(r.id)
        val done = repository.reminders.value.getValue(r.id)
        assertEquals(ReminderStatus.COMPLETED, done.status)
        assertEquals(now, done.completedAt)
        assertEquals(listOf(r.id), scheduler.dismissed)
        assertEquals(1, repository.completions.size)
    }

    @Test
    fun `completamento ricorrente - avanza alla prossima occorrenza senza duplicare`() = runTest {
        val r = reminder(today)
        saveDraft(r, RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, interval = 3, startDate = today))
        complete(r.id)
        val next = repository.reminders.value.getValue(r.id)
        assertEquals(ReminderStatus.ACTIVE, next.status)
        assertEquals(LocalDate.of(2026, 12, 29), next.dueDate)
        assertEquals(1, repository.reminders.value.size)
        assertEquals(today, repository.completions.single().occurrenceDate)
    }

    @Test
    fun `completamento ricorrente in ritardo - salta le occorrenze passate`() = runTest {
        val start = today.minusDays(5)
        val r = reminder(start)
        saveDraft(r, RecurrenceRule(frequency = RecurrenceFrequency.DAILY, startDate = start))
        complete(r.id)
        assertEquals(today, repository.reminders.value.getValue(r.id).dueDate)
    }

    @Test
    fun `fine ricorrenza - l'ultima occorrenza completata chiude il promemoria`() = runTest {
        val r = reminder(today)
        saveDraft(r, RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY, startDate = today, endDate = today.plusDays(3)))
        complete(r.id)
        assertEquals(ReminderStatus.COMPLETED, repository.reminders.value.getValue(r.id).status)
    }

    @Test
    fun `completamento a chilometri - calcola la prossima scadenza dai km attuali`() = runTest {
        val car = Item(name = "Fiat Panda", odometerKm = 99_200, createdAt = now, updatedAt = now)
        repository.items[car.id] = car
        val r = reminder(today).copy(dueOdometerKm = 99_000, odometerIntervalKm = 15_000)
        saveDraft(r, items = setOf(car.id))
        complete(r.id)
        val next = repository.reminders.value.getValue(r.id)
        assertEquals(ReminderStatus.ACTIVE, next.status)
        assertEquals(114_200, next.dueOdometerKm)
        assertEquals(today.plusYears(1), next.dueDate)
    }

    @Test
    fun `riapertura di un promemoria completato`() = runTest {
        val r = reminder(today.plusDays(1))
        saveDraft(r)
        complete(r.id)
        reopen(r.id)
        assertEquals(ReminderStatus.ACTIVE, repository.reminders.value.getValue(r.id).status)
    }

    @Test
    fun `snooze - rimanda la notifica e riprogramma`() = runTest {
        val r = reminder(today, LocalTime.of(18, 0))
        saveDraft(r)
        val before = scheduler.refreshCount
        snooze(r.id, SnoozeOption.TEN_MINUTES)
        assertEquals(now.plusSeconds(600), repository.reminders.value.getValue(r.id).snoozedUntil)
        assertTrue(scheduler.refreshCount > before)
        assertEquals(listOf(r.id), scheduler.dismissed)
    }

    @Test
    fun `cancellazione - rimuove promemoria, ricorrenza e notifica`() = runTest {
        val r = reminder(today.plusDays(2))
        saveDraft(r, RecurrenceRule(frequency = RecurrenceFrequency.YEARLY, startDate = today))
        delete(r.id)
        assertTrue(repository.reminders.value.isEmpty())
        assertTrue(repository.rules.isEmpty())
        assertEquals(listOf(r.id), scheduler.dismissed)
    }

    @Test
    fun `rimozione della ricorrenza in modifica elimina la regola orfana`() = runTest {
        val r = reminder(today.plusDays(2))
        saveDraft(r, RecurrenceRule(frequency = RecurrenceFrequency.YEARLY, startDate = today))
        saveDraft(repository.reminders.value.getValue(r.id), rule = null)
        assertTrue(repository.rules.isEmpty())
        assertNull(repository.reminders.value.getValue(r.id).recurrenceRuleId)
    }
}
