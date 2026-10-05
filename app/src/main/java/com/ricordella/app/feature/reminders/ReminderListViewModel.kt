package com.ricordella.app.feature.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.ItemWithCategory
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.Priority
import com.ricordella.app.domain.model.ReminderFilter
import com.ricordella.app.domain.model.ReminderListScope
import com.ricordella.app.domain.model.ReminderSortOrder
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.repository.ItemRepository
import com.ricordella.app.domain.repository.PersonRepository
import com.ricordella.app.domain.repository.ReminderRepository
import com.ricordella.app.domain.usecase.CompleteReminderUseCase
import com.ricordella.app.domain.usecase.ReopenReminderUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import com.ricordella.app.domain.date.RecurrenceCalculator
import com.ricordella.app.domain.model.PeriodKind
import com.ricordella.app.domain.model.ReminderPeriod

data class ReminderListUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val filter: ReminderFilter = ReminderFilter(),
    val reminders: List<ReminderWithLinks> = emptyList(),
    val now: LocalDateTime = LocalDateTime.now(),
    val people: List<Person> = emptyList(),
    val items: List<ItemWithCategory> = emptyList(),
    val categories: List<String> = emptyList(),
    /** Per i ricorrenti mostrati in un periodo: la prima occorrenza che vi cade. */
    val occurrenceDates: Map<String, LocalDate> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ReminderListViewModel(
    reminders: ReminderRepository,
    people: PersonRepository,
    items: ItemRepository,
    private val completeReminder: CompleteReminderUseCase,
    private val reopenReminder: ReopenReminderUseCase,
    private val recurrence: RecurrenceCalculator,
    time: TimeSource,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(ReminderFilter())
    private val ticks = time.minuteTicks().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val results = combine(
        query.debounce(250).distinctUntilChanged(),
        filter,
        ticks.map { it.toLocalDate() }.distinctUntilChanged(),
    ) { text, options, today -> Triple(text, options, today) }
        .flatMapLatest { (text, options, today) -> reminders.observeFiltered(options.copy(query = text), today) }

    private val lookups = combine(
        people.observePeople(archived = false),
        items.observeItems(archived = false),
        reminders.observeCategories(),
    ) { p, i, c -> Triple(p, i, c) }

    val uiState: StateFlow<ReminderListUiState> = combine(results, lookups, query, filter, ticks) { list, lookup, text, options, now ->
        val range = options.period.range
        val occurrenceDates = mutableMapOf<String, LocalDate>()
        // Le sveglie hanno la loro sezione: qui solo se le si cerca con il filtro del tipo.
        val all = if (options.type == ReminderType.ALARM) list else list.filterNot { it.reminder.type == ReminderType.ALARM }
        val visible = if (range == null) all else all.filter { entry ->
            val reminder = entry.reminder
            if (reminder.dueDate in range) return@filter true
            val first = recurrence.daysCoveredInRange(reminder, entry.recurrenceRule, range.start, range.endInclusive).firstOrNull()?.second
            first?.let { occurrenceDates[reminder.id] = it }
            first != null
        }
        ReminderListUiState(
            isLoading = false,
            query = text,
            filter = options,
            occurrenceDates = occurrenceDates,
            reminders = visible,
            now = now,
            people = lookup.first,
            items = lookup.second,
            categories = lookup.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderListUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onScopeChange(scope: ReminderListScope) = filter.update { it.copy(scope = scope) }
    fun onPeriodKind(kind: PeriodKind) = filter.update { it.copy(period = ReminderPeriod(kind)) }
    fun onShiftPeriod(steps: Long) = filter.update { it.copy(period = it.period.shift(steps)) }
    fun onSortChange(order: ReminderSortOrder) = filter.update { it.copy(sortOrder = order) }
    fun onPersonFilter(id: String?) = filter.update { it.copy(personId = id) }
    fun onItemFilter(id: String?) = filter.update { it.copy(itemId = id) }
    fun onCategoryFilter(category: String?) = filter.update { it.copy(category = category) }
    fun onTypeFilter(type: ReminderType?) = filter.update { it.copy(type = type) }
    fun onPriorityFilter(priority: Priority?) = filter.update { it.copy(priority = priority) }
    fun onClearSecondaryFilters() = filter.update {
        it.copy(personId = null, itemId = null, category = null, type = null, priority = null)
    }

    fun onToggleComplete(entry: ReminderWithLinks) {
        viewModelScope.launch {
            if (entry.reminder.status == ReminderStatus.ACTIVE) completeReminder(entry.reminder.id)
            else reopenReminder(entry.reminder.id)
        }
    }
}
