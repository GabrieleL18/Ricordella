package com.ricordella.app.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ricordella.app.core.alarm.AlarmRingService
import com.ricordella.app.core.widget.CalendarWidgetProvider
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.model.SchedulingCandidate
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
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

    private companion object {
        val PREVIEW_GRACE: Duration = Duration.ofMinutes(10)
    }

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    override suspend fun refresh() = mutex.withLock {
        // Ogni modifica ai promemoria passa di qui: è il punto giusto per ridisegnare il widget.
        CalendarWidgetProvider.requestUpdate(context)
        com.ricordella.app.core.widget.AgendaWidgetProvider.requestUpdate(context)
        com.ricordella.app.core.widget.AlarmWidgetProvider.requestUpdate(context)
        val appSettings = settings.current()
        if (!appSettings.notificationsEnabled) {
            alarmManager.cancel(alarmIntent())
            return@withLock
        }
        val now = time.now()
        val candidates = reminders.getSchedulingCandidates()
        val plan = planner.plan(candidates, now, appSettings.allDayNotificationTime, time.zone)
        val nextPreview = showAlarmPreviews(candidates, appSettings.alarmPreNotice, now, appSettings.allDayNotificationTime)

        if (plan.dueNow.isNotEmpty()) {
            val today = time.today()
            plan.dueNow.forEach { id ->
                val entry = reminders.getReminder(id) ?: return@forEach
                // Una sveglia condivisa suona solo sul telefono della persona a cui è assegnata.
                if (entry.reminder.type == ReminderType.ALARM && !isMine(entry, appSettings.sharedMeId)) return@forEach
                // Le sveglie (e, se scelto, i promemoria importanti/urgenti) suonano a tutto schermo
                // finché non si risponde; se Android non lo consente, notifica normale.
                val insistent = entry.reminder.type == ReminderType.ALARM ||
                    (entry.reminder.type.isCompletable && appSettings.insistentLevel.applies(entry.reminder.priority))
                val rang = insistent && AlarmRingService.start(context, id, entry.reminder.title, appSettings, entry.reminder.alarmStyle ?: appSettings.alarmStyle)
                if (!rang) notifier.show(entry, today)
            }
            reminders.markNotified(plan.dueNow, now)
            plan.dueNow.forEach(notifier::dismissAlarmPreview)
        }

        val next = plan.nextAlarmAt
        when {
            nextPreview != null && (next == null || nextPreview.isBefore(next)) -> setAlarm(nextPreview.toEpochMilli(), exact = true)
            next == null -> alarmManager.cancel(alarmIntent())
            else -> setAlarm(next.toEpochMilli(), exact = plan.nextAlarmIsTimed)
        }
    }

    /**
     * Avviso silenzioso [leadMinutes] prima di ogni sveglia, una volta per occorrenza
     * (se la sveglia è stata creata a ridosso dell'orario non si avvisa). Restituisce il prossimo avviso da programmare.
     */
    private suspend fun showAlarmPreviews(candidates: List<SchedulingCandidate>, leadMinutes: Int, now: Instant, allDay: LocalTime): Instant? {
        if (leadMinutes <= 0) return null
        val prefs = context.getSharedPreferences("alarm_previews", Context.MODE_PRIVATE)
        var next: Instant? = null
        for (c in candidates) {
            if (!c.isAlarm || c.snoozedUntil != null) continue
            val trigger = planner.triggerAt(c, allDay, time.zone)
            if (!trigger.isAfter(now)) continue
            val preview = trigger.minusSeconds(leadMinutes * 60L)
            if (preview.isAfter(now)) {
                if (next == null || preview.isBefore(next)) next = preview
            } else if (Duration.between(preview, now) <= PREVIEW_GRACE && prefs.getLong(c.id, 0L) != trigger.toEpochMilli()) {
                reminders.getReminder(c.id)?.takeIf { isMine(it, settings.current().sharedMeId) }?.let(notifier::showAlarmPreview)
                prefs.edit().putLong(c.id, trigger.toEpochMilli()).apply()
            }
        }
        return next
    }

    /** Senza persona scelta come "io" (nessuna condivisione) vale tutto; altrimenti la sveglia deve essere assegnata a me. */
    private fun isMine(entry: com.ricordella.app.domain.model.ReminderWithLinks, meId: String?): Boolean =
        meId == null || entry.people.isEmpty() || entry.people.any { it.id == meId }

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
