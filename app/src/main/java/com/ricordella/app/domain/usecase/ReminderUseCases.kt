package com.ricordella.app.domain.usecase

import com.ricordella.app.domain.ReminderScheduler
import com.ricordella.app.domain.date.RecurrenceCalculator
import com.ricordella.app.domain.date.ReminderAlarmPlanner
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.ReminderCompletion
import com.ricordella.app.domain.model.ReminderDraft
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.SnoozeOption
import com.ricordella.app.domain.model.snoozeUntil
import com.ricordella.app.domain.repository.ReminderRepository
import com.ricordella.app.domain.repository.SettingsRepository

/** Crea o modifica un promemoria e aggiorna la pianificazione delle notifiche. */
class SaveReminderUseCase(
    private val reminders: ReminderRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val planner: ReminderAlarmPlanner,
    private val time: TimeSource,
) {
    suspend operator fun invoke(draft: ReminderDraft) {
        val now = time.now()
        val input = draft.reminder
        val existing = reminders.getReminder(input.id)?.reminder
        val dateChanged = existing == null || existing.dueDate != input.dueDate || existing.dueTime != input.dueTime
        val base = input.copy(
            title = input.title.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            snoozedUntil = if (dateChanged) null else existing.snoozedUntil,
            lastNotifiedAt = existing?.lastNotifiedAt,
        )
        // Se l'orario della notifica è già passato non si notifica "a sorpresa" subito dopo il salvataggio.
        val trigger = planner.triggerAt(
            base.dueDate,
            base.dueTime,
            base.notifyOffsetMinutes,
            base.snoozedUntil,
            settings.current().allDayNotificationTime,
            time.zone,
        )
        val reminder = if (!trigger.isAfter(now)) base.copy(lastNotifiedAt = now) else base
        val recurrence = draft.recurrence?.copy(startDate = reminder.dueDate)
        reminders.save(draft.copy(reminder = reminder, recurrence = recurrence))
        scheduler.refresh()
    }
}

/**
 * Completa l'occorrenza corrente. Per i ricorrenti registra lo storico e avanza
 * all'occorrenza successiva invece di duplicare il promemoria.
 */
class CompleteReminderUseCase(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val recurrence: RecurrenceCalculator,
    private val time: TimeSource,
) {
    suspend operator fun invoke(reminderId: String) {
        val entry = reminders.getReminder(reminderId) ?: return
        val reminder = entry.reminder
        if (reminder.status != ReminderStatus.ACTIVE || !reminder.type.isCompletable) return
        val now = time.now()
        val today = time.today()
        val completion = ReminderCompletion(reminderId = reminder.id, occurrenceDate = reminder.dueDate, completedAt = now)

        // Un promemoria in ritardo salta le occorrenze ormai passate.
        val after = maxOf(reminder.dueDate, today.minusDays(1))
        val nextDate = entry.recurrenceRule?.let { recurrence.nextOccurrenceAfter(it, after) }
        val kmInterval = reminder.odometerIntervalKm
        val nextKm = if (kmInterval != null && reminder.dueOdometerKm != null) {
            (entry.odometerItem?.odometerKm ?: reminder.dueOdometerKm) + kmInterval
        } else {
            null
        }

        val updated = when {
            nextDate != null -> reminder.copy(dueDate = nextDate, dueOdometerKm = nextKm ?: reminder.dueOdometerKm)
            // Scadenza a km senza ricorrenza per data: vale "ogni N km o al massimo ogni anno".
            nextKm != null -> reminder.copy(dueDate = today.plusYears(1), dueOdometerKm = nextKm)
            else -> reminder.copy(status = ReminderStatus.COMPLETED, completedAt = now)
        }.copy(snoozedUntil = null, updatedAt = now)

        reminders.recordCompletion(updated, completion)
        scheduler.dismissNotification(reminder.id)
        scheduler.refresh()
    }
}

/** Riporta attivo un promemoria completato per errore. */
class ReopenReminderUseCase(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val time: TimeSource,
) {
    suspend operator fun invoke(reminderId: String) {
        val reminder = reminders.getReminder(reminderId)?.reminder ?: return
        if (reminder.status == ReminderStatus.ACTIVE) return
        reminders.update(
            reminder.copy(status = ReminderStatus.ACTIVE, completedAt = null, lastNotifiedAt = time.now(), updatedAt = time.now()),
        )
        scheduler.refresh()
    }
}

/** Rimanda la notifica di un promemoria. Unico punto in cui è definito il comportamento di "Rimanda". */
class SnoozeReminderUseCase(
    private val reminders: ReminderRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val time: TimeSource,
) {
    suspend operator fun invoke(reminderId: String, option: SnoozeOption) {
        val reminder = reminders.getReminder(reminderId)?.reminder ?: return
        if (reminder.status != ReminderStatus.ACTIVE) return
        val now = time.now()
        val until = option.snoozeUntil(now, time.zone, settings.current().allDayNotificationTime)
        reminders.update(reminder.copy(snoozedUntil = until, updatedAt = now))
        scheduler.dismissNotification(reminderId)
        scheduler.refresh()
    }
}

class DeleteReminderUseCase(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(reminderId: String) {
        reminders.delete(reminderId)
        scheduler.dismissNotification(reminderId)
        scheduler.refresh()
    }
}
