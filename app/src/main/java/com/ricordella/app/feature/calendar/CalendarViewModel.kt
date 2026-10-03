package com.ricordella.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricordella.app.domain.date.RecurrenceCalculator
import com.ricordella.app.domain.date.ReminderTimeline
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.CycleCalendar
import com.ricordella.app.domain.model.CycleDay
import com.ricordella.app.domain.model.Priority
import com.ricordella.app.domain.model.ReminderOccurrence
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.repository.ReminderRepository
import com.ricordella.app.domain.repository.SettingsRepository
import com.ricordella.app.domain.usecase.CompleteReminderUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class CalendarMode { MONTH, DAY, AGENDA }

data class CalendarUiState(
    val isLoading: Boolean = true,
    val mode: CalendarMode = CalendarMode.MONTH,
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val now: LocalDateTime = LocalDateTime.now(),
    /** Occorrenze per giorno nel periodo caricato (griglia del mese, giorno o agenda). */
    val occurrences: Map<LocalDate, List<ReminderOccurrence>> = emptyMap(),
    /** Giorni di ciclo (veri e previsti) nel periodo caricato. */
    val cycleDays: Map<LocalDate, List<CycleDay>> = emptyMap(),
) {
    val gridDays: List<LocalDate> get() = monthGrid(month, firstDayOfWeek)
    val selectedOccurrences: List<ReminderOccurrence> get() = occurrences[selectedDate].orEmpty()
}

/** 6 settimane complete che contengono il mese, a partire dal primo giorno della settimana scelto. */
fun monthGrid(month: YearMonth, firstDayOfWeek: DayOfWeek): List<LocalDate> {
    val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    return List(42) { start.plusDays(it.toLong()) }
}

private data class Navigation(val mode: CalendarMode, val month: YearMonth, val selected: LocalDate)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    reminders: ReminderRepository,
    private val settings: SettingsRepository,
    private val recurrence: RecurrenceCalculator,
    private val completeReminder: CompleteReminderUseCase,
    time: TimeSource,
) : ViewModel() {

    private val navigation = MutableStateFlow(Navigation(CalendarMode.MONTH, YearMonth.from(time.today()), time.today()))
    private val firstDay = settings.settings.map { it.firstDayOfWeek }.distinctUntilChanged()

    private val occurrences = combine(navigation, firstDay) { nav, first -> rangeFor(nav, first) }
        .distinctUntilChanged()
        .flatMapLatest { (from, to) ->
            reminders.observeForRange(from, to).map { entries ->
                entries
                    .flatMap { entry ->
                        recurrence.daysCoveredInRange(entry.reminder, entry.recurrenceRule, from, to)
                            .map { (day, start) -> ReminderOccurrence(entry, day, start) }
                    }
                    .groupBy { it.date }
                    .mapValues { (_, list) -> list.sortedWith(compareBy(ReminderTimeline.chronologicalOrder) { it.reminder }) }
            }
        }

    private val cycle = settings.settings.map { it.cycleProfiles to it.cycleLog }.distinctUntilChanged()

    val uiState: StateFlow<CalendarUiState> = combine(navigation, firstDay, occurrences, time.minuteTicks(), cycle) { nav, first, all, now, cyc ->
        val (profiles, log) = cyc
        val (from, to) = rangeFor(nav, first)
        val cycleDays = profiles.flatMap { CycleCalendar.days(it, log, from, to).entries }
            .groupBy({ it.key }, { it.value })
        // Le sveglie hanno la loro sezione: nel calendario non compaiono.
        val byDate = all.mapValues { (_, list) -> list.filterNot { it.reminder.type == ReminderType.ALARM } }
        CalendarUiState(
            isLoading = false,
            mode = nav.mode,
            month = nav.month,
            selectedDate = nav.selected,
            firstDayOfWeek = first,
            now = now,
            occurrences = byDate,
            cycleDays = cycleDays,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    private fun rangeFor(nav: Navigation, first: DayOfWeek): Pair<LocalDate, LocalDate> = when (nav.mode) {
        CalendarMode.MONTH -> monthGrid(nav.month, first).let { it.first() to it.last() }
        CalendarMode.DAY -> nav.selected to nav.selected
        CalendarMode.AGENDA -> nav.selected to nav.selected.plusDays(AGENDA_DAYS)
    }

    fun onModeChange(mode: CalendarMode) = navigation.update { it.copy(mode = mode) }

    fun onSelectDate(date: LocalDate) = navigation.update { it.copy(selected = date, month = YearMonth.from(date)) }

    fun onShiftMonth(months: Long) = navigation.update {
        val month = it.month.plusMonths(months)
        it.copy(month = month, selected = month.atDay(1).withDayOfMonth(minOf(it.selected.dayOfMonth, month.lengthOfMonth())))
    }

    fun onShiftDay(days: Long) = onSelectDate(navigation.value.selected.plusDays(days))

    fun onToday(today: LocalDate) = onSelectDate(today)

    fun onComplete(reminderId: String) {
        viewModelScope.launch { completeReminder(reminderId) }
    }

    private companion object {
        const val AGENDA_DAYS = 90L
    }
}
