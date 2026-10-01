package com.ricordella.app.feature.settings

import java.time.LocalTime
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.ui.DropdownField
import com.ricordella.app.core.ui.TutorialDialog
import com.ricordella.app.core.ui.YearlyTask
import com.ricordella.app.core.ui.tutorialPage
import com.ricordella.app.core.ui.BackupTutorialPages
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.model.AutoMode
import com.ricordella.app.domain.usecase.Housekeeping
import java.time.LocalDate

val AutoMode.label: String
    get() = when (this) {
        AutoMode.AUTOMATIC -> tr("Automatico")
        AutoMode.ASK -> tr("Chiedimi")
        AutoMode.OFF -> tr("Mai")
    }

/**
 * Scelte sulle operazioni periodiche: feste all'anno nuovo, pulizia annuale e (se [includeBackup])
 * ogni quanto ricordare il backup. Ogni voce ha un "?" che apre il tutorial animato di cosa succede.
 */
@Composable
fun AutomationSettings(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, includeBackup: Boolean) {
    var help by rememberSaveable { mutableStateOf<String?>(null) }
    val year = LocalDate.now().year
    Column(verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM)) {
        ModeRow(
            title = tr("Feste all'anno nuovo"),
            subtitle = tr("A gennaio sposta all'anno nuovo le feste importate da Google (Pasqua ricalcolata)."),
            selected = settings.holidayMode,
            onHelp = { help = YearlyTask.HOLIDAYS.name },
            onSelected = { mode -> update { it.copy(holidayMode = mode) } },
        )
        ModeRow(
            title = tr("Pulizia annuale"),
            subtitle = tr("A gennaio elimina i promemoria più vecchi di un anno non importanti o senza persone e cose: l'ultimo anno resta sempre."),
            selected = settings.cleanupMode,
            onHelp = { help = YearlyTask.CLEANUP.name },
            onSelected = { mode -> update { it.copy(cleanupMode = mode) } },
        )
        if (includeBackup) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Valori negativi = ore (-3 = ogni 3 ore), positivi = giorni: un solo menu per entrambi.
                DropdownField(
                    label = tr("Ogni quanto aggiornare il backup"),
                    options = Housekeeping.BACKUP_HOURS.map { -it } + Housekeeping.BACKUP_INTERVALS,
                    selected = if (settings.backupEveryHours > 0) -settings.backupEveryHours else settings.backupIntervalDays,
                    optionLabel = { if (it < 0) Housekeeping.hoursLabel(-it) else Housekeeping.intervalLabel(it) },
                    onSelected = { value ->
                        update {
                            // Ogni tot ore ha senso solo in automatico: lo si accende se era spento.
                            if (value < 0) it.copy(backupEveryHours = -value, autoBackupTime = it.autoBackupTime ?: LocalTime.of(22, 0))
                            else it.copy(backupEveryHours = 0, backupIntervalDays = value)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { help = "BACKUP" }) { Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = tr("Come funziona")) }
            }
        }
    }
    when (help) {
        null -> Unit
        "BACKUP" -> TutorialDialog(BackupTutorialPages, onDismiss = { help = null })
        else -> TutorialDialog(listOf(YearlyTask.valueOf(help!!).tutorialPage(year)), onDismiss = { help = null })
    }
}

@Composable
private fun ModeRow(title: String, subtitle: String, selected: AutoMode, onHelp: () -> Unit, onSelected: (AutoMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onHelp) { Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = tr("Come funziona")) }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AutoMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, AutoMode.entries.size),
                ) { Text(mode.label, maxLines = 1) }
            }
        }
        if (selected == AutoMode.AUTOMATIC) {
            Text(
                tr("Lo faccio da sola e ti avviso con un messaggio."),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
