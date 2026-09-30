package com.ricordella.app.core.ui

import androidx.core.net.toUri
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ricordella.app.domain.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** File scelto dall'utente, con il nome leggibile e il tipo. */
data class PickedFile(val uri: String, val displayName: String, val mimeType: String?)

/**
 * Restituisce una funzione che apre il selettore file di sistema (Storage Access Framework).
 * Il permesso di lettura viene reso persistente: il file non viene copiato.
 */
@Composable
fun rememberFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val picked = withContext(Dispatchers.IO) { describe(context, uri) }
                onPicked(picked)
            }
        }
    }
    return { launcher.launch(arrayOf("*/*")) }
}

/** Selettore di foto di sistema (Photo Picker): non richiede permessi di archiviazione. */
@Composable
fun rememberPhotoPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            onPicked(uri.toString())
        }
    }
    return {
        launcher.launch(
            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }
}

private fun describe(context: Context, uri: Uri): PickedFile {
    persistReadPermission(context, uri)
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return PickedFile(uri.toString(), name ?: "Documento", context.contentResolver.getType(uri))
}

private fun persistReadPermission(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
        // Alcuni provider non offrono permessi persistenti: il riferimento resta comunque salvato.
    }
}

/** Apre un file con l'app di sistema adatta. Restituisce false se nessuna app può aprirlo. */
fun openFile(context: Context, uri: String, mimeType: String?): Boolean {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri.toUri(), mimeType ?: context.contentResolver.getType(uri.toUri()) ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/** Sezione "Documenti/Allegati" delle schede di dettaglio. */
@Composable
fun AttachmentsSection(
    title: String,
    attachments: List<Attachment>,
    onAdd: (PickedFile) -> Unit,
    onRemove: (Attachment) -> Unit,
    onOpenFailed: () -> Unit,
) {
    val context = LocalContext.current
    val pickFile = rememberFilePicker(onAdd)
    SectionHeader(title) {
        TextButton(onClick = pickFile) {
            Icon(Icons.Rounded.AttachFile, contentDescription = null)
            Text("Allega", modifier = Modifier.padding(start = 4.dp))
        }
    }
    if (attachments.isEmpty()) {
        Text("Nessun documento allegato.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    attachments.forEach { attachment ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (!openFile(context, attachment.uri, attachment.mimeType)) onOpenFailed() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                attachment.displayName,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { onRemove(attachment) }) {
                Icon(Icons.Rounded.Close, contentDescription = "Rimuovi ${attachment.displayName}")
            }
        }
    }
}

