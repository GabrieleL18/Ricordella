package com.ricordella.app.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.data.documents.DocxReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Tipi di file che l'app sa mostrare. */
enum class DocKind { IMAGE, PDF, DOCX, OTHER }

fun documentKind(context: Context, uri: String, mimeType: String?, name: String): DocKind {
    val mime = mimeType ?: runCatching { context.contentResolver.getType(uri.toUri()) }.getOrNull()
    val lower = name.lowercase()
    return when {
        mime?.startsWith("image/") == true -> DocKind.IMAGE
        mime == "application/pdf" || lower.endsWith(".pdf") -> DocKind.PDF
        mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || lower.endsWith(".docx") -> DocKind.DOCX
        else -> DocKind.OTHER
    }
}

private sealed interface Preview {
    data class Picture(val bitmap: Bitmap) : Preview
    data class Snippet(val text: String) : Preview
    data object None : Preview
}

private const val THUMB_PX = 320

/** Miniatura di un file: foto ridotta, prima pagina del PDF o prime righe del documento Word. */
private fun loadPreview(context: Context, uri: String, kind: DocKind): Preview = runCatching {
    val parsed = uri.toUri()
    when (kind) {
        DocKind.IMAGE -> Preview.Picture(
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, parsed)) { decoder, info, _ ->
                val side = maxOf(info.size.width, info.size.height)
                if (side > THUMB_PX) decoder.setTargetSampleSize(side / THUMB_PX)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            },
        )
        DocKind.PDF -> context.contentResolver.openFileDescriptor(parsed, "r")!!.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(0).use { page ->
                    val height = (THUMB_PX.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(THUMB_PX, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    Preview.Picture(bitmap)
                }
            }
        }
        DocKind.DOCX -> context.contentResolver.openInputStream(parsed)!!.use { input ->
            Preview.Snippet(DocxReader.read(input).take(6).joinToString("\n") { it.text }.take(220))
        }
        DocKind.OTHER -> Preview.None
    }
}.getOrDefault(Preview.None)

/**
 * Tessera di un allegato: anteprima in alto (foto, prima pagina del PDF, testo del Word
 * o icona col tipo), nome sotto; tocco = apri, X = rimuovi (se [onRemove] è presente).
 */
@Composable
fun FileTile(
    uri: String,
    mimeType: String?,
    name: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val kind = remember(uri) { documentKind(context, uri, mimeType, name) }
    val preview by produceState<Preview?>(null, uri) { value = withContext(Dispatchers.IO) { loadPreview(context, uri, kind) } }
    val colors = MaterialTheme.ricordellaColors
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .width(112.dp)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = tr("Apri"), onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(112.dp, 140.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when (val shown = preview) {
                is Preview.Picture -> Image(shown.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                is Preview.Snippet -> Text(
                    shown.text,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 9,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White).padding(8.dp),
                    color = androidx.compose.ui.graphics.Color.DarkGray,
                )
                else -> Icon(
                    if (kind == DocKind.PDF) Icons.Rounded.PictureAsPdf else Icons.Rounded.Description,
                    contentDescription = null,
                    tint = colors.cyan.solid,
                    modifier = Modifier.size(40.dp),
                )
            }
            // Etichetta del tipo in basso a sinistra.
            Text(
                name.substringAfterLast('.', "").uppercase().take(4).ifEmpty { if (kind == DocKind.IMAGE) "IMG" else "FILE" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(colors.cyan.solid, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            if (onRemove != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.9f))
                        .clickable(role = Role.Button, onClickLabel = tr("Rimuovi"), onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, contentDescription = tr("Rimuovi"), modifier = Modifier.size(16.dp)) }
            }
        }
        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
