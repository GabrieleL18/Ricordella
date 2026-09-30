package com.ricordella.app.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricordella.app.data.backup.BackupReadResult
import com.ricordella.app.data.backup.BackupRepository
import com.ricordella.app.data.backup.BackupSummary
import com.ricordella.app.data.backup.PendingRestore
import com.ricordella.app.domain.ReminderScheduler
import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.repository.SettingsRepository
import com.ricordella.app.domain.usecase.DeleteAllDataUseCase
import com.ricordella.app.domain.usecase.RestoreBackupUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isBusy: Boolean = false,
    /** Riepilogo del backup da confermare prima del ripristino. */
    val restoreSummary: BackupSummary? = null,
    val message: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val restoreBackup: RestoreBackupUseCase,
    private val deleteAllData: DeleteAllDataUseCase,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState())
    private var pendingRestore: PendingRestore? = null

    val uiState: StateFlow<SettingsUiState> = combine(settingsRepository.settings, local) { settings, state ->
        state.copy(settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Aggiorna le preferenze; se cambiano le notifiche, la pianificazione viene ricostruita. */
    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val before = settingsRepository.current()
            settingsRepository.update(transform)
            val after = settingsRepository.current()
            if (before.notificationsEnabled != after.notificationsEnabled || before.allDayNotificationTime != after.allDayNotificationTime) {
                scheduler.refresh()
            }
        }
    }

    fun onNotificationSettingsChanged() {
        viewModelScope.launch { scheduler.refresh() }
    }

    fun export(destination: Uri) = runBusy {
        backupRepository.export(destination)
        showMessage("Backup esportato.")
    }

    fun readBackup(source: Uri) = runBusy {
        val (result, pending) = backupRepository.read(source)
        when (result) {
            is BackupReadResult.Valid -> {
                pendingRestore = pending
                local.update { it.copy(restoreSummary = result.contents.summary) }
            }
            BackupReadResult.NotABackup -> showMessage("Il file scelto non è un backup di Ricordella.")
            is BackupReadResult.IncompatibleVersion ->
                showMessage("Questo backup è stato creato con una versione più recente di Ricordella. Aggiorna l'app e riprova.")
            is BackupReadResult.Corrupted -> showMessage("Il backup è danneggiato (${result.reason}). Nessun dato è stato modificato.")
        }
    }

    fun confirmRestore() {
        val pending = pendingRestore ?: return
        pendingRestore = null
        local.update { it.copy(restoreSummary = null) }
        runBusy {
            restoreBackup(pending)
            showMessage("Backup ripristinato.")
        }
    }

    fun cancelRestore() {
        pendingRestore?.let(backupRepository::discard)
        pendingRestore = null
        local.update { it.copy(restoreSummary = null) }
    }

    fun deleteEverything() = runBusy {
        deleteAllData()
        showMessage("Tutti i dati sono stati eliminati.")
    }

    fun onMessageShown() = local.update { it.copy(message = null) }

    private fun showMessage(message: String) = local.update { it.copy(message = message) }

    private fun runBusy(action: suspend () -> Unit) {
        viewModelScope.launch {
            local.update { it.copy(isBusy = true) }
            try {
                action()
            } catch (_: Exception) {
                showMessage("Operazione non riuscita. Nessun dato è stato perso: riprova.")
            } finally {
                local.update { it.copy(isBusy = false) }
            }
        }
    }

    override fun onCleared() {
        pendingRestore?.let(backupRepository::discard)
    }
}
