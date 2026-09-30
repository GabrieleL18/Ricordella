package com.ricordella.app.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.data.backup.BackupRepository
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.repository.SettingsRepository
import com.ricordella.app.domain.usecase.Housekeeping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Backup automatico: ogni giorno all'orario scelto, se è passato l'intervallo, sovrascrive da solo
 * il file di backup. Se l'utente non ne ha scelto uno (o non è più scrivibile) ne crea uno in Download/Remindella.
 */
class AutoBackup(
    private val context: Context,
    private val settings: SettingsRepository,
    private val housekeeping: Housekeeping,
    private val backup: BackupRepository,
    private val time: TimeSource,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun watch(scope: CoroutineScope) {
        scope.launch {
            settings.settings
                .map { Triple(it.autoBackupTime, it.backupTargetUri, it.backupIntervalDays) }
                .distinctUntilChanged()
                .collect { refresh() }
        }
    }

    /**
     * Programma il prossimo backup; se è dovuto e l'orario di oggi è già passato (telefono spento),
     * lo fa subito. Dopo un tentativo ([afterRun]) si passa comunque a domani, anche se è fallito.
     */
    suspend fun refresh(afterRun: Boolean = false) {
        val at = settings.current().autoBackupTime
        if (at == null) {
            alarmManager.cancel(alarmIntent())
            return
        }
        val now = LocalDateTime.now(time.zone)
        val todayAt = now.toLocalDate().atTime(at)
        val next = when {
            todayAt > now -> todayAt
            !afterRun && isDue() -> now.plusMinutes(1)
            else -> todayAt.plusDays(1)
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atZone(time.zone).toInstant().toEpochMilli(), alarmIntent())
    }

    suspend fun run() {
        if (isDue()) runCatching {
            if (!housekeeping.overwriteBackup()) backup.createAutoTarget()?.let { housekeeping.exportToNewFile(it) }
        }
        refresh(afterRun = true)
    }

    /** Mai fatto un backup su file: il primo si fa subito, senza aspettare l'intervallo. */
    private suspend fun isDue(): Boolean {
        val app = settings.current()
        return app.backupTargetUri == null || Housekeeping.isBackupDue(app, time.today())
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ALARM,
        Intent(context, AutoBackupReceiver::class.java).setAction(AutoBackupReceiver.ACTION_ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val REQUEST_ALARM = 7_200
    }
}

class AutoBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return
        val container = (context.applicationContext as RicordellaApplication).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.autoBackup.run()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "com.ricordella.app.action.AUTO_BACKUP"
    }
}
