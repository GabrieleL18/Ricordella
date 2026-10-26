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
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.domain.date.RecurrenceCalculator
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.repository.ReminderRepository
import com.ricordella.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Riepilogo della settimana: la domenica alle 19 una notifica con quello che arriva da lunedì a domenica
 * (giorno e titolo delle prime cose). Se la settimana è vuota non arriva niente. Si spegne nelle Impostazioni.
 */
class WeeklySummary(
    private val context: Context,
    private val settings: SettingsRepository,
    private val reminders: ReminderRepository,
    private val calculator: RecurrenceCalculator,
    private val time: TimeSource,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val manager = NotificationManagerCompat.from(context)

    fun createChannel() {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, tr("Riepilogo della settimana"), NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Riprogramma da solo quando cambia l'interruttore. */
    fun watch(scope: CoroutineScope) {
        scope.launch {
            settings.settings.map { it.notificationsEnabled && it.weeklySummary }.distinctUntilChanged().collect { refresh() }
        }
    }

    suspend fun refresh() {
        val app = settings.current()
        if (!app.notificationsEnabled || !app.weeklySummary) {
            alarmManager.cancel(alarmIntent())
            return
        }
        val now = LocalDateTime.now(time.zone)
        var next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(SEND_TIME)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atZone(time.zone).toInstant().toEpochMilli(), alarmIntent())
    }

    /** Scatta la domenica sera: manda il riepilogo (se c'è qualcosa), poi programma la domenica dopo. */
    suspend fun ring() {
        val app = settings.current()
        if (app.notificationsEnabled && app.weeklySummary && manager.areNotificationsEnabled()) {
            val start = weekAfter(time.today())
            val items = weekItems(start, start.plusDays(6))
            if (items.isNotEmpty()) show(items)
        }
        refresh()
    }

    /** Giorno e titolo di ogni promemoria ancora da fare nella settimana, in ordine. */
    private suspend fun weekItems(start: LocalDate, end: LocalDate): List<Pair<LocalDate, String>> {
        val done = reminders.getCompletionsBetween(start, end).mapTo(HashSet()) { it.reminderId to it.occurrenceDate }
        return reminders.observeForRange(start, end).first()
            .filter { it.reminder.status == ReminderStatus.ACTIVE }
            .flatMap { entry ->
                // Un evento di più giorni conta una volta sola, il giorno in cui inizia.
                calculator.daysCoveredInRange(entry.reminder, entry.recurrenceRule, start, end)
                    .map { (_, occurrence) -> occurrence }.distinct()
                    .filter { it in start..end && (entry.reminder.id to it) !in done }
                    .map { Triple(it, entry.reminder.dueTime ?: LocalTime.MIN, entry.reminder.title) }
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.first to it.third }
    }

    private fun show(items: List<Pair<LocalDate, String>>) {
        val lines = items.take(MAX_LINES).map { (day, title) -> DateTexts.weekdayFull(day.dayOfWeek) + " " + day.dayOfMonth + " · " + title } +
            listOfNotNull(trf("e altre %1\$s", items.size - MAX_LINES).takeIf { items.size > MAX_LINES })
        val title = if (items.size == 1) tr("La prossima settimana: 1 cosa") else trf("La prossima settimana: %1\$s cose", items.size)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    REQUEST_OPEN,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permesso revocato: le stesse cose si vedono in Home.
        }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ALARM,
        Intent(context, WeeklySummaryReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val CHANNEL = "weekly_summary"
        private const val NOTIFICATION_ID = 7_400
        private const val REQUEST_ALARM = 7_400
        private const val REQUEST_OPEN = 7_401
        private const val MAX_LINES = 5
        private val SEND_TIME: LocalTime = LocalTime.of(19, 0)

        /** Il lunedì della settimana da riassumere: domani se è domenica, oggi se l'allarme è arrivato in ritardo a lunedì. */
        internal fun weekAfter(today: LocalDate): LocalDate = today.plusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}

/** Allarme della domenica sera. */
class WeeklySummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as RicordellaApplication).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.weeklySummary.ring()
            } finally {
                pending.finish()
            }
        }
    }
}
