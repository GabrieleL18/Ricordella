package com.ricordella.app.feature.items

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import com.ricordella.app.domain.model.ExpenseProfiles
import com.ricordella.app.domain.model.ExpenseKind
import com.ricordella.app.domain.model.ExpenseStats
import com.ricordella.app.core.i18n.Lang
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.ui.graphics.vector.ImageVector
import com.ricordella.app.core.ui.HappyWizard
import com.ricordella.app.core.ui.wizardSceneFor
import com.ricordella.app.core.ui.HistoryItem
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.navigation.AppNavigator
import com.ricordella.app.core.ui.AttachmentsSection
import com.ricordella.app.core.ui.ConfirmDialog
import com.ricordella.app.core.ui.DateField
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.IconBadge
import com.ricordella.app.core.ui.InfoRow
import com.ricordella.app.core.ui.FileTile
import com.ricordella.app.core.ui.LinkChip
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.ReminderCard
import com.ricordella.app.core.ui.ReminderDateMode
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.StatusBadge
import com.ricordella.app.core.ui.UriImage
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.icon
import com.ricordella.app.core.ui.tone
import com.ricordella.app.core.ui.label
import com.ricordella.app.core.ui.openFile
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.date.RelativeDateDescriber
import com.ricordella.app.domain.model.ItemWithCategory
import com.ricordella.app.domain.model.MaintenanceRecord
import com.ricordella.app.domain.model.WarrantyStatus
import com.ricordella.app.domain.model.displayName
import com.ricordella.app.domain.model.warrantyStatus
import com.ricordella.app.domain.usecase.NextMaintenance
import java.time.LocalDate

/** Su schermi almeno così larghi la scheda si divide in due colonne. */
private val TWO_PANE_WIDTH = 840.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemDetailScreen(navigator: AppNavigator) {
    val viewModel = appViewModel { c, handle ->
        ItemDetailViewModel(
            handle, c.itemRepository, c.personRepository, c.reminderRepository, c.maintenanceRepository,
            c.attachmentRepository, c.addMaintenanceRecord, c.completeReminder, c.deleteItem, c.time,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showOdometer by rememberSaveable { mutableStateOf(false) }
    var addingKind by rememberSaveable { mutableStateOf<ExpenseKind?>(null) }
    var editingRecord by remember { mutableStateOf<com.ricordella.app.domain.model.MaintenanceRecord?>(null) }

    LaunchedEffect(state.deleted) { if (state.deleted) navigator.back() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.onMessageShown() }
    }

    val entry = state.entry
    DetailScaffold(
        title = entry?.item?.name ?: tr("Cosa"),
        onBack = navigator::back,
        snackbarHostState = snackbar,
        actions = {
            if (entry != null) {
                IconButton(onClick = { navigator.editItem(entry.item.id) }) { Icon(Icons.Rounded.Edit, contentDescription = tr("Modifica")) }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = tr("Altre azioni")) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (entry.item.isArchived) tr("Ripristina dall'archivio") else tr("Archivia")) },
                            onClick = { menuOpen = false; viewModel.onToggleArchived() },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Elimina")) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (entry == null) return@DetailScaffold
        val profile = remember(entry.category?.kind, entry.group) { ExpenseProfiles.of(entry.category?.kind, entry.group) }
        // Chi è la cosa: foto, dati, garanzia.
        val identity: @Composable ColumnScope.() -> Unit = {
            ItemHeader(entry)
            InfoSection(entry, onUpdateOdometer = { showOdometer = true })
            WarrantySection(entry, state.now.toLocalDate(), onOpenDocument = { navigator.openViewer(it, null, tr("Documento di garanzia")) })
        }
        // Cosa succede: scadenze, manutenzione e spese, promemoria.
        val activity: @Composable ColumnScope.() -> Unit = {
            if (state.deadlines.isNotEmpty()) {
                SectionHeader(tr("Scadenze"))
                state.deadlines.forEach { reminder ->
                    ReminderCard(
                        entry = reminder,
                        now = state.now,
                        onClick = { navigator.openReminder(reminder.reminder.id) },
                        onToggleComplete = { viewModel.onCompleteReminder(reminder.reminder.id) },
                        dateMode = ReminderDateMode.RELATIVE,
                    )
                }
            }

            ExpensesSection(
                profile = profile,
                records = state.maintenance,
                year = state.now.year,
                isVehicle = entry.isVehicle,
                onAdd = { addingKind = it },
                onDelete = viewModel::onDeleteMaintenance,
                onEdit = { editingRecord = it },
            )

            SectionHeader(tr("Promemoria")) {
                TextButton(onClick = { navigator.newReminder(itemId = entry.item.id, personId = state.owners.firstOrNull()?.person?.id) }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(tr("Promemoria"), modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (state.reminders.isEmpty()) {
                Text(tr("Nessun altro promemoria."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.reminders.forEach { reminder ->
                ReminderCard(
                    entry = reminder,
                    now = state.now,
                    onClick = { navigator.openReminder(reminder.reminder.id) },
                    onToggleComplete = { viewModel.onCompleteReminder(reminder.reminder.id) },
                )
            }
        }
        // Il resto: persone, documenti, note.
        val extras: @Composable ColumnScope.() -> Unit = {
            if (state.owners.isNotEmpty()) {
                SectionHeader(tr("Persone"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.owners.forEach { owner ->
                        LinkChip("${owner.person.displayName} · ${owner.role.label}", onClick = { navigator.openPerson(owner.person.id) })
                    }
                }
            }

            AttachmentsSection(
                title = tr("Documenti"),
                attachments = state.attachments,
                onAdd = viewModel::onAddAttachment,
                onRemove = viewModel::onRemoveAttachment,
                onOpen = { navigator.openViewer(it.uri, it.mimeType, it.displayName) },
            )
            entry.item.notes?.let {
                SectionHeader(tr("Note"))
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            com.ricordella.app.core.ui.EditDeleteRow(onEdit = { navigator.editItem(entry.item.id) }, onDelete = { confirmDelete = true })
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val column = Modifier.verticalScroll(rememberScrollState())
            val spacing = Arrangement.spacedBy(RicordellaDimensions.spaceS)
            if (maxWidth >= TWO_PANE_WIDTH) {
                // Tablet e schermi larghi: a sinistra la cosa, a destra quello che le succede.
                Row(
                    Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 1280.dp).padding(horizontal = RicordellaDimensions.spaceL),
                    horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceXl),
                ) {
                    Column(Modifier.weight(1f).then(column).padding(vertical = RicordellaDimensions.screenPadding), verticalArrangement = spacing) {
                        identity()
                        extras()
                    }
                    Column(Modifier.weight(1.2f).then(Modifier.verticalScroll(rememberScrollState())).padding(vertical = RicordellaDimensions.screenPadding), verticalArrangement = spacing) {
                        activity()
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().then(column).contentWidth().padding(RicordellaDimensions.screenPadding), verticalArrangement = spacing) {
                    identity()
                    activity()
                    extras()
                }
            }
        }
    }

    if (showOdometer && entry != null) {
        OdometerDialog(
            current = entry.item.odometerKm,
            onDismiss = { showOdometer = false },
            onConfirm = { viewModel.onUpdateOdometer(it); showOdometer = false },
        )
    }
    editingRecord?.let { record ->
        val current = entry ?: return@let
        ExpenseDialog(
            profile = ExpenseProfiles.of(current.category?.kind, current.group),
            initialKind = record.kind,
            isVehicle = current.isVehicle,
            currentKm = current.item.odometerKm,
            initial = record,
            onDismiss = { editingRecord = null },
            onConfirm = { expenseKind, title, date, km, cost, liters, description, _ ->
                viewModel.onUpdateMaintenance(record.copy(kind = expenseKind, title = title, date = date, odometerKm = km, costCents = cost, liters = liters, description = description))
                editingRecord = null
            },
        )
    }
    addingKind?.let { kind ->
        val current = entry ?: return@let
        ExpenseDialog(
            profile = ExpenseProfiles.of(current.category?.kind, current.group),
            initialKind = kind,
            isVehicle = current.isVehicle,
            currentKm = current.item.odometerKm,
            onDismiss = { addingKind = null },
            onConfirm = { expenseKind, title, date, km, cost, liters, description, next ->
                viewModel.onAddMaintenance(expenseKind, title, date, km, cost, liters, description, next)
                addingKind = null
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = tr("Eliminare questa cosa?"),
            message = tr("Verranno eliminati anche lo storico delle manutenzioni e il promemoria della garanzia. ") +
                tr("Gli altri promemoria restano. Se vuoi solo nasconderla, usa \"Archivia\". ") +
                tr("Lo ritrovi nel Cestino (Impostazioni) per 7 giorni."),
            confirmLabel = tr("Elimina"),
            destructive = true,
            onConfirm = viewModel::onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ItemHeader(entry: ItemWithCategory) {
    val item = entry.item
    val photo = item.photoUri
    if (photo != null) {
        UriImage(
            photo,
            contentDescription = trf("Foto di %1\$s", item.name),
            maxSizePx = 1200,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .clip(RoundedCornerShape(RicordellaDimensions.cardRadius)),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM)) {
        IconBadge(entry.category?.kind.icon, containerColor = entry.group.tone.container, contentColor = entry.group.tone.content)
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                listOfNotNull(entry.category?.label, if (item.isArchived) tr("Archiviata") else null).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HappyWizard(size = 88.dp, scene = wizardSceneFor(entry.group))
    }
}

@Composable
private fun InfoSection(entry: ItemWithCategory, onUpdateOdometer: () -> Unit) {
    val item = entry.item
    val settings = LocalAppSettings.current
    val rows = buildList {
        item.licensePlate?.let { add(tr("Targa") to it) }
        item.productionYear?.let { add(tr("Anno") to it.toString()) }
        item.brand?.let { add(tr("Marca") to it) }
        item.model?.let { add(tr("Modello") to it) }
        item.serialNumber?.let { add(tr("N. di serie") to it) }
        item.purchaseDate?.let { add(tr("Acquistata") to DateTexts.date(it, settings.dateFormat)) }
        item.purchasePriceCents?.let { add(tr("Prezzo") to DateTexts.money(it)) }
    }
    if (rows.isEmpty() && !entry.isVehicle) return
    SectionHeader(tr("Informazioni"))
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(RicordellaDimensions.spaceL)) {
            rows.forEach { (label, value) -> InfoRow(label, value) }
            if (entry.isVehicle) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoRow(tr("Km"), item.odometerKm?.let(DateTexts::kilometers) ?: tr("Non indicati"), modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onUpdateOdometer) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(tr("Aggiorna"), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WarrantySection(entry: ItemWithCategory, today: LocalDate, onOpenDocument: (String) -> Unit) {
    val item = entry.item
    val status = item.warrantyStatus(today) ?: return
    val end = item.warrantyEndDate ?: return
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    SectionHeader(tr("Garanzia"))
    val colors = MaterialTheme.ricordellaColors
    val (text, container, content, icon) = when (status) {
        WarrantyStatus.ACTIVE -> WarrantyBadge(tr("Attiva"), colors.successContainer, colors.onSuccessContainer, Icons.Rounded.CheckCircle)
        WarrantyStatus.EXPIRING -> WarrantyBadge(
            RelativeDateDescriber.describeDeadline(end, today),
            colors.warningContainer,
            colors.onWarningContainer,
            Icons.Rounded.Warning,
        )
        WarrantyStatus.EXPIRED -> WarrantyBadge(tr("Scaduta"), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Rounded.ErrorOutline)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(RicordellaDimensions.spaceL), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusBadge(text, container, content, icon = icon)
            item.warrantyStartDate?.let { InfoRow(tr("Dal"), DateTexts.date(it, settings.dateFormat)) }
            InfoRow(tr("Scadenza"), DateTexts.date(end, settings.dateFormat))
            item.warrantySeller?.let { InfoRow(tr("Venditore"), it) }
            item.warrantyDocumentUri?.let { uri ->
                FileTile(uri = uri, mimeType = null, name = tr("Documento di garanzia"), onOpen = { onOpenDocument(uri) }, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private data class WarrantyBadge(
    val text: String,
    val container: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun OdometerDialog(current: Int?, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var value by rememberSaveable { mutableStateOf(current?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Aggiorna chilometri")) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit).take(7) },
                label = { Text(tr("Km attuali")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { value.toIntOrNull()?.let(onConfirm) }, enabled = value.toIntOrNull() != null) { Text(tr("Salva")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annulla")) } },
    )
}
