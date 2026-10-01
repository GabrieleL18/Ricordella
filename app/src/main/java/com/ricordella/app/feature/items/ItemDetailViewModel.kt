package com.ricordella.app.feature.items

import com.ricordella.app.core.i18n.tr

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ricordella.app.core.navigation.ItemDetailRoute
import com.ricordella.app.core.ui.PickedFile
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.Attachment
import com.ricordella.app.domain.model.AttachmentOwnerType
import com.ricordella.app.domain.model.ItemWithCategory
import com.ricordella.app.domain.model.MaintenanceRecord
import com.ricordella.app.domain.model.ExpenseKind
import com.ricordella.app.domain.model.PersonWithRole
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.repository.AttachmentRepository
import com.ricordella.app.domain.repository.ItemRepository
import com.ricordella.app.domain.repository.MaintenanceRepository
import com.ricordella.app.domain.repository.PersonRepository
import com.ricordella.app.domain.repository.ReminderRepository
import com.ricordella.app.domain.usecase.AddMaintenanceRecordUseCase
import com.ricordella.app.domain.usecase.CompleteReminderUseCase
import com.ricordella.app.domain.usecase.DeleteItemUseCase
import com.ricordella.app.domain.usecase.NextMaintenance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

data class ItemDetailUiState(
    val isLoading: Boolean = true,
    val entry: ItemWithCategory? = null,
    val owners: List<PersonWithRole> = emptyList(),
    /** Scadenze attive (garanzia, assicurazione, revisione, pagamenti...). */
    val deadlines: List<ReminderWithLinks> = emptyList(),
    /** Altri promemoria attivi collegati. */
    val reminders: List<ReminderWithLinks> = emptyList(),
    val maintenance: List<MaintenanceRecord> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val now: LocalDateTime = LocalDateTime.now(),
    val deleted: Boolean = false,
    val message: String? = null,
)

class ItemDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val items: ItemRepository,
    people: PersonRepository,
    reminders: ReminderRepository,
    private val maintenance: MaintenanceRepository,
    private val attachments: AttachmentRepository,
    private val addMaintenanceRecord: AddMaintenanceRecordUseCase,
    private val completeReminder: CompleteReminderUseCase,
    private val deleteItem: DeleteItemUseCase,
    private val time: TimeSource,
) : ViewModel() {

    private val itemId = savedStateHandle.toRoute<ItemDetailRoute>().id
    private val local = MutableStateFlow(ItemDetailUiState())

    private val linked = combine(
        people.observePeopleForItem(itemId),
        reminders.observeForItem(itemId),
        maintenance.observeForItem(itemId),
        attachments.observeFor(AttachmentOwnerType.ITEM, itemId),
    ) { owners, reminderList, records, files -> Linked(owners, reminderList, records, files) }

    val uiState: StateFlow<ItemDetailUiState> = combine(items.observeItem(itemId), linked, time.minuteTicks(), local) { entry, links, now, state ->
        val active = links.reminders.filter { it.reminder.status == ReminderStatus.ACTIVE }
        val (deadlines, others) = active.partition { it.reminder.type.isDeadlineLike }
        state.copy(
            isLoading = false,
            entry = entry,
            owners = links.owners,
            deadlines = deadlines,
            reminders = others,
            maintenance = links.records,
            attachments = links.files,
            now = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailUiState())

    fun onUpdateOdometer(km: Int) {
        val item = uiState.value.entry?.item ?: return
        launchAction(tr("Chilometraggio aggiornato")) { items.update(item.copy(odometerKm = km, updatedAt = time.now())) }
    }

    fun onAddMaintenance(
        kind: ExpenseKind,
        title: String,
        date: LocalDate,
        odometerKm: Int?,
        costCents: Long?,
        liters: Double?,
        description: String?,
        next: NextMaintenance?,
    ) = launchAction(if (kind == ExpenseKind.SERVICE) tr("Intervento registrato") else tr("Spesa registrata")) {
        addMaintenanceRecord(
            MaintenanceRecord(
                itemId = itemId,
                title = title,
                date = date,
                odometerKm = odometerKm,
                costCents = costCents,
                description = description,
                createdAt = time.now(),
                kind = kind,
                liters = liters,
            ),
            next,
        )
    }

    fun onUpdateMaintenance(record: MaintenanceRecord) = launchAction(tr("Voce aggiornata")) { maintenance.save(record) }

    fun onDeleteMaintenance(record: MaintenanceRecord) = launchAction(tr("Intervento eliminato")) { maintenance.delete(record.id) }

    fun onCompleteReminder(id: String) = launchAction(null) { completeReminder(id) }

    fun onToggleArchived() {
        val item = uiState.value.entry?.item ?: return
        launchAction(if (item.isArchived) tr("Ripristinata") else tr("Archiviata")) {
            items.update(item.copy(isArchived = !item.isArchived, updatedAt = time.now()))
        }
    }

    fun onDelete() {
        viewModelScope.launch {
            deleteItem(itemId)
            local.update { it.copy(deleted = true) }
        }
    }

    fun onAddAttachment(file: PickedFile) = launchAction(null) {
        attachments.add(
            Attachment(
                ownerType = AttachmentOwnerType.ITEM,
                ownerId = itemId,
                uri = file.uri,
                displayName = file.displayName,
                mimeType = file.mimeType,
                createdAt = time.now(),
            ),
        )
    }

    fun onRemoveAttachment(attachment: Attachment) = launchAction(null) { attachments.delete(attachment.id) }

    fun showMessage(message: String) = local.update { it.copy(message = message) }

    fun onMessageShown() = local.update { it.copy(message = null) }

    private fun launchAction(successMessage: String?, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                successMessage?.let(::showMessage)
            } catch (_: Exception) {
                showMessage(tr("Operazione non riuscita. Riprova."))
            }
        }
    }

    private data class Linked(
        val owners: List<PersonWithRole>,
        val reminders: List<ReminderWithLinks>,
        val records: List<MaintenanceRecord>,
        val files: List<Attachment>,
    )
}
