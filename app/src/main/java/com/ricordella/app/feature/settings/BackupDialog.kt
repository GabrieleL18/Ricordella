package com.ricordella.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ricordella.app.core.ui.BackupTutorialPages
import com.ricordella.app.core.ui.MiniTutorial
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors

private class BackupChoice(val icon: ImageVector, val title: String, val subtitle: String, val tone: Tone, val onClick: () -> Unit)

/**
 * Esporta o importa in un'unica finestra: in alto il tutorial animato (scorre da solo),
 * sotto le scelte. In orizzontale tutorial e scelte stanno affiancati.
 */
@Composable
fun BackupDialog(
    export: Boolean,
    hasTarget: Boolean,
    onDismiss: () -> Unit,
    onOverwrite: () -> Unit,
    onNewVersion: () -> Unit,
    onShare: () -> Unit,
    onChooseFile: () -> Unit,
) {
    val colors = MaterialTheme.ricordellaColors
    val pages = if (export) BackupTutorialPages.take(3) else listOf(BackupTutorialPages[3], BackupTutorialPages[1])
    val choices = if (export) {
        listOfNotNull(
            BackupChoice(
                Icons.Rounded.Save,
                if (hasTarget) "Aggiorna il backup" else "Salva il backup",
                if (hasTarget) "Sovrascrive il file precedente." else "Scegli dove salvarlo (es. Drive): le prossime volte verrà sovrascritto.",
                colors.mint,
            ) { onDismiss(); onOverwrite() },
            if (hasTarget) BackupChoice(Icons.Rounded.AddCircleOutline, "Nuova versione", "Crea un nuovo file e tiene quello vecchio.", colors.cyan) { onDismiss(); onNewVersion() } else null,
            BackupChoice(Icons.Rounded.Share, "Condividi", "Invialo con un'app (email, chat...).", colors.lavender) { onDismiss(); onShare() },
        )
    } else {
        listOf(
            BackupChoice(
                Icons.Rounded.FolderOpen,
                "Scegli il file",
                "Il file .zip di Remindella. Prima di sostituire i dati ti mostro cosa contiene.",
                colors.mint,
            ) { onDismiss(); onChooseFile() },
        )
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = RicordellaDimensions.spaceL, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (export) "Esporta backup" else "Importa backup", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Chiudi") }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val options: @Composable (Modifier) -> Unit = { modifier ->
                        Column(modifier.padding(RicordellaDimensions.spaceL), verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                            choices.forEach { ChoiceRow(it) }
                        }
                    }
                    if (maxWidth > maxHeight) {
                        Row(Modifier.fillMaxSize()) {
                            MiniTutorial(pages, Modifier.weight(1f).verticalScroll(rememberScrollState()))
                            options(Modifier.weight(1f).verticalScroll(rememberScrollState()))
                        }
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            MiniTutorial(pages, Modifier.fillMaxWidth())
                            options(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(choice: BackupChoice) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(choice.tone.container)
            .clickable(role = Role.Button, onClick = choice.onClick)
            .padding(RicordellaDimensions.spaceL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Box(Modifier.size(44.dp).background(choice.tone.solid, CircleShape), contentAlignment = Alignment.Center) {
            Icon(choice.icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest)
        }
        Column(Modifier.weight(1f)) {
            Text(choice.title, style = MaterialTheme.typography.titleMedium, color = choice.tone.content)
            Text(choice.subtitle, style = MaterialTheme.typography.bodyMedium, color = choice.tone.content)
        }
    }
}
