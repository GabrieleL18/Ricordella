package com.ricordella.app.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.ricordella.app.data.local.dao.ReminderDao
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderCompletion
import com.ricordella.app.domain.model.ReminderDraft
import com.ricordella.app.domain.model.ReminderFilter
import com.ricordella.app.domain.model.ReminderListScope
import com.ricordella.app.domain.model.ReminderSortOrder
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.model.SchedulingCandidate
import com.ricordella.app.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

class RoomReminderRepository(private val dao: ReminderDao) : ReminderRepository {

    override fun observeReminder(id: String): Flow<ReminderWithLinks?> = dao.observeWithLinks(id)

    override fun observeActiveUntil(until: LocalDate, limit: Int) = dao.observeActiveUntil(until, limit)

    override fun observeForRange(from: LocalDate, to: LocalDate) = dao.observeForRange(from, to)

    override fun observeFiltered(filter: ReminderFilter, today: LocalDate): Flow<List<ReminderWithLinks>> =
        dao.observeFiltered(buildFilterQuery(filter, today))

    override fun observeForPerson(personId: String) = dao.observeForPerson(personId)

    override fun observeForItem(itemId: String) = dao.observeForItem(itemId)

    override fun observeCompletions(reminderId: String) = dao.observeCompletions(reminderId)

    override fun observeCategories() = dao.observeCategories()

    override suspend fun getReminder(id: String) = dao.getWithLinks(id)

    override suspend fun getRemindersForItem(itemId: String) = dao.getForItem(itemId)

    override suspend fun save(draft: ReminderDraft) =
        dao.save(draft.reminder, draft.recurrence, draft.personIds, draft.itemIds)

    override suspend fun update(reminder: Reminder) = dao.updateReminder(reminder)

    override suspend fun recordCompletion(updated: Reminder, completion: ReminderCompletion, rule: RecurrenceRule?) =
        dao.recordCompletion(updated, completion, rule)

    override suspend fun undoCompletion(updated: Reminder, completionId: String, rule: RecurrenceRule?) =
        dao.undoCompletion(updated, completionId, rule)

    override suspend fun getCompletionsBetween(from: LocalDate, to: LocalDate) = dao.getCompletionsBetween(from, to)

    override suspend fun delete(id: String) = dao.deleteWithDependencies(id)

    override suspend fun getSchedulingCandidates(): List<SchedulingCandidate> = dao.getSchedulingCandidates()

    override suspend fun markNotified(ids: List<String>, at: Instant) {
        if (ids.isNotEmpty()) dao.markNotified(ids, at)
    }

    override suspend fun search(query: String, limit: Int) = dao.search(likePattern(query), limit)

    /** Costruisce la query filtrata eseguita da SQLite, senza caricare tutto in memoria. */
    private fun buildFilterQuery(filter: ReminderFilter, today: LocalDate): SimpleSQLiteQuery {
        val where = mutableListOf("reminder.isArchived = 0")
        val args = mutableListOf<Any>()
        val todayValue = today.toEpochDay()

        when (filter.scope) {
            ReminderListScope.ALL -> where += "reminder.status != 'CANCELLED'"
            ReminderListScope.TODAY -> {
                where += "reminder.status = 'ACTIVE' AND reminder.dueDate = ?"
                args += todayValue
            }
            ReminderListScope.UPCOMING -> {
                where += "reminder.status = 'ACTIVE' AND reminder.dueDate > ?"
                args += todayValue
            }
            ReminderListScope.OVERDUE -> {
                where += "reminder.status = 'ACTIVE' AND reminder.dueDate < ?"
                args += todayValue
            }
            ReminderListScope.COMPLETED -> where += "reminder.status = 'COMPLETED'"
        }

        // Periodo: le scadenze nel periodo, più i ricorrenti attivi che potrebbero avere
        // un'occorrenza nel periodo (li rifinisce il ViewModel con il calcolo delle ricorrenze).
        filter.period.range?.let { range ->
            where += "(reminder.dueDate BETWEEN ? AND ? OR (reminder.status = 'ACTIVE' AND reminder.recurrenceRuleId IS NOT NULL AND reminder.dueDate <= ?))"
            args += range.start.toEpochDay()
            args += range.endInclusive.toEpochDay()
            args += range.endInclusive.toEpochDay()
        }

        if (filter.query.isNotBlank()) {
            val pattern = likePattern(filter.query)
            where += """(
                reminder.title LIKE ? ESCAPE '\' OR reminder.description LIKE ? ESCAPE '\'
                OR reminder.notes LIKE ? ESCAPE '\' OR reminder.category LIKE ? ESCAPE '\'
                OR reminder.id IN (SELECT reminder_item.reminderId FROM reminder_item
                    JOIN item ON item.id = reminder_item.itemId WHERE item.name LIKE ? ESCAPE '\')
                OR reminder.id IN (SELECT reminder_person.reminderId FROM reminder_person
                    JOIN person ON person.id = reminder_person.personId
                    WHERE person.name LIKE ? ESCAPE '\' OR person.surname LIKE ? ESCAPE '\')
            )"""
            repeat(7) { args += pattern }
        }
        filter.personId?.let {
            where += "reminder.id IN (SELECT reminderId FROM reminder_person WHERE personId = ?)"
            args += it
        }
        filter.itemId?.let {
            where += "reminder.id IN (SELECT reminderId FROM reminder_item WHERE itemId = ?)"
            args += it
        }
        filter.category?.let {
            where += "reminder.category = ?"
            args += it
        }
        filter.type?.let {
            where += "reminder.type = ?"
            args += it.name
        }
        filter.priority?.let {
            where += "reminder.priority = ?"
            args += it.name
        }

        val orderBy = when (filter.sortOrder) {
            ReminderSortOrder.DATE_ASC -> "reminder.dueDate ASC, reminder.dueTime ASC"
            ReminderSortOrder.DATE_DESC -> "reminder.dueDate DESC, reminder.dueTime DESC"
            ReminderSortOrder.PRIORITY ->
                "CASE reminder.priority WHEN 'URGENT' THEN 0 WHEN 'IMPORTANT' THEN 1 ELSE 2 END, reminder.dueDate ASC"
            ReminderSortOrder.TITLE -> "reminder.title COLLATE NOCASE ASC"
        }
        args += filter.limit
        val sql = "SELECT reminder.* FROM reminder WHERE ${where.joinToString(" AND ")} ORDER BY $orderBy LIMIT ?"
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
}
