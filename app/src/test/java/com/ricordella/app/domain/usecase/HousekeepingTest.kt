package com.ricordella.app.domain.usecase

import com.ricordella.app.data.calendar.CalendarImporter
import com.ricordella.app.domain.date.Holidays
import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.ReminderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HousekeepingTest {

    private val today = LocalDate.of(2026, 9, 30)

    @Test
    fun backupIsDueAfterThreeMonths() {
        assertFalse(Housekeeping.isBackupDue(AppSettings(), today))
        assertFalse(Housekeeping.isBackupDue(AppSettings(backupCheckEpochDay = today.minusDays(89).toEpochDay()), today))
        assertTrue(Housekeeping.isBackupDue(AppSettings(backupCheckEpochDay = today.minusDays(90).toEpochDay()), today))
    }

    @Test
    fun parsesSimpleGoogleRecurrences() {
        val weekly = CalendarImporter.parseRRule("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH;UNTIL=20271231T000000Z", today)!!
        assertEquals(RecurrenceFrequency.WEEKLY, weekly.frequency)
        assertEquals(2, weekly.interval)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), weekly.daysOfWeek)
        assertEquals(LocalDate.of(2027, 12, 31), weekly.endDate)

        val yearly = CalendarImporter.parseRRule("RRULE:FREQ=YEARLY", today)!!
        assertEquals(9, yearly.monthOfYear)
        assertEquals(30, yearly.dayOfMonth)

        assertNull(CalendarImporter.parseRRule(null, today))
        assertNull(CalendarImporter.parseRRule("FREQ=SECONDLY", today))
    }

    @Test
    fun backupIntervalIsConfigurable() {
        val weekly = AppSettings(backupIntervalDays = 7, backupCheckEpochDay = today.minusDays(7).toEpochDay())
        assertTrue(Housekeeping.isBackupDue(weekly, today))
        assertFalse(Housekeeping.isBackupDue(weekly.copy(backupCheckEpochDay = today.minusDays(6).toEpochDay()), today))
    }

    @Test
    fun easterAndMovableHolidaysFollowTheYear() {
        assertEquals(LocalDate.of(2026, 4, 5), Holidays.easter(2026))
        assertEquals(LocalDate.of(2027, 3, 28), Holidays.easter(2027))
        assertEquals(LocalDate.of(2027, 3, 28), Holidays.moveToYear("Pasqua", LocalDate.of(2026, 4, 5), 2027))
        assertEquals(LocalDate.of(2027, 3, 29), Holidays.moveToYear("Lunedì dell'Angelo", LocalDate.of(2026, 4, 6), 2027))
        assertEquals(LocalDate.of(2027, 12, 25), Holidays.moveToYear("Natale", LocalDate.of(2026, 12, 25), 2027))
        // Seconda domenica di maggio; ultima domenica di marzo.
        assertEquals(LocalDate.of(2027, 5, 9), Holidays.moveToYear("Festa della mamma", LocalDate.of(2026, 5, 10), 2027))
        assertEquals(LocalDate.of(2027, 3, 28), Holidays.moveToYear("Inizio dell'ora legale", LocalDate.of(2026, 3, 29), 2027))
        // "Arcangelo" non è Pasquetta: resta una festa a data fissa.
        assertEquals(LocalDate.of(2027, 9, 29), Holidays.moveToYear("San Michele Arcangelo", LocalDate.of(2026, 9, 29), 2027))
    }

    @Test
    fun guessesTypeFromDescription() {
        assertEquals(ReminderType.BIRTHDAY, CalendarImporter.guessType("Marco", "compleanno, portare torta"))
        assertEquals(ReminderType.PAYMENT, CalendarImporter.guessType("Luce", "pagare la bolletta"))
    }

    @Test
    fun guessesTypeFromTitle() {
        assertEquals(ReminderType.MEDICAL_VISIT, CalendarImporter.guessType("Visita dal dentista"))
        assertEquals(ReminderType.BIRTHDAY, CalendarImporter.guessType("Compleanno di Anna"))
        assertEquals(ReminderType.EVENT, CalendarImporter.guessType("Cena"))
    }
}
