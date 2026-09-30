package com.ricordella.app.feature.reminders

import java.time.LocalTime
import java.time.LocalDate
import com.ricordella.app.core.ui.currentMinute
import com.ricordella.app.core.i18n.tr
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import android.provider.Settings
import android.os.Build
import android.content.Intent
import android.app.NotificationManager
import com.ricordella.app.core.i18n.trf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChipDefaults
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.icon
import com.ricordella.app.core.ui.tone
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
import com.ricordella.app.core.ui.birthdayAgeLabel
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
        title = when {
            !form.isNew -> tr("Modifica")
            form.type == ReminderType.EVENT -> tr("Nuovo evento")
            form.type == ReminderType.ALARM -> tr("Nuova sveglia")
            else -> tr("Nuovo promemoria")
        },
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            TextButton(onClick = viewModel::save, enabled = !form.isSaving && !form.isLoading) { Text(tr("Salva")) }
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
            BasicFields(form, viewModel::update, viewModel::onTypeChange)
            if (form.type == ReminderType.VACATION) {
                TripFields(form.trip, onChange = { trip -> viewModel.update { it.copy(trip = trip) } })
            }
            TextButton(onClick = { viewModel.update { it.copy(showAdvanced = !it.showAdvanced) } }) {
                Icon(if (form.showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                Text(if (form.showAdvanced) tr("Nascondi opzioni") else tr("Altre opzioni"), modifier = Modifier.padding(start = 8.dp))
            }
            if (form.showAdvanced) {
                AdvancedFields(form, viewModel)
            }
            PushButton(
                text = tr("Salva"),
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
private fun BasicFields(form: ReminderForm, update: ((ReminderForm) -> ReminderForm) -> Unit, onTypeChange: (ReminderType) -> Unit) {
    OutlinedTextField(
        value = form.title,
        onValueChange = { value -> update { it.copy(title = value) } },
        label = { Text(tr("Titolo *")) },
        isError = form.titleError,
        supportingText = if (form.titleError) ({ Text(tr("Inserisci un titolo")) }) else null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
        DateField(
            label = tr("Data *"),
            value = form.date,
            onValueChange = { value -> update { it.copy(date = value) } },
            isError = form.dateError,
            modifier = Modifier.weight(1.6f),
        )
        TimeField(
            label = if (form.isAlarm) tr("Ora *") else tr("Ora"),
            value = form.time,
            onValueChange = { value -> update { it.copy(time = value) } },
            modifier = Modifier.weight(1f),
            isError = form.timeError,
            // Per oggi il selettore parte dall'ora attuale, per gli altri giorni dalle 9.
            defaultTime = if (form.date == LocalDate.now()) currentMinute() else LocalTime.of(9, 0),
        )
    }
    if (form.timeError) {
        Text(tr("La sveglia ha bisogno di un orario."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (form.isAlarm) {
        AlarmFields(form, update)
    }
    if (form.type == ReminderType.BIRTHDAY) {
        val age = form.birthYear.toIntOrNull()?.let { year -> form.date?.year?.minus(year) }?.takeIf { it in 1..150 }
        OutlinedTextField(
            value = form.birthYear,
            onValueChange = { value -> update { it.copy(birthYear = value.filter(Char::isDigit).take(4)) } },
            label = { Text(tr("Anno di nascita")) },
            supportingText = { Text(age?.let(::birthdayAgeLabel) ?: tr("Facoltativo: così vedi quanti anni compie.")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    // Eventi di più giorni: vacanze, viaggi, ricoveri... Nel calendario appaiono come una barra continua.
    if (!form.isAlarm) Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Dura più giorni"), style = MaterialTheme.typography.bodyLarge)
            Text(tr("Es. una vacanza o un viaggio"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = form.multiDay, onCheckedChange = { value ->
            update { it.copy(multiDay = value, endDate = if (value) it.endDate ?: it.date?.plusDays(1) else it.endDate) }
        })
    }
    AnimatedVisibility(form.multiDay) {
        DateField(
            label = tr("Fino al *"),
            value = form.endDate,
            onValueChange = { value -> update { it.copy(endDate = value) } },
            isError = form.endDateError,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (form.endDateError) {
        Text(tr("L'ultimo giorno deve venire dopo il primo."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (form.time == null && !form.isAlarm) {
        Text(tr("Senza orario il promemoria vale per tutto il giorno."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(tr("Tipo"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
        ReminderType.entries.forEach { type ->
            val tone = type.tone
            FilterChip(
                selected = form.type == type,
                onClick = { onTypeChange(type) },
                label = { Text(type.label) },
                leadingIcon = { Icon(type.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = tone.container,
                    selectedLabelColor = tone.content,
                    selectedLeadingIconColor = tone.content,
                ),
            )
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
        label = { Text(tr("Descrizione")) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )

    SectionHeader(tr("Ricorrenza"))
    DropdownField(
        label = tr("Si ripete"),
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
                label = { Text(tr("Ogni")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(0.4f),
            )
            DropdownField(
                label = tr("Unità"),
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
            label = tr("Fino al (opzionale)"),
            value = form.recurrenceEnd,
            onValueChange = { value -> update { it.copy(recurrenceEnd = value) } },
            clearable = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    SectionHeader(tr("Priorità"))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Priority.entries.forEachIndexed { index, priority ->
            SegmentedButton(
                selected = form.priority == priority,
                onClick = { update { it.copy(priority = priority) } },
                shape = SegmentedButtonDefaults.itemShape(index, Priority.entries.size),
            ) { Text(priority.label) }
        }
    }

    SectionHeader(tr("Categoria"))
    OutlinedTextField(
        value = form.category,
        onValueChange = { value -> update { it.copy(category = value) } },
        label = { Text(tr("Categoria (es. Casa, Lavoro)")) },
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

    SectionHeader(tr("Collegamenti"))
    Text(tr("Persone"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        people.filter { it.id in form.personIds }.forEach { person ->
            InputChip(selected = true, onClick = { update { it.copy(personIds = it.personIds - person.id) } }, label = { Text(person.displayName) })
        }
        AssistAddChip(tr("Persona")) { showPeoplePicker = true }
    }
    Text(tr("Cose"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.filter { it.item.id in form.itemIds }.forEach { entry ->
            InputChip(selected = true, onClick = { update { it.copy(itemIds = it.itemIds - entry.item.id) } }, label = { Text(entry.item.name) })
        }
        AssistAddChip(tr("Cosa")) { showItemPicker = true }
    }

    val linkedVehicle = items.firstOrNull { it.item.id in form.itemIds && it.isVehicle }
    if (linkedVehicle != null || form.dueOdometerKm.isNotEmpty()) {
        SectionHeader(tr("Chilometraggio"))
        linkedVehicle?.item?.odometerKm?.let {
            Text(trf("Attuali: %1\$s", DateTexts.kilometers(it)), style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            OutlinedTextField(
                value = form.dueOdometerKm,
                onValueChange = { value -> update { it.copy(dueOdometerKm = value.filter(Char::isDigit).take(7)) } },
                label = { Text(tr("Scadenza a km")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = form.odometerIntervalKm,
                onValueChange = { value -> update { it.copy(odometerIntervalKm = value.filter(Char::isDigit).take(7)) } },
                label = { Text(tr("Ogni km")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }

    SectionHeader(tr("Notifica"))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Avvisami"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = form.notificationsEnabled, onCheckedChange = { value -> update { it.copy(notificationsEnabled = value) } })
    }
    if (form.notificationsEnabled) {
        val offsets = (NotifyOffsetPresets + form.notifyOffsetMinutes).distinct().sorted()
        DropdownField(
            label = tr("Quando"),
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
        label = { Text(tr("Note")) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )

    if (showPeoplePicker) {
        MultiSelectDialog(
            title = tr("Persone collegate"),
            options = people,
            initiallySelected = form.personIds,
            idOf = { it.id },
            labelOf = { it.displayName },
            emptyMessage = tr("Non hai ancora aggiunto persone."),
            onDismiss = { showPeoplePicker = false },
            onConfirm = { ids -> update { it.copy(personIds = ids) }; showPeoplePicker = false },
        )
    }
    if (showItemPicker) {
        MultiSelectDialog(
            title = tr("Cose collegate"),
            options = items,
            initiallySelected = form.itemIds,
            idOf = { it.item.id },
            labelOf = { it.item.name },
            emptyMessage = tr("Non hai ancora aggiunto cose."),
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

/** Opzioni della sveglia: ripetizione quotidiana e controllo dei permessi che la fanno suonare. */
@Composable
private fun AlarmFields(form: ReminderForm, update: ((ReminderForm) -> ReminderForm) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Ripeti ogni giorno"), style = MaterialTheme.typography.bodyLarge)
            Text(tr("Es. per le medicine"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = form.recurrencePreset == RecurrencePreset.DAILY,
            onCheckedChange = { daily -> update { it.copy(recurrencePreset = if (daily) RecurrencePreset.DAILY else RecurrencePreset.NONE) } },
        )
    }
    // Da Android 14 la schermata a tutto schermo può essere disattivata: si invita a riattivarla.
    val canFullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    if (!canFullScreen) {
        Text(
            tr("Per mostrare la sveglia a tutto schermo serve un permesso."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri()))
            }
        }) { Text(tr("Concedi permesso")) }
    }
}
