package com.ricordella.app.domain.usecase

import com.ricordella.app.domain.date.RecurrenceCalculator
import com.ricordella.app.domain.date.ReminderAlarmPlanner
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.date.nextAlarmDate
import com.ricordella.app.domain.date.nextRing
import com.ricordella.app.domain.model.Note
import com.ricordella.app.domain.model.NoteLine
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderDraft
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.model.SnoozeOption
import com.ricordella.app.domain.model.toList
import com.ricordella.app.domain.model.toText
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AlarmsAndNotesTest {

    private val zone = ZoneId.of("Europe/Rome")
    /** "Adesso" = sabato 3/10/2026 12:00 ora di Roma. */
    private val now = Instant.parse("2026-10-03T10:00:00Z")
    private val nowLocal = LocalDateTime.of(2026, 10, 3, 12, 0)
    private val time = TimeSource(Clock.fixed(now, zone)) { zone }
    private val repository = FakeReminderRepository()
    private val scheduler = FakeScheduler()
    private val settings = FakeSettingsRepository()
    private val save = SaveReminderUseCase(repository, settings, scheduler, ReminderAlarmPlanner(), time)
    private val complete = CompleteReminderUseCase(repository, scheduler, RecurrenceCalculator(), time)
    private val setEnabled = SetAlarmEnabledUseCase(repository, save, scheduler, time)
    private val snooze = SnoozeReminderUseCase(repository, settings, scheduler, time)

    private fun alarm(date: LocalDate, at: LocalTime) =
        Reminder(title = "Sveglia", type = ReminderType.ALARM, dueDate = date, dueTime = at, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    // --- sveglie ---

    @Test
    fun `prossimo giorno - oggi se l'ora non e passata, altrimenti domani, e solo nei giorni scelti`() {
        assertEquals(LocalDate.of(2026, 10, 3), nextAlarmDate(LocalTime.of(18, 0), emptySet(), nowLocal))
        assertEquals(LocalDate.of(2026, 10, 4), nextAlarmDate(LocalTime.of(7, 0), emptySet(), nowLocal))
        // Sabato 12:00, sveglia alle 7 lun-ven: lunedì 5.
        assertEquals(LocalDate.of(2026, 10, 5), nextAlarmDate(LocalTime.of(7, 0), DayOfWeek.entries.take(5).toSet(), nowLocal))
        // In pausa fino al 10 (sabato) con giorni lun-ven: lunedì 12.
        assertEquals(LocalDate.of(2026, 10, 12), nextAlarmDate(LocalTime.of(7, 0), DayOfWeek.entries.take(5).toSet(), nowLocal, LocalDate.of(2026, 10, 10)))
    }

    @Test
    fun `una sveglia che non si ripete, fermata, si spegne e resta attiva nell'elenco`() = runTest {
        val a = alarm(LocalDate.of(2026, 10, 3), LocalTime.of(11, 0))
        save(ReminderDraft(a, null, emptySet(), emptySet()))
        complete(a.id)
        val saved = repository.reminders.value.getValue(a.id)
        assertEquals(ReminderStatus.ACTIVE, saved.status)
        assertFalse(saved.notificationsEnabled)
        assertNull(saved.nextRing(nowLocal, zone))
    }

    @Test
    fun `riaccesa, la sveglia suona alla prossima occasione`() = runTest {
        val a = alarm(LocalDate.of(2026, 9, 1), LocalTime.of(7, 30)).copy(notificationsEnabled = false)
        save(ReminderDraft(a, null, emptySet(), emptySet()))
        setEnabled(a.id, true)
        val saved = repository.reminders.value.getValue(a.id)
        assertTrue(saved.notificationsEnabled)
        assertEquals(LocalDate.of(2026, 10, 4), saved.dueDate)
        assertEquals(LocalDateTime.of(2026, 10, 4, 7, 30), saved.nextRing(nowLocal, zone))
        setEnabled(a.id, false)
        assertFalse(repository.reminders.value.getValue(a.id).notificationsEnabled)
    }

    @Test
    fun `una sveglia ripetuta, fermata, passa al giorno dopo e resta accesa`() = runTest {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, startDate = LocalDate.of(2026, 10, 3))
        val a = alarm(LocalDate.of(2026, 10, 3), LocalTime.of(11, 0))
        save(ReminderDraft(a, rule, emptySet(), emptySet()))
        complete(a.id)
        val saved = repository.reminders.value.getValue(a.id)
        assertTrue(saved.notificationsEnabled)
        assertEquals(LocalDate.of(2026, 10, 4), saved.dueDate)
    }

    // --- rimando dalla notifica ---

    @Test
    fun `domani - restituisce l'istante e la notifica torna domani mattina`() = runTest {
        val r = Reminder(title = "Pagare", dueDate = LocalDate.of(2026, 10, 3), createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        save(ReminderDraft(r, null, emptySet(), emptySet()))
        val until = snooze(r.id, SnoozeOption.TomorrowMorning)
        assertNotNull(until)
        assertEquals(LocalDateTime.of(2026, 10, 4, 9, 0), LocalDateTime.ofInstant(until, zone))
        assertEquals(until, repository.reminders.value.getValue(r.id).snoozedUntil)
    }

    // --- note ---

    @Test
    fun `lista della spesa - totale, carrello e quantita`() {
        val note = Note(
            isList = true, prices = true,
            lines = listOf(
                NoteLine(text = "Latte", priceCents = 150, qty = 2, done = true),
                NoteLine(text = "Pane", priceCents = 230),
                NoteLine(text = "Sale"),
            ),
        )
        assertEquals(530L, note.totalCents)
        assertEquals(300L, note.inCartCents)
        assertFalse(note.isEmpty)
        assertTrue(Note().isEmpty)
    }

    @Test
    fun `da testo a lista e ritorno`() {
        val list = Note(text = "uova\n\nlatte ").toList()
        assertEquals(listOf("uova", "latte"), list.lines.map { it.text })
        assertEquals("uova\nlatte", list.toText().text)
    }
}
