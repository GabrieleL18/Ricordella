package com.ricordella.app.domain.usecase

import com.ricordella.app.domain.ReminderScheduler
import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.model.Item
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderCompletion
import com.ricordella.app.domain.model.ReminderDraft
import com.ricordella.app.domain.model.ReminderFilter
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.model.SchedulingCandidate
import com.ricordella.app.domain.repository.ReminderRepository
import com.ricordella.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/** Repository in memoria che riproduce la semantica del repository Room per i test dei use case. */
class FakeReminderRepository : ReminderRepository {

    val reminders = MutableStateFlow<Map<String, Reminder>>(emptyMap())
    val rules = mutableMapOf<String, RecurrenceRule>()
    val personLinks = mutableMapOf<String, Set<String>>()
    val itemLinks = mutableMapOf<String, Set<String>>()
    val completions = mutableListOf<ReminderCompletion>()
    val people = mutableMapOf<String, Person>()
    val items = mutableMapOf<String, Item>()

    private fun withLinks(reminder: Reminder) = ReminderWithLinks(
        reminder = reminder,
        people = personLinks[reminder.id].orEmpty().mapNotNull(people::get),
        items = itemLinks[reminder.id].orEmpty().mapNotNull(items::get),
        recurrenceRule = reminder.recurrenceRuleId?.let(rules::get),
    )

    override fun observeReminder(id: String): Flow<ReminderWithLinks?> = reminders.map { map -> map[id]?.let(::withLinks) }
    override fun observeActiveUntil(until: LocalDate, limit: Int) = reminders.map { m -> m.values.map(::withLinks) }
    override fun observeForRange(from: LocalDate, to: LocalDate) = reminders.map { m -> m.values.map(::withLinks) }
    override fun observeFiltered(filter: ReminderFilter, today: LocalDate) = reminders.map { m -> m.values.map(::withLinks) }
    override fun observeForPerson(personId: String) = reminders.map { m -> m.values.map(::withLinks) }
    override fun observePayments() = reminders.map { m -> m.values.map(::withLinks) }
    override fun observeForItem(itemId: String) = reminders.map { m -> m.values.map(::withLinks) }
    override fun observeCompletions(reminderId: String) = MutableStateFlow(completions.filter { it.reminderId == reminderId })
    override fun observeCategories() = MutableStateFlow(emptyList<String>())

    override suspend fun getReminder(id: String) = reminders.value[id]?.let(::withLinks)

    override suspend fun getRemindersForItem(itemId: String) =
        reminders.value.values.filter { itemId in itemLinks[it.id].orEmpty() }.map(::withLinks)

    override suspend fun save(draft: ReminderDraft) {
        val previousRule = reminders.value[draft.reminder.id]?.recurrenceRuleId
        draft.recurrence?.let { rules[it.id] = it }
        if (previousRule != null && previousRule != draft.recurrence?.id) rules.remove(previousRule)
        val reminder = draft.reminder.copy(recurrenceRuleId = draft.recurrence?.id)
        reminders.value = reminders.value + (reminder.id to reminder)
        personLinks[reminder.id] = draft.personIds
        itemLinks[reminder.id] = draft.itemIds
    }

    override suspend fun update(reminder: Reminder) {
        reminders.value = reminders.value + (reminder.id to reminder)
    }

    override suspend fun recordCompletion(updated: Reminder, completion: ReminderCompletion, rule: RecurrenceRule?) {
        rule?.let { rules[it.id] = it }
        update(updated)
        completions += completion
    }

    override suspend fun undoCompletion(updated: Reminder, completionId: String, rule: RecurrenceRule?) {
        rule?.let { rules[it.id] = it }
        update(updated)
        completions.removeAll { it.id == completionId }
    }

    override suspend fun getCompletionsBetween(from: LocalDate, to: LocalDate) =
        completions.filter { !it.occurrenceDate.isBefore(from) && !it.occurrenceDate.isAfter(to) }

    override suspend fun delete(id: String) {
        reminders.value[id]?.recurrenceRuleId?.let(rules::remove)
        reminders.value = reminders.value - id
        personLinks.remove(id)
        itemLinks.remove(id)
        completions.removeAll { it.reminderId == id }
    }

    override suspend fun getSchedulingCandidates() = reminders.value.values
        .filter { it.status == ReminderStatus.ACTIVE && !it.isArchived && it.notificationsEnabled }
        .map { SchedulingCandidate(it.id, it.dueDate, it.dueTime, it.notifyOffsetMinutes, it.snoozedUntil, it.lastNotifiedAt) }

    override suspend fun markNotified(ids: List<String>, at: Instant) {
        ids.forEach { id -> reminders.value[id]?.let { update(it.copy(lastNotifiedAt = at)) } }
    }

    override suspend fun search(query: String, limit: Int) =
        reminders.value.values.filter { it.title.contains(query, ignoreCase = true) }.map(::withLinks)
}

class FakeScheduler : ReminderScheduler {
    var refreshCount = 0
    val dismissed = mutableListOf<String>()
    override suspend fun refresh() {
        refreshCount++
    }
    override fun dismissNotification(reminderId: String) {
        dismissed += reminderId
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun current() = state.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
