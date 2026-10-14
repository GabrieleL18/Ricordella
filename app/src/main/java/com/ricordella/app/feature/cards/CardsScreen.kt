package com.ricordella.app.feature.cards

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.text.KeyboardOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.ScanCard
import com.ricordella.app.core.ui.ScanFileTypes
import com.ricordella.app.core.ui.ScanSource
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.scanInputImage
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.LoyaltyCard
import kotlinx.coroutines.launch

/**
 * Carte fedeltà: si scansionano con la fotocamera (o da una foto/PDF, anche quelli di Klarna e simili)
 * o si scrive il numero, e alla cassa si mostra il codice a schermo, a tutta luminosità.
 */
@Composable
fun CardsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as RicordellaApplication).container
    val scope = rememberCoroutineScope()
    val cards = LocalAppSettings.current.cards
    // Carta in corso di inserimento (codice letto o scritto) e carta mostrata a schermo intero.
    var adding by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable { mutableStateOf("") }
    var draftCode by rememberSaveable { mutableStateOf("") }
    var draftFormat by rememberSaveable { mutableIntStateOf(Barcode.FORMAT_CODE_128) }
    var shownId by rememberSaveable { mutableStateOf<String?>(null) }
    fun edit(transform: (List<LoyaltyCard>) -> List<LoyaltyCard>) =
        scope.launch { container.settingsRepository.update { it.copy(cards = transform(it.cards)) } }
    fun startAdding(code: String, format: Int) {
        draftName = ""; draftCode = code; draftFormat = format; adding = true
    }
    val onCodes: (List<Barcode>) -> Unit = { found ->
        val first = found.firstOrNull { it.rawValue != null }
        if (first == null) Toast.makeText(context, tr("Nessun codice trovato"), Toast.LENGTH_LONG).show()
        else startAdding(first.rawValue!!, first.format)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readBarcodes(context, it, onCodes) }
    }

    DetailScaffold(title = tr("Carte"), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .contentWidth()
                .padding(horizontal = RicordellaDimensions.screenPadding)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
        ) {
            ScanCard(
                title = tr("Scansiona una carta"),
                subtitle = tr("Inquadra il codice a barre o il QR: lo rivedi qui, pronto per la cassa."),
                tone = MaterialTheme.ricordellaColors.lavender,
                onChoose = { source ->
                    when (source) {
                        ScanSource.CAMERA -> GmsBarcodeScanning.getClient(context).startScan()
                            .addOnSuccessListener { onCodes(listOf(it)) }
                            .addOnFailureListener { Toast.makeText(context, tr("Scanner non disponibile"), Toast.LENGTH_LONG).show() }
                        ScanSource.FILE -> picker.launch(ScanFileTypes)
                    }
                },
            )
            TextButton(onClick = { startAdding("", Barcode.FORMAT_CODE_128) }) { Text(tr("Scrivi il numero a mano")) }

            val colors = MaterialTheme.ricordellaColors
            val tones = listOf(colors.cyan, colors.pear, colors.coral, colors.mint, colors.lavender)
            cards.forEachIndexed { index, card ->
                val tone = tones[index % tones.size]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(tone.container, MaterialTheme.shapes.large)
                        .clickable { shownId = card.id }
                        .padding(RicordellaDimensions.spaceL),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
                ) {
                    Box(Modifier.size(44.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(card.name, style = MaterialTheme.typography.titleMedium, color = tone.content)
                        Text(card.code, style = MaterialTheme.typography.bodySmall, color = tone.content, maxLines = 1)
                    }
                }
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(tr("Nuova carta")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                    OutlinedTextField(
                        draftName, { draftName = it.take(40) },
                        label = { Text(tr("Nome (Decathlon, Coop...)")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    OutlinedTextField(draftCode, { draftCode = it.trim() }, label = { Text(tr("Numero")) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = draftName.isNotBlank() && draftCode.isNotBlank(),
                    onClick = {
                        // A mano il formato non si sa: 13 o 8 cifre sono EAN, il resto Code 128.
                        val format = when {
                            draftFormat != Barcode.FORMAT_CODE_128 -> draftFormat
                            draftCode.all { it.isDigit() } && draftCode.length == 13 -> Barcode.FORMAT_EAN_13
                            draftCode.all { it.isDigit() } && draftCode.length == 8 -> Barcode.FORMAT_EAN_8
                            else -> draftFormat
                        }
                        val card = LoyaltyCard(name = draftName.trim(), code = draftCode, format = format)
                        edit { it + card }
                        adding = false
                    },
                ) { Text(tr("Salva")) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(tr("Annulla")) } },
        )
    }

    cards.firstOrNull { it.id == shownId }?.let { card ->
        CardDialog(card, onDelete = { edit { all -> all.filterNot { it.id == card.id } }; shownId = null }, onDismiss = { shownId = null })
    }
}

/** La carta a tutto schermo: sfondo bianco, codice grande e luminosità al massimo, perché il lettore lo legga bene. */
@Composable
private fun CardDialog(card: LoyaltyCard, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.attributes = window?.attributes?.apply { screenBrightness = 1f }
            onDispose {}
        }
        val bitmap = remember(card) { runCatching { renderBarcode(card) }.getOrNull() }
        Column(
            Modifier.fillMaxSize().background(Color.White).padding(RicordellaDimensions.spaceXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(card.name, style = MaterialTheme.typography.headlineMedium, color = Color.Black, textAlign = TextAlign.Center)
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(), contentDescription = card.name, filterQuality = FilterQuality.None,
                    contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().padding(vertical = RicordellaDimensions.spaceXl),
                )
            }
            Text(card.code, style = MaterialTheme.typography.titleLarge, color = Color.Black, textAlign = TextAlign.Center)
            Row(Modifier.padding(top = RicordellaDimensions.spaceXl)) {
                TextButton(onClick = onDismiss) { Text(tr("Chiudi")) }
                TextButton(onClick = { confirming = true }) { Text(tr("Elimina")) }
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(tr("Eliminare la carta?")) },
            text = { Text(card.name) },
            confirmButton = { TextButton(onClick = onDelete) { Text(tr("Elimina")) } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(tr("Annulla")) } },
        )
    }
}

private fun zxingFormat(mlKit: Int): BarcodeFormat = when (mlKit) {
    Barcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
    Barcode.FORMAT_CODE_93 -> BarcodeFormat.CODE_93
    Barcode.FORMAT_CODABAR -> BarcodeFormat.CODABAR
    Barcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
    Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
    Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
    Barcode.FORMAT_ITF -> BarcodeFormat.ITF
    Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
    Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
    Barcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
    Barcode.FORMAT_PDF417 -> BarcodeFormat.PDF_417
    Barcode.FORMAT_AZTEC -> BarcodeFormat.AZTEC
    else -> BarcodeFormat.CODE_128
}

private fun renderBarcode(card: LoyaltyCard): Bitmap {
    val format = zxingFormat(card.format)
    val square = format == BarcodeFormat.QR_CODE || format == BarcodeFormat.DATA_MATRIX || format == BarcodeFormat.AZTEC
    // Un EAN con cifra di controllo sbagliata non si disegna: si ripiega su Code 128, che prende qualsiasi testo.
    val matrix = runCatching { MultiFormatWriter().encode(card.code, format, 1000, if (square) 1000 else 300) }
        .getOrElse { MultiFormatWriter().encode(card.code, BarcodeFormat.CODE_128, 1000, 300) }
    val pixels = IntArray(matrix.width * matrix.height) { if (matrix[it % matrix.width, it / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}

private fun readBarcodes(context: Context, uri: Uri, onCodes: (List<Barcode>) -> Unit) {
    val image = runCatching { scanInputImage(context, uri) }.getOrElse { return onCodes(emptyList()) }
    BarcodeScanning.getClient().process(image)
        .addOnSuccessListener(onCodes)
        .addOnFailureListener { onCodes(emptyList()) }
}
