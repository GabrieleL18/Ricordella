package com.ricordella.app.feature.reminders

import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.ReminderType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class ListGroupTest {

    private val now = LocalDateTime.of(2026, 10, 26, 12, 0)
    private val today = now.toLocalDate()

    private fun group(due: LocalDate, type: ReminderType = ReminderType.TASK, status: ReminderStatus = ReminderStatus.ACTIVE) =
        listGroup(Reminder(title = "x", dueDate = due, type = type, status = status, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH), now)

    @Test
    fun groupsByTime() {
        assertEquals(ListGroup.OVERDUE, group(today.minusDays(2)))
        assertEquals(ListGroup.TODAY, group(today))
        assertEquals(ListGroup.TOMORROW, group(today.plusDays(1)))
        assertEquals(ListGroup.WEEK, group(today.plusDays(7)))
        assertEquals(ListGroup.LATER, group(today.plusDays(8)))
        assertEquals(ListGroup.DONE, group(today.minusDays(2), status = ReminderStatus.COMPLETED))
    }

    @Test
    fun pastHolidaysAreNotToday() = assertEquals(ListGroup.PAST, group(today.minusDays(3), type = ReminderType.HOLIDAY))
}
