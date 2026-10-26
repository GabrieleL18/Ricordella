package com.ricordella.app.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.AppSettings

/**
 * Dove vanno i backup, scritto chiaro: con la spunta verde e il nome della cartella o del file
 * scelti (e l'app che li tiene, es. Drive), oppure l'avviso che per ora restano sul telefono.
 */
@Composable
fun BackupPlaceCard(settings: AppSettings, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val folder = settings.backupFolderUri?.toUri()
    val chosen = folder ?: settings.backupTargetUri?.toUri()
    val name = remember(chosen) { chosen?.let { context.documentName(it, isFolder = folder != null) } }
    val where = remember(chosen) { chosen?.let { context.storageName(it) } }
    val tone = if (chosen != null) MaterialTheme.ricordellaColors.mint else MaterialTheme.ricordellaColors.pear
    Row(
        modifier
            .fillMaxWidth()
            .background(tone.container, MaterialTheme.shapes.large)
            .padding(start = RicordellaDimensions.spaceL, top = RicordellaDimensions.spaceM, bottom = RicordellaDimensions.spaceM, end = RicordellaDimensions.spaceS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Icon(if (chosen != null) Icons.Rounded.CheckCircle else Icons.Rounded.PhoneAndroid, contentDescription = null, tint = tone.content)
        Column(Modifier.weight(1f)) {
            Text(
                if (chosen != null) tr("Il backup va qui") else tr("Non hai scelto dove salvarlo"),
                style = MaterialTheme.typography.titleSmall,
                color = tone.content,
            )
            Text(
                when {
                    chosen == null -> tr("Per ora resta sul telefono, in Download/Remindella: se cambi telefono lo perdi.")
                    folder != null -> trf("Cartella «%1\$s»", name ?: "?")
                    else -> trf("File «%1\$s»", name ?: "?")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = tone.content,
            )
            if (where != null) Text(trf("Su %1\$s", where), style = MaterialTheme.typography.bodySmall, color = tone.content)
        }
        TextButton(onClick = onChoose) { Text(if (chosen != null) tr("Cambia") else tr("Scegli")) }
    }
}

/** Nome del file o della cartella scelti, come lo mostra il selettore di Android. */
private fun Context.documentName(uri: Uri, isFolder: Boolean): String? = runCatching {
    val document = if (isFolder) DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)) else uri
    contentResolver.query(document, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')

/** Dove sta: Google Drive, il telefono o il nome dell'app che fornisce il file. */
private fun Context.storageName(uri: Uri): String? {
    val authority = uri.authority ?: return null
    return when {
        authority.startsWith("com.google.android.apps.docs") -> "Google Drive"
        authority == "com.android.externalstorage.documents" || authority == "com.android.providers.downloads.documents" -> tr("questo telefono")
        else -> runCatching { packageManager.resolveContentProvider(authority, 0)?.loadLabel(packageManager)?.toString() }.getOrNull()
    }
}
