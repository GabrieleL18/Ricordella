package com.ricordella.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.ui.defaultTone
import com.ricordella.app.domain.model.AppSection
import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.model.AppointmentMessage
import com.ricordella.app.domain.model.CalendarGroup
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

internal val CalendarPalette = listOf(
    0xFF1E88E5, 0xFF00897B, 0xFF43A047, 0xFFC0CA33, 0xFFFB8C00,
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF6D4C41, 0xFF546E7A,
)

private val CalendarGroup.label: String
    get() = when (this) {
        CalendarGroup.TASKS -> tr("Attività")
        CalendarGroup.EVENTS -> tr("Eventi")
        CalendarGroup.DEADLINES -> tr("Scadenze")
        CalendarGroup.PAYMENTS -> tr("Pagamenti")
    }

/** Un colore per ogni gruppo della legenda del calendario; il primo pallino è quello dell'app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CalendarColorsSection(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    Text(tr("Colori del calendario"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
    CalendarGroup.entries.forEach { group ->
        val chosen = settings.calendarColors[group]
        Text(group.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Swatch(group.defaultTone.solid, chosen == null, tr("Predefinito")) {
                update { it.copy(calendarColors = it.calendarColors - group) }
            }
            CalendarPalette.forEach { argb ->
                Swatch(Color(argb), chosen == argb, group.label) {
                    update { it.copy(calendarColors = it.calendarColors + (group to argb)) }
                }
            }
        }
    }
}

@Composable
internal fun Swatch(color: Color, selected: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .background(color, CircleShape)
            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            .semantics {
                contentDescription = description
                this.selected = selected
            }
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = if (color.luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(18.dp))
    }
}

/** Testo che si manda ai clienti per ricordare l'appuntamento, con i dati da inserire. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppointmentMessageSection(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    var text by rememberSaveable { mutableStateOf(settings.appointmentTemplate ?: tr(AppointmentMessage.DEFAULT_TEMPLATE)) }
    // Si salva poco dopo aver smesso di scrivere, non a ogni lettera.
    LaunchedEffect(text) {
        delay(500)
        update { it.copy(appointmentTemplate = text.takeIf { t -> t.isNotBlank() && t != tr(AppointmentMessage.DEFAULT_TEMPLATE) }) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            tr("Il messaggio che mandi ai clienti dall'evento. Data e ora ci sono sempre: se le togli, vengono aggiunte in fondo."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(tr("Messaggio")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("{nome}", "{titolo}", "{data}", "{ora}").forEach { tag ->
                AssistChip(onClick = { text = text.trimEnd() + " " + tag }, label = { Text(tag) })
            }
        }
        Text(tr("Anteprima"), style = MaterialTheme.typography.labelLarge)
        Text(
            AppointmentMessage.fill(text, tr("Mario"), tr("Visita"), DateTexts.fullDate(LocalDate.now().plusDays(1)), DateTexts.time(LocalTime.of(10, 30))),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private val AppSection.label: String
    get() = when (this) {
        AppSection.ALARMS -> tr("Sveglie")
        AppSection.TIMERS -> tr("Timer")
        AppSection.POTIONS -> tr("Pozioni")
        AppSection.VACATIONS -> tr("Vacanza")
        AppSection.MEDICAL_VISITS -> tr("Visita medica")
        AppSection.EXPENSES -> tr("Spesa")
        AppSection.CYCLE -> tr("Ciclo")
        AppSection.APPOINTMENTS -> tr("Appuntamento con clienti")
    }

/** Sezioni facoltative da mostrare: spente spariscono da "Aggiungi", dalla Home e dai moduli (i dati restano). */
@Composable
internal fun SectionsSettings(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    Text(
        tr("Promemoria, eventi, note, cose e persone ci sono sempre. Spegnendo una sezione i suoi dati restano e la ritrovi riaccendendola."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    AppSection.entries.forEach { section ->
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(section.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(
                checked = settings.shows(section),
                onCheckedChange = { on -> update { it.copy(hiddenSections = if (on) it.hiddenSections - section else it.hiddenSections + section) } },
            )
        }
    }
}
