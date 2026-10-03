package com.ricordella.app.domain.usecase

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import com.ricordella.app.domain.ReminderScheduler
import com.ricordella.app.domain.date.RecurrenceCalculator
import com.ricordella.app.domain.date.ReminderAlarmPlanner
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.ReminderCompletion
import com.ricordella.app.domain.model.ReminderDraft
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.date.nextAlarmDate
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
    /** Conferma per le cose degli altri (file condiviso); null = nessun controllo. */
    var guard: OwnershipGuard? = null

    /** [guarded] = false quando il salvataggio fa parte di un'azione già confermata (es. la garanzia di una cosa). */
    suspend operator fun invoke(draft: ReminderDraft, guarded: Boolean = true) {
        val guard = guard.takeIf { guarded }
        val now = time.now()
        val input = draft.reminder
        val existingEntry = reminders.getReminder(input.id)
        val existing = existingEntry?.reminder
        if (existingEntry != null && guard?.allow(existingEntry.people, existingEntry.reminder.title, GuardedAction.EDIT) == false) return
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
        if (existingEntry != null) guard?.done(existingEntry.people, reminder.title, GuardedAction.EDIT)
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
    var guard: OwnershipGuard? = null

    suspend operator fun invoke(reminderId: String) {
        val entry = reminders.getReminder(reminderId) ?: return
        val reminder = entry.reminder
        if (reminder.status != ReminderStatus.ACTIVE || !reminder.type.isCompletable) return
        if (guard?.allow(entry.people, reminder.title, GuardedAction.COMPLETE) == false) return
        val now = time.now()
        val today = time.today()
        // Una sveglia che non si ripete non si "completa": si spegne e resta nell'elenco, pronta da riaccendere.
        if (reminder.type == ReminderType.ALARM && entry.recurrenceRule == null) {
            reminders.update(reminder.copy(notificationsEnabled = false, snoozedUntil = null, lastNotifiedAt = now, updatedAt = now))
            scheduler.dismissNotification(reminder.id)
            scheduler.refresh()
            return
        }
        val completion = ReminderCompletion(reminderId = reminder.id, occurrenceDate = reminder.dueDate, completedAt = now)

        // Pagamento a rate: si segna pagata la rata di questa scadenza e si passa alla prossima ancora da pagare
        // (anche se in ritardo non se ne salta nessuna). Finite le rate l'evento si completa.
        reminder.plan?.let { plan ->
            val index = (0 until plan.count).firstOrNull { plan.dueDate(it) == reminder.dueDate && !plan.isPaid(it) } ?: plan.nextUnpaid() ?: return
            val paid = plan.pay(index, today)
            val next = paid.nextUnpaid()
            val updated = if (next != null) reminder.copy(plan = paid, dueDate = paid.dueDate(next), updatedAt = now, snoozedUntil = null)
            else reminder.copy(plan = paid, status = ReminderStatus.COMPLETED, completedAt = now, updatedAt = now, snoozedUntil = null)
            reminders.recordCompletion(updated, completion.copy(occurrenceDate = plan.dueDate(index)), entry.recurrenceRule?.takeIf { next != null }?.copy(startDate = paid.dueDate(next!!)))
            scheduler.dismissNotification(reminder.id)
            scheduler.refresh()
            guard?.done(entry.people, reminder.title, GuardedAction.COMPLETE)
            return
        }

        // Un promemoria in ritardo salta le occorrenze ormai passate. Le regole "dall'ultima volta"
        // ripartono da oggi: fatto prima o dopo, si spostano anche tutte le occorrenze successive.
        val after = maxOf(reminder.dueDate, today.minusDays(1))
        val rule = entry.recurrenceRule
        val nextDate = when {
            rule == null -> null
            rule.fromLastDone -> recurrence.nextOccurrenceAfter(rule.copy(startDate = today), today)
            else -> recurrence.nextOccurrenceAfter(rule, after)
        }
        val movedRule = if (rule != null && rule.fromLastDone && nextDate != null) rule.copy(startDate = nextDate) else null
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

        reminders.recordCompletion(updated, completion, movedRule)
        scheduler.dismissNotification(reminder.id)
        scheduler.refresh()
        guard?.done(entry.people, reminder.title, GuardedAction.COMPLETE)
    }
}

/**
 * Annulla il "fatto" dell'occorrenza [occurrence] (es. dal widget): il promemoria torna a quella data,
 * attivo, e la riga dello storico sparisce. Si annulla solo l'ultimo completamento, così un ricorrente
 * non salta indietro di più occorrenze.
 */
class UndoCompletionUseCase(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val time: TimeSource,
) {
    var guard: OwnershipGuard? = null

    suspend operator fun invoke(reminderId: String, occurrence: LocalDate) {
        val entry = reminders.getReminder(reminderId) ?: return
        val latest = reminders.observeCompletions(reminderId).first().maxByOrNull { it.completedAt } ?: return
        if (latest.occurrenceDate != occurrence) return
        if (guard?.allow(entry.people, entry.reminder.title, GuardedAction.REOPEN) == false) return
        val now = time.now()
        // ponytail: i km della prossima manutenzione restano quelli avanzati; lo storico non salva i precedenti.
        val updated = entry.reminder.copy(
            dueDate = occurrence,
            status = ReminderStatus.ACTIVE,
            completedAt = null,
            snoozedUntil = null,
            lastNotifiedAt = now,
            updatedAt = now,
        )
        val plan = entry.reminder.plan
        val unpaid = plan?.let { p -> (0 until p.count).firstOrNull { p.dueDate(it) == occurrence && p.isPaid(it) } }
        val restored = if (plan != null && unpaid != null) updated.copy(plan = plan.unpay(unpaid)) else updated
        val rule = entry.recurrenceRule?.takeIf { it.fromLastDone }?.copy(startDate = occurrence)
        reminders.undoCompletion(restored, latest.id, rule)
        scheduler.refresh()
        guard?.done(entry.people, entry.reminder.title, GuardedAction.REOPEN)
    }
}

/** Riporta attivo un promemoria completato per errore. */
class ReopenReminderUseCase(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val time: TimeSource,
) {
    var guard: OwnershipGuard? = null

    suspend operator fun invoke(reminderId: String) {
        val entry = reminders.getReminder(reminderId) ?: return
        val reminder = entry.reminder
        if (reminder.status == ReminderStatus.ACTIVE) return
        if (guard?.allow(entry.people, reminder.title, GuardedAction.REOPEN) == false) return
        val plan = reminder.plan?.let { p -> p.paid.maxByOrNull { it.index }?.let { p.unpay(it.index) } }
        reminders.update(
            reminder.copy(
                status = ReminderStatus.ACTIVE, completedAt = null, lastNotifiedAt = time.now(), updatedAt = time.now(),
                plan = plan ?: reminder.plan,
                dueDate = plan?.nextUnpaid()?.let(plan::dueDate) ?: reminder.dueDate,
            ),
        )
        scheduler.refresh()
        guard?.done(entry.people, reminder.title, GuardedAction.REOPEN)
    }
}

/** Rimanda la notifica di un promemoria. Unico punto in cui è definito il comportamento di "Rimanda". */
class SnoozeReminderUseCase(
    private val reminders: ReminderRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val time: TimeSource,
) {
    /** Restituisce quando la notifica tornerà, o null se non c'era nulla da rimandare. */
    suspend operator fun invoke(reminderId: String, option: SnoozeOption): java.time.Instant? {
        val reminder = reminders.getReminder(reminderId)?.reminder ?: return null
        if (reminder.status != ReminderStatus.ACTIVE) return null
        val now = time.now()
        val until = option.snoozeUntil(now, time.zone, settings.current().allDayNotificationTime)
        reminders.update(reminder.copy(snoozedUntil = until, updatedAt = now))
        scheduler.dismissNotification(reminderId)
        scheduler.refresh()
        return until
    }
}

class DeleteReminderUseCase(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val trash: com.ricordella.app.data.trash.Trash? = null,
) {
    suspend operator fun invoke(reminderId: String) {
        trash?.saveReminders(listOf(reminderId))
        reminders.delete(reminderId)
        scheduler.dismissNotification(reminderId)
        scheduler.refresh()
    }
}

/**
 * Accende o spegne una sveglia. Riaccendendola si ricalcola il prossimo giorno in cui suona
 * (una sveglia spenta per giorni non deve restare ferma a una data passata).
 */
class SetAlarmEnabledUseCase(
    private val reminders: ReminderRepository,
    private val save: SaveReminderUseCase,
    private val scheduler: ReminderScheduler,
    private val time: TimeSource,
) {
    suspend operator fun invoke(reminderId: String, enabled: Boolean) {
        val entry = reminders.getReminder(reminderId) ?: return
        val alarm = entry.reminder
        if (alarm.type != ReminderType.ALARM || alarm.notificationsEnabled == enabled) return
        if (!enabled) {
            reminders.update(alarm.copy(notificationsEnabled = false, snoozedUntil = null, updatedAt = time.now()))
            scheduler.dismissNotification(reminderId)
            scheduler.refresh()
            return
        }
        val at = alarm.dueTime ?: return
        val rule = entry.recurrenceRule
        val date = nextAlarmDate(at, rule?.daysOfWeek.orEmpty(), time.localNow(), alarm.pausedUntil)
        save(
            ReminderDraft(alarm.copy(notificationsEnabled = true, dueDate = date), rule, entry.people.mapTo(mutableSetOf()) { it.id }, entry.items.mapTo(mutableSetOf()) { it.id }),
            guarded = false,
        )
    }
}
