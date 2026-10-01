package com.ricordella.app.feature.reminders

import com.ricordella.app.core.i18n.tr

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ricordella.app.core.navigation.ReminderEditRoute
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.model.ItemWithCategory
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.Priority
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderDraft
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.model.TripInfo
import com.ricordella.app.domain.repository.ItemRepository
import com.ricordella.app.domain.repository.PersonRepository
import com.ricordella.app.domain.repository.ReminderRepository
import com.ricordella.app.domain.repository.SettingsRepository
import com.ricordella.app.domain.usecase.SaveReminderUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Stato del modulo di creazione/modifica. Solo titolo e data sono obbligatori. */
data class ReminderForm(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val title: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    /** Ultimo giorno per gli eventi di più giorni (vacanze, viaggi...). */
    val multiDay: Boolean = false,
    /** Dettagli di viaggio, usati solo per le vacanze. */
    val trip: TripInfo = TripInfo(),
    /** Anno di nascita (testo del campo), usato solo per i compleanni. */
    val birthYear: String = "",
    val endDate: LocalDate? = null,
    val type: ReminderType = ReminderType.TASK,
    val description: String = "",
    val notes: String = "",
    val priority: Priority = Priority.NORMAL,
    val category: String = "",
    val recurrencePreset: RecurrencePreset = RecurrencePreset.NONE,
    val customFrequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val customInterval: String = "1",
    val weekDays: Set<DayOfWeek> = emptySet(),
    val recurrenceEnd: LocalDate? = null,
    /** Ricorrenza contata dall'ultima volta che è stato fatto (le date successive si spostano). */
    val fromLastDone: Boolean = false,
    val personIds: Set<String> = emptySet(),
    val itemIds: Set<String> = emptySet(),
    val dueOdometerKm: String = "",
    val odometerIntervalKm: String = "",
    val notificationsEnabled: Boolean = true,
    val notifyOffsetMinutes: Int = 0,
    val showAdvanced: Boolean = false,
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null,
) {
    val titleError: Boolean get() = showErrors && title.isBlank()
    val dateError: Boolean get() = showErrors && date == null
    val isAlarm: Boolean get() = type == ReminderType.ALARM
    /** La sveglia deve avere giorno e orario. */
    val timeError: Boolean get() = showErrors && isAlarm && time == null
    val endDateError: Boolean get() = showErrors && multiDay && (endDate == null || date == null || !endDate.isAfter(date))
    val isRecurring: Boolean get() = recurrencePreset != RecurrencePreset.NONE
}

class ReminderEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val reminders: ReminderRepository,
    people: PersonRepository,
    items: ItemRepository,
    private val settings: SettingsRepository,
    private val saveReminder: SaveReminderUseCase,
    private val time: TimeSource,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ReminderEditRoute>()
    private var existing: Reminder? = null
    /** Impostazioni lette all'apertura: servono i predefiniti per tipo quando si cambia tipo. */
    private var appSettings = AppSettings()
    private var existingRuleId: String? = null

    private val _form = MutableStateFlow(ReminderForm())
    val form: StateFlow<ReminderForm> = _form.asStateFlow()

    val people: StateFlow<List<Person>> =
        people.observePeople(archived = false).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val items: StateFlow<List<ItemWithCategory>> =
        items.observeItems(archived = false).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<String>> =
        reminders.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val entry = route.id?.let { reminders.getReminder(it) }
        appSettings = settings.current()
        if (entry == null) {
            val type = route.type?.let { runCatching { ReminderType.valueOf(it) }.getOrNull() } ?: ReminderType.TASK
            val defaults = appSettings.defaultsFor(type)
            _form.value = ReminderForm(
                isLoading = false,
                isNew = true,
                date = route.epochDay?.let(LocalDate::ofEpochDay) ?: time.today(),
                type = type,
                time = defaults.time,
                notificationsEnabled = defaults.notificationsEnabled,
                recurrencePreset = if (route.type == ReminderType.BIRTHDAY.name) RecurrencePreset.YEARLY else RecurrencePreset.NONE,
                multiDay = route.type == ReminderType.VACATION.name,
                endDate = if (route.type == ReminderType.VACATION.name) (route.epochDay?.let(LocalDate::ofEpochDay) ?: time.today()).plusDays(7) else null,
                personIds = setOfNotNull(route.personId),
                itemIds = setOfNotNull(route.itemId),
                notifyOffsetMinutes = defaults.notifyOffsetMinutes,
                showAdvanced = route.itemId != null || route.personId != null,
            )
            return
        }
        val reminder = entry.reminder
        val rule = entry.recurrenceRule
        existing = reminder
        existingRuleId = rule?.id
        _form.value = ReminderForm(
            isLoading = false,
            isNew = false,
            title = reminder.title,
            date = reminder.dueDate,
            multiDay = reminder.endDate != null,
            trip = reminder.trip ?: TripInfo(),
            birthYear = reminder.birthYear?.toString().orEmpty(),
            endDate = reminder.endDate,
            time = reminder.dueTime,
            type = reminder.type,
            description = reminder.description.orEmpty(),
            notes = reminder.notes.orEmpty(),
            priority = reminder.priority,
            category = reminder.category.orEmpty(),
            recurrencePreset = RecurrencePreset.from(rule),
            customFrequency = rule?.frequency ?: RecurrenceFrequency.MONTHLY,
            customInterval = (rule?.interval ?: 1).toString(),
            weekDays = rule?.daysOfWeek.orEmpty(),
            recurrenceEnd = rule?.endDate,
            fromLastDone = rule?.fromLastDone == true,
            personIds = entry.people.mapTo(mutableSetOf()) { it.id },
            itemIds = entry.items.mapTo(mutableSetOf()) { it.id },
            dueOdometerKm = reminder.dueOdometerKm?.toString().orEmpty(),
            odometerIntervalKm = reminder.odometerIntervalKm?.toString().orEmpty(),
            notificationsEnabled = reminder.notificationsEnabled,
            notifyOffsetMinutes = reminder.notifyOffsetMinutes,
            showAdvanced = true,
        )
    }

    fun update(transform: (ReminderForm) -> ReminderForm) = _form.update(transform)

    /** I compleanni si ripetono ogni anno di default; tornando a un altro tipo la ripetizione automatica si toglie. */
    fun onTypeChange(type: ReminderType) = _form.update { form ->
        val preset = when {
            type == ReminderType.BIRTHDAY && form.recurrencePreset == RecurrencePreset.NONE -> RecurrencePreset.YEARLY
            form.type == ReminderType.BIRTHDAY && type != ReminderType.BIRTHDAY && form.recurrencePreset == RecurrencePreset.YEARLY -> RecurrencePreset.NONE
            else -> form.recurrencePreset
        }
        // Una vacanza dura di solito più giorni: si propone una settimana.
        val vacation = type == ReminderType.VACATION && !form.multiDay
        // Nei nuovi promemoria i predefiniti del tipo sostituiscono quelli del tipo precedente,
        // ma solo i campi che l'utente non ha già cambiato a mano.
        val old = appSettings.defaultsFor(form.type)
        val new = appSettings.defaultsFor(type)
        form.copy(
            time = if (form.isNew && form.time == old.time) new.time else form.time,
            notificationsEnabled = if (form.isNew && form.notificationsEnabled == old.notificationsEnabled) new.notificationsEnabled else form.notificationsEnabled,
            notifyOffsetMinutes = if (form.isNew && form.notifyOffsetMinutes == old.notifyOffsetMinutes) new.notifyOffsetMinutes else form.notifyOffsetMinutes,
            type = type,
            recurrencePreset = preset,
            // Una sveglia suona in un momento preciso: niente eventi di più giorni.
            multiDay = type != ReminderType.ALARM && (form.multiDay || vacation),
            endDate = if (vacation) form.endDate ?: form.date?.plusDays(7) else form.endDate,
        )
    }

    fun onErrorShown() = _form.update { it.copy(errorMessage = null) }

    fun save() {
        val form = _form.value
        val date = form.date
        val invalid = form.title.isBlank() || date == null || (form.isAlarm && form.time == null) ||
            (form.multiDay && (form.endDate == null || !form.endDate.isAfter(date)))
        if (invalid || date == null) {
            _form.update { it.copy(showErrors = true) }
            return
        }
        _form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                saveReminder(buildDraft(form, date))
                _form.update { it.copy(isSaving = false, saved = true) }
            } catch (_: Exception) {
                _form.update { it.copy(isSaving = false, errorMessage = tr("Non è stato possibile salvare il promemoria. Riprova.")) }
            }
        }
    }

    private fun buildDraft(form: ReminderForm, date: LocalDate): ReminderDraft {
        val now = time.now()
        val base = existing ?: Reminder(title = "", dueDate = date, createdAt = now, updatedAt = now)
        val reminder = base.copy(
            title = form.title.trim(),
            dueDate = date,
            endDate = if (form.multiDay) form.endDate else null,
            trip = form.trip.takeIf { form.type == ReminderType.VACATION && !it.isEmpty },
            birthYear = form.birthYear.toIntOrNull()?.takeIf { form.type == ReminderType.BIRTHDAY && it in 1900..date.year },
            dueTime = form.time,
            type = form.type,
            description = form.description.trim().ifEmpty { null },
            notes = form.notes.trim().ifEmpty { null },
            priority = form.priority,
            category = form.category.trim().ifEmpty { null },
            dueOdometerKm = form.dueOdometerKm.toIntOrNull(),
            odometerIntervalKm = form.odometerIntervalKm.toIntOrNull(),
            // La sveglia suona sempre, esattamente all'orario.
            notificationsEnabled = form.isAlarm || form.notificationsEnabled,
            notifyOffsetMinutes = if (form.isAlarm) 0 else form.notifyOffsetMinutes,
        )
        return ReminderDraft(reminder, buildRule(form, date), form.personIds, form.itemIds)
    }

    private fun buildRule(form: ReminderForm, date: LocalDate): RecurrenceRule? {
        val preset = form.recurrencePreset
        val (frequency, interval) = when (preset) {
            RecurrencePreset.NONE -> return null
            RecurrencePreset.CUSTOM -> form.customFrequency to (form.customInterval.toIntOrNull() ?: 1).coerceIn(1, 999)
            else -> requireNotNull(preset.frequency) to preset.interval
        }
        return RecurrenceRule(
            id = existingRuleId ?: com.ricordella.app.domain.model.newId(),
            frequency = frequency,
            interval = interval,
            startDate = date,
            endDate = if (preset == RecurrencePreset.CUSTOM) form.recurrenceEnd else null,
            daysOfWeek = if (preset == RecurrencePreset.CUSTOM && frequency == RecurrenceFrequency.WEEKLY) form.weekDays else emptySet(),
            fromLastDone = preset == RecurrencePreset.CUSTOM && form.fromLastDone,
        )
    }
}
