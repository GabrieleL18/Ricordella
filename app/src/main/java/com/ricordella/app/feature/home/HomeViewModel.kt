package com.ricordella.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricordella.app.domain.date.ReminderTimeline
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.repository.ReminderRepository
import com.ricordella.app.domain.usecase.CompleteReminderUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class HomeUiState(
    val isLoading: Boolean = true,
    val now: LocalDateTime = LocalDateTime.now(),
    val attention: List<ReminderWithLinks> = emptyList(),
    val today: List<ReminderWithLinks> = emptyList(),
    val upcoming: List<ReminderWithLinks> = emptyList(),
    val hasMoreUpcoming: Boolean = false,
) {
    val isEmpty: Boolean get() = attention.isEmpty() && today.isEmpty() && upcoming.isEmpty()
}

/** Risponde alla domanda "Cosa devo ricordarmi?", ordinando per rilevanza temporale. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    reminders: ReminderRepository,
    private val completeReminder: CompleteReminderUseCase,
    time: TimeSource,
) : ViewModel() {

    private val ticks = time.minuteTicks().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val uiState: StateFlow<HomeUiState> = combine(
        ticks.map { it.toLocalDate() }.distinctUntilChanged().flatMapLatest { today ->
            reminders.observeActiveUntil(today.plusDays(HORIZON_DAYS), MAX_LOADED)
        },
        ticks,
    ) { entries, now -> buildState(entries, now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(now = time.localNow()))

    fun onToggleComplete(reminderId: String) {
        viewModelScope.launch { completeReminder(reminderId) }
    }

    private fun buildState(entries: List<ReminderWithLinks>, now: LocalDateTime): HomeUiState {
        val today = now.toLocalDate()
        val sorted = entries.sortedWith(compareBy(ReminderTimeline.chronologicalOrder) { it.reminder })
        val (attention, others) = sorted.partition { ReminderTimeline.needsAttention(it, now) }
        val todayItems = others.filter { it.reminder.dueDate == today }
        val upcoming = others.filter { it.reminder.dueDate.isAfter(today) }
        return HomeUiState(
            isLoading = false,
            now = now,
            attention = attention,
            today = todayItems,
            upcoming = upcoming.take(UPCOMING_SHOWN),
            hasMoreUpcoming = upcoming.size > UPCOMING_SHOWN,
        )
    }

    private companion object {
        const val HORIZON_DAYS = 60L
        const val MAX_LOADED = 300
        const val UPCOMING_SHOWN = 8
    }
}
