package com.ricordella.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.ricordella.app.data.backup.BackupDatabaseContent
import com.ricordella.app.domain.model.Attachment
import com.ricordella.app.domain.model.Category
import com.ricordella.app.domain.model.Item
import com.ricordella.app.domain.model.MaintenanceRecord
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.PersonItemCrossRef
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderCompletion
import com.ricordella.app.domain.model.ReminderItemCrossRef
import com.ricordella.app.domain.model.ReminderPersonCrossRef

/** Accesso "in blocco" a tutte le tabelle, usato da backup, ripristino e cancellazione dati. */
@Dao
abstract class BackupDao {

    @Query("SELECT * FROM person") abstract suspend fun people(): List<Person>
    @Query("SELECT * FROM category") abstract suspend fun categories(): List<Category>
    @Query("SELECT * FROM item") abstract suspend fun items(): List<Item>
    @Query("SELECT * FROM recurrence_rule") abstract suspend fun recurrenceRules(): List<RecurrenceRule>
    @Query("SELECT * FROM reminder") abstract suspend fun reminders(): List<Reminder>
    @Query("SELECT * FROM reminder_completion") abstract suspend fun completions(): List<ReminderCompletion>
    @Query("SELECT * FROM maintenance_record") abstract suspend fun maintenance(): List<MaintenanceRecord>
    @Query("SELECT * FROM attachment") abstract suspend fun attachments(): List<Attachment>
    @Query("SELECT * FROM reminder_person") abstract suspend fun reminderPeople(): List<ReminderPersonCrossRef>
    @Query("SELECT * FROM reminder_item") abstract suspend fun reminderItems(): List<ReminderItemCrossRef>
    @Query("SELECT * FROM person_item") abstract suspend fun personItems(): List<PersonItemCrossRef>

    @Insert abstract suspend fun insertPeople(values: List<Person>)
    @Insert abstract suspend fun insertCategories(values: List<Category>)
    @Insert abstract suspend fun insertItems(values: List<Item>)
    @Insert abstract suspend fun insertRecurrenceRules(values: List<RecurrenceRule>)
    @Insert abstract suspend fun insertReminders(values: List<Reminder>)
    @Insert abstract suspend fun insertCompletions(values: List<ReminderCompletion>)
    @Insert abstract suspend fun insertMaintenance(values: List<MaintenanceRecord>)
    @Insert abstract suspend fun insertAttachments(values: List<Attachment>)
    @Insert abstract suspend fun insertReminderPeople(values: List<ReminderPersonCrossRef>)
    @Insert abstract suspend fun insertReminderItems(values: List<ReminderItemCrossRef>)
    @Insert abstract suspend fun insertPersonItems(values: List<PersonItemCrossRef>)

    @Query("DELETE FROM reminder_person") abstract suspend fun clearReminderPeople()
    @Query("DELETE FROM reminder_item") abstract suspend fun clearReminderItems()
    @Query("DELETE FROM person_item") abstract suspend fun clearPersonItems()
    @Query("DELETE FROM reminder_completion") abstract suspend fun clearCompletions()
    @Query("DELETE FROM maintenance_record") abstract suspend fun clearMaintenance()
    @Query("DELETE FROM attachment") abstract suspend fun clearAttachments()
    @Query("DELETE FROM reminder") abstract suspend fun clearReminders()
    @Query("DELETE FROM recurrence_rule") abstract suspend fun clearRecurrenceRules()
    @Query("DELETE FROM item") abstract suspend fun clearItems()
    @Query("DELETE FROM category") abstract suspend fun clearCategories()
    @Query("DELETE FROM person") abstract suspend fun clearPeople()

    @Transaction
    open suspend fun readAll(): BackupDatabaseContent = BackupDatabaseContent(
        people = people(),
        categories = categories(),
        items = items(),
        recurrenceRules = recurrenceRules(),
        reminders = reminders(),
        completions = completions(),
        maintenance = maintenance(),
        attachments = attachments(),
        reminderPeople = reminderPeople(),
        reminderItems = reminderItems(),
        personItems = personItems(),
    )

    @Transaction
    open suspend fun clearAll() {
        clearReminderPeople()
        clearReminderItems()
        clearPersonItems()
        clearCompletions()
        clearMaintenance()
        clearAttachments()
        clearReminders()
        clearRecurrenceRules()
        clearItems()
        clearCategories()
        clearPeople()
    }

    /** Sostituisce tutti i dati in un'unica transazione: o riesce tutto o non cambia nulla. */
    @Transaction
    open suspend fun replaceAll(content: BackupDatabaseContent) {
        clearAll()
        insertPeople(content.people)
        insertCategories(content.categories)
        insertItems(content.items)
        insertRecurrenceRules(content.recurrenceRules)
        insertReminders(content.reminders)
        insertCompletions(content.completions)
        insertMaintenance(content.maintenance)
        insertAttachments(content.attachments)
        insertReminderPeople(content.reminderPeople)
        insertReminderItems(content.reminderItems)
        insertPersonItems(content.personItems)
    }
}
