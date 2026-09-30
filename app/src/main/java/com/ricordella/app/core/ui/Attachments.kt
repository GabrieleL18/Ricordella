package com.ricordella.app.core.ui

import com.ricordella.app.core.ui.theme.ricordellaColors
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import androidx.core.net.toUri
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.ricordella.app.data.media.MediaStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** File scelto dall'utente, con il nome leggibile e il tipo. */
data class PickedFile(val uri: String, val displayName: String, val mimeType: String?)

/**
 * Restituisce una funzione che apre il selettore file di sistema (Storage Access Framework).
 * Le immagini vengono salvate come copia compressa (leggerissima); gli altri file non vengono
 * copiati: si rende persistente il permesso di lettura e si conserva solo il riferimento.
 */
@Composable
fun rememberFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val picked = withContext(Dispatchers.IO) {
                    val described = describe(context, uri)
                    val compact = if (described.mimeType?.startsWith("image/") == true) MediaStorage.storeCompressedImage(context, uri) else null
                    if (compact != null) described.copy(uri = compact, mimeType = "image/webp") else described
                }
                onPicked(picked)
            }
        }
    }
    return { launcher.launch(arrayOf("*/*")) }
}

/**
 * Selettore di foto di sistema (Photo Picker): non richiede permessi di archiviazione.
 * La foto viene salvata come copia ridotta in WebP, così occupa pochissimo spazio.
 */
@Composable
fun rememberPhotoPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val stored = withContext(Dispatchers.IO) { MediaStorage.storeCompressedImage(context, uri) }
                if (stored == null) persistReadPermission(context, uri)
                onPicked(stored ?: uri.toString())
            }
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
    return PickedFile(uri.toString(), name ?: tr("Documento"), context.contentResolver.getType(uri))
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
    onOpen: (Attachment) -> Unit,
) {
    val pickFile = rememberFilePicker(onAdd)
    SectionHeader(
        if (attachments.isEmpty()) title else "$title · ${attachments.size}",
        icon = Icons.Rounded.AttachFile,
        tone = MaterialTheme.ricordellaColors.lavender,
    )
    // Anteprime affiancate e scorrevoli: foto, prima pagina dei PDF, testo dei documenti Word; in fondo il "+".
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        attachments.forEach { attachment ->
            FileTile(
                uri = attachment.uri,
                mimeType = attachment.mimeType,
                name = attachment.displayName,
                onOpen = { onOpen(attachment) },
                onRemove = { onRemove(attachment) },
            )
        }
        AddFileTile(onClick = pickFile)
    }
}

/** Tessera tratteggiata per allegare: stessa forma delle anteprime, così la fila resta ordinata. */
@Composable
private fun AddFileTile(onClick: () -> Unit) {
    val tone = MaterialTheme.ricordellaColors.lavender
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            Modifier
                .size(112.dp, 140.dp)
                .clip(shape)
                .drawBehind {
                    drawRoundRect(
                        tone.solid,
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
                    )
                }
                .clickable(role = Role.Button, onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = tone.solid, modifier = Modifier.size(36.dp))
            Text(tr("Allega"), style = MaterialTheme.typography.labelLarge, color = tone.content)
        }
        Text(tr("Foto, PDF o documenti"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}


/** Apre il menu "Condividi" di Android per il file .zip del backup. */
fun shareBackup(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/zip")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, tr("Backup di Remindella"))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, tr("Salva o condividi il backup")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
