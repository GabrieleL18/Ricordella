package com.ricordella.app.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.mlkit.vision.common.InputImage
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors

/** Da dove leggere un documento: fotocamera al momento, oppure una foto o un PDF già sul telefono. */
enum class ScanSource { CAMERA, FILE }

/** Tipi di file proposti per "Foto o PDF". */
val ScanFileTypes = arrayOf("image/*", "application/pdf")

/**
 * Riquadro unico "Scansiona…", uguale ovunque (scontrini, carte d'imbarco): un tocco apre la
 * scelta tra fotocamera e foto/PDF, poi [onChoose] avvia la lettura. Mentre legge mostra la rotella.
 */
@Composable
fun ScanCard(
    title: String,
    subtitle: String,
    onChoose: (ScanSource) -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    tone: Tone = MaterialTheme.ricordellaColors.cyan,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .background(tone.container, MaterialTheme.shapes.large)
            .clickable(enabled = !busy, role = Role.Button) { choosing = true }
            .padding(RicordellaDimensions.spaceL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Box(Modifier.size(44.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.surfaceContainerLowest)
            else Icon(Icons.Rounded.DocumentScanner, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = tone.content)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tone.content)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = tone.content)
    }
    if (choosing) {
        ScanChoiceDialog(
            title = title,
            onChoose = { choosing = false; onChoose(it) },
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun ScanChoiceDialog(title: String, onChoose: (ScanSource) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.widthIn(max = 460.dp)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(RicordellaDimensions.spaceXl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
            ) {
                HappyWizard(size = 88.dp, scene = WizardScene.SEARCHING)
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    tr("Come vuoi darmelo? Leggo tutto sul telefono, senza Internet."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                ChoiceTile(Icons.Rounded.PhotoCamera, tr("Fotocamera"), tr("Inquadra il documento adesso"), colors.cyan) { onChoose(ScanSource.CAMERA) }
                ChoiceTile(Icons.Rounded.PictureAsPdf, tr("Foto o PDF"), tr("Un file già sul telefono, anche un PDF ricevuto per email"), colors.lavender) { onChoose(ScanSource.FILE) }
                TextButton(onClick = onDismiss) { Text(tr("Annulla")) }
            }
        }
    }
}

@Composable
internal fun ChoiceTile(icon: ImageVector, title: String, subtitle: String, tone: Tone, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(tone.container, MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(RicordellaDimensions.spaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Box(Modifier.size(40.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = tone.content)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tone.content)
        }
    }
}

/** Immagine da analizzare: la foto così com'è, oppure la prima pagina del PDF disegnata in bianco. */
fun scanInputImage(context: Context, uri: Uri): InputImage =
    if (context.contentResolver.getType(uri) == "application/pdf") InputImage.fromBitmap(renderFirstPdfPage(context, uri), 0)
    else InputImage.fromFilePath(context, uri)

// ponytail: legge solo la prima pagina del PDF, dove c'è quasi sempre quello che serve; scorrere le pagine se servisse.
private fun renderFirstPdfPage(context: Context, uri: Uri): Bitmap =
    context.contentResolver.openFileDescriptor(uri, "r")!!.use { fd ->
        PdfRenderer(fd).use { pdf ->
            pdf.openPage(0).use { page ->
                val width = 2000
                Bitmap.createBitmap(width, width * page.height / page.width, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(android.graphics.Color.WHITE)
                    page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }
