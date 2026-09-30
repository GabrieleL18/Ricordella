package com.ricordella.app.feature.settings

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessAlarm
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.NotificationAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.CodeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.ricordella.app.BuildConfig
import com.ricordella.app.core.DeveloperInfo
import com.ricordella.app.core.DeveloperTools
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.data.local.database.RicordellaDatabase
import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.model.ReminderType
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme

private const val TAPS_TO_UNLOCK = 7

/** Riga "Versione": 7 tocchi sbloccano la sezione Sviluppatore (con il conto alla rovescia dal 4° tocco). */
@Composable
fun VersionRow(developerMode: Boolean, onUnlock: () -> Unit) {
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    ListItem(
        headlineContent = { Text(tr("Versione")) },
        supportingContent = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
        leadingContent = { Icon(Icons.Rounded.Code, contentDescription = null) },
        modifier = Modifier.clickable {
            if (developerMode) {
                Toast.makeText(context, tr("Sei già uno sviluppatore"), Toast.LENGTH_SHORT).show()
                return@clickable
            }
            taps++
            val left = TAPS_TO_UNLOCK - taps
            when {
                left <= 0 -> {
                    taps = 0
                    onUnlock()
                    Toast.makeText(context, tr("Ora sei uno sviluppatore! 🧙"), Toast.LENGTH_SHORT).show()
                }
                left <= 3 -> Toast.makeText(context, if (left == 1) tr("Ancora 1 tocco") else trf("Ancora %1\$s tocchi", left), Toast.LENGTH_SHORT).show()
            }
        },
    )
}

/** Comandi per sviluppatori: notifiche di prova, dati di esempio, forzature e informazioni tecniche. */
@Composable
fun DeveloperSection(tools: DeveloperTools, update: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val info by produceState<DeveloperInfo?>(null, refresh) { value = tools.info() }
    fun done(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    fun run(message: String, action: suspend () -> Unit) {
        scope.launch {
            action()
            refresh++
            done(message)
        }
    }

    SectionHeader(tr("Sviluppatore"), icon = Icons.Rounded.Code, tone = MaterialTheme.ricordellaColors.lavender)
    DevRow(Icons.Rounded.NotificationsActive, tr("Invia notifica di prova"), tr("Subito, per vedere l'aspetto della notifica.")) {
        tools.sendTestNotification()
    }
    DevRow(Icons.Rounded.NotificationAdd, tr("Notifica di prova tra 10 secondi"), tr("Chiudi l'app per vederla arrivare in background.")) {
        tools.sendTestNotification(ReminderType.BIRTHDAY, delaySeconds = 10)
        done(tr("Arriva tra 10 secondi"))
    }
    DevRow(Icons.Rounded.DataObject, tr("Crea dati di esempio"), tr("Una persona, un'auto, una vacanza con volo e nave, una visita, un compleanno e un pagamento scaduto.")) {
        run(tr("Dati di esempio creati")) { tools.createDemoData() }
    }
    DevRow(Icons.Rounded.CloudUpload, tr("Mostra l'invito al backup"), tr("Fa comparire subito in Home la richiesta di aggiornare il backup.")) {
        run(tr("Guarda la Home")) { tools.forceBackupReminder() }
    }
    DevRow(Icons.Rounded.CleaningServices, tr("Proponi la pulizia annuale"), tr("Alla prossima apertura della Home.")) {
        run(tr("Guarda la Home")) { tools.forceYearlyCleanup() }
    }
    DevRow(Icons.Rounded.Celebration, tr("Sposta le feste all'anno corrente"), tr("Esegue ora il passaggio che avviene a gennaio.")) {
        run(tr("Feste aggiornate")) { tools.rollHolidays() }
    }
    DevRow(Icons.Rounded.AccessAlarm, tr("Ricalcola le notifiche"), tr("Riprogramma gli allarmi di tutti i promemoria.")) {
        run(tr("Notifiche riprogrammate")) { tools.rescheduleAlarms() }
    }
    DevRow(Icons.Rounded.Widgets, tr("Aggiorna il widget"), tr("Ridisegna subito il widget del calendario.")) {
        tools.refreshWidget()
        done(tr("Widget aggiornato"))
    }
    DevRow(Icons.Rounded.RestartAlt, tr("Rifai la configurazione iniziale"), tr("Mostra di nuovo benvenuto, tutorial e import dal calendario.")) {
        run(tr("Configurazione riavviata")) { tools.restartOnboarding() }
    }
    val current = info
    DevRow(
        Icons.Rounded.Storage,
        tr("Spazio occupato"),
        if (current == null) tr("Calcolo...") else
            trf("Database: %1\$s (versione %2\$s) · ", Formatter.formatShortFileSize(context, current.databaseBytes), RicordellaDatabase.VERSION) +
                trf("Foto: %1\$s file, %2\$s · ", current.mediaFiles, Formatter.formatShortFileSize(context, current.mediaBytes)) +
                trf("Cache: %1\$s", Formatter.formatShortFileSize(context, current.cacheBytes)),
    ) { refresh++ }
    DevRow(Icons.Rounded.DeleteSweep, tr("Svuota la cache"), tr("Backup temporanei, suoni e file di lavoro (si ricreano da soli).")) {
        tools.clearCache()
        refresh++
        done(tr("Cache svuotata"))
    }
    DevRow(Icons.Rounded.CodeOff, tr("Esci dalla modalità sviluppatore"), tr("La sezione sparisce; 7 tocchi sulla versione per riaverla.")) {
        update { it.copy(developerMode = false) }
    }
}

@Composable
private fun DevRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
