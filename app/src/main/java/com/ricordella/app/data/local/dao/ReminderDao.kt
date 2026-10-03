package com.ricordella.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.ricordella.app.domain.model.AttachmentOwnerType
import com.ricordella.app.domain.model.Item
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderCompletion
import com.ricordella.app.domain.model.ReminderItemCrossRef
import com.ricordella.app.domain.model.ReminderPersonCrossRef
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.model.SchedulingCandidate
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
abstract class ReminderDao {

    @Transaction
    @Query("SELECT * FROM reminder WHERE id = :id")
    abstract fun observeWithLinks(id: String): Flow<ReminderWithLinks?>

    @Transaction
    @Query("SELECT * FROM reminder WHERE id = :id")
    abstract suspend fun getWithLinks(id: String): ReminderWithLinks?

    @Query("SELECT * FROM reminder WHERE id = :id")
    abstract suspend fun getById(id: String): Reminder?

    @Transaction
    @Query(
        """
        SELECT * FROM reminder
        WHERE status = 'ACTIVE' AND isArchived = 0 AND dueDate <= :until
        ORDER BY dueDate, dueTime
        LIMIT :limit
        """,
    )
    abstract fun observeActiveUntil(until: LocalDate, limit: Int): Flow<List<ReminderWithLinks>>

    @Transaction
    @Query(
        """
        SELECT * FROM reminder
        WHERE isArchived = 0 AND status != 'CANCELLED' AND (
            (dueDate BETWEEN :from AND :to)
            OR (endDate IS NOT NULL AND dueDate < :from AND endDate >= :from)
            OR (status = 'ACTIVE' AND recurrenceRuleId IS NOT NULL AND dueDate <= :to)
        )
        ORDER BY dueDate, dueTime
        """,
    )
    abstract fun observeForRange(from: LocalDate, to: LocalDate): Flow<List<ReminderWithLinks>>

    @Transaction
    @RawQuery(
        observedEntities = [
            Reminder::class,
            ReminderPersonCrossRef::class,
            ReminderItemCrossRef::class,
            Person::class,
            Item::class,
            RecurrenceRule::class,
        ],
    )
    abstract fun observeFiltered(query: SupportSQLiteQuery): Flow<List<ReminderWithLinks>>

    /** Tutti i pagamenti, di qualunque anno: servono al riepilogo delle spese. */
    @Transaction
    @Query("SELECT * FROM reminder WHERE type = 'PAYMENT' AND isArchived = 0 AND status != 'CANCELLED'")
    abstract fun observePayments(): Flow<List<ReminderWithLinks>>

    @Transaction
    @Query(
        """
        SELECT reminder.* FROM reminder
        JOIN reminder_person ON reminder_person.reminderId = reminder.id
        WHERE reminder_person.personId = :personId AND reminder.isArchived = 0 AND reminder.status != 'CANCELLED'
        ORDER BY reminder.dueDate, reminder.dueTime
        """,
    )
    abstract fun observeForPerson(personId: String): Flow<List<ReminderWithLinks>>

    @Transaction
    @Query(
        """
        SELECT reminder.* FROM reminder
        JOIN reminder_item ON reminder_item.reminderId = reminder.id
        WHERE reminder_item.itemId = :itemId AND reminder.isArchived = 0 AND reminder.status != 'CANCELLED'
        ORDER BY reminder.dueDate, reminder.dueTime
        """,
    )
    abstract fun observeForItem(itemId: String): Flow<List<ReminderWithLinks>>

    @Transaction
    @Query(
        """
        SELECT reminder.* FROM reminder
        JOIN reminder_item ON reminder_item.reminderId = reminder.id
        WHERE reminder_item.itemId = :itemId
        """,
    )
    abstract suspend fun getForItem(itemId: String): List<ReminderWithLinks>

    @Query("SELECT * FROM reminder_completion WHERE reminderId = :reminderId ORDER BY completedAt DESC")
    abstract fun observeCompletions(reminderId: String): Flow<List<ReminderCompletion>>

    @Query(
        """
        SELECT DISTINCT category FROM reminder
        WHERE category IS NOT NULL AND category != ''
        ORDER BY category COLLATE NOCASE
        """,
    )
    abstract fun observeCategories(): Flow<List<String>>

    @Query(
        """
        SELECT id, dueDate, dueTime, notifyOffsetMinutes, snoozedUntil, lastNotifiedAt,
            (type = 'ALARM') AS isAlarm FROM reminder
        WHERE status = 'ACTIVE' AND isArchived = 0 AND notificationsEnabled = 1
        """,
    )
    abstract suspend fun getSchedulingCandidates(): List<SchedulingCandidate>

    @Query("UPDATE reminder SET lastNotifiedAt = :at WHERE id IN (:ids)")
    abstract suspend fun markNotified(ids: List<String>, at: Instant)

    @Transaction
    @Query(
        """
        SELECT * FROM reminder
        WHERE title LIKE :pattern ESCAPE '\' OR description LIKE :pattern ESCAPE '\'
            OR notes LIKE :pattern ESCAPE '\' OR category LIKE :pattern ESCAPE '\'
        ORDER BY status = 'ACTIVE' DESC, dueDate
        LIMIT :limit
        """,
    )
    abstract suspend fun search(pattern: String, limit: Int): List<ReminderWithLinks>

    @Upsert
    abstract suspend fun upsertReminder(reminder: Reminder)

    @Update
    abstract suspend fun updateReminder(reminder: Reminder)

    @Upsert
    abstract suspend fun upsertRule(rule: RecurrenceRule)

    @Query("DELETE FROM recurrence_rule WHERE id = :id")
    abstract suspend fun deleteRule(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPersonLinks(links: List<ReminderPersonCrossRef>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertItemLinks(links: List<ReminderItemCrossRef>)

    @Query("DELETE FROM reminder_person WHERE reminderId = :reminderId")
    abstract suspend fun deletePersonLinks(reminderId: String)

    @Query("DELETE FROM reminder_item WHERE reminderId = :reminderId")
    abstract suspend fun deleteItemLinks(reminderId: String)

    @Insert
    abstract suspend fun insertCompletion(completion: ReminderCompletion)

    @Query("DELETE FROM reminder_completion WHERE id = :id")
    abstract suspend fun deleteCompletion(id: String)

    /** Occorrenze completate nel periodo (per mostrarle come fatte anche dopo che il ricorrente è avanzato). */
    @Query("SELECT * FROM reminder_completion WHERE occurrenceDate BETWEEN :from AND :to")
    abstract suspend fun getCompletionsBetween(from: LocalDate, to: LocalDate): List<ReminderCompletion>

    @Query("DELETE FROM reminder WHERE id = :id")
    abstract suspend fun deleteReminder(id: String)

    @Query("DELETE FROM attachment WHERE ownerType = :ownerType AND ownerId = :ownerId")
    abstract suspend fun deleteAttachments(ownerType: AttachmentOwnerType, ownerId: String)

    /**
     * Salva promemoria, ricorrenza e collegamenti. La ricorrenza precedente viene rimossa
     * se sostituita o eliminata, per non lasciare regole orfane.
     */
    @Transaction
    open suspend fun save(
        reminder: Reminder,
        rule: RecurrenceRule?,
        personIds: Set<String>,
        itemIds: Set<String>,
    ) {
        val previousRuleId = getById(reminder.id)?.recurrenceRuleId
        if (rule != null) upsertRule(rule)
        upsertReminder(reminder.copy(recurrenceRuleId = rule?.id))
        if (previousRuleId != null && previousRuleId != rule?.id) deleteRule(previousRuleId)
        deletePersonLinks(reminder.id)
        deleteItemLinks(reminder.id)
        insertPersonLinks(personIds.map { ReminderPersonCrossRef(reminder.id, it) })
        insertItemLinks(itemIds.map { ReminderItemCrossRef(reminder.id, it) })
    }

    /** Annulla un completamento: riporta il promemoria all'occorrenza e cancella la riga dello storico. */
    @Transaction
    open suspend fun undoCompletion(updated: Reminder, completionId: String, rule: RecurrenceRule?) {
        rule?.let { upsertRule(it) }
        updateReminder(updated)
        deleteCompletion(completionId)
    }

    @Transaction
    open suspend fun recordCompletion(updated: Reminder, completion: ReminderCompletion, rule: RecurrenceRule? = null) {
        rule?.let { upsertRule(it) }
        updateReminder(updated)
        insertCompletion(completion)
    }

    /**
     * Promemoria ed eventi con scadenza prima di [before] che possono essere eliminati:
     * non importanti, oppure senza persone o cose (attive) collegate.
     * I ricorrenti ancora attivi sono esclusi: continuano a proporre occorrenze future.
     */
    @Query(
        """
        SELECT r.id FROM reminder r
        WHERE r.dueDate < :before
            AND r.type != 'HOLIDAY'
            AND NOT (r.status = 'ACTIVE' AND r.recurrenceRuleId IS NOT NULL)
            AND (r.plan IS NULL OR r.plan LIKE '%"deleteAfter":true%')
            AND (
                r.priority = 'NORMAL' OR (
                    NOT EXISTS (
                        SELECT 1 FROM reminder_person rp JOIN person p ON p.id = rp.personId
                        WHERE rp.reminderId = r.id AND p.isArchived = 0
                    )
                    AND NOT EXISTS (
                        SELECT 1 FROM reminder_item ri JOIN item i ON i.id = ri.itemId
                        WHERE ri.reminderId = r.id AND i.isArchived = 0
                    )
                )
            )
        """,
    )
    abstract suspend fun getCleanupCandidates(before: LocalDate): List<String>

    /** Feste rimaste in un anno passato: vanno riportate all'anno corrente. */
    @Query("SELECT * FROM reminder WHERE type = 'HOLIDAY' AND dueDate < :before")
    abstract suspend fun getHolidaysBefore(before: LocalDate): List<Reminder>

    /** Quanti promemoria hanno già questo titolo in questa data (per non importare doppioni). */
    @Query("SELECT COUNT(*) FROM reminder WHERE title = :title AND dueDate = :date")
    abstract suspend fun countSame(title: String, date: LocalDate): Int

    @Query("SELECT id FROM reminder WHERE title = :title AND dueDate = :date")
    abstract suspend fun findSameIds(title: String, date: LocalDate): List<String>

    @Transaction
    open suspend fun deleteAllWithDependencies(ids: List<String>) {
        ids.forEach { deleteWithDependencies(it) }
    }

    @Transaction
    open suspend fun deleteWithDependencies(id: String) {
        val ruleId = getById(id)?.recurrenceRuleId
        deleteAttachments(AttachmentOwnerType.REMINDER, id)
        deleteReminder(id)
        if (ruleId != null) deleteRule(ruleId)
    }
}
