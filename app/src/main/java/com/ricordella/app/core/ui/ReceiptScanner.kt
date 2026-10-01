package com.ricordella.app.core.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.data.media.MediaStorage
import com.ricordella.app.domain.text.ReceiptInfo
import com.ricordella.app.domain.text.ReceiptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Scontrino letto: i dati riconosciuti e la foto (copia compressa) da allegare come prova d'acquisto. */
data class ScannedReceipt(val info: ReceiptInfo, val imageUri: String?)

/**
 * Riconosce il testo di una foto o della prima pagina di un PDF con ML Kit, sul telefono
 * (niente Internet). Restituisce le righe nell'ordine in cui compaiono.
 */
suspend fun recognizeLines(context: Context, image: Uri): List<String> = suspendCancellableCoroutine { continuation ->
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val input = runCatching { scanInputImage(context, image) }.getOrElse {
        continuation.resume(emptyList())
        return@suspendCancellableCoroutine
    }
    recognizer.process(input)
        .addOnSuccessListener { result ->
            val lines = result.textBlocks.flatMap { it.lines }.sortedBy { it.boundingBox?.top ?: 0 }.map { it.text }
            continuation.resume(lines)
        }
        .addOnFailureListener { continuation.resume(emptyList()) }
        .addOnCompleteListener { recognizer.close() }
}

/**
 * Riquadro "Scansiona lo scontrino": fotocamera oppure foto/PDF, lettura del testo e compilazione
 * automatica di data d'acquisto, prezzo, negozio e garanzia.
 */
@Composable
fun ReceiptScanCard(onScanned: (ScannedReceipt) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }

    fun read(uri: Uri) {
        busy = true
        failed = false
        scope.launch {
            val lines = recognizeLines(context, uri)
            // La foto si allega compressa; un PDF resta solo letto (non è un'immagine da copiare).
            val stored = withContext(Dispatchers.IO) { MediaStorage.storeCompressedImage(context, uri) }
            busy = false
            if (lines.isEmpty()) failed = true
            else onScanned(ScannedReceipt(ReceiptParser.parse(lines), stored))
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val path = cameraFile
        if (taken && path != null) read(FileProvider.getUriForFile(context, "${context.packageName}.files", File(path)))
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::read) }

    ScanCard(
        title = tr("Scansiona lo scontrino"),
        subtitle = if (failed) tr("Non sono riuscita a leggere il testo: prova con una foto più nitida e dritta.")
        else tr("Leggo data, prezzo e negozio e preparo la garanzia. Tutto sul telefono."),
        busy = busy,
        modifier = modifier,
        onChoose = { source ->
            when (source) {
                ScanSource.CAMERA -> {
                    val target = File(File(context.cacheDir, "camera").apply { mkdirs() }, "receipt.jpg")
                    cameraFile = target.path
                    camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", target))
                }
                ScanSource.FILE -> file.launch(ScanFileTypes)
            }
        },
    )
}
