package com.ricordella.app.feature.settings

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

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
import com.ricordella.app.domain.usecase.Housekeeping
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
    /** Backup appena creato, da passare al menu Condividi. */
    val shareUri: Uri? = null,
    /** Nessun file di backup scelto (o non più scrivibile): va chiesto dove salvarlo. */
    val askNewFile: Boolean = false,
    /** Il backup scelto è rovinato ma c'è la copia di riserva: si propone di usare quella. */
    val offerReserve: Boolean = false,
    /** Quando è stata fatta la copia di riserva (millisecondi). */
    val reserveSavedAt: Long? = null,
    /** Cartella scelta dove c'è già un backup con questo nome: va compilato l'alias. */
    val needAlias: Uri? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val restoreBackup: RestoreBackupUseCase,
    private val deleteAllData: DeleteAllDataUseCase,
    private val scheduler: ReminderScheduler,
    private val housekeeping: Housekeeping,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState())
    private var pendingRestore: PendingRestore? = null

    val uiState: StateFlow<SettingsUiState> = combine(settingsRepository.settings, local) { settings, state ->
        // lastBackupMillis cambia a ogni backup: così la data della copia di riserva resta aggiornata.
        state.copy(settings = settings, reserveSavedAt = backupRepository.reserveSavedAt())
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

    fun export() = runBusy {
        val uri = housekeeping.exportBackup()
        local.update { it.copy(shareUri = uri) }
    }

    fun onShared() = local.update { it.copy(shareUri = null) }

    /** Aggiorna il backup sovrascrivendo il file precedente; se manca, chiede dove salvarlo. */
    fun exportOverwrite() = runBusy {
        if (housekeeping.overwriteBackup()) showMessage(tr("Backup aggiornato (file precedente sovrascritto)."))
        else local.update { it.copy(askNewFile = true) }
    }

    /** Primo backup o nuova versione: da ora in poi è questo il file che viene sovrascritto. */
    fun exportNewFile(destination: Uri) = runBusy {
        housekeeping.exportToNewFile(destination)
        showMessage(tr("Backup salvato."))
    }

    fun onAskedNewFile() = local.update { it.copy(askNewFile = false) }

    fun readBackup(source: Uri) = runBusy { handleRead(backupRepository.read(source), fromReserve = false) }

    /** Ripristino dalla copia di riserva che l'app tiene sul telefono. */
    fun readReserve(file: java.io.File? = null) = runBusy {
        local.update { it.copy(offerReserve = false) }
        handleRead(if (file == null) backupRepository.readReserve() else backupRepository.readReserve(file), fromReserve = true)
    }

    /** Le ultime copie di riserva sul telefono, dalla più recente. */
    fun reserveCopies(): List<java.io.File> = backupRepository.reserveCopies()

    /** Sceglie la cartella (es. su Drive) dove salvare i backup con la data, ultime 3 copie. */
    fun setBackupFolder(folder: Uri) = runBusy {
        if (housekeeping.backupNameTaken(folder)) {
            local.update { it.copy(needAlias = folder) }
            return@runBusy
        }
        if (housekeeping.setBackupFolder(folder)) showMessage(tr("Cartella scelta: backup salvato."))
        else showMessage(tr("Non riesco a scrivere in quella cartella. Prova con un'altra, oppure usa «Esporta backup»."))
    }

    fun cancelAlias() = local.update { it.copy(needAlias = null) }

    /** Salva l'alias e riprova con la cartella: se il nome è ancora uguale a uno già presente lo richiede. */
    fun confirmAlias(folder: Uri, alias: String) {
        local.update { it.copy(needAlias = null) }
        viewModelScope.launch {
            housekeeping.setBackupAlias(alias)
            setBackupFolder(folder)
        }
    }

    fun clearBackupFolder() {
        viewModelScope.launch { housekeeping.clearBackupFolder() }
    }

    fun onReserveOfferDismissed() = local.update { it.copy(offerReserve = false) }

    private fun handleRead(read: Pair<BackupReadResult, PendingRestore?>, fromReserve: Boolean) {
        val (result, pending) = read
        if (!fromReserve && result is BackupReadResult.Corrupted && backupRepository.reserveSavedAt() != null) {
            local.update { it.copy(offerReserve = true) }
            return
        }
        when (result) {
            is BackupReadResult.Valid -> {
                pendingRestore = pending
                local.update { it.copy(restoreSummary = result.contents.summary) }
            }
            BackupReadResult.NotABackup -> showMessage(tr("Il file scelto non è un backup di Remindella."))
            is BackupReadResult.IncompatibleVersion ->
                showMessage(tr("Questo backup è stato creato con una versione più recente di Remindella. Aggiorna l'app e riprova."))
            is BackupReadResult.Corrupted -> showMessage(trf("Il backup è danneggiato (%1\$s). Nessun dato è stato modificato.", result.reason))
        }
    }

    fun confirmRestore() {
        val pending = pendingRestore ?: return
        pendingRestore = null
        local.update { it.copy(restoreSummary = null) }
        runBusy {
            restoreBackup(pending)
            showMessage(tr("Backup ripristinato."))
        }
    }

    fun cancelRestore() {
        pendingRestore?.let(backupRepository::discard)
        pendingRestore = null
        local.update { it.copy(restoreSummary = null) }
    }

    fun deleteEverything() = runBusy {
        deleteAllData()
        showMessage(tr("Tutti i dati sono stati eliminati."))
    }

    fun onMessageShown() = local.update { it.copy(message = null) }

    private fun showMessage(message: String) = local.update { it.copy(message = message) }

    private fun runBusy(action: suspend () -> Unit) {
        viewModelScope.launch {
            local.update { it.copy(isBusy = true) }
            try {
                action()
            } catch (_: Exception) {
                showMessage(tr("Operazione non riuscita. Nessun dato è stato perso: riprova."))
            } finally {
                local.update { it.copy(isBusy = false) }
            }
        }
    }

    override fun onCleared() {
        pendingRestore?.let(backupRepository::discard)
    }
}
