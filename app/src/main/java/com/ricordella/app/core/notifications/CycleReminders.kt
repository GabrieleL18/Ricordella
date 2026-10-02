package com.ricordella.app.core.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ricordella.app.MainActivity
import com.ricordella.app.R
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.CycleCalendar
import com.ricordella.app.domain.repository.PersonRepository
import com.ricordella.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Avviso di ritardo del ciclo: una volta al giorno alle 9 (allarme tutto suo, come le pozioni) controlla le persone
 * che hanno l'avviso acceso e avvisa il primo giorno di ritardo e poi ogni settimana. Per i giorni previsti non c'è
 * nessuna notifica. La notifica non mostra il testo sulla schermata di blocco.
 */
class CycleReminders(
    private val context: Context,
    private val settings: SettingsRepository,
    private val people: PersonRepository,
    private val time: TimeSource,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val manager = NotificationManagerCompat.from(context)

    fun createChannel() {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, tr("Ciclo"), NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Riprogramma da solo quando cambiano i cicli o l'interruttore delle notifiche. */
    fun watch(scope: CoroutineScope) {
        scope.launch {
            settings.settings.map { Triple(it.notificationsEnabled, it.cycleProfiles, it.cycleLog) }.distinctUntilChanged().collect { refresh() }
        }
    }

    suspend fun refresh() {
        val app = settings.current()
        if (!app.notificationsEnabled || app.cycleProfiles.none { it.notifyLate }) {
            alarmManager.cancel(alarmIntent())
            return
        }
        val now = LocalDateTime.now(time.zone)
        val next = now.toLocalDate().atTime(CHECK_TIME).let { if (it.isAfter(now)) it else it.plusDays(1) }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atZone(time.zone).toInstant().toEpochMilli(), alarmIntent())
    }

    /** Scatta l'allarme delle 9: avvisa chi è in ritardo, poi programma il giorno dopo. */
    suspend fun ring() {
        val app = settings.current()
        val today = time.today()
        if (app.notificationsEnabled && manager.areNotificationsEnabled()) {
            app.cycleProfiles.filter { it.notifyLate }.forEach { profile ->
                val late = CycleCalendar.lateDays(profile, app.cycleLog, today)
                if (late == 1 || (late > 0 && late % 7 == 0)) {
                    val name = people.getPerson(profile.personId)?.name ?: return@forEach
                    show(profile.personId, name, late)
                }
            }
        }
        refresh()
    }

    private fun show(personId: String, name: String, late: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(trf("Ciclo di %1\$s in ritardo", name))
            .setContentText(if (late == 1) tr("È previsto da ieri. Se è arrivato, segnalo qui.") else trf("È in ritardo di %1\$s giorni.", late))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    REQUEST_OPEN,
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_CYCLE, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .addAction(
                0,
                tr("È arrivato oggi"),
                PendingIntent.getBroadcast(
                    context,
                    personId.hashCode(),
                    Intent(context, CycleReceiver::class.java).setAction(CycleReceiver.ACTION_STARTED).putExtra(CycleReceiver.EXTRA_PERSON, personId),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        try {
            manager.notify(personId, NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permesso revocato: il ritardo si vede comunque nella schermata del ciclo.
        }
    }

    /** Dal tasto della notifica: segna l'inizio di oggi. */
    suspend fun started(personId: String) {
        val today = time.today()
        settings.update { app ->
            val profile = app.cycleProfiles.firstOrNull { it.personId == personId } ?: return@update app
            app.copy(cycleLog = CycleCalendar.withStart(app.cycleLog, profile, today))
        }
        manager.cancel(personId, NOTIFICATION_ID)
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ALARM,
        Intent(context, CycleReceiver::class.java).setAction(CycleReceiver.ACTION_ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val CHANNEL = "cycle"
        const val NOTIFICATION_ID = 7_300
        const val REQUEST_ALARM = 7_300
        const val REQUEST_OPEN = 7_302
        val CHECK_TIME: LocalTime = LocalTime.of(9, 0)
    }
}

/** Allarme delle 9 e tasto «È arrivato oggi» della notifica. */
class CycleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as RicordellaApplication).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                when (intent.action) {
                    ACTION_ALARM -> container.cycleReminders.ring()
                    ACTION_STARTED -> intent.getStringExtra(EXTRA_PERSON)?.let { container.cycleReminders.started(it) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "com.ricordella.app.action.CYCLE_ALARM"
        const val ACTION_STARTED = "com.ricordella.app.action.CYCLE_STARTED"
        const val EXTRA_PERSON = "com.ricordella.app.extra.CYCLE_PERSON"
    }
}
