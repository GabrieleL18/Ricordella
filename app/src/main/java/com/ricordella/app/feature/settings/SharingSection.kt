package com.ricordella.app.feature.settings

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import com.ricordella.app.core.ui.WizardAvatar
import com.ricordella.app.core.ui.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.TouchApp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.ConfirmDialog
import com.ricordella.app.core.ui.MiniTutorial
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.SharingTutorialPages
import com.ricordella.app.core.ui.TutorialDialog
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.data.share.SharedSpace
import com.ricordella.app.domain.model.AppSettings
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Condivisione con un'altra persona tramite un file su Google Drive: creare il file,
 * aprire quello ricevuto, sincronizzare a mano e smettere di condividere. Con tutorial animato.
 */
@Composable
fun SharingSection(settings: AppSettings, space: SharedSpace) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.ricordella.app.RicordellaApplication).container
    val status by space.status.collectAsStateWithLifecycle()
    val activity by space.activity.collectAsStateWithLifecycle()
    var help by rememberSaveable { mutableStateOf(false) }
    var created by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var choosingMe by rememberSaveable { mutableStateOf(false) }
    // Prima di creare o aprire il file si sceglie come sincronizzare: "create" o "join".
    var choosingMode by rememberSaveable { mutableStateOf<String?>(null) }
    var automatic by rememberSaveable { mutableStateOf(true) }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { scope.launch { if (space.create(it, automatic)) created = true } }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch { space.join(it, automatic) } }
    }
    val colors = MaterialTheme.ricordellaColors
    val shared = settings.sharedFileUri != null
    fun update(transform: (AppSettings) -> AppSettings) = scope.launch { container.settingsRepository.update(transform) }

    SectionHeader(tr("Condivisione"), icon = Icons.Rounded.CloudSync, tone = colors.cyan) {
        TextButton(onClick = { help = true }) {
            Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(tr("Come funziona"), modifier = Modifier.padding(start = 4.dp))
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.cyan.container, MaterialTheme.shapes.large)
            .padding(RicordellaDimensions.spaceL),
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
    ) {
        WizardFriends(Modifier.align(Alignment.CenterHorizontally))
        if (!shared) {
            Text(
                tr("Condividi promemoria, persone, cose e spese con un'altra persona (partner, famiglia) tramite un file su Google Drive."),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.cyan.content,
            )
            PushButton(
                tr("Crea il file condiviso"),
                onClick = { choosingMode = "create" },
                icon = Icons.Rounded.NoteAdd,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { choosingMode = "join" }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(tr("Apri il file condiviso con me"), modifier = Modifier.padding(start = 6.dp))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                Box(Modifier.size(36.dp).background(colors.cyan.solid, CircleShape), contentAlignment = Alignment.Center) {
                    if (status is SharedSpace.Status.Syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.surfaceContainerLowest)
                    else Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(tr("Condivisione attiva"), style = MaterialTheme.typography.titleMedium, color = colors.cyan.content)
                    Text(
                        when (val current = status) {
                            SharedSpace.Status.Syncing -> tr("Sincronizzo…")
                            is SharedSpace.Status.Failed -> tr("Ultimo tentativo non riuscito: il file non è raggiungibile (sei offline o è stato spostato). Le tue modifiche restano qui e partono appena possibile.")
                            else -> settings.sharedLastSync?.let { millis ->
                                val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                                trf("Ultimo aggiornamento: %1\$s", DateTexts.relativeWithTime(local.toLocalDate(), local.toLocalTime().withSecond(0).withNano(0), LocalDateTime.now().toLocalDate()))
                            } ?: tr("Mai sincronizzato")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.cyan.content,
                    )
                }
            }
            // Modalità: automatica o manuale, si cambia quando si vuole.
            SyncModeTiles(settings.sharedAutoSync, onChange = { value -> update { it.copy(sharedAutoSync = value) } })
            // Chi sono io: le mie cose vanno da sole, quelle degli altri chiedono conferma.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
                    .clickable { choosingMe = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
            ) {
                val me = settings.sharedMeName
                if (me != null) WizardAvatar(seed = me, size = 40.dp)
                else Box(Modifier.size(40.dp).background(colors.cyan.solid, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (me != null) trf("Io sono %1\$s", me) else tr("Chi sei tu?"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        tr("Le tue cose si sincronizzano da sole. Se spunti o modifichi quelle di un altro ti chiedo conferma e glielo faccio sapere."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (activity.isNotEmpty()) {
                Text(tr("Ultime attività"), style = MaterialTheme.typography.titleSmall, color = colors.cyan.content, modifier = Modifier.padding(top = 4.dp))
                activity.take(5).forEach { entry -> ActivityRow(entry) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                PushButton(
                    if (status is SharedSpace.Status.Syncing) tr("Sincronizzo…") else tr("Sincronizza ora"),
                    onClick = {
                        if (status is SharedSpace.Status.Syncing) return@PushButton
                        scope.launch {
                            val ok = space.sync()
                            Toast.makeText(context, if (ok) tr("Sincronizzato ✓") else tr("Sincronizzazione non riuscita"), Toast.LENGTH_SHORT).show()
                        }
                    },
                    icon = Icons.Rounded.Sync,
                )
                TextButton(onClick = { confirmLeave = true }) {
                    Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(tr("Smetti"), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }

    if (help) TutorialDialog(SharingTutorialPages, onDismiss = { help = false })
    choosingMode?.let { mode ->
        SyncModeDialog(
            initial = automatic,
            onDismiss = { choosingMode = null },
            onConfirm = { chosen ->
                automatic = chosen
                choosingMode = null
                if (mode == "create") createFile.launch("remindella-condiviso.rmd") else openFile.launch(arrayOf("*/*"))
            },
        )
    }
    if (choosingMe) {
        val people by container.personRepository.observePeople(archived = false).collectAsStateWithLifecycle(initialValue = emptyList())
        MePickerDialog(
            people = people,
            selectedId = settings.sharedMeId,
            onPick = { person ->
                choosingMe = false
                update { it.copy(sharedMeId = person?.id, sharedMeName = person?.name) }
            },
            onDismiss = { choosingMe = false },
        )
    }
    if (created) {
        AlertDialog(
            onDismissRequest = { created = false },
            title = { Text(tr("File creato! Ora condividilo")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                    MiniTutorial(SharingTutorialPages.drop(1).take(1))
                    Text(tr("Apri Google Drive, trova «remindella-condiviso.rmd», tocca ⋮ › Condividi e aggiungi l'altra persona come Editor. Se lei non lo vede nel selettore, in Drive può usare «Aggiungi scorciatoia a Il mio Drive»."))
                }
            },
            confirmButton = { TextButton(onClick = { created = false }) { Text(tr("Ho capito")) } },
        )
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = tr("Smettere di condividere?"),
            message = tr("I dati restano su questo telefono e nel file, ma non si aggiorneranno più. Potrai riaprire il file quando vuoi."),
            confirmLabel = tr("Smetti"),
            onConfirm = { scope.launch { space.leave() } },
            onDismiss = { confirmLeave = false },
        )
    }
}

/** Le due modalità come riquadri da scegliere, con la spiegazione delle differenze. */
@Composable
private fun SyncModeTiles(automatic: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    Column(verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
        ModeTile(
            icon = Icons.Rounded.Sync,
            title = tr("Automatica"),
            body = tr("Si aggiorna da sola: quando apri e chiudi l'app, pochi secondi dopo ogni modifica, ogni due minuti mentre l'app è aperta e ogni mezz'ora in background."),
            tone = colors.cyan,
            selected = automatic,
            onClick = { onChange(true) },
        )
        ModeTile(
            icon = Icons.Rounded.TouchApp,
            title = tr("Manuale"),
            body = tr("Si aggiorna solo quando tocchi «Sincronizza ora»: decidi tu quando mandare e ricevere le modifiche."),
            tone = colors.lavender,
            selected = !automatic,
            onClick = { onChange(false) },
        )
    }
}

@Composable
private fun ModeTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, tone: com.ricordella.app.core.ui.theme.Tone, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) tone.container else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
            .border(2.dp, if (selected) tone.solid else androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Box(Modifier.size(36.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (selected) tone.content else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = tr("Scelta"), tint = tone.solid, modifier = Modifier.size(20.dp))
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = if (selected) tone.content else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Prima di creare o aprire il file: automatica o manuale, e in entrambi i casi nessuna modifica va persa. */
@Composable
private fun SyncModeDialog(initial: Boolean, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var automatic by rememberSaveable { mutableStateOf(initial) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.widthIn(max = 500.dp)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(RicordellaDimensions.spaceXl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
            ) {
                WizardFriends()
                Text(tr("Come vuoi sincronizzare?"), style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                SyncModeTiles(automatic, onChange = { automatic = it })
                Text(
                    tr("In tutti e due i casi, se avete modificato entrambi (anche senza Internet) unisco le modifiche: niente viene sovrascritto o perso. Puoi cambiare modalità quando vuoi nelle Impostazioni."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(tr("Annulla")) }
                    PushButton(tr("Continua"), onClick = { onConfirm(automatic) })
                }
            }
        }
    }
}

/** "Chi sono io": l'elenco delle persone con il loro maghetto, più "nessuno". */
@Composable
private fun MePickerDialog(people: List<com.ricordella.app.domain.model.Person>, selectedId: String?, onPick: (com.ricordella.app.domain.model.Person?) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.widthIn(max = 460.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(RicordellaDimensions.spaceXl), verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                Text(tr("Chi sei tu?"), style = MaterialTheme.typography.headlineSmall)
                Text(
                    tr("Scegliti tra le persone. Promemoria e cose collegati solo ad altri saranno «loro»: prima di toccarli ti chiedo conferma."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (people.isEmpty()) {
                    Text(tr("Non hai ancora aggiunto persone: aggiungi te stesso nella sezione Persone."), style = MaterialTheme.typography.bodyMedium)
                }
                people.forEach { person ->
                    val selected = person.id == selectedId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (selected) colors.bolt else MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                            .clickable { onPick(person) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
                    ) {
                        WizardAvatar(seed = person.name, size = 40.dp)
                        Text(person.name, style = MaterialTheme.typography.titleMedium, color = if (selected) colors.onBolt else MaterialTheme.colorScheme.onSurface)
                    }
                }
                TextButton(onClick = { onPick(null) }, modifier = Modifier.align(Alignment.End)) { Text(tr("Non impostare")) }
            }
        }
    }
}

/** Una riga delle ultime attività: chi, cosa, di chi. */
@Composable
private fun ActivityRow(entry: com.ricordella.app.data.share.ShareActivity) {
    val text = when (entry.action) {
        com.ricordella.app.data.share.ShareAction.COMPLETED -> trf("%1\$s ha segnato come fatto «%2\$s» di %3\$s", entry.by, entry.title, entry.owner)
        com.ricordella.app.data.share.ShareAction.REOPENED -> trf("%1\$s ha riaperto «%2\$s» di %3\$s", entry.by, entry.title, entry.owner)
        com.ricordella.app.data.share.ShareAction.EDITED -> trf("%1\$s ha modificato «%2\$s» di %3\$s", entry.by, entry.title, entry.owner)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
        WizardAvatar(seed = entry.by, size = 28.dp)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.ricordellaColors.cyan.content, modifier = Modifier.weight(1f))
    }
}

/** Maghetti felici, ognuno del suo colore, che saltellano insieme: la condivisione è una cosa di gruppo. */
@Composable
private fun WizardFriends(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val t = if (reduced) 0f else rememberInfiniteTransition(label = "friends").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "friendsT",
    ).value
    val friends = listOf("Giulia" to 56.dp, "Marco" to 68.dp, "Sofia" to 60.dp, "Nonna Rosa" to 52.dp)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy((-10).dp), verticalAlignment = Alignment.Bottom) {
        friends.forEachIndexed { index, (seed, size) ->
            // Saltelli sfalsati: uno dopo l'altro, come una piccola ola.
            val hop = abs(sin((t + index * 0.22f) * PI.toFloat()))
            WizardAvatar(
                seed = seed,
                size = size,
                toneIndex = index + 1, // un colore diverso per ognuno, sul fondo ciano
                modifier = Modifier.graphicsLayer {
                    translationY = -hop * 10.dp.toPx()
                    rotationZ = (hop - 0.5f) * if (index % 2 == 0) 6f else -6f
                },
            )
        }
    }
}
