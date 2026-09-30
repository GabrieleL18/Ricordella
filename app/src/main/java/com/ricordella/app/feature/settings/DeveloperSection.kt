package com.ricordella.app.feature.settings

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.WbSunny
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import com.ricordella.app.core.ui.ConfirmDialog

private const val TAPS_TO_UNLOCK = 7

/** Un solo messaggio alla volta: il nuovo sostituisce subito il precedente invece di accodarsi. */
private var lastToast: Toast? = null

private fun toast(context: android.content.Context, text: String) {
    lastToast?.cancel()
    lastToast = Toast.makeText(context, text, Toast.LENGTH_SHORT).also { it.show() }
}

/**
 * Riga "Versione": 7 tocchi sbloccano la sezione Sviluppatore. Dal 4° tocco compare il conto
 * alla rovescia; ogni messaggio sostituisce il precedente, così la risposta è immediata.
 */
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
                toast(context, tr("Sei già uno sviluppatore: trovi la sezione qui sotto"))
                return@clickable
            }
            taps++
            val left = TAPS_TO_UNLOCK - taps
            when {
                left <= 0 -> {
                    taps = 0
                    onUnlock()
                    toast(context, tr("Ora sei uno sviluppatore! 🧙"))
                }
                left <= 3 -> toast(context, if (left == 1) tr("Ancora 1 tocco") else trf("Ancora %1\$s tocchi", left))
            }
        },
    )
}

/** Comandi per sviluppatori: notifiche di prova, dati di esempio, simulazioni e informazioni tecniche. */
@Composable
fun DeveloperSection(tools: DeveloperTools, update: ((AppSettings) -> AppSettings) -> Unit, justUnlocked: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val info by produceState<DeveloperInfo?>(null, refresh) { value = tools.info() }
    // La sezione compare in fondo alle Impostazioni: appena sbloccata la si porta in vista.
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(justUnlocked) { if (justUnlocked) requester.bringIntoView() }
    fun run(message: String, action: suspend () -> Unit) {
        scope.launch {
            action()
            refresh++
            toast(context, message)
        }
    }

    Column(Modifier.bringIntoViewRequester(requester)) {
        SectionHeader(tr("Sviluppatore"), icon = Icons.Rounded.Code, tone = MaterialTheme.ricordellaColors.lavender)
    }
    DevRow(Icons.Rounded.NotificationsActive, tr("Invia notifica di prova"), tr("Subito, per vedere l'aspetto della notifica.")) {
        tools.sendTestNotification()
    }
    DevRow(Icons.Rounded.NotificationAdd, tr("Notifica di prova tra 10 secondi"), tr("Chiudi l'app per vederla arrivare in background.")) {
        tools.sendTestNotification(ReminderType.BIRTHDAY, delaySeconds = 10)
        toast(context, tr("Arriva tra 10 secondi"))
    }
    DevRow(Icons.Rounded.WbSunny, tr("Sveglia di giorno tra 10 secondi"), tr("Il maghetto e l'orso che dorme. Blocca lo schermo per vederla a tutto schermo.")) {
        tools.ringTestAlarm(night = false)
        toast(context, tr("Suona tra 10 secondi"))
    }
    DevRow(Icons.Rounded.Bedtime, tr("Sveglia di notte tra 10 secondi"), tr("Il maghetto sotto le stelle. Blocca lo schermo per vederla a tutto schermo.")) {
        tools.ringTestAlarm(night = true)
        toast(context, tr("Suona tra 10 secondi"))
    }
    DevRow(
        Icons.Rounded.DataObject,
        tr("Crea dati di esempio"),
        tr("Una persona, un'auto, una vacanza con volo e nave, una visita, un compleanno, un pagamento scaduto e, dell'anno scorso, due feste e una cena."),
        confirm = tr("Aggiungo ai tuoi dati alcuni promemoria, una persona e un'auto di esempio. Potrai eliminarli quando vuoi."),
    ) { run(tr("Dati di esempio creati")) { tools.createDemoData() } }
    DevRow(
        Icons.Rounded.Celebration,
        tr("Simula l'inizio di un anno nuovo"),
        tr("Fa comparire in Home le proposte di spostare le feste e di fare la pulizia annuale, con la loro animazione."),
        confirm = tr("Faccio come se fosse il primo avvio dell'anno: tornando alla Home ti verranno proposte le operazioni di inizio anno."),
    ) {
        scope.launch {
            val (holidays, cleanup) = tools.simulateNewYear()
            toast(
                context,
                if (holidays == 0 && cleanup == 0) tr("Niente da proporre: non ci sono feste né promemoria degli anni passati. Crea prima i dati di esempio.")
                else trf("Torna alla Home: %1\$s feste da spostare, %2\$s promemoria da pulire", holidays, cleanup),
            )
        }
    }
    DevRow(
        Icons.Rounded.CloudUpload,
        tr("Mostra l'invito al backup"),
        tr("Fa comparire subito in Home la richiesta di aggiornare il backup."),
        confirm = tr("Faccio come se fosse scaduto l'intervallo del backup."),
    ) { run(tr("Guarda la Home")) { tools.forceBackupReminder() } }
    DevRow(
        Icons.Rounded.AccessAlarm,
        tr("Ricalcola le notifiche"),
        tr("Riprogramma gli allarmi di tutti i promemoria."),
        confirm = tr("Riprogrammo le notifiche di tutti i promemoria. Quelle già passate non vengono ripetute."),
    ) { run(tr("Notifiche riprogrammate")) { tools.rescheduleAlarms() } }
    DevRow(Icons.Rounded.Widgets, tr("Aggiorna il widget"), tr("Ridisegna subito il widget del calendario.")) {
        tools.refreshWidget()
        toast(context, tr("Widget aggiornato"))
    }
    DevRow(
        Icons.Rounded.RestartAlt,
        tr("Rifai la configurazione iniziale"),
        tr("Mostra di nuovo benvenuto, tutorial e import dal calendario."),
        confirm = tr("Ti mostro di nuovo la configurazione iniziale. I tuoi dati restano."),
    ) { run(tr("Configurazione riavviata")) { tools.restartOnboarding() } }
    val current = info
    DevRow(
        Icons.Rounded.Storage,
        tr("Spazio occupato"),
        if (current == null) tr("Calcolo...") else
            trf("Database: %1\$s (versione %2\$s) · ", Formatter.formatShortFileSize(context, current.databaseBytes), RicordellaDatabase.VERSION) +
                trf("Foto: %1\$s file, %2\$s · ", current.mediaFiles, Formatter.formatShortFileSize(context, current.mediaBytes)) +
                trf("Cache: %1\$s", Formatter.formatShortFileSize(context, current.cacheBytes)),
    ) { refresh++ }
    DevRow(
        Icons.Rounded.DeleteSweep,
        tr("Svuota la cache"),
        tr("Backup temporanei, suoni e file di lavoro (si ricreano da soli)."),
        confirm = tr("Svuoto la cache. Non tocca i tuoi dati, le foto né i backup salvati."),
    ) {
        tools.clearCache()
        refresh++
        toast(context, tr("Cache svuotata"))
    }
    DevRow(
        Icons.Rounded.CodeOff,
        tr("Esci dalla modalità sviluppatore"),
        tr("La sezione sparisce; 7 tocchi sulla versione per riaverla."),
        confirm = tr("La sezione Sviluppatore sparirà. Per riaverla tocca 7 volte la versione."),
    ) { update { it.copy(developerMode = false) } }
}

/** Riga di comando; se [confirm] è presente, prima di eseguire chiede "Sei sicuro?". */
@Composable
private fun DevRow(icon: ImageVector, title: String, subtitle: String, confirm: String? = null, onClick: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable { if (confirm != null) asking = true else onClick() },
    )
    if (asking && confirm != null) {
        ConfirmDialog(
            title = tr("Sei sicuro?"),
            message = confirm,
            confirmLabel = tr("Procedi"),
            onConfirm = onClick,
            onDismiss = { asking = false },
        )
    }
}
