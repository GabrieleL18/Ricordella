package com.ricordella.app.feature.settings

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
    val status by space.status.collectAsStateWithLifecycle()
    var help by rememberSaveable { mutableStateOf(false) }
    var created by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { scope.launch { if (space.create(it)) created = true } }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch { space.join(it) } }
    }
    val colors = MaterialTheme.ricordellaColors
    val shared = settings.sharedFileUri != null

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
                onClick = { createFile.launch("remindella-condiviso.rmd") },
                icon = Icons.Rounded.NoteAdd,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { openFile.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
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
                            is SharedSpace.Status.Failed -> tr("Ultimo tentativo non riuscito: il file non è raggiungibile (sei offline o è stato spostato).")
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
            Text(
                tr("Si aggiorna da solo quando apri e chiudi Remindella."),
                style = MaterialTheme.typography.bodySmall,
                color = colors.cyan.content,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                PushButton(tr("Sincronizza ora"), onClick = { scope.launch { space.sync() } }, icon = Icons.Rounded.Sync)
                TextButton(onClick = { confirmLeave = true }) {
                    Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(tr("Smetti"), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }

    if (help) TutorialDialog(SharingTutorialPages, onDismiss = { help = false })
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
                modifier = Modifier.graphicsLayer {
                    translationY = -hop * 10.dp.toPx()
                    rotationZ = (hop - 0.5f) * if (index % 2 == 0) 6f else -6f
                },
            )
        }
    }
}
