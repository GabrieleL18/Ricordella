package com.ricordella.app.feature.settings

import com.ricordella.app.core.ui.tone
import com.ricordella.app.core.ui.currentMinute
import com.ricordella.app.core.ui.TimePickerDialogFor
import com.ricordella.app.core.ui.PushButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.ui.DropdownField
import com.ricordella.app.core.ui.NotifyOffsetPresets
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.icon
import com.ricordella.app.core.ui.label
import com.ricordella.app.core.ui.notifyOffsetLabel
import com.ricordella.app.domain.model.AlarmSound
import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.model.TypeDefaults

/** Predefiniti per tipo: notifica, orario e preavviso proposti quando si crea un promemoria nuovo. */
@Composable
internal fun TypeDefaultsSection(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<ReminderType?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Una sola voce che si apre come sottomenù: i tipi sono tanti e affollerebbero le impostazioni.
    SettingRow(
        icon = Icons.Rounded.Tune,
        title = tr("Predefiniti per tipo"),
        subtitle = tr("Orario, notifica e preavviso proposti quando crei un promemoria di ogni tipo."),
        onClick = { expanded = !expanded },
        trailing = { Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null) },
    )
    AnimatedVisibility(expanded) {
        Column(
            Modifier
                .padding(start = 16.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large),
        ) {
            // Le feste arrivano dal calendario importato: non si creano a mano.
            ReminderType.entries.filter { it != ReminderType.HOLIDAY }.forEach { type ->
                SettingRow(icon = type.icon, title = type.label, subtitle = summary(type, settings.defaultsFor(type)), onClick = { editing = type })
            }
        }
    }
    editing?.let { type ->
        TypeDefaultsDialog(
            type = type,
            initial = settings.defaultsFor(type),
            onDismiss = { editing = null },
            onSave = { value ->
                update { it.copy(typeDefaults = it.typeDefaults + (type to value)) }
                editing = null
            },
        )
    }
}

private fun summary(type: ReminderType, defaults: TypeDefaults): String = when {
    type == ReminderType.ALARM -> defaults.time?.let { trf("Alle %1\$s", DateTexts.time(it)) } ?: tr("Orario da scegliere")
    !defaults.notificationsEnabled -> listOfNotNull(defaults.time?.let(DateTexts::time) ?: tr("Tutto il giorno"), tr("senza notifica")).joinToString(" · ")
    else -> listOf(defaults.time?.let(DateTexts::time) ?: tr("Tutto il giorno"), notifyOffsetLabel(defaults.notifyOffsetMinutes)).joinToString(" · ")
}

/**
 * Finestra dei predefiniti di un tipo: testata colorata con l'icona del tipo, poi tre blocchi
 * (orario, notifica, preavviso) con scelte a pillola invece dei menu a tendina.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeDefaultsDialog(type: ReminderType, initial: TypeDefaults, onDismiss: () -> Unit, onSave: (TypeDefaults) -> Unit) {
    var value by remember(type) { mutableStateOf(initial) }
    var pickTime by remember { mutableStateOf(false) }
    val isAlarm = type == ReminderType.ALARM
    val tone = type.tone
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Testata: icona del tipo nel suo colore e spiegazione breve.
                Row(
                    Modifier.fillMaxWidth().background(tone.container).padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(48.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(26.dp))
                    }
                    Column {
                        Text(type.label, style = MaterialTheme.typography.titleLarge, color = tone.content)
                        Text(tr("Proposti per i nuovi promemoria"), style = MaterialTheme.typography.bodySmall, color = tone.content.copy(alpha = 0.8f))
                    }
                }
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    DefaultsBlock(Icons.Rounded.Schedule, tr("Orario")) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isAlarm) {
                                FilterChip(
                                    selected = value.time == null,
                                    onClick = { value = value.copy(time = null) },
                                    label = { Text(tr("Tutto il giorno")) },
                                )
                            }
                            FilterChip(
                                selected = value.time != null,
                                onClick = { pickTime = true },
                                label = { Text(value.time?.let(DateTexts::time) ?: tr("Scegli l'orario")) },
                                leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                    // La sveglia suona sempre, all'orario esatto: notifica e preavviso non si scelgono.
                    if (!isAlarm) {
                        DefaultsBlock(Icons.Rounded.Notifications, tr("Notifica")) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (value.notificationsEnabled) tr("Ti avviso io") else tr("Nessuna notifica"),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(checked = value.notificationsEnabled, onCheckedChange = { value = value.copy(notificationsEnabled = it) })
                            }
                        }
                        AnimatedVisibility(value.notificationsEnabled) {
                            DefaultsBlock(Icons.Rounded.NotificationsActive, tr("Preavviso")) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NotifyOffsetPresets.forEach { minutes ->
                                        FilterChip(
                                            selected = value.notifyOffsetMinutes == minutes,
                                            onClick = { value = value.copy(notifyOffsetMinutes = minutes) },
                                            label = { Text(notifyOffsetLabel(minutes)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text(tr("Annulla")) }
                        PushButton(text = tr("Salva"), icon = Icons.Rounded.Check, onClick = { onSave(value) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
    if (pickTime) {
        // Il selettore parte dall'orario già scelto o, se manca, dall'ora attuale.
        TimePickerDialogFor(
            initial = value.time ?: currentMinute(),
            onDismiss = { pickTime = false },
            onConfirm = { value = value.copy(time = it); pickTime = false },
        )
    }
}

/** Blocco della finestra: icona e titolo piccoli sopra, contenuto sotto. */
@Composable
private fun DefaultsBlock(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

/** Impostazioni della sveglia: vibrazione, durata del posticipo, volume crescente e suono. */
@Composable
internal fun AlarmSettingsSection(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    SectionHeader(tr("Sveglia"))
    SettingRow(
        icon = Icons.Rounded.Vibration,
        title = tr("Vibrazione"),
        subtitle = tr("La sveglia vibra mentre suona."),
        trailing = { Switch(checked = settings.alarmVibration, onCheckedChange = { value -> update { it.copy(alarmVibration = value) } }) },
    )
    SettingRow(
        icon = Icons.Rounded.GraphicEq,
        title = tr("Volume crescente"),
        subtitle = tr("Parte piano e arriva al massimo in 30 secondi."),
        trailing = { Switch(checked = settings.alarmCrescendo, onCheckedChange = { value -> update { it.copy(alarmCrescendo = value) } }) },
    )
    DropdownField(
        label = tr("Posticipa di"),
        options = listOf(5, 10, 15, 20),
        selected = settings.alarmSnoozeMinutes,
        optionLabel = { trf("%1\$s minuti", it) },
        onSelected = { minutes -> update { it.copy(alarmSnoozeMinutes = minutes) } },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
    DropdownField(
        label = tr("Suono"),
        options = AlarmSound.entries,
        selected = settings.alarmSound,
        optionLabel = {
            when (it) {
                AlarmSound.MAGIC -> tr("Incantesimo di Remindella")
                AlarmSound.SYSTEM -> tr("Suoneria sveglia del telefono")
            }
        },
        onSelected = { sound -> update { it.copy(alarmSound = sound) } },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}
