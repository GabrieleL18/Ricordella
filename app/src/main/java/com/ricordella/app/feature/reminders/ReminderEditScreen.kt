package com.ricordella.app.feature.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.ui.DateField
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.DropdownField
import com.ricordella.app.core.ui.MultiSelectDialog
import com.ricordella.app.core.ui.NotifyOffsetPresets
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.TimeField
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.label
import com.ricordella.app.core.ui.notifyOffsetLabel
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.domain.model.Priority
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.model.displayName
import java.time.DayOfWeek

@Composable
fun ReminderEditScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { c, handle ->
        ReminderEditViewModel(handle, c.reminderRepository, c.personRepository, c.itemRepository, c.settingsRepository, c.saveReminder, c.time)
    }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(form.saved) { if (form.saved) onBack() }
    LaunchedEffect(form.errorMessage) {
        form.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    DetailScaffold(
        title = if (form.isNew) "Nuovo ${if (form.type == ReminderType.EVENT) "evento" else "promemoria"}" else "Modifica",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            TextButton(onClick = viewModel::save, enabled = !form.isSaving && !form.isLoading) { Text("Salva") }
        },
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
            BasicFields(form, viewModel::update)
            TextButton(onClick = { viewModel.update { it.copy(showAdvanced = !it.showAdvanced) } }) {
                Icon(if (form.showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                Text(if (form.showAdvanced) "Nascondi opzioni" else "Altre opzioni", modifier = Modifier.padding(start = 8.dp))
            }
            if (form.showAdvanced) {
                AdvancedFields(form, viewModel)
            }
            Button(
                onClick = viewModel::save,
                enabled = !form.isSaving,
                modifier = Modifier.fillMaxWidth().padding(vertical = RicordellaDimensions.spaceL),
            ) { Text("Salva") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BasicFields(form: ReminderForm, update: ((ReminderForm) -> ReminderForm) -> Unit) {
    OutlinedTextField(
        value = form.title,
        onValueChange = { value -> update { it.copy(title = value) } },
        label = { Text("Titolo *") },
        isError = form.titleError,
        supportingText = if (form.titleError) ({ Text("Inserisci un titolo") }) else null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
        DateField(
            label = "Data *",
            value = form.date,
            onValueChange = { value -> update { it.copy(date = value) } },
            isError = form.dateError,
            modifier = Modifier.weight(1.2f),
        )
        TimeField(
            label = "Ora",
            value = form.time,
            onValueChange = { value -> update { it.copy(time = value) } },
            modifier = Modifier.weight(1f),
        )
    }
    if (form.time == null) {
        Text("Senza orario il promemoria vale per tutto il giorno.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("Tipo", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
        ReminderType.entries.forEach { type ->
            FilterChip(selected = form.type == type, onClick = { update { it.copy(type = type) } }, label = { Text(type.label) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedFields(form: ReminderForm, viewModel: ReminderEditViewModel) {
    val update = viewModel::update
    val people by viewModel.people.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var showPeoplePicker by rememberSaveable { mutableStateOf(false) }
    var showItemPicker by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = form.description,
        onValueChange = { value -> update { it.copy(description = value) } },
        label = { Text("Descrizione") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )

    SectionHeader("Ricorrenza")
    DropdownField(
        label = "Si ripete",
        options = RecurrencePreset.entries,
        selected = form.recurrencePreset,
        optionLabel = { it.label },
        onSelected = { preset -> update { it.copy(recurrencePreset = preset) } },
    )
    if (form.recurrencePreset == RecurrencePreset.CUSTOM) {
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = form.customInterval,
                onValueChange = { value -> update { it.copy(customInterval = value.filter(Char::isDigit).take(3)) } },
                label = { Text("Ogni") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(0.4f),
            )
            DropdownField(
                label = "Unità",
                options = RecurrenceFrequency.entries,
                selected = form.customFrequency,
                optionLabel = { it.unitLabel },
                onSelected = { frequency -> update { it.copy(customFrequency = frequency) } },
                modifier = Modifier.weight(0.6f),
            )
        }
        if (form.customFrequency == RecurrenceFrequency.WEEKLY) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    val selected = day in form.weekDays
                    FilterChip(
                        selected = selected,
                        onClick = { update { it.copy(weekDays = if (selected) it.weekDays - day else it.weekDays + day) } },
                        label = { Text(DateTexts.weekdayFull(day).take(3)) },
                    )
                }
            }
        }
        DateField(
            label = "Fino al (opzionale)",
            value = form.recurrenceEnd,
            onValueChange = { value -> update { it.copy(recurrenceEnd = value) } },
            clearable = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    SectionHeader("Priorità")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Priority.entries.forEachIndexed { index, priority ->
            SegmentedButton(
                selected = form.priority == priority,
                onClick = { update { it.copy(priority = priority) } },
                shape = SegmentedButtonDefaults.itemShape(index, Priority.entries.size),
            ) { Text(priority.label) }
        }
    }

    SectionHeader("Categoria")
    OutlinedTextField(
        value = form.category,
        onValueChange = { value -> update { it.copy(category = value) } },
        label = { Text("Categoria (es. Casa, Lavoro)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
    val suggestions = categories.filter { it != form.category && it.contains(form.category, ignoreCase = true) }.take(6)
    if (suggestions.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            suggestions.forEach { suggestion ->
                FilterChip(selected = false, onClick = { update { it.copy(category = suggestion) } }, label = { Text(suggestion) })
            }
        }
    }

    SectionHeader("Collegamenti")
    Text("Persone", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        people.filter { it.id in form.personIds }.forEach { person ->
            InputChip(selected = true, onClick = { update { it.copy(personIds = it.personIds - person.id) } }, label = { Text(person.displayName) })
        }
        AssistAddChip("Persona") { showPeoplePicker = true }
    }
    Text("Cose", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.filter { it.item.id in form.itemIds }.forEach { entry ->
            InputChip(selected = true, onClick = { update { it.copy(itemIds = it.itemIds - entry.item.id) } }, label = { Text(entry.item.name) })
        }
        AssistAddChip("Cosa") { showItemPicker = true }
    }

    val linkedVehicle = items.firstOrNull { it.item.id in form.itemIds && it.isVehicle }
    if (linkedVehicle != null || form.dueOdometerKm.isNotEmpty()) {
        SectionHeader("Chilometraggio")
        linkedVehicle?.item?.odometerKm?.let {
            Text("Attuali: ${DateTexts.kilometers(it)}", style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            OutlinedTextField(
                value = form.dueOdometerKm,
                onValueChange = { value -> update { it.copy(dueOdometerKm = value.filter(Char::isDigit).take(7)) } },
                label = { Text("Scadenza a km") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = form.odometerIntervalKm,
                onValueChange = { value -> update { it.copy(odometerIntervalKm = value.filter(Char::isDigit).take(7)) } },
                label = { Text("Ogni km") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }

    SectionHeader("Notifica")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Avvisami", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = form.notificationsEnabled, onCheckedChange = { value -> update { it.copy(notificationsEnabled = value) } })
    }
    if (form.notificationsEnabled) {
        val offsets = (NotifyOffsetPresets + form.notifyOffsetMinutes).distinct().sorted()
        DropdownField(
            label = "Quando",
            options = offsets,
            selected = form.notifyOffsetMinutes,
            optionLabel = ::notifyOffsetLabel,
            onSelected = { value -> update { it.copy(notifyOffsetMinutes = value) } },
        )
    }

    HorizontalDivider(Modifier.padding(vertical = RicordellaDimensions.spaceS))
    OutlinedTextField(
        value = form.notes,
        onValueChange = { value -> update { it.copy(notes = value) } },
        label = { Text("Note") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )

    if (showPeoplePicker) {
        MultiSelectDialog(
            title = "Persone collegate",
            options = people,
            initiallySelected = form.personIds,
            idOf = { it.id },
            labelOf = { it.displayName },
            emptyMessage = "Non hai ancora aggiunto persone.",
            onDismiss = { showPeoplePicker = false },
            onConfirm = { ids -> update { it.copy(personIds = ids) }; showPeoplePicker = false },
        )
    }
    if (showItemPicker) {
        MultiSelectDialog(
            title = "Cose collegate",
            options = items,
            initiallySelected = form.itemIds,
            idOf = { it.item.id },
            labelOf = { it.item.name },
            emptyMessage = "Non hai ancora aggiunto cose.",
            onDismiss = { showItemPicker = false },
            onConfirm = { ids -> update { it.copy(itemIds = ids) }; showItemPicker = false },
        )
    }
}

@Composable
private fun AssistAddChip(label: String, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
    )
}
