package com.ricordella.app.feature.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ricordella.app.core.navigation.PersonDetailRoute
import com.ricordella.app.core.navigation.PersonEditRoute
import com.ricordella.app.domain.date.ReminderTimeline
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.ItemWithCategory
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.model.displayName
import com.ricordella.app.domain.repository.ItemRepository
import com.ricordella.app.domain.repository.PersonRepository
import com.ricordella.app.domain.repository.ReminderRepository
import com.ricordella.app.domain.usecase.CompleteReminderUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class PersonListUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val showArchived: Boolean = false,
    val people: List<Person> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class PersonListViewModel(people: PersonRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val showArchived = MutableStateFlow(false)

    val uiState: StateFlow<PersonListUiState> = combine(
        showArchived.flatMapLatest { people.observePeople(archived = it) },
        query,
        showArchived,
    ) { list, text, archived ->
        val filtered = if (text.isBlank()) list else list.filter {
            it.displayName.contains(text.trim(), ignoreCase = true) || it.notes?.contains(text.trim(), ignoreCase = true) == true
        }
        PersonListUiState(isLoading = false, query = text, showArchived = archived, people = filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonListUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onShowArchived(value: Boolean) {
        showArchived.value = value
    }
}

data class PersonForm(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val surname: String = "",
    val notes: String = "",
    val photoUri: String? = null,
    val showErrors: Boolean = false,
    val savedId: String? = null,
    val errorMessage: String? = null,
) {
    val nameError: Boolean get() = showErrors && name.isBlank()
}

class PersonEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val people: PersonRepository,
    private val time: TimeSource,
) : ViewModel() {

    private val personId = savedStateHandle.toRoute<PersonEditRoute>().id
    private var existing: Person? = null
    private val _form = MutableStateFlow(PersonForm())
    val form: StateFlow<PersonForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val person = personId?.let { people.getPerson(it) }
            existing = person
            _form.value = if (person == null) {
                PersonForm(isLoading = false)
            } else {
                PersonForm(
                    isLoading = false,
                    isNew = false,
                    name = person.name,
                    surname = person.surname.orEmpty(),
                    notes = person.notes.orEmpty(),
                    photoUri = person.photoUri,
                )
            }
        }
    }

    fun update(transform: (PersonForm) -> PersonForm) = _form.update(transform)

    fun onErrorShown() = _form.update { it.copy(errorMessage = null) }

    fun save() {
        val form = _form.value
        if (form.name.isBlank()) {
            _form.update { it.copy(showErrors = true) }
            return
        }
        viewModelScope.launch {
            val now = time.now()
            val person = (existing ?: Person(name = "", createdAt = now, updatedAt = now)).copy(
                name = form.name.trim(),
                surname = form.surname.trim().ifEmpty { null },
                notes = form.notes.trim().ifEmpty { null },
                photoUri = form.photoUri,
                updatedAt = now,
            )
            try {
                people.save(person)
                _form.update { it.copy(savedId = person.id) }
            } catch (_: Exception) {
                _form.update { it.copy(errorMessage = "Non è stato possibile salvare la persona. Riprova.") }
            }
        }
    }
}

data class PersonDetailUiState(
    val isLoading: Boolean = true,
    val person: Person? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    val today: List<ReminderWithLinks> = emptyList(),
    val events: List<ReminderWithLinks> = emptyList(),
    val reminders: List<ReminderWithLinks> = emptyList(),
    val items: List<ItemWithCategory> = emptyList(),
    val deleted: Boolean = false,
)

class PersonDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val people: PersonRepository,
    reminders: ReminderRepository,
    items: ItemRepository,
    private val completeReminder: CompleteReminderUseCase,
    private val time: TimeSource,
) : ViewModel() {

    private val personId = savedStateHandle.toRoute<PersonDetailRoute>().id
    private val deleted = MutableStateFlow(false)

    val uiState: StateFlow<PersonDetailUiState> = combine(
        people.observePerson(personId),
        reminders.observeForPerson(personId),
        items.observeItemsForPerson(personId),
        time.minuteTicks(),
        deleted,
    ) { person, linked, ownedItems, now, isDeleted ->
        val today = now.toLocalDate()
        val active = linked.filter { it.reminder.status == ReminderStatus.ACTIVE }
        val (todayList, later) = active.partition { !it.reminder.dueDate.isAfter(today) }
        val (events, others) = later.partition { it.reminder.type == ReminderType.EVENT || it.reminder.type == ReminderType.BIRTHDAY }
        PersonDetailUiState(
            isLoading = false,
            person = person,
            now = now,
            today = todayList.sortedWith(compareBy(ReminderTimeline.chronologicalOrder) { it.reminder }),
            events = events,
            reminders = others,
            items = ownedItems,
            deleted = isDeleted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonDetailUiState())

    fun onToggleArchived() {
        val person = uiState.value.person ?: return
        viewModelScope.launch { people.save(person.copy(isArchived = !person.isArchived, updatedAt = time.now())) }
    }

    fun onDelete() {
        viewModelScope.launch {
            people.delete(personId)
            deleted.value = true
        }
    }

    fun onCompleteReminder(id: String) {
        viewModelScope.launch { completeReminder(id) }
    }
}
