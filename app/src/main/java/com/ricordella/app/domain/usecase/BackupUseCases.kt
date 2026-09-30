package com.ricordella.app.domain.usecase

import com.ricordella.app.data.backup.BackupRepository
import com.ricordella.app.data.backup.PendingRestore
import com.ricordella.app.domain.ReminderScheduler

/** Ripristina un backup già validato e riprogramma tutte le notifiche. */
class RestoreBackupUseCase(
    private val backup: BackupRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(pending: PendingRestore) {
        backup.restore(pending)
        scheduler.refresh()
    }
}

/** Cancella tutti i dati dell'utente e le notifiche programmate. */
class DeleteAllDataUseCase(
    private val backup: BackupRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke() {
        backup.deleteAllData()
        scheduler.refresh()
    }
}
