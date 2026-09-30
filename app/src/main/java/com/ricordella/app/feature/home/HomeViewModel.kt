package com.ricordella.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricordella.app.domain.date.ReminderTimeline
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.repository.ReminderRepository
import com.ricordella.app.domain.usecase.CompleteReminderUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import android.net.Uri
import com.ricordella.app.domain.repository.SettingsRepository
import com.ricordella.app.domain.usecase.Housekeeping
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    /** C'è almeno un promemoria scaduto e non completato: la palla si riempie di fumo rosso. */
    val hasOverdue: Boolean = false,
    /** È passato l'intervallo scelto dall'ultimo backup. */
    val backupDue: Boolean = false,
    val backupIntervalDays: Int = 90,
) {
    val isEmpty: Boolean get() = attention.isEmpty() && today.isEmpty() && upcoming.isEmpty()
}

enum class BackupEvent { Updated, ChooseFile, Failed }

/** Risponde alla domanda "Cosa devo ricordarmi?", ordinando per rilevanza temporale. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    reminders: ReminderRepository,
    private val completeReminder: CompleteReminderUseCase,
    time: TimeSource,
    settings: SettingsRepository,
    private val housekeeping: Housekeeping,
) : ViewModel() {

    /** Promemoria vecchi proposti per la pulizia annuale (vuoto = niente da chiedere). */
    private val _cleanupIds = MutableStateFlow<List<String>>(emptyList())
    val cleanupIds: StateFlow<List<String>> = _cleanupIds.asStateFlow()

    /** Esito dell'ultimo "Aggiorna ora": messaggio da mostrare, o richiesta di scegliere il file. */
    private val _backupEvent = MutableStateFlow<BackupEvent?>(null)
    val backupEvent: StateFlow<BackupEvent?> = _backupEvent.asStateFlow()

    init {
        viewModelScope.launch {
            housekeeping.startBackupClockIfNeeded()
            runCatching { housekeeping.rollHolidays() }
            _cleanupIds.value = runCatching { housekeeping.cleanupCandidates() }.getOrDefault(emptyList())
        }
    }

    private val ticks = time.minuteTicks().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val uiState: StateFlow<HomeUiState> = combine(
        ticks.map { it.toLocalDate() }.distinctUntilChanged().flatMapLatest { today ->
            reminders.observeActiveUntil(today.plusDays(HORIZON_DAYS), MAX_LOADED)
        },
        ticks,
        settings.settings,
    ) { entries, now, appSettings ->
        buildState(entries, now).copy(
            backupDue = Housekeeping.isBackupDue(appSettings, now.toLocalDate()),
            backupIntervalDays = appSettings.backupIntervalDays,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(now = time.localNow()))

    fun onToggleComplete(reminderId: String) {
        viewModelScope.launch { completeReminder(reminderId) }
    }

    /** Sovrascrive il backup nel file già scelto; se non c'è, la UI chiede dove salvarlo. */
    fun onExportBackup() {
        viewModelScope.launch {
            _backupEvent.value = if (housekeeping.overwriteBackup()) BackupEvent.Updated else BackupEvent.ChooseFile
        }
    }

    fun onNewBackupFile(uri: Uri) {
        viewModelScope.launch {
            _backupEvent.value = if (runCatching { housekeeping.exportToNewFile(uri) }.isSuccess) BackupEvent.Updated else BackupEvent.Failed
        }
    }

    fun onBackupEventHandled() {
        _backupEvent.value = null
    }

    fun onPostponeBackup() {
        viewModelScope.launch { housekeeping.postponeBackup() }
    }

    fun onConfirmCleanup() {
        val ids = _cleanupIds.value
        _cleanupIds.value = emptyList()
        viewModelScope.launch { housekeeping.cleanup(ids) }
    }

    fun onDismissCleanup() {
        _cleanupIds.value = emptyList()
        viewModelScope.launch { housekeeping.dismissCleanup() }
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
            hasOverdue = attention.any { ReminderTimeline.isOverdue(it.reminder, now) },
        )
    }

    private companion object {
        const val HORIZON_DAYS = 60L
        const val MAX_LOADED = 300
        const val UPCOMING_SHOWN = 8
    }
}
