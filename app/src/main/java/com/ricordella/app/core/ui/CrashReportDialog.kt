package com.ricordella.app.core.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.CrashReport
import com.ricordella.app.core.i18n.tr

/** Dopo un arresto anomalo: chiede se mandare il rapporto allo sviluppatore, mostrando cosa contiene. */
@Composable
fun CrashReportDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashReport.pending(context)) }
    val text = report ?: return
    AlertDialog(
        onDismissRequest = { CrashReport.clear(context); report = null },
        title = { Text(tr("Remindella si è chiusa all'improvviso")) },
        text = {
            Column {
                Text(
                    tr("Vuoi mandare il rapporto dell'errore allo sviluppatore? Si apre la tua app di posta: contiene versione, modello del telefono e l'errore tecnico, non i tuoi promemoria. Qui sotto puoi leggerlo prima di inviarlo."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp).heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (CrashReport.send(context, text)) { CrashReport.clear(context); report = null }
                else Toast.makeText(context, tr("Nessuna app di posta trovata"), Toast.LENGTH_LONG).show()
            }) { Text(tr("Invia")) }
        },
        dismissButton = { TextButton(onClick = { CrashReport.clear(context); report = null }) { Text(tr("Non ora")) } },
    )
}
