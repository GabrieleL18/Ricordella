package com.ricordella.app.feature.items

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
    var showMaintenance by rememberSaveable { mutableStateOf(false) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .contentWidth()
                .padding(RicordellaDimensions.screenPadding),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
        ) {
            ItemHeader(entry)
            InfoSection(entry, onUpdateOdometer = { showOdometer = true })
            WarrantySection(entry, state.now.toLocalDate(), onOpenDocument = { navigator.openViewer(it, null, tr("Documento di garanzia")) })

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

            SectionHeader(tr("Manutenzione")) {
                TextButton(onClick = { showMaintenance = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(tr("Intervento"), modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (state.maintenance.isEmpty()) {
                Text(tr("Nessun intervento registrato."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.maintenance.forEach { record -> MaintenanceRow(record, onDelete = { viewModel.onDeleteMaintenance(record) }) }

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
        }
    }

    if (showOdometer && entry != null) {
        OdometerDialog(
            current = entry.item.odometerKm,
            onDismiss = { showOdometer = false },
            onConfirm = { viewModel.onUpdateOdometer(it); showOdometer = false },
        )
    }
    if (showMaintenance && entry != null) {
        MaintenanceDialog(
            isVehicle = entry.isVehicle,
            currentKm = entry.item.odometerKm,
            onDismiss = { showMaintenance = false },
            onConfirm = { title, date, km, cost, description, next ->
                viewModel.onAddMaintenance(title, date, km, cost, description, next)
                showMaintenance = false
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = tr("Eliminare questa cosa?"),
            message = tr("Verranno eliminati anche lo storico delle manutenzioni e il promemoria della garanzia. ") +
                tr("Gli altri promemoria restano. Se vuoi solo nasconderla, usa \"Archivia\"."),
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
        Column {
            Text(item.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                listOfNotNull(entry.category?.label, if (item.isArchived) tr("Archiviata") else null).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
private fun MaintenanceRow(record: MaintenanceRecord, onDelete: () -> Unit) {
    val settings = LocalAppSettings.current
    var confirm by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = RicordellaDimensions.spaceL, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(record.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        DateTexts.date(record.date, settings.dateFormat),
                        record.odometerKm?.let(DateTexts::kilometers),
                        record.costCents?.let(DateTexts::money),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                record.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, contentDescription = trf("Elimina intervento %1\$s", record.title)) }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = tr("Eliminare l'intervento?"),
            message = trf("\"%1\$s\" verrà rimosso dallo storico.", record.title),
            confirmLabel = tr("Elimina"),
            destructive = true,
            onConfirm = onDelete,
            onDismiss = { confirm = false },
        )
    }
}

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

private val MaintenanceTitles get() = listOf(tr("Tagliando"), tr("Pneumatici"), tr("Revisione"), tr("Pulizia"), tr("Riparazione"), tr("Controllo"))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaintenanceDialog(
    isVehicle: Boolean,
    currentKm: Int?,
    onDismiss: () -> Unit,
    onConfirm: (String, LocalDate, Int?, Long?, String?, NextMaintenance?) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var km by rememberSaveable { mutableStateOf(currentKm?.toString().orEmpty()) }
    var cost by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var scheduleNext by rememberSaveable { mutableStateOf(false) }
    var nextMonths by rememberSaveable { mutableStateOf("12") }
    var nextKm by rememberSaveable { mutableStateOf(if (isVehicle) "15000" else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Nuovo intervento")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(tr("Intervento *")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MaintenanceTitles.forEach { suggestion ->
                        FilterChip(selected = title == suggestion, onClick = { title = suggestion }, label = { Text(suggestion) })
                    }
                }
                DateField(tr("Data"), date, { it?.let { picked -> date = picked } }, modifier = Modifier.fillMaxWidth())
                if (isVehicle) {
                    OutlinedTextField(
                        value = km,
                        onValueChange = { km = it.filter(Char::isDigit).take(7) },
                        label = { Text(tr("Km")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(10) },
                    label = { Text(tr("Costo €")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(tr("Descrizione")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Ricordami il prossimo"), modifier = Modifier.weight(1f))
                    Switch(checked = scheduleNext, onCheckedChange = { scheduleNext = it })
                }
                if (scheduleNext) {
                    Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                        OutlinedTextField(
                            value = nextMonths,
                            onValueChange = { nextMonths = it.filter(Char::isDigit).take(3) },
                            label = { Text(tr("Tra mesi")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (isVehicle) {
                            OutlinedTextField(
                                value = nextKm,
                                onValueChange = { nextKm = it.filter(Char::isDigit).take(6) },
                                label = { Text(tr("o tra km")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val next = if (scheduleNext) NextMaintenance(nextMonths.toIntOrNull(), nextKm.toIntOrNull()) else null
                    onConfirm(title, date, km.toIntOrNull(), parseCents(cost), description.trim().ifEmpty { null }, next)
                },
            ) { Text(tr("Salva")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annulla")) } },
    )
}

