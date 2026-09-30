package com.ricordella.app.feature.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import com.ricordella.app.core.ui.PushButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.ui.DateField
import com.ricordella.app.core.ui.DatePickerDialogFor
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.IconBadge
import com.ricordella.app.core.ui.MultiSelectDialog
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.UriImage
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.icon
import com.ricordella.app.core.ui.label
import com.ricordella.app.core.ui.rememberFilePicker
import com.ricordella.app.core.ui.rememberPhotoPicker
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.domain.model.ItemGroup
import com.ricordella.app.domain.model.ItemReminderTemplates
import com.ricordella.app.domain.model.PersonItemRole
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.ReminderSuggestion
import com.ricordella.app.domain.model.displayName

@Composable
fun ItemEditScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { c, handle -> ItemEditViewModel(handle, c.itemRepository, c.personRepository, c.saveItem, c.time) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(form.saved) { if (form.saved) onBack() }
    LaunchedEffect(form.errorMessage) {
        form.errorMessage?.let { snackbar.showSnackbar(it); viewModel.onErrorShown() }
    }

    DetailScaffold(
        title = if (form.isNew) "Aggiungi ${form.category?.name?.lowercase() ?: "cosa"}" else "Modifica",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = { TextButton(onClick = viewModel::save, enabled = !form.isSaving && !form.isLoading) { Text("Salva") } },
    ) { padding ->
        if (form.isLoading) return@DetailScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .contentWidth()
                .padding(horizontal = RicordellaDimensions.screenPadding, vertical = RicordellaDimensions.spaceS),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            CategorySelector(form, viewModel)
            PhotoRow(form, viewModel)
            MainFields(form, viewModel)
            if (ItemReminderTemplates.supportsWarranty(form.group)) WarrantyFields(form, viewModel)
            PeopleFields(form, viewModel)
            if (form.isNew && form.suggestions.isNotEmpty()) SuggestionFields(form, viewModel)
            OutlinedTextField(
                value = form.notes,
                onValueChange = { value -> viewModel.update { it.copy(notes = value) } },
                label = { Text("Note") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            PushButton(
                text = "Salva",
                onClick = viewModel::save,
                icon = Icons.Rounded.Check,
                loading = form.isSaving,
                modifier = Modifier.fillMaxWidth().padding(vertical = RicordellaDimensions.spaceL),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySelector(form: ItemForm, viewModel: ItemEditViewModel) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var group by rememberSaveable { mutableStateOf(form.category?.itemGroup ?: ItemGroup.VEHICLES) }
    SectionHeader("Categoria")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ItemGroup.entries.forEach { g ->
            FilterChip(
                selected = group == g,
                onClick = { group = g },
                label = { Text(g.label) },
                leadingIcon = { Icon(g.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        categories.filter { it.itemGroup == group }.forEach { category ->
            FilterChip(
                selected = form.category?.id == category.id,
                onClick = { viewModel.onCategorySelected(category) },
                label = { Text(category.name) },
                leadingIcon = { Icon(category.kind.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun PhotoRow(form: ItemForm, viewModel: ItemEditViewModel) {
    val pickPhoto = rememberPhotoPicker { uri -> viewModel.update { it.copy(photoUri = uri) } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceL)) {
        val placeholder: @Composable () -> Unit = { IconBadge(form.category?.kind.icon, modifier = Modifier.size(72.dp)) }
        val photo = form.photoUri
        if (photo != null) {
            UriImage(photo, contentDescription = "Foto", modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)), fallback = placeholder)
        } else {
            placeholder()
        }
        Column {
            OutlinedButton(onClick = pickPhoto) {
                Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                Text(if (photo == null) "Aggiungi foto" else "Cambia foto", modifier = Modifier.padding(start = 8.dp))
            }
            if (photo != null) TextButton(onClick = { viewModel.update { it.copy(photoUri = null) } }) { Text("Rimuovi foto") }
        }
    }
}

@Composable
private fun MainFields(form: ItemForm, viewModel: ItemEditViewModel) {
    val update = viewModel::update
    OutlinedTextField(
        value = form.name,
        onValueChange = { value -> update { it.copy(name = value) } },
        label = { Text("Nome *") },
        isError = form.nameError,
        supportingText = if (form.nameError) ({ Text("Inserisci un nome") }) else ({ Text("Es. Fiat Panda, Lavatrice Samsung") }),
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
    if (form.group != ItemGroup.DOCUMENTS) {
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            TextInput("Marca", form.brand, Modifier.weight(1f)) { value -> update { it.copy(brand = value) } }
            TextInput("Modello", form.model, Modifier.weight(1f)) { value -> update { it.copy(model = value) } }
        }
    }
    if (form.isVehicle) {
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            TextInput("Targa", form.licensePlate, Modifier.weight(1f), capitalization = KeyboardCapitalization.Characters) { value ->
                update { it.copy(licensePlate = value) }
            }
            NumberInput("Anno", form.productionYear, Modifier.weight(0.7f), maxLength = 4) { value -> update { it.copy(productionYear = value) } }
        }
        NumberInput("Chilometri attuali", form.odometerKm, Modifier.fillMaxWidth(), maxLength = 7) { value -> update { it.copy(odometerKm = value) } }
    }
    if (form.group != ItemGroup.DOCUMENTS) {
        TextInput("Numero di serie", form.serialNumber, Modifier.fillMaxWidth(), capitalization = KeyboardCapitalization.Characters) { value ->
            update { it.copy(serialNumber = value) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            DateField(
                label = "Data acquisto",
                value = form.purchaseDate,
                onValueChange = { value -> update { it.copy(purchaseDate = value) } },
                clearable = true,
                modifier = Modifier.weight(1.2f),
            )
            OutlinedTextField(
                value = form.purchasePrice,
                onValueChange = { value -> update { it.copy(purchasePrice = value.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(10)) } },
                label = { Text("Prezzo €") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WarrantyFields(form: ItemForm, viewModel: ItemEditViewModel) {
    val update = viewModel::update
    val pickDocument = rememberFilePicker { file -> update { it.copy(warrantyDocumentUri = file.uri) } }
    SectionHeader("Garanzia")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Ha una garanzia", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = form.hasWarranty, onCheckedChange = { value ->
            update { it.copy(hasWarranty = value) }
            if (value && form.warrantyEnd == null) viewModel.onWarrantyDuration(24)
        })
    }
    if (!form.hasWarranty) return
    DateField(
        label = "Inizio garanzia",
        value = form.warrantyStart,
        onValueChange = { value -> update { it.copy(warrantyStart = value) } },
        modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(12L, 24L, 36L).forEach { months ->
            FilterChip(
                selected = form.warrantyStart != null && form.warrantyEnd == form.warrantyStart.plusMonths(months),
                onClick = { viewModel.onWarrantyDuration(months) },
                label = { Text("${months / 12} ${if (months == 12L) "anno" else "anni"}") },
            )
        }
    }
    DateField(
        label = "Scadenza garanzia *",
        value = form.warrantyEnd,
        onValueChange = { value -> update { it.copy(warrantyEnd = value) } },
        isError = form.warrantyError,
        modifier = Modifier.fillMaxWidth(),
    )
    TextInput("Venditore", form.warrantySeller, Modifier.fillMaxWidth()) { value -> update { it.copy(warrantySeller = value) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = pickDocument) {
            Icon(Icons.Rounded.AttachFile, contentDescription = null)
            Text(if (form.warrantyDocumentUri == null) "Allega documento" else "Cambia documento", modifier = Modifier.padding(start = 8.dp))
        }
        if (form.warrantyDocumentUri != null) {
            TextButton(onClick = { update { it.copy(warrantyDocumentUri = null) } }) { Text("Rimuovi") }
        }
    }
    Text(
        "Ricordella ti avviserà 30 giorni prima della scadenza.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeopleFields(form: ItemForm, viewModel: ItemEditViewModel) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    SectionHeader("Persone")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        people.filter { it.id in form.people }.forEach { person ->
            var menu by remember { mutableStateOf(false) }
            val role = form.people.getValue(person.id)
            Box {
                InputChip(selected = true, onClick = { menu = true }, label = { Text("${person.displayName} · ${role.label}") })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    PersonItemRole.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { menu = false; viewModel.update { it.copy(people = it.people + (person.id to option)) } },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rimuovi") },
                        onClick = { menu = false; viewModel.update { it.copy(people = it.people - person.id) } },
                    )
                }
            }
        }
        FilterChip(
            selected = false,
            onClick = { showPicker = true },
            label = { Text("Persona") },
            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        )
    }
    if (showPicker) {
        MultiSelectDialog(
            title = "Persone associate",
            options = people,
            initiallySelected = form.people.keys,
            idOf = { it.id },
            labelOf = { it.displayName },
            emptyMessage = "Non hai ancora aggiunto persone.",
            onDismiss = { showPicker = false },
            onConfirm = { ids ->
                viewModel.update { current ->
                    current.copy(people = ids.associateWith { current.people[it] ?: PersonItemRole.OWNER })
                }
                showPicker = false
            },
        )
    }
}

@Composable
private fun SuggestionFields(form: ItemForm, viewModel: ItemEditViewModel) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    SectionHeader("Cosa vuoi ricordare?")
    form.suggestions.forEachIndexed { index, choice ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Checkbox) { viewModel.onToggleSuggestion(index) },
        ) {
            Checkbox(checked = choice.selected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f)) {
                Text(choice.suggestion.title, style = MaterialTheme.typography.bodyLarge)
                Text(choice.suggestion.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (choice.selected) {
                TextButton(onClick = { editingIndex = index }) { Text(DateTexts.date(choice.firstDueDate, com.ricordella.app.domain.model.DateFormatStyle.NUMERIC)) }
            }
        }
    }
    Text(
        "Potrai modificare date e dettagli in qualsiasi momento.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    editingIndex?.let { index ->
        DatePickerDialogFor(
            initial = form.suggestions[index].firstDueDate,
            onDismiss = { editingIndex = null },
            onConfirm = { viewModel.onSuggestionDate(index, it); editingIndex = null },
        )
    }
}

private fun ReminderSuggestion.describe(): String {
    val recurrence = when (frequency) {
        RecurrenceFrequency.DAILY -> if (interval == 1) "Ogni giorno" else "Ogni $interval giorni"
        RecurrenceFrequency.WEEKLY -> if (interval == 1) "Ogni settimana" else "Ogni $interval settimane"
        RecurrenceFrequency.MONTHLY -> if (interval == 1) "Ogni mese" else "Ogni $interval mesi"
        RecurrenceFrequency.YEARLY -> if (interval == 1) "Ogni anno" else "Ogni $interval anni"
        null -> null
    }
    val km = odometerIntervalKm?.let { "ogni ${DateTexts.kilometers(it)} o un anno" }
    return listOfNotNull(recurrence, km).joinToString(" · ").ifEmpty { "Una volta" }.replaceFirstChar { it.uppercase() }
}

@Composable
private fun TextInput(
    label: String,
    value: String,
    modifier: Modifier,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = capitalization),
        modifier = modifier,
    )
}

@Composable
private fun NumberInput(label: String, value: String, modifier: Modifier, maxLength: Int, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(maxLength)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
