package com.ricordella.app.data.share

import com.ricordella.app.data.backup.BackupDatabaseContent
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderPersonCrossRef
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.model.ShareScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ShareScopeTest {
    private val t = Instant.EPOCH
    private val anna = Person(id = "p", name = "Anna", createdAt = t, updatedAt = t)
    private val task = Reminder(id = "r", title = "Dentista", dueDate = LocalDate.of(2026, 10, 3), createdAt = t, updatedAt = t)
    private val alarm = Reminder(id = "a", title = "Lavoro", type = ReminderType.ALARM, dueDate = LocalDate.of(2026, 10, 3), createdAt = t, updatedAt = t)
    private val content = BackupDatabaseContent(
        people = listOf(anna),
        reminders = listOf(task, alarm),
        reminderPeople = listOf(ReminderPersonCrossRef("r", "p"), ReminderPersonCrossRef("a", "p")),
    )

    @Test
    fun alarmsAreNotSharedByDefault() {
        val shared = ShareMerge.strip(content, ShareScope())
        assertEquals(listOf("r"), shared.reminders.map { it.id })
        assertEquals(listOf(ReminderPersonCrossRef("r", "p")), shared.reminderPeople)
    }

    @Test
    fun sharedPlusRestIsAlwaysEverything() {
        for (people in listOf(true, false)) for (alarms in listOf(true, false)) for (reminders in listOf(true, false)) {
            val scope = ShareScope(people = people, reminders = reminders, alarms = alarms)
            val joined = ShareMerge.plus(ShareMerge.strip(content, scope), ShareMerge.rest(content, scope))
            assertEquals(content.people.toSet(), joined.people.toSet())
            assertEquals(content.reminders.toSet(), joined.reminders.toSet())
            assertEquals(content.reminderPeople.toSet(), joined.reminderPeople.toSet())
            assertTrue(joined.reminders.size == content.reminders.size)
        }
    }
}
