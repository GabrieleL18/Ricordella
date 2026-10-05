package com.ricordella.app.feature.alarms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.ui.DateField
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.TimePickerDialogFor
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.date.nextAlarmDate
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderDraft
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.model.displayName
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Creazione e modifica di una sveglia: solo ora, giorni, nome e per chi suona. Niente date né tipi di promemoria.
 * Salvarla la accende.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmEditScreen(id: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as RicordellaApplication).container }
    val scope = rememberCoroutineScope()
    val people by remember { container.personRepository.observePeople(archived = false) }.collectAsStateWithLifecycle(initialValue = emptyList())

    var loaded by remember { mutableStateOf(id == null) }
    var existing by remember { mutableStateOf<Reminder?>(null) }
    var ruleId by remember { mutableStateOf<String?>(null) }
    var time by remember { mutableStateOf(LocalTime.of(7, 0)) }
    var days by remember { mutableStateOf(emptySet<DayOfWeek>()) }
    var label by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(emptySet<String>()) }
    var pausedUntil by remember { mutableStateOf<LocalDate?>(null) }
    var pickTime by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != null) {
            container.reminderRepository.getReminder(id)?.let { entry ->
                existing = entry.reminder
                ruleId = entry.recurrenceRule?.id
                time = entry.reminder.dueTime ?: time
                days = entry.recurrenceRule?.let { r -> if (r.frequency == RecurrenceFrequency.DAILY) DayOfWeek.entries.toSet() else r.daysOfWeek }.orEmpty()
                label = entry.reminder.title
                chosen = entry.people.mapTo(mutableSetOf()) { it.id }
                pausedUntil = entry.reminder.pausedUntil?.takeIf { it.isAfter(container.time.today()) }
            }
        } else {
            // Una sveglia nuova è di chi sono io, se lo so.
            container.settingsRepository.current().sharedMeId?.let { chosen = setOf(it) }
        }
        loaded = true
    }

    fun save() {
        val who = chosen
        if (who.isEmpty()) { showError = true; return }
        saving = true
        scope.launch {
            val now = container.time.now()
            val date = nextAlarmDate(time, days, container.time.localNow(), pausedUntil)
            val rule = when {
                days.isEmpty() -> null
                else -> RecurrenceRule(
                    id = ruleId ?: com.ricordella.app.domain.model.newId(),
                    frequency = if (days.size == 7) RecurrenceFrequency.DAILY else RecurrenceFrequency.WEEKLY,
                    startDate = date,
                    daysOfWeek = if (days.size == 7) emptySet() else days,
                )
            }
            val base = existing ?: Reminder(title = "", type = ReminderType.ALARM, dueDate = date, createdAt = now, updatedAt = now)
            val alarm = base.copy(
                title = label.trim().ifEmpty { tr("Sveglia") },
                type = ReminderType.ALARM,
                dueDate = date,
                dueTime = time,
                pausedUntil = pausedUntil.takeIf { days.isNotEmpty() },
                notificationsEnabled = true,
                notifyOffsetMinutes = 0,
            )
            container.saveReminder(ReminderDraft(alarm, rule, who, emptySet()))
            onBack()
        }
    }

    DetailScaffold(title = if (id == null) tr("Nuova sveglia") else tr("Sveglia"), onBack = onBack) { padding ->
        if (!loaded) return@DetailScaffold
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .contentWidth()
                .padding(horizontal = RicordellaDimensions.screenPadding)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            val coral = MaterialTheme.ricordellaColors.coral
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(coral.container)
                    .clickable { pickTime = true }
                    .padding(vertical = RicordellaDimensions.spaceXl),
                contentAlignment = Alignment.Center,
            ) { AlarmClockText(time, coral.content, size = 72) }
            Text(
                com.ricordella.app.core.i18n.trf("Suona %1\$s", untilText(java.time.Duration.between(container.time.localNow(), nextAlarmDate(time, days, container.time.localNow(), pausedUntil).atTime(time)))),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(tr("Nome (facoltativo)")) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(tr("Si ripete"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in days,
                        onClick = { days = if (day in days) days - day else days + day },
                        label = { Text(DateTexts.weekdayFull(day).take(3)) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { days = DayOfWeek.entries.take(5).toSet() }, label = { Text(tr("Lun–Ven")) })
                AssistChip(onClick = { days = DayOfWeek.entries.toSet() }, label = { Text(tr("Ogni giorno")) })
                AssistChip(onClick = { days = emptySet() }, label = { Text(tr("Una volta sola")) })
            }
            if (days.isNotEmpty()) {
                DateField(
                    label = tr("Sospendi fino al (opzionale)"),
                    value = pausedUntil,
                    onValueChange = { pausedUntil = it },
                    clearable = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(tr("Per chi suona *"), style = MaterialTheme.typography.labelLarge)
            if (people.isEmpty()) {
                Text(tr("Non hai ancora aggiunto persone: aggiungile dalla sezione Persone."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                people.forEach { person ->
                    val on = person.id in chosen
                    FilterChip(selected = on, onClick = { chosen = if (on) chosen - person.id else chosen + person.id }, label = { Text(person.displayName) })
                }
            }
            if (showError && chosen.isEmpty()) {
                Text(tr("Scegli almeno una persona."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            PushButton(text = tr("Salva"), icon = Icons.Rounded.Check, onClick = { if (!saving) save() }, modifier = Modifier.fillMaxWidth())
        }
    }

    if (pickTime) TimePickerDialogFor(initial = time, onDismiss = { pickTime = false }, onConfirm = { time = it; pickTime = false })
}
