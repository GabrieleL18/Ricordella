package com.ricordella.app.feature.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.StarBurst
import com.ricordella.app.core.ui.rememberReducedMotion
import com.ricordella.app.core.ui.tone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.navigation.AppNavigator
import com.ricordella.app.core.ui.AttachmentsSection
import com.ricordella.app.core.ui.ConfirmDialog
import com.ricordella.app.core.ui.DatePickerDialogFor
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.IconBadge
import com.ricordella.app.core.ui.InfoRow
import com.ricordella.app.core.ui.LinkChip
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.PriorityIndicator
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.StatusBadge
import com.ricordella.app.core.ui.TimePickerDialogFor
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.describe
import com.ricordella.app.core.ui.icon
import com.ricordella.app.core.ui.label
import com.ricordella.app.core.ui.notifyOffsetLabel
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.date.RelativeDateDescriber
import com.ricordella.app.domain.date.ReminderTimeline
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.ReminderTimeStatus
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.model.SnoozeOption
import com.ricordella.app.domain.model.displayName
import com.ricordella.app.domain.model.odometerStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun ReminderDetailScreen(navigator: AppNavigator) {
    val viewModel = appViewModel { c, handle ->
        ReminderDetailViewModel(
            handle, c.reminderRepository, c.attachmentRepository, c.completeReminder,
            c.reopenReminder, c.snoozeReminder, c.deleteReminder, c.time,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) { if (state.deleted) navigator.back() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    val entry = state.entry
    DetailScaffold(
        title = entry?.reminder?.type?.label ?: "Promemoria",
        onBack = navigator::back,
        snackbarHostState = snackbar,
        actions = {
            if (entry != null) {
                IconButton(onClick = { navigator.editReminder(entry.reminder.id) }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Modifica")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Altre azioni") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (entry.reminder.isArchived) "Ripristina dall'archivio" else "Archivia") },
                            onClick = { menuOpen = false; viewModel.onToggleArchived() },
                        )
                        DropdownMenuItem(
                            text = { Text("Elimina") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; showDeleteConfirm = true },
                        )
                    }
                }
            }
        },
        bottomBar = { if (entry != null) ActionBar(entry, viewModel) },
    ) { padding ->
        if (entry == null) {
            if (!state.isLoading) {
                Text("Questo promemoria non esiste più.", modifier = Modifier.padding(padding).padding(RicordellaDimensions.screenPadding))
            }
            return@DetailScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .contentWidth()
                .padding(RicordellaDimensions.screenPadding),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
        ) {
            Header(entry, state.now)
            Details(entry, state.now)
            Links(entry, navigator)
            AttachmentsSection(
                title = "Allegati",
                attachments = state.attachments,
                onAdd = viewModel::onAddAttachment,
                onRemove = viewModel::onRemoveAttachment,
                onOpenFailed = { viewModel.showMessage("Nessuna app disponibile per aprire il file.") },
            )
            if (state.completions.isNotEmpty()) {
                SectionHeader("Storico")
                val dateFormat = LocalAppSettings.current.dateFormat
                state.completions.forEach { completion ->
                    val completedOn = LocalDateTime.ofInstant(completion.completedAt, ZoneId.systemDefault())
                    Text(
                        "Occorrenza del ${DateTexts.date(completion.occurrenceDate, dateFormat)} — completata il " +
                            DateTexts.dateWithTime(completedOn.toLocalDate(), completedOn.toLocalTime(), dateFormat),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Eliminare il promemoria?",
            message = "Il promemoria e il suo storico verranno eliminati definitivamente. Le persone e le cose collegate non vengono toccate.",
            confirmLabel = "Elimina",
            destructive = true,
            onConfirm = viewModel::onDelete,
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun Header(entry: ReminderWithLinks, now: LocalDateTime) {
    val reminder = entry.reminder
    val status = ReminderTimeline.timeStatus(reminder, now)
    val tone = reminder.type.tone
    val reduced = rememberReducedMotion()
    val pop = remember { Animatable(if (reduced) 1f else 0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tone.container, MaterialTheme.shapes.extraLarge)
            .padding(RicordellaDimensions.spaceL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceL),
    ) {
        Box(
            Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    rotationZ = (1f - pop.value) * -20f
                }
                .background(tone.solid, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(reminder.type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(34.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(reminder.title, style = MaterialTheme.typography.headlineSmall, color = tone.content)
            Text(reminder.type.label, style = MaterialTheme.typography.titleSmall, color = tone.content)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (status) {
            ReminderTimeStatus.OVERDUE ->
                StatusBadge("Scaduto", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            ReminderTimeStatus.TODAY ->
                StatusBadge("Oggi", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            ReminderTimeStatus.COMPLETED ->
                StatusBadge("Completato", MaterialTheme.ricordellaColors.successContainer, MaterialTheme.ricordellaColors.onSuccessContainer, icon = Icons.Rounded.Check)
            ReminderTimeStatus.CANCELLED ->
                StatusBadge("Annullato", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            ReminderTimeStatus.UPCOMING ->
                StatusBadge("In arrivo", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        }
        if (reminder.isArchived) {
            StatusBadge("Archiviato", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PriorityIndicator(reminder.priority)
    }
}

@Composable
private fun Details(entry: ReminderWithLinks, now: LocalDateTime) {
    val reminder = entry.reminder
    val settings = LocalAppSettings.current
    val today = now.toLocalDate()
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(RicordellaDimensions.spaceL)) {
            InfoRow("Data", DateTexts.date(reminder.dueDate, settings.dateFormat))
            InfoRow("Ora", reminder.dueTime?.let(DateTexts::time) ?: "Tutto il giorno")
            InfoRow(
                "Quando",
                if (reminder.type.isDeadlineLike) RelativeDateDescriber.describeDeadline(reminder.dueDate, today)
                else RelativeDateDescriber.describe(reminder.dueDate, today).replaceFirstChar { it.uppercase() },
            )
            entry.recurrenceRule?.let { InfoRow("Ricorrenza", it.describe()) }
            reminder.category?.let { InfoRow("Categoria", it) }
            reminder.dueOdometerKm?.let { dueKm ->
                val current = entry.odometerItem?.odometerKm
                InfoRow("Scadenza km", DateTexts.kilometers(dueKm) + (reminder.odometerIntervalKm?.let { " (ogni ${DateTexts.kilometers(it)})" } ?: ""))
                current?.let { InfoRow("Km attuali", DateTexts.kilometers(it) + (entry.odometerStatus?.let { s -> " · ${s.label}" } ?: "")) }
            }
            InfoRow(
                "Notifica",
                if (reminder.notificationsEnabled) notifyOffsetLabel(reminder.notifyOffsetMinutes) else "Disattivata",
            )
            reminder.snoozedUntil?.takeIf { reminder.status == ReminderStatus.ACTIVE }?.let {
                val local = LocalDateTime.ofInstant(it, ZoneId.systemDefault())
                if (local.isAfter(now)) {
                    InfoRow("Rimandato a", DateTexts.dateWithTime(local.toLocalDate(), local.toLocalTime(), settings.dateFormat))
                }
            }
        }
    }
    reminder.description?.let {
        SectionHeader("Descrizione")
        Text(it, style = MaterialTheme.typography.bodyLarge)
    }
    reminder.notes?.let {
        SectionHeader("Note")
        Text(it, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Links(entry: ReminderWithLinks, navigator: AppNavigator) {
    if (entry.people.isNotEmpty()) {
        SectionHeader("Persone")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entry.people.forEach { person -> LinkChip(person.displayName, onClick = { navigator.openPerson(person.id) }) }
        }
    }
    if (entry.items.isNotEmpty()) {
        SectionHeader("Cose")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entry.items.forEach { item -> LinkChip(item.name, onClick = { navigator.openItem(item.id) }) }
        }
    }
}

@Composable
private fun ActionBar(entry: ReminderWithLinks, viewModel: ReminderDetailViewModel) {
    if (!entry.reminder.type.isCompletable) return
    val isActive = entry.reminder.status == ReminderStatus.ACTIVE
    var snoozeMenu by remember { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    var burst by remember { mutableIntStateOf(0) }
    val extra = MaterialTheme.ricordellaColors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = RicordellaDimensions.screenPadding, vertical = RicordellaDimensions.spaceM),
            horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isActive) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { snoozeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Snooze, contentDescription = null)
                        Text("Rimanda", modifier = Modifier.padding(start = 8.dp))
                    }
                    DropdownMenu(expanded = snoozeMenu, onDismissRequest = { snoozeMenu = false }) {
                        DropdownMenuItem(text = { Text("Tra 10 minuti") }, onClick = { snoozeMenu = false; viewModel.onSnooze(SnoozeOption.TEN_MINUTES) })
                        DropdownMenuItem(text = { Text("Tra 1 ora") }, onClick = { snoozeMenu = false; viewModel.onSnooze(SnoozeOption.ONE_HOUR) })
                        DropdownMenuItem(text = { Text("Domani mattina") }, onClick = { snoozeMenu = false; viewModel.onSnooze(SnoozeOption.TomorrowMorning) })
                        DropdownMenuItem(text = { Text("Scegli data e ora…") }, onClick = { snoozeMenu = false; pickDate = true })
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    StarBurst(
                        trigger = burst,
                        colors = listOf(extra.bolt, extra.coral.solid, extra.mint.solid),
                        modifier = Modifier.requiredSize(160.dp),
                    )
                    PushButton(
                        text = "Completa",
                        icon = Icons.Rounded.Check,
                        onClick = {
                            burst++
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            scope.launch {
                                delay(380)
                                viewModel.onComplete()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                OutlinedButton(onClick = viewModel::onReopen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null)
                    Text("Segna come da fare", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (pickDate) {
        DatePickerDialogFor(
            initial = LocalDate.now(),
            onDismiss = { pickDate = false },
            onConfirm = { pickedDate = it; pickDate = false },
        )
    }
    pickedDate?.let { date ->
        TimePickerDialogFor(
            initial = LocalTime.now().plusHours(1).withMinute(0),
            onDismiss = { pickedDate = null },
            onConfirm = { time ->
                pickedDate = null
                viewModel.onSnooze(SnoozeOption.At(date.atTime(time)))
            },
        )
    }
}
