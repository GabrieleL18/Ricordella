package com.ricordella.app.domain.usecase

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import android.net.Uri
import androidx.core.net.toUri
import com.ricordella.app.data.backup.BackupRepository
import com.ricordella.app.data.local.dao.ReminderDao
import com.ricordella.app.domain.ReminderScheduler
import com.ricordella.app.domain.date.Holidays
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.model.AutoMode
import com.ricordella.app.domain.repository.SettingsRepository
import java.time.LocalDate

/**
 * Manutenzione periodica dei dati: promemoria del backup (ogni giorno o come scelto),
 * una volta l'anno, la proposta di eliminare i promemoria vecchi e poco utili.
 */
class Housekeeping(
    private val backup: BackupRepository,
    private val reminderDao: ReminderDao,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val time: TimeSource,
) {

    /** Crea il backup da condividere e fa ripartire il conteggio. */
    suspend fun exportBackup(): Uri {
        val uri = backup.exportForSharing()
        markBackupDone()
        return uri
    }

    /**
     * Sovrascrive il backup nel file già scelto. Restituisce false se non c'è un file
     * o non è più scrivibile (cancellato, permesso perso): allora va chiesto dove salvarlo.
     */
    suspend fun overwriteBackup(): Boolean {
        val target = settings.current().backupTargetUri?.toUri() ?: return false
        val ok = runCatching { backup.exportTo(target) }.isSuccess
        if (ok) markBackupDone() else settings.update { it.copy(backupTargetUri = null) }
        return ok
    }

    /** Salva in un nuovo file (primo backup o "nuova versione"), che diventa quello da sovrascrivere. */
    suspend fun exportToNewFile(uri: Uri) {
        backup.exportTo(uri)
        backup.keepAccess(uri)
        settings.update { it.copy(backupTargetUri = uri.toString()) }
        markBackupDone()
    }

    private suspend fun markBackupDone() {
        val today = time.today().toEpochDay()
        settings.update { it.copy(backupCheckEpochDay = today) }
    }

    /** Alla prima apertura fa partire il conteggio: il primo invito arriva dopo l'intervallo scelto. */
    suspend fun startBackupClockIfNeeded() {
        if (settings.current().backupCheckEpochDay != null) return
        val today = time.today().toEpochDay()
        settings.update { if (it.backupCheckEpochDay == null) it.copy(backupCheckEpochDay = today) else it }
    }

    /** "Più tardi": l'invito torna dopo una settimana (o dopo l'intervallo, se più breve). */
    suspend fun postponeBackup() {
        val today = time.today().toEpochDay()
        settings.update {
            val interval = it.backupIntervalDays.toLong()
            it.copy(backupCheckEpochDay = today - interval + minOf(POSTPONE_DAYS, interval))
        }
    }

    /**
     * Promemoria degli anni precedenti che la pulizia annuale eliminerebbe, se è il momento
     * (primo avvio in un anno nuovo); lista vuota altrimenti. Il primo anno d'uso non propone nulla.
     */
    suspend fun cleanupCandidates(): List<String> {
        val year = time.today().year
        val last = settings.current().lastCleanupYear
        if (last == null) {
            settings.update { it.copy(lastCleanupYear = year) }
            return emptyList()
        }
        if (last >= year) return emptyList()
        return reminderDao.getCleanupCandidates(LocalDate.of(year, 1, 1))
    }

    /** Quanti promemoria degli anni passati eliminerebbe la pulizia (senza guardare se è il momento). */
    suspend fun countCleanupCandidates(): Int = reminderDao.getCleanupCandidates(LocalDate.of(time.today().year, 1, 1)).size

    /** Quante feste sono rimaste negli anni passati e andrebbero spostate all'anno corrente. */
    suspend fun holidaysToRoll(): Int = reminderDao.getHolidaysBefore(LocalDate.of(time.today().year, 1, 1)).size

    /**
     * Operazioni annuali all'avvio: quelle in automatico vengono eseguite subito,
     * quelle "chiedimi" restituite come proposta; "mai" non fa nulla.
     */
    suspend fun yearlyCheck(): YearlyCheck {
        val current = settings.current()
        var rolled = 0
        var cleaned = 0
        var holidays = 0
        var cleanupIds = emptyList<String>()
        val pastHolidays = holidaysToRoll()
        when (current.holidayMode) {
            AutoMode.AUTOMATIC -> if (pastHolidays > 0) { rollHolidays(); rolled = pastHolidays }
            AutoMode.ASK -> holidays = pastHolidays
            AutoMode.OFF -> Unit
        }
        val candidates = cleanupCandidates()
        when (current.cleanupMode) {
            AutoMode.AUTOMATIC -> if (candidates.isNotEmpty()) { cleanup(candidates); cleaned = candidates.size } else if (current.lastCleanupYear != null) dismissCleanup()
            AutoMode.ASK -> cleanupIds = candidates
            AutoMode.OFF -> dismissCleanup()
        }
        return YearlyCheck(holidays, cleanupIds, rolled, cleaned)
    }

    /**
     * Riporta le feste dell'anno passato a quello corrente, Pasqua e feste mobili comprese.
     * Un doppione (stessa festa già presente in quella data) viene eliminato.
     */
    suspend fun rollHolidays() {
        val year = time.today().year
        val old = reminderDao.getHolidaysBefore(LocalDate.of(year, 1, 1))
        if (old.isEmpty()) return
        val now = time.now()
        old.forEach { holiday ->
            val date = Holidays.moveToYear(holiday.title, holiday.dueDate, year)
            if (reminderDao.countSame(holiday.title, date) > 0) reminderDao.deleteWithDependencies(holiday.id)
            else reminderDao.updateReminder(holiday.copy(dueDate = date, updatedAt = now, lastNotifiedAt = null))
        }
        scheduler.refresh()
    }

    suspend fun cleanup(ids: List<String>) {
        reminderDao.deleteAllWithDependencies(ids)
        ids.forEach(scheduler::dismissNotification)
        scheduler.refresh()
        dismissCleanup()
    }

    suspend fun dismissCleanup() {
        val year = time.today().year
        settings.update { it.copy(lastCleanupYear = year) }
    }

    /** Esito del controllo annuale: proposte da chiedere e operazioni già fatte in automatico. */
    data class YearlyCheck(val holidaysToAsk: Int, val cleanupToAsk: List<String>, val holidaysRolled: Int, val remindersCleaned: Int)

    companion object {
        private const val POSTPONE_DAYS = 7L

        /** Intervalli proposti nelle impostazioni, in giorni. */
        val BACKUP_INTERVALS = listOf(1, 7, 30, 90, 365)

        fun isBackupDue(settings: AppSettings, today: LocalDate): Boolean {
            val since = settings.backupCheckEpochDay ?: return false
            return today.toEpochDay() - since >= settings.backupIntervalDays
        }

        fun intervalLabel(days: Int): String = when (days) {
            1 -> tr("Ogni giorno")
            7 -> tr("Ogni settimana")
            30 -> tr("Ogni mese")
            90 -> tr("Ogni 3 mesi")
            365 -> tr("Ogni anno")
            else -> trf("Ogni %1\$s giorni", days)
        }
    }
}
