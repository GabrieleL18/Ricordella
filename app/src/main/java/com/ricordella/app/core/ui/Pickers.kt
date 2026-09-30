package com.ricordella.app.core.ui

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.date.DateTexts
import java.time.LocalDate
import java.time.LocalTime

/** Campo data: al tocco apre il selettore data Material. */
@Composable
fun DateField(
    label: String,
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = false,
    isError: Boolean = false,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val settings = LocalAppSettings.current
    ClickableField(
        label = label,
        text = value?.let { DateTexts.date(it, settings.dateFormat) } ?: "",
        icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
        onClick = { showPicker = true },
        onClear = if (clearable && value != null) ({ onValueChange(null) }) else null,
        isError = isError,
        modifier = modifier,
    )
    if (showPicker) {
        DatePickerDialogFor(
            initial = value,
            onDismiss = { showPicker = false },
            onConfirm = { onValueChange(it); showPicker = false },
        )
    }
}

/** Campo orario: al tocco apre il selettore orario Material, su [value] o, se vuoto, su [defaultTime]. */
@Composable
fun TimeField(
    label: String,
    value: LocalTime?,
    onValueChange: (LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = true,
    isError: Boolean = false,
    defaultTime: LocalTime = LocalTime.of(9, 0),
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    ClickableField(
        label = label,
        text = value?.let(DateTexts::time) ?: "",
        icon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
        onClick = { showPicker = true },
        onClear = if (clearable && value != null) ({ onValueChange(null) }) else null,
        modifier = modifier,
        isError = isError,
    )
    if (showPicker) {
        TimePickerDialogFor(
            initial = value ?: defaultTime,
            onDismiss = { showPicker = false },
            onConfirm = { onValueChange(it); showPicker = false },
        )
    }
}

@Composable
private fun ClickableField(
    label: String,
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = icon,
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
            // Date lunghe ("28 settembre 2026") vanno a capo invece di essere tagliate.
            maxLines = 2,
            textStyle = if (text.length > 12) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(end = if (onClear != null) 48.dp else 0.dp)
                .semantics { contentDescription = "$label: ${text.ifEmpty { tr("non impostato") }}" }
                .clickable(role = Role.Button, onClick = onClick),
        )
        if (onClear != null) {
            IconButton(onClick = onClear, modifier = Modifier.align(Alignment.CenterEnd).padding(top = 8.dp)) {
                Icon(Icons.Rounded.Clear, contentDescription = trf("Rimuovi %1\$s", label))
            }
        }
    }
}

/** Adesso, al minuto: l'orario proposto quando si imposta qualcosa per oggi. */
fun currentMinute(): LocalTime = LocalTime.now().withSecond(0).withNano(0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogFor(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Scegli l'orario")) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annulla")) } },
    )
}

/** Dialog di selezione multipla (persone o cose da collegare). */
@Composable
fun <T> MultiSelectDialog(
    title: String,
    options: List<T>,
    initiallySelected: Set<String>,
    idOf: (T) -> String,
    labelOf: (T) -> String,
    emptyMessage: String,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(initiallySelected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (options.isEmpty()) {
                Text(emptyMessage, style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(options, key = idOf) { option ->
                        val id = idOf(option)
                        val checked = id in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Checkbox) { selected = if (checked) selected - id else selected + id }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(labelOf(option), modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(tr("Conferma")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annulla")) } },
    )
}
