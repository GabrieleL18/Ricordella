package com.ricordella.app.domain.usecase

import com.ricordella.app.core.i18n.trf

import com.ricordella.app.domain.ReminderScheduler
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.Item
import com.ricordella.app.domain.model.MaintenanceRecord
import com.ricordella.app.domain.model.PersonItemRole
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderDraft
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.ReminderSuggestion
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.repository.ItemRepository
import com.ricordella.app.domain.repository.MaintenanceRepository
import com.ricordella.app.domain.repository.ReminderRepository
import com.ricordella.app.domain.repository.SettingsRepository
import java.time.LocalDate

/** Anticipo della notifica di scadenza garanzia: 30 giorni. */
const val WARRANTY_NOTICE_MINUTES = 30 * 24 * 60

/** Suggerimento selezionato dall'utente, con la data scelta per la prima scadenza. */
data class SelectedSuggestion(val suggestion: ReminderSuggestion, val firstDueDate: LocalDate)

/**
 * Salva una cosa con le persone associate, mantiene allineato il promemoria di garanzia
 * e crea i promemoria suggeriti scelti dall'utente.
 */
class SaveItemUseCase(
    private val items: ItemRepository,
    private val reminders: ReminderRepository,
    private val settings: SettingsRepository,
    private val saveReminder: SaveReminderUseCase,
    private val time: TimeSource,
) {
    var guard: OwnershipGuard? = null

    suspend operator fun invoke(
        item: Item,
        people: Map<String, PersonItemRole>,
        suggestions: List<SelectedSuggestion> = emptyList(),
    ) {
        val now = time.now()
        val existing = items.getItem(item.id)?.item
        val owners = if (existing != null) items.getOwners(item.id).map { it.person } else emptyList()
        if (existing != null && guard?.allow(owners, existing.name, GuardedAction.EDIT) == false) return
        val saved = item.copy(name = item.name.trim(), createdAt = existing?.createdAt ?: now, updatedAt = now)
        items.save(saved, people)
        syncWarrantyReminder(saved, people.keys)
        val offset = settings.current().defaultNotifyOffsetMinutes
        suggestions.forEach { createSuggestedReminder(saved, people.keys, it, offset) }
        if (existing != null) guard?.done(owners, saved.name, GuardedAction.EDIT)
    }

    private suspend fun syncWarrantyReminder(item: Item, personIds: Set<String>) {
        val current = reminders.getRemindersForItem(item.id).firstOrNull { it.reminder.type == ReminderType.WARRANTY }
        val endDate = item.warrantyEndDate
        if (endDate == null) {
            current?.let { reminders.delete(it.reminder.id) }
            return
        }
        val now = time.now()
        val base = current?.reminder ?: Reminder(
            title = "",
            type = ReminderType.WARRANTY,
            dueDate = endDate,
            notifyOffsetMinutes = WARRANTY_NOTICE_MINUTES,
            createdAt = now,
            updatedAt = now,
        )
        val reminder = base.copy(
            title = trf("Garanzia %1\$s", item.name.trim()),
            dueDate = endDate,
            // Se la data di scadenza cambia, la garanzia torna attiva.
            status = if (base.dueDate != endDate) ReminderStatus.ACTIVE else base.status,
        )
        saveReminder(ReminderDraft(reminder, recurrence = null, personIds = personIds, itemIds = setOf(item.id)), guarded = false)
    }

    private suspend fun createSuggestedReminder(
        item: Item,
        personIds: Set<String>,
        selected: SelectedSuggestion,
        notifyOffsetMinutes: Int,
    ) {
        val now = time.now()
        val suggestion = selected.suggestion
        val reminder = Reminder(
            title = "${suggestion.title} ${item.name.trim()}",
            type = suggestion.type,
            dueDate = selected.firstDueDate,
            dueOdometerKm = suggestion.odometerIntervalKm?.let { interval -> (item.odometerKm ?: 0) + interval },
            odometerIntervalKm = suggestion.odometerIntervalKm,
            notifyOffsetMinutes = notifyOffsetMinutes,
            createdAt = now,
            updatedAt = now,
        )
        val rule = suggestion.frequency?.let {
            RecurrenceRule(frequency = it, interval = suggestion.interval, startDate = selected.firstDueDate)
        }
        saveReminder(ReminderDraft(reminder, rule, personIds, setOf(item.id)))
    }
}

class DeleteItemUseCase(
    private val items: ItemRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(itemId: String) {
        items.delete(itemId)
        scheduler.refresh()
    }
}

/** Pianificazione opzionale della prossima manutenzione, per data e/o chilometri. */
data class NextMaintenance(val afterMonths: Int?, val afterKm: Int?)

/**
 * Registra un intervento di manutenzione (storico), aggiorna il chilometraggio del veicolo
 * e, se richiesto, crea il promemoria per l'intervento successivo.
 */
class AddMaintenanceRecordUseCase(
    private val maintenance: MaintenanceRepository,
    private val items: ItemRepository,
    private val settings: SettingsRepository,
    private val saveReminder: SaveReminderUseCase,
    private val time: TimeSource,
) {
    suspend operator fun invoke(record: MaintenanceRecord, next: NextMaintenance?) {
        val now = time.now()
        maintenance.save(record.copy(title = record.title.trim(), createdAt = now))

        val item = items.getItem(record.itemId)?.item ?: return
        val recordKm = record.odometerKm
        if (recordKm != null && recordKm > (item.odometerKm ?: 0)) {
            items.update(item.copy(odometerKm = recordKm, updatedAt = now))
        }

        if (next == null || (next.afterMonths == null && next.afterKm == null)) return
        val dueDate = record.date.plusMonths((next.afterMonths ?: 12).toLong())
        val reminder = Reminder(
            title = "${record.title.trim()} ${item.name}",
            type = ReminderType.MAINTENANCE,
            dueDate = dueDate,
            dueOdometerKm = next.afterKm?.let { (recordKm ?: item.odometerKm ?: 0) + it },
            odometerIntervalKm = next.afterKm,
            notifyOffsetMinutes = settings.current().defaultNotifyOffsetMinutes,
            createdAt = now,
            updatedAt = now,
        )
        val owners = items.getOwners(item.id).mapTo(mutableSetOf()) { it.person.id }
        saveReminder(ReminderDraft(reminder, recurrence = null, personIds = owners, itemIds = setOf(item.id)))
    }
}
