package com.ricordella.app.domain.date

import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MultiDayTest {

    private val holiday = Reminder(
        title = "Vacanza",
        type = ReminderType.EVENT,
        dueDate = LocalDate.of(2026, 8, 1),
        endDate = LocalDate.of(2026, 8, 7),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun coversEveryDayOfTheSpanInsideTheRange() {
        val days = RecurrenceCalculator().daysCoveredInRange(holiday, null, LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 31))
        assertEquals((5..7).map { LocalDate.of(2026, 8, it) }, days.map { it.first })
        assertTrue(days.all { it.second == holiday.dueDate })
    }

    @Test
    fun ongoingUntilTheLastDayAndNeverOverdueBefore() {
        val middle = LocalDate.of(2026, 8, 4)
        assertTrue(ReminderTimeline.isOngoing(holiday, middle))
        assertFalse(ReminderTimeline.isOverdue(holiday, middle.atTime(12, 0)))
        assertTrue(ReminderTimeline.isOverdue(holiday, LocalDate.of(2026, 8, 8).atTime(9, 0)))
    }
}
