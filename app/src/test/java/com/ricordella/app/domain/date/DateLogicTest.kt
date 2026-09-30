package com.ricordella.app.domain.date

import com.ricordella.app.domain.model.OdometerStatus
import com.ricordella.app.domain.model.Priority
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.ReminderTimeStatus
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.model.SnoozeOption
import com.ricordella.app.domain.model.odometerStatus
import com.ricordella.app.domain.model.snoozeUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderTimelineTest {

    private val now = LocalDateTime.of(2026, 9, 29, 12, 0)

    private fun reminder(date: LocalDate, time: LocalTime? = null, status: ReminderStatus = ReminderStatus.ACTIVE) =
        Reminder(title = "R", dueDate = date, dueTime = time, status = status, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun entry(reminder: Reminder) = ReminderWithLinks(reminder, emptyList(), emptyList(), null)

    @Test
    fun `stato temporale derivato dalla data`() {
        val today = now.toLocalDate()
        assertEquals(ReminderTimeStatus.OVERDUE, ReminderTimeline.timeStatus(reminder(today.minusDays(1)), now))
        assertEquals(ReminderTimeStatus.TODAY, ReminderTimeline.timeStatus(reminder(today), now))
        assertEquals(ReminderTimeStatus.TODAY, ReminderTimeline.timeStatus(reminder(today, LocalTime.of(18, 0)), now))
        assertEquals(ReminderTimeStatus.OVERDUE, ReminderTimeline.timeStatus(reminder(today, LocalTime.of(9, 0)), now))
        assertEquals(ReminderTimeStatus.UPCOMING, ReminderTimeline.timeStatus(reminder(today.plusDays(1)), now))
        assertEquals(ReminderTimeStatus.COMPLETED, ReminderTimeline.timeStatus(reminder(today.minusDays(3), status = ReminderStatus.COMPLETED), now))
    }

    @Test
    fun `fasce temporali per la home`() {
        val today = now.toLocalDate()
        assertEquals(TimeHorizon.NEXT_DAYS, ReminderTimeline.horizon(reminder(today.plusDays(7)), now))
        assertEquals(TimeHorizon.NEXT_WEEKS, ReminderTimeline.horizon(reminder(today.plusDays(8)), now))
        assertEquals(TimeHorizon.LATER, ReminderTimeline.horizon(reminder(today.plusDays(31)), now))
    }

    @Test
    fun `richiedono attenzione - scaduti, urgenti e scadenze vicine`() {
        val today = now.toLocalDate()
        assertTrue(ReminderTimeline.needsAttention(entry(reminder(today.minusDays(1))), now))
        assertTrue(ReminderTimeline.needsAttention(entry(reminder(today.plusDays(5)).copy(priority = Priority.URGENT)), now))
        assertTrue(ReminderTimeline.needsAttention(entry(reminder(today.plusDays(5)).copy(type = ReminderType.WARRANTY)), now))
        assertFalse(ReminderTimeline.needsAttention(entry(reminder(today.plusDays(5))), now))
        assertFalse(ReminderTimeline.needsAttention(entry(reminder(today.plusDays(20)).copy(type = ReminderType.WARRANTY)), now))
    }
}

class RelativeDateDescriberTest {

    private val today = LocalDate.of(2026, 12, 30)

    @Test
    fun `descrizioni relative`() {
        assertEquals("oggi", RelativeDateDescriber.describe(today, today))
        assertEquals("domani", RelativeDateDescriber.describe(today.plusDays(1), today))
        assertEquals("tra 5 giorni", RelativeDateDescriber.describe(today.plusDays(5), today))
        assertEquals("3 giorni fa", RelativeDateDescriber.describe(today.minusDays(3), today))
        assertEquals("tra 4 mesi", RelativeDateDescriber.describe(today.plusMonths(4), today))
        assertEquals("tra 3 anni", RelativeDateDescriber.describe(today.plusYears(3), today))
    }

    @Test
    fun `scadenze`() {
        assertEquals("Scade tra 5 giorni", RelativeDateDescriber.describeDeadline(today.plusDays(5), today))
        assertEquals("Scaduto da 2 giorni", RelativeDateDescriber.describeDeadline(today.minusDays(2), today))
        assertEquals("Scade domani", RelativeDateDescriber.describeDeadline(today.plusDays(1), today))
    }
}

class OdometerStatusTest {

    @Test
    fun `stato della manutenzione a km`() {
        assertEquals(OdometerStatus.FAR, odometerStatus(currentKm = 84_000, dueKm = 99_000))
        assertEquals(OdometerStatus.NEAR, odometerStatus(currentKm = 98_000, dueKm = 99_000))
        assertEquals(OdometerStatus.DUE, odometerStatus(currentKm = 99_100, dueKm = 99_000))
        assertEquals(OdometerStatus.OVERDUE, odometerStatus(currentKm = 100_000, dueKm = 99_000))
    }
}

class ReminderAlarmPlannerTest {

    private val rome = ZoneId.of("Europe/Rome")
    private val planner = ReminderAlarmPlanner()
    private val nine = LocalTime.of(9, 0)

    private fun candidate(
        id: String = "a",
        date: LocalDate,
        time: LocalTime? = null,
        offset: Int = 0,
        snoozedUntil: Instant? = null,
        lastNotifiedAt: Instant? = null,
    ) = com.ricordella.app.domain.model.SchedulingCandidate(id, date, time, offset, snoozedUntil, lastNotifiedAt)

    @Test
    fun `promemoria tutto il giorno usa l'orario predefinito`() {
        val trigger = planner.triggerAt(candidate(date = LocalDate.of(2026, 7, 1)), nine, rome)
        assertEquals(Instant.parse("2026-07-01T07:00:00Z"), trigger)
    }

    @Test
    fun `cambio ora legale - un giorno prima alle 9 resta alle 9`() {
        // Il 29/03/2026 in Italia si passa all'ora legale (UTC+1 -> UTC+2).
        val trigger = planner.triggerAt(candidate(date = LocalDate.of(2026, 3, 30), offset = 24 * 60), nine, rome)
        assertEquals(Instant.parse("2026-03-29T07:00:00Z"), trigger)
        // Il 25/10/2026 si torna all'ora solare (UTC+2 -> UTC+1).
        val autumn = planner.triggerAt(candidate(date = LocalDate.of(2026, 10, 26), offset = 24 * 60), nine, rome)
        assertEquals(Instant.parse("2026-10-25T08:00:00Z"), autumn)
    }

    @Test
    fun `orario inesistente per il cambio d'ora viene spostato in avanti`() {
        val trigger = planner.triggerAt(candidate(date = LocalDate.of(2026, 3, 29), time = LocalTime.of(2, 30)), nine, rome)
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), trigger)
    }

    @Test
    fun `il rinvio ha la precedenza sulla scadenza`() {
        val snooze = Instant.parse("2026-07-01T10:10:00Z")
        assertEquals(snooze, planner.triggerAt(candidate(date = LocalDate.of(2026, 7, 1), snoozedUntil = snooze), nine, rome))
    }

    @Test
    fun `piano - notifica subito le recenti, salta le già notificate e le troppo vecchie`() {
        val now = Instant.parse("2026-07-01T12:00:00Z")
        val plan = planner.plan(
            listOf(
                candidate("recent", LocalDate.of(2026, 7, 1)),
                candidate("done", LocalDate.of(2026, 7, 1), lastNotifiedAt = Instant.parse("2026-07-01T07:00:00Z")),
                candidate("stale", LocalDate.of(2026, 6, 20)),
                candidate("later", LocalDate.of(2026, 7, 3), time = LocalTime.of(18, 0)),
                candidate("sooner", LocalDate.of(2026, 7, 2)),
            ),
            now,
            nine,
            rome,
        )
        assertEquals(listOf("recent"), plan.dueNow)
        assertEquals(Instant.parse("2026-07-02T07:00:00Z"), plan.nextAlarmAt)
        assertFalse(plan.nextAlarmIsTimed)
    }

    @Test
    fun `piano vuoto non programma allarmi`() {
        val plan = planner.plan(emptyList(), Instant.parse("2026-07-01T12:00:00Z"), nine, rome)
        assertTrue(plan.dueNow.isEmpty())
        assertEquals(null, plan.nextAlarmAt)
    }
}

class SnoozeOptionTest {

    private val rome = ZoneId.of("Europe/Rome")
    private val now = Instant.parse("2026-03-28T20:00:00Z")

    @Test
    fun `rimanda di minuti e ore`() {
        assertEquals(Instant.parse("2026-03-28T20:10:00Z"), SnoozeOption.TEN_MINUTES.snoozeUntil(now, rome, LocalTime.of(9, 0)))
        assertEquals(Instant.parse("2026-03-28T21:00:00Z"), SnoozeOption.ONE_HOUR.snoozeUntil(now, rome, LocalTime.of(9, 0)))
    }

    @Test
    fun `rimanda a domani mattina anche col cambio d'ora`() {
        // 28/03 21:00 ora di Roma; domani (29/03) è già ora legale: 09:00 = 07:00Z.
        assertEquals(Instant.parse("2026-03-29T07:00:00Z"), SnoozeOption.TomorrowMorning.snoozeUntil(now, rome, LocalTime.of(9, 0)))
    }

    @Test
    fun `rimanda a data e ora scelte`() {
        val until = SnoozeOption.At(LocalDateTime.of(2026, 4, 2, 15, 30)).snoozeUntil(now, rome, LocalTime.of(9, 0))
        assertEquals(Instant.parse("2026-04-02T13:30:00Z"), until)
    }
}
