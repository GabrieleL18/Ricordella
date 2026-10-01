package com.ricordella.app.feature.settings

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.HappyWizard
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.WizardScene
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.data.backup.BackupSummary
import java.time.Instant
import java.time.ZoneId

/**
 * Backup aperto da fuori (file manager, Drive, allegato email) o scelto nella configurazione
 * iniziale ([firstStart]): mostra cosa contiene e propone subito il ripristino.
 */
@Composable
fun OpenedBackupImport(uri: Uri, onDone: () -> Unit, firstStart: Boolean = false) {
    val viewModel = appViewModel { c, _ ->
        SettingsViewModel(c.settingsRepository, c.backupRepository, c.restoreBackup, c.deleteAllData, c.reminderScheduler, c.housekeeping)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uri) { viewModel.readBackup(uri) }
    val summary = state.restoreSummary
    val message = state.message
    when {
        summary != null -> RestoreDialog(summary, onConfirm = viewModel::confirmRestore, onCancel = { viewModel.cancelRestore(); onDone() }, replacing = !firstStart)
        message != null -> MagicDialog(onDismiss = { viewModel.onMessageShown(); onDone() }) {
            HappyWizard(size = 110.dp)
            Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            PushButton(tr("Ok"), onClick = { viewModel.onMessageShown(); onDone() }, modifier = Modifier.fillMaxWidth())
        }
        state.isBusy -> MagicDialog(onDismiss = {}) {
            HappyWizard(size = 110.dp, scene = WizardScene.CONJURING)
            Text(tr("Un attimo di magia…"), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

/** Riepilogo del backup e conferma del ripristino, con la grafica dell'app. */
@Composable
internal fun RestoreDialog(summary: BackupSummary, onConfirm: () -> Unit, onCancel: () -> Unit, replacing: Boolean = true) {
    val colors = MaterialTheme.ricordellaColors
    val settings = LocalAppSettings.current
    val date = runCatching { DateTexts.date(Instant.parse(summary.createdAt).atZone(ZoneId.systemDefault()).toLocalDate(), settings.dateFormat) }
        .getOrDefault(summary.createdAt.take(10))
    MagicDialog(onDismiss = onCancel) {
        HappyWizard(size = 110.dp, scene = WizardScene.CONJURING)
        Text(tr("Ripristinare il backup?"), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(trf("Backup del %1\$s", date), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Count(summary.reminders, tr("promemoria"), colors.pear)
            Count(summary.people, tr("persone"), colors.lavender)
            Count(summary.items, tr("cose"), colors.cyan)
            Count(summary.maintenance, tr("manutenzioni"), colors.mint)
            Count(summary.files, tr("file allegati"), colors.coral)
        }
        val tone = if (replacing) colors.coral else colors.mint
        Text(
            if (replacing) tr("Attenzione: tutti i dati attuali verranno sostituiti da quelli del backup.")
            else tr("Ritrovi tutto com'era: promemoria, cose, persone, foto e impostazioni."),
            style = MaterialTheme.typography.bodyMedium,
            color = tone.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(tone.container, MaterialTheme.shapes.large).padding(RicordellaDimensions.spaceM),
        )
        PushButton(
            if (replacing) tr("Ripristina sostituendo i dati") else tr("Ripristina"),
            onClick = onConfirm,
            icon = Icons.Rounded.Restore,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onCancel) { Text(tr("Annulla")) }
    }
}

@Composable
private fun Count(value: Int, label: String, tone: Tone) {
    Column(
        Modifier.background(tone.container, MaterialTheme.shapes.medium).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", style = MaterialTheme.typography.titleLarge, color = tone.content)
        Text(label, style = MaterialTheme.typography.labelMedium, color = tone.content)
    }
}

/** Finestra arrotondata con lo sfondo dell'app, come le proposte di inizio anno. */
@Composable
private fun MagicDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.widthIn(max = 460.dp)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(RicordellaDimensions.spaceXl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
            ) { content() }
        }
    }
}
