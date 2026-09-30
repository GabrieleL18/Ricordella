package com.ricordella.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class BirthdayAndDefaultsTest {

    private fun reminder(type: ReminderType, birthYear: Int?) =
        Reminder(title = "Anna", type = type, dueDate = LocalDate.of(2026, 10, 12), birthYear = birthYear, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    @Test
    fun ageOnlyForBirthdaysWithYear() {
        assertEquals(35, reminder(ReminderType.BIRTHDAY, 1991).ageOn(LocalDate.of(2026, 10, 12)))
        assertNull(reminder(ReminderType.BIRTHDAY, null).ageOn(LocalDate.of(2026, 10, 12)))
        assertNull(reminder(ReminderType.EVENT, 1991).ageOn(LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun typeDefaultsFallBackToGlobalOffset() {
        val settings = AppSettings(
            defaultNotifyOffsetMinutes = 60,
            typeDefaults = mapOf(ReminderType.MEDICAL_VISIT to TypeDefaults(time = LocalTime.of(9, 0), notifyOffsetMinutes = 1440)),
        )
        assertEquals(LocalTime.of(9, 0), settings.defaultsFor(ReminderType.MEDICAL_VISIT).time)
        assertEquals(1440, settings.defaultsFor(ReminderType.MEDICAL_VISIT).notifyOffsetMinutes)
        assertEquals(60, settings.defaultsFor(ReminderType.TASK).notifyOffsetMinutes)
        // La sveglia suona sempre all'orario esatto.
        assertEquals(0, settings.defaultsFor(ReminderType.ALARM).notifyOffsetMinutes)
    }
}
