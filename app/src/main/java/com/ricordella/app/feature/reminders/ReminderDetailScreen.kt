package com.ricordella.app.feature.reminders

import com.ricordella.app.core.ui.HappyWizard
import com.ricordella.app.core.ui.wizardProp
import com.ricordella.app.core.ui.CompletionHistory
import com.ricordella.app.core.ui.birthdayAgeLabel
import com.ricordella.app.domain.model.ageOn
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
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
        title = entry?.reminder?.type?.label ?: tr("Promemoria"),
        onBack = navigator::back,
        snackbarHostState = snackbar,
        actions = {
            if (entry != null) {
                IconButton(onClick = { navigator.editReminder(entry.reminder.id) }) {
                    Icon(Icons.Rounded.Edit, contentDescription = tr("Modifica"))
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = tr("Elimina"), tint = MaterialTheme.colorScheme.error)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = tr("Altre azioni")) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (entry.reminder.isArchived) tr("Ripristina dall'archivio") else tr("Archivia")) },
                            onClick = { menuOpen = false; viewModel.onToggleArchived() },
                        )
                    }
                }
            }
        },
        bottomBar = { if (entry != null) ActionBar(entry, viewModel) },
    ) { padding ->
        if (entry == null) {
            if (!state.isLoading) {
                Text(tr("Questo promemoria non esiste più."), modifier = Modifier.padding(padding).padding(RicordellaDimensions.screenPadding))
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
                title = tr("Allegati"),
                attachments = state.attachments,
                onAdd = viewModel::onAddAttachment,
                onRemove = viewModel::onRemoveAttachment,
                onOpen = { navigator.openViewer(it.uri, it.mimeType, it.displayName) },
            )
            if (state.completions.isNotEmpty()) CompletionHistory(state.completions)
            com.ricordella.app.core.ui.EditDeleteRow(onEdit = { navigator.editReminder(entry.reminder.id) }, onDelete = { showDeleteConfirm = true })
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = tr("Eliminare il promemoria?"),
            message = tr("Il promemoria e il suo storico vanno nel Cestino per 7 giorni, poi vengono eliminati. Le persone e le cose collegate non vengono toccate."),
            confirmLabel = tr("Elimina"),
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
            Text(
                listOfNotNull(reminder.type.label, reminder.ageOn(reminder.dueDate)?.let(::birthdayAgeLabel)).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
                color = tone.content,
            )
        }
        HappyWizard(size = 88.dp, holding = reminder.type.wizardProp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (status) {
            ReminderTimeStatus.OVERDUE ->
                StatusBadge(tr("Scaduto"), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            ReminderTimeStatus.TODAY ->
                StatusBadge(tr("Oggi"), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            ReminderTimeStatus.COMPLETED ->
                StatusBadge(tr("Completato"), MaterialTheme.ricordellaColors.successContainer, MaterialTheme.ricordellaColors.onSuccessContainer, icon = Icons.Rounded.Check)
            ReminderTimeStatus.CANCELLED ->
                StatusBadge(tr("Annullato"), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            ReminderTimeStatus.UPCOMING ->
                StatusBadge(tr("In arrivo"), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        }
        if (reminder.isArchived) {
            StatusBadge(tr("Archiviato"), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
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
            InfoRow(if (reminder.endDate != null) tr("Dal") else tr("Data"), DateTexts.date(reminder.dueDate, settings.dateFormat))
            reminder.endDate?.let { InfoRow(tr("Al"), DateTexts.date(it, settings.dateFormat)) }
            InfoRow(tr("Ora"), reminder.dueTime?.let(DateTexts::time) ?: tr("Tutto il giorno"))
            InfoRow(
                tr("Quando"),
                if (reminder.type.isDeadlineLike) RelativeDateDescriber.describeDeadline(reminder.dueDate, today)
                else RelativeDateDescriber.describe(reminder.dueDate, today).replaceFirstChar { it.uppercase() },
            )
            entry.recurrenceRule?.let { InfoRow(tr("Ricorrenza"), it.describe()) }
            reminder.category?.let { InfoRow(tr("Categoria"), it) }
            reminder.dueOdometerKm?.let { dueKm ->
                val current = entry.odometerItem?.odometerKm
                InfoRow(tr("Scadenza km"), DateTexts.kilometers(dueKm) + (reminder.odometerIntervalKm?.let { trf(" (ogni %1\$s)", DateTexts.kilometers(it)) } ?: ""))
                current?.let { InfoRow(tr("Km attuali"), DateTexts.kilometers(it) + (entry.odometerStatus?.let { s -> " · ${s.label}" } ?: "")) }
            }
            InfoRow(
                tr("Notifica"),
                if (reminder.notificationsEnabled) notifyOffsetLabel(reminder.notifyOffsetMinutes) else tr("Disattivata"),
            )
            reminder.pausedUntil?.takeIf { it.isAfter(today) }?.let { InfoRow(tr("In pausa fino al"), DateTexts.date(it, settings.dateFormat)) }
            reminder.snoozedUntil?.takeIf { reminder.status == ReminderStatus.ACTIVE }?.let {
                val local = LocalDateTime.ofInstant(it, ZoneId.systemDefault())
                if (local.isAfter(now)) {
                    InfoRow(tr("Rimandato a"), DateTexts.dateWithTime(local.toLocalDate(), local.toLocalTime(), settings.dateFormat))
                }
            }
        }
    }
    reminder.trip?.takeIf { !it.isEmpty }?.let { TripSection(it) }
    reminder.description?.let {
        SectionHeader(tr("Descrizione"))
        Text(it, style = MaterialTheme.typography.bodyLarge)
    }
    reminder.notes?.let {
        SectionHeader(tr("Note"))
        Text(it, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Links(entry: ReminderWithLinks, navigator: AppNavigator) {
    if (entry.people.isNotEmpty()) {
        SectionHeader(tr("Persone"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entry.people.forEach { person -> LinkChip(person.displayName, onClick = { navigator.openPerson(person.id) }) }
        }
    }
    if (entry.items.isNotEmpty()) {
        SectionHeader(tr("Cose"))
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
                        Text(tr("Rimanda"), modifier = Modifier.padding(start = 8.dp))
                    }
                    DropdownMenu(expanded = snoozeMenu, onDismissRequest = { snoozeMenu = false }) {
                        DropdownMenuItem(text = { Text(tr("Tra 10 minuti")) }, onClick = { snoozeMenu = false; viewModel.onSnooze(SnoozeOption.TEN_MINUTES) })
                        DropdownMenuItem(text = { Text(tr("Tra 1 ora")) }, onClick = { snoozeMenu = false; viewModel.onSnooze(SnoozeOption.ONE_HOUR) })
                        DropdownMenuItem(text = { Text(tr("Domani mattina")) }, onClick = { snoozeMenu = false; viewModel.onSnooze(SnoozeOption.TomorrowMorning) })
                        DropdownMenuItem(text = { Text(tr("Scegli data e ora…")) }, onClick = { snoozeMenu = false; pickDate = true })
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    StarBurst(
                        trigger = burst,
                        colors = listOf(extra.bolt, extra.coral.solid, extra.mint.solid),
                        modifier = Modifier.requiredSize(160.dp),
                    )
                    PushButton(
                        text = tr("Completa"),
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
                    Text(tr("Segna come da fare"), modifier = Modifier.padding(start = 8.dp))
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
