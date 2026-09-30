package com.ricordella.app.feature.settings

import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.data.backup.BackupSummary

/**
 * Backup aperto da fuori (file manager, Drive, allegato email): mostra cosa contiene e propone
 * subito il ripristino, anche prima della configurazione iniziale.
 */
@Composable
fun OpenedBackupImport(uri: Uri, onDone: () -> Unit) {
    val viewModel = appViewModel { c, _ ->
        SettingsViewModel(c.settingsRepository, c.backupRepository, c.restoreBackup, c.deleteAllData, c.reminderScheduler, c.housekeeping)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(uri) { viewModel.readBackup(uri) }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.onMessageShown()
            onDone()
        }
    }
    state.restoreSummary?.let { summary ->
        RestoreDialog(summary, onConfirm = viewModel::confirmRestore, onCancel = { viewModel.cancelRestore(); onDone() })
    }
}

@Composable
internal fun RestoreDialog(summary: BackupSummary, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(tr("Ripristinare il backup?")) },
        text = {
            Text(
                trf("Backup del %1\$s\n\n", summary.createdAt.take(10)) +
                    trf("• %1\$s persone\n• %2\$s cose\n• %3\$s promemoria\n", summary.people, summary.items, summary.reminders) +
                    trf("• %1\$s manutenzioni\n• %2\$s file allegati\n\n", summary.maintenance, summary.files) +
                    tr("Attenzione: tutti i dati attuali verranno sostituiti da quelli del backup."),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(tr("Ripristina sostituendo i dati"), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(tr("Annulla")) } },
    )
}
