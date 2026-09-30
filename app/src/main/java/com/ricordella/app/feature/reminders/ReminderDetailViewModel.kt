package com.ricordella.app.feature.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ricordella.app.core.navigation.ReminderDetailRoute
import com.ricordella.app.core.ui.PickedFile
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.Attachment
import com.ricordella.app.domain.model.AttachmentOwnerType
import com.ricordella.app.domain.model.ReminderCompletion
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.model.SnoozeOption
import com.ricordella.app.domain.repository.AttachmentRepository
import com.ricordella.app.domain.repository.ReminderRepository
import com.ricordella.app.domain.usecase.CompleteReminderUseCase
import com.ricordella.app.domain.usecase.DeleteReminderUseCase
import com.ricordella.app.domain.usecase.ReopenReminderUseCase
import com.ricordella.app.domain.usecase.SnoozeReminderUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class ReminderDetailUiState(
    val isLoading: Boolean = true,
    val entry: ReminderWithLinks? = null,
    val completions: List<ReminderCompletion> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val now: LocalDateTime = LocalDateTime.now(),
    val deleted: Boolean = false,
    val message: String? = null,
)

class ReminderDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val reminders: ReminderRepository,
    private val attachments: AttachmentRepository,
    private val completeReminder: CompleteReminderUseCase,
    private val reopenReminder: ReopenReminderUseCase,
    private val snoozeReminder: SnoozeReminderUseCase,
    private val deleteReminder: DeleteReminderUseCase,
    private val time: TimeSource,
) : ViewModel() {

    private val reminderId = savedStateHandle.toRoute<ReminderDetailRoute>().id
    private val events = MutableStateFlow(ReminderDetailUiState())

    val uiState: StateFlow<ReminderDetailUiState> = combine(
        reminders.observeReminder(reminderId),
        reminders.observeCompletions(reminderId),
        attachments.observeFor(AttachmentOwnerType.REMINDER, reminderId),
        time.minuteTicks(),
        events,
    ) { entry, completions, files, now, local ->
        local.copy(isLoading = false, entry = entry, completions = completions, attachments = files, now = now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderDetailUiState())

    fun onComplete() = launchAction("Completato") { completeReminder(reminderId) }

    fun onReopen() = launchAction("Riaperto") { reopenReminder(reminderId) }

    fun onSnooze(option: SnoozeOption) = launchAction("Notifica rimandata") { snoozeReminder(reminderId, option) }

    fun onToggleArchived() {
        val reminder = uiState.value.entry?.reminder ?: return
        launchAction(if (reminder.isArchived) "Ripristinato" else "Archiviato") {
            reminders.update(reminder.copy(isArchived = !reminder.isArchived, updatedAt = time.now()))
        }
    }

    fun onDelete() {
        viewModelScope.launch {
            deleteReminder(reminderId)
            events.update { it.copy(deleted = true) }
        }
    }

    fun onAddAttachment(file: PickedFile) = launchAction(null) {
        attachments.add(
            Attachment(
                ownerType = AttachmentOwnerType.REMINDER,
                ownerId = reminderId,
                uri = file.uri,
                displayName = file.displayName,
                mimeType = file.mimeType,
                createdAt = time.now(),
            ),
        )
    }

    fun onRemoveAttachment(attachment: Attachment) = launchAction(null) { attachments.delete(attachment.id) }

    fun showMessage(message: String) = events.update { it.copy(message = message) }

    fun onMessageShown() = events.update { it.copy(message = null) }

    private fun launchAction(successMessage: String?, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                successMessage?.let(::showMessage)
            } catch (_: Exception) {
                showMessage("Operazione non riuscita. Riprova.")
            }
        }
    }
}
