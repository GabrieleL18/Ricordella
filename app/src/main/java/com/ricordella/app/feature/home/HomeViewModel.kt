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
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.YearlyTask
import com.ricordella.app.domain.model.AutoMode
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

/** Esito delle azioni di backup dalla Home. */
sealed interface BackupEvent {
    data object Updated : BackupEvent
    data object ChooseFile : BackupEvent
    data object Failed : BackupEvent
    data class Share(val uri: Uri) : BackupEvent
}

/** Operazioni annuali in attesa di risposta. */
data class YearlyProposal(val holidays: Int = 0, val cleanupIds: List<String> = emptyList())

/** Risponde alla domanda "Cosa devo ricordarmi?", ordinando per rilevanza temporale. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    reminders: ReminderRepository,
    private val completeReminder: CompleteReminderUseCase,
    private val time: TimeSource,
    private val settings: SettingsRepository,
    private val housekeeping: Housekeeping,
) : ViewModel() {

    /** Operazioni annuali da proporre (feste da spostare, promemoria da pulire). */
    private val _yearly = MutableStateFlow(YearlyProposal())
    val yearly: StateFlow<YearlyProposal> = _yearly.asStateFlow()

    /** Messaggio breve da mostrare (es. "Feste spostate al 2027"). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** "Più tardi": in questa sessione non si ripropone. */
    private val postponed = mutableSetOf<YearlyTask>()

    /** Esito dell'ultimo "Aggiorna ora": messaggio da mostrare, o richiesta di scegliere il file. */
    private val _backupEvent = MutableStateFlow<BackupEvent?>(null)
    val backupEvent: StateFlow<BackupEvent?> = _backupEvent.asStateFlow()

    init {
        viewModelScope.launch { housekeeping.startBackupClockIfNeeded() }
        // Il controllo annuale si ripete quando cambiano le modalità o l'anno dell'ultima pulizia
        // (anche dalla sezione Sviluppatore), non solo alla creazione della Home.
        viewModelScope.launch {
            settings.settings
                .map { Triple(it.holidayMode, it.cleanupMode, it.lastCleanupYear) }
                .distinctUntilChanged()
                .collect { runYearlyCheck() }
        }
    }

    private suspend fun runYearlyCheck() {
        val result = runCatching { housekeeping.yearlyCheck() }.getOrNull() ?: return
        val year = time.today().year
        val done = listOfNotNull(
            result.holidaysRolled.takeIf { it > 0 }?.let { trf("Ho spostato %1\$s feste al %2\$s", it, year) },
            result.remindersCleaned.takeIf { it > 0 }?.let { trf("Pulizia annuale: eliminati %1\$s promemoria vecchi", it) },
        )
        if (done.isNotEmpty()) _message.value = done.joinToString(" · ")
        _yearly.value = YearlyProposal(
            holidays = if (YearlyTask.HOLIDAYS in postponed) 0 else result.holidaysToAsk,
            cleanupIds = if (YearlyTask.CLEANUP in postponed) emptyList() else result.cleanupToAsk,
        )
    }

    fun onConfirmYearly(task: YearlyTask, makeAutomatic: Boolean) {
        val proposal = _yearly.value
        viewModelScope.launch {
            when (task) {
                YearlyTask.HOLIDAYS -> {
                    housekeeping.rollHolidays()
                    _message.value = trf("Ho spostato %1\$s feste al %2\$s", proposal.holidays, time.today().year)
                    _yearly.value = proposal.copy(holidays = 0)
                    if (makeAutomatic) settings.update { it.copy(holidayMode = AutoMode.AUTOMATIC) }
                }
                YearlyTask.CLEANUP -> {
                    housekeeping.cleanup(proposal.cleanupIds)
                    _message.value = trf("Eliminati %1\$s promemoria vecchi", proposal.cleanupIds.size)
                    _yearly.value = proposal.copy(cleanupIds = emptyList())
                    if (makeAutomatic) settings.update { it.copy(cleanupMode = AutoMode.AUTOMATIC) }
                }
            }
        }
    }

    fun onLaterYearly(task: YearlyTask) {
        postponed += task
        _yearly.value = when (task) {
            YearlyTask.HOLIDAYS -> _yearly.value.copy(holidays = 0)
            YearlyTask.CLEANUP -> _yearly.value.copy(cleanupIds = emptyList())
        }
    }

    fun onNeverYearly(task: YearlyTask) {
        onLaterYearly(task)
        viewModelScope.launch {
            settings.update { if (task == YearlyTask.HOLIDAYS) it.copy(holidayMode = AutoMode.OFF) else it.copy(cleanupMode = AutoMode.OFF) }
        }
    }

    fun onMessageShown() {
        _message.value = null
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

    fun onShareBackup() {
        viewModelScope.launch {
            val uri = runCatching { housekeeping.exportBackup() }.getOrNull()
            _backupEvent.value = if (uri != null) BackupEvent.Share(uri) else BackupEvent.Failed
        }
    }

    private fun buildState(entries: List<ReminderWithLinks>, now: LocalDateTime): HomeUiState {
        val today = now.toLocalDate()
        val sorted = entries.sortedWith(compareBy(ReminderTimeline.chronologicalOrder) { it.reminder })
        val (attention, others) = sorted.partition { ReminderTimeline.needsAttention(it, now) }
        val todayItems = others.filter { ReminderTimeline.isOngoing(it.reminder, today) }
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
