package com.ricordella.app.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ricordella.app.core.widget.CalendarWidgetProvider
import com.ricordella.app.domain.ReminderScheduler
import com.ricordella.app.domain.date.ReminderAlarmPlanner
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.repository.ReminderRepository
import com.ricordella.app.domain.repository.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Implementazione Android di [ReminderScheduler] basata su AlarmManager.
 *
 * Mantiene un solo allarme: quello della prossima notifica. Quando scatta
 * ([ReminderAlarmReceiver]) si mostrano i promemoria dovuti e si programma il successivo.
 * Gli exact alarm si usano solo per promemoria con orario preciso e se consentiti dall'utente.
 */
class AlarmManagerReminderScheduler(
    private val context: Context,
    private val reminders: ReminderRepository,
    private val settings: SettingsRepository,
    private val notifier: ReminderNotifier,
    private val planner: ReminderAlarmPlanner,
    private val time: TimeSource,
) : ReminderScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    override suspend fun refresh() = mutex.withLock {
        // Ogni modifica ai promemoria passa di qui: è il punto giusto per ridisegnare il widget.
        CalendarWidgetProvider.requestUpdate(context)
        val appSettings = settings.current()
        if (!appSettings.notificationsEnabled) {
            alarmManager.cancel(alarmIntent())
            return@withLock
        }
        val now = time.now()
        val plan = planner.plan(reminders.getSchedulingCandidates(), now, appSettings.allDayNotificationTime, time.zone)

        if (plan.dueNow.isNotEmpty()) {
            val today = time.today()
            plan.dueNow.forEach { id -> reminders.getReminder(id)?.let { notifier.show(it, today) } }
            reminders.markNotified(plan.dueNow, now)
        }

        val next = plan.nextAlarmAt
        if (next == null) {
            alarmManager.cancel(alarmIntent())
        } else {
            setAlarm(next.toEpochMilli(), exact = plan.nextAlarmIsTimed)
        }
    }

    override fun dismissNotification(reminderId: String) = notifier.dismiss(reminderId)

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun setAlarm(triggerAtMillis: Long, exact: Boolean) {
        val pendingIntent = alarmIntent()
        if (exact && canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                return
            } catch (_: SecurityException) {
                // Permesso revocato: si ripiega sull'allarme non esatto.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderAlarmReceiver::class.java).setAction(ReminderAlarmReceiver.ACTION_ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
