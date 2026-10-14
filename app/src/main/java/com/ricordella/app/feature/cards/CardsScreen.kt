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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.ChoiceTile
import com.ricordella.app.core.ui.ConfirmDialog
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.EmptyState
import com.ricordella.app.core.ui.HappyWizard
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.ScanFileTypes
import com.ricordella.app.core.ui.WizardScene
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.scanInputImage
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.model.LoyaltyCard
import com.ricordella.app.feature.settings.CalendarPalette
import com.ricordella.app.feature.settings.Swatch
import kotlinx.coroutines.launch

/** Oltre questo numero di carte, una categoria mostra le prime e un "Mostra altre". */
private const val COLLAPSED_COUNT = 3

/** Un gruppo dell'elenco: le preferite, una categoria o le carte senza categoria. */
private data class CardGroup(val key: String, val title: String, val color: Long?, val favorites: Boolean, val cards: List<LoyaltyCard>)

/** Il modulo di una carta, nuova ([id] null) o da modificare. */
private data class CardForm(val id: String?, val name: String, val category: String, val color: Long?, val code: String, val format: Int)

/**
 * Carte fedeltà: si scansionano con la fotocamera (o da una foto/PDF), o si scrive il numero, e alla cassa
 * si mostra il codice a schermo, a tutta luminosità. Si organizzano per categoria (con un colore a scelta),
 * si cercano per nome, categoria o numero e si possono mettere tra le preferite, che stanno in cima.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as RicordellaApplication).container
    val scope = rememberCoroutineScope()
    val settings = LocalAppSettings.current
    val cards = settings.cards
    val categories = cards.map { it.category }.filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase() }
    val suggestions = (categories + DefaultCategories).distinct()
    /** Il colore della categoria: quello scelto, altrimenti uno della tavolozza (fisso una volta salvata la carta). */
    fun colorOf(category: String): Long =
        settings.cardColors[category] ?: CalendarPalette[(categories.indexOf(category).takeIf { it >= 0 } ?: categories.size) % CalendarPalette.size]

    var choosing by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var formName by rememberSaveable { mutableStateOf("") }
    var formCategory by rememberSaveable { mutableStateOf("") }
    var formColor by rememberSaveable { mutableStateOf<Long?>(null) }
    var formCode by rememberSaveable { mutableStateOf("") }
    var formFormat by rememberSaveable { mutableIntStateOf(Barcode.FORMAT_CODE_128) }
    var formId by rememberSaveable { mutableStateOf<String?>(null) }
    var formOpen by rememberSaveable { mutableStateOf(false) }
    var shownId by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }

    fun update(transform: (AppSettings) -> AppSettings) = scope.launch { container.settingsRepository.update(transform) }
    fun openForm(form: CardForm) {
        formId = form.id; formName = form.name; formCategory = form.category; formColor = form.color
        formCode = form.code; formFormat = form.format; formOpen = true
    }
    fun save() {
        val category = formCategory.trim()
        val name = formName.trim()
        val picked = formColor ?: colorOf(category)
        // A mano il formato non si sa: 13 o 8 cifre sono EAN, il resto Code 128.
        val format = when {
            formFormat != Barcode.FORMAT_CODE_128 -> formFormat
            formCode.all { it.isDigit() } && formCode.length == 13 -> Barcode.FORMAT_EAN_13
            formCode.all { it.isDigit() } && formCode.length == 8 -> Barcode.FORMAT_EAN_8
            else -> formFormat
        }
        val id = formId
        update { s ->
            val list = if (id == null) s.cards + LoyaltyCard(name = name, category = category, code = formCode, format = format)
            else s.cards.map { if (it.id == id) it.copy(name = name, category = category) else it }
            s.copy(cards = list, cardColors = if (category.isEmpty()) s.cardColors else s.cardColors + (category to picked))
        }
        formOpen = false
    }
    val onCodes: (List<Barcode>) -> Unit = { found ->
        val first = found.firstOrNull { it.rawValue != null }
        if (first == null) Toast.makeText(context, tr("Nessun codice trovato"), Toast.LENGTH_LONG).show()
        else openForm(CardForm(null, "", "", null, first.rawValue!!, first.format))
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readBarcodes(context, it, onCodes) }
    }

    DetailScaffold(
        title = tr("Carte"),
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { choosing = true },
                containerColor = MaterialTheme.ricordellaColors.bolt,
                contentColor = MaterialTheme.ricordellaColors.onBolt,
            ) { Icon(Icons.Rounded.Add, contentDescription = tr("Aggiungi una carta")) }
        },
    ) { padding ->
        if (cards.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.CreditCard,
                title = tr("Nessuna carta"),
                message = tr("Aggiungi le tessere di negozi e supermercati: le scansioni una volta e le mostri alla cassa."),
                actionLabel = tr("Aggiungi una carta"),
                onAction = { choosing = true },
                modifier = Modifier.padding(padding),
            )
        } else {
            val searching = query.isNotBlank()
            val shown = if (!searching) cards else cards.filter { c -> listOf(c.name, c.category, c.code).any { it.contains(query.trim(), ignoreCase = true) } }
            val groups = buildList {
                shown.filter { it.favorite }.takeIf { it.isNotEmpty() }?.let { add(CardGroup("*fav", tr("Preferite"), null, true, it)) }
                val rest = shown.filter { !it.favorite }
                categories.forEach { c -> rest.filter { it.category == c }.takeIf { it.isNotEmpty() }?.let { add(CardGroup(c, c, colorOf(c), false, it)) } }
                rest.filter { it.category.isEmpty() }.takeIf { it.isNotEmpty() }
                    ?.let { add(CardGroup("", if (isEmpty()) "" else tr("Senza categoria"), null, false, it)) }
            }
            LazyColumn(
                Modifier.fillMaxSize().contentWidth(),
                contentPadding = PaddingValues(
                    start = RicordellaDimensions.screenPadding,
                    end = RicordellaDimensions.screenPadding,
                    top = padding.calculateTopPadding() + RicordellaDimensions.spaceS,
                    bottom = 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
            ) {
                item(key = "search") {
                    OutlinedTextField(
                        query, { query = it },
                        placeholder = { Text(tr("Cerca una carta")) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = { if (searching) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = tr("Cancella")) } },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (shown.isEmpty()) item(key = "none") {
                    Text(tr("Nessun risultato"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(RicordellaDimensions.spaceL))
                }
                groups.forEach { group ->
                    if (group.title.isNotEmpty()) item(key = "h/${group.key}") { GroupHeader(group) }
                    // Una sola carta in più non vale un "Mostra altre": si ripiega solo se ne restano almeno due. Cercando, si vede tutto.
                    val collapsible = group.cards.size > COLLAPSED_COUNT + 1 && !searching
                    val open = !collapsible || group.key in expanded
                    (if (open) group.cards else group.cards.take(COLLAPSED_COUNT)).forEach { card ->
                        item(key = "c/${card.id}") {
                            CardRow(card, cardTone(group.color ?: card.category.takeIf { it.isNotEmpty() }?.let(::colorOf)), onClick = { shownId = card.id })
                        }
                    }
                    if (collapsible) item(key = "m/${group.key}") {
                        TextButton(onClick = { expanded = if (open) expanded - group.key else expanded + group.key }) {
                            Text(if (open) tr("Mostra meno") else trf("Mostra altre %1\$s", group.cards.size - COLLAPSED_COUNT))
                        }
                    }
                }
            }
        }
    }

    if (choosing) {
        Dialog(onDismissRequest = { choosing = false }) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.widthIn(max = 460.dp)) {
                val colors = MaterialTheme.ricordellaColors
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(RicordellaDimensions.spaceXl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
                ) {
                    HappyWizard(size = 88.dp, scene = WizardScene.SEARCHING)
                    Text(tr("Aggiungi una carta"), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    ChoiceTile(Icons.Rounded.PhotoCamera, tr("Fotocamera"), tr("Inquadra il documento adesso"), colors.cyan) {
                        choosing = false
                        GmsBarcodeScanning.getClient(context).startScan()
                            .addOnSuccessListener { onCodes(listOf(it)) }
                            .addOnFailureListener { Toast.makeText(context, tr("Scanner non disponibile"), Toast.LENGTH_LONG).show() }
                    }
                    ChoiceTile(Icons.Rounded.PictureAsPdf, tr("Foto o PDF"), tr("Un file già sul telefono, anche un PDF ricevuto per email"), colors.lavender) {
                        choosing = false; picker.launch(ScanFileTypes)
                    }
                    ChoiceTile(Icons.Rounded.Edit, tr("Scrivi il numero a mano"), tr("Digita il numero stampato sulla tessera"), colors.pear) {
                        choosing = false; openForm(CardForm(null, "", "", null, "", Barcode.FORMAT_CODE_128))
                    }
                    TextButton(onClick = { choosing = false }) { Text(tr("Annulla")) }
                }
            }
        }
    }

    if (formOpen) {
        AlertDialog(
            onDismissRequest = { formOpen = false },
            title = { Text(if (formId == null) tr("Nuova carta") else tr("Modifica carta")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                    OutlinedTextField(
                        formName, { formName = it.take(40) },
                        label = { Text(tr("Nome (Decathlon, Coop...)")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    if (formId == null) {
                        OutlinedTextField(formCode, { formCode = it.trim() }, label = { Text(tr("Numero")) }, singleLine = true)
                    }
                    OutlinedTextField(
                        formCategory, { formCategory = it.take(24); formColor = null },
                        label = { Text(tr("Categoria")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                        suggestions.forEach { s ->
                            FilterChip(selected = s == formCategory.trim(), onClick = { formCategory = s; formColor = null }, label = { Text(s) })
                        }
                    }
                    if (formCategory.isNotBlank()) {
                        Text(tr("Colore della categoria"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                        val chosen = formColor ?: colorOf(formCategory.trim())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CalendarPalette.forEach { argb ->
                                Swatch(Color(argb), chosen == argb, formCategory.trim()) { formColor = argb }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = formName.isNotBlank() && formCode.isNotBlank(), onClick = ::save) { Text(tr("Salva")) }
            },
            dismissButton = { TextButton(onClick = { formOpen = false }) { Text(tr("Annulla")) } },
        )
    }

    cards.firstOrNull { it.id == shownId }?.let { card ->
        CardDialog(
            card,
            onFavorite = { update { s -> s.copy(cards = s.cards.map { if (it.id == card.id) it.copy(favorite = !it.favorite) else it }) } },
            onEdit = { openForm(CardForm(card.id, card.name, card.category, null, card.code, card.format)) },
            onDelete = { update { s -> s.copy(cards = s.cards.filterNot { it.id == card.id }) }; shownId = null },
            onDismiss = { shownId = null },
        )
    }
}

/** I colori di una carta: il colore della categoria schiarito per lo sfondo, come le altre schede dell'app. */
@Composable
private fun cardTone(argb: Long?): Tone {
    if (argb == null) return MaterialTheme.ricordellaColors.lavender
    val color = Color(argb)
    return Tone(lerp(color, MaterialTheme.colorScheme.surface, 0.82f), MaterialTheme.colorScheme.onSurface, color)
}

@Composable
private fun GroupHeader(group: CardGroup) {
    Row(
        Modifier.padding(top = RicordellaDimensions.spaceS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
    ) {
        if (group.favorites) Icon(Icons.Rounded.Star, contentDescription = null, tint = MaterialTheme.ricordellaColors.pear.solid, modifier = Modifier.size(16.dp))
        else Box(Modifier.size(12.dp).background(group.color?.let(::Color) ?: MaterialTheme.ricordellaColors.lavender.solid, CircleShape))
        Text(group.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${group.cards.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CardRow(card: LoyaltyCard, tone: Tone, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(tone.container, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(RicordellaDimensions.spaceM),
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
        if (card.favorite) Icon(Icons.Rounded.Star, contentDescription = tr("Preferita"), tint = tone.solid)
    }
}

/** La carta a tutto schermo: sfondo bianco, codice grande e luminosità al massimo, perché il lettore lo legga bene. */
@Composable
private fun CardDialog(card: LoyaltyCard, onFavorite: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.attributes = window?.attributes?.apply { screenBrightness = 1f }
            onDispose {}
        }
        val bitmap = remember(card.code, card.format) { runCatching { renderBarcode(card) }.getOrNull() }
        Column(
            Modifier.fillMaxSize().background(Color.White).systemBarsPadding().padding(RicordellaDimensions.spaceL),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.name, style = MaterialTheme.typography.headlineMedium, color = Color.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (card.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (card.favorite) tr("Togli dai preferiti") else tr("Aggiungi ai preferiti"),
                        tint = MaterialTheme.ricordellaColors.pear.solid,
                    )
                }
            }
            // Il codice prende lo spazio che resta (weight): i pulsanti sotto restano sempre raggiungibili.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (bitmap != null) {
                    Image(
                        bitmap.asImageBitmap(), contentDescription = card.name, filterQuality = FilterQuality.None,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(vertical = RicordellaDimensions.spaceM),
                    )
                }
            }
            Text(card.code, style = MaterialTheme.typography.titleLarge, color = Color.Black, textAlign = TextAlign.Center)
            Row {
                TextButton(onClick = onDismiss) { Text(tr("Chiudi")) }
                TextButton(onClick = onEdit) { Text(tr("Modifica")) }
                TextButton(onClick = { confirming = true }) { Text(tr("Elimina")) }
            }
        }
    }
    if (confirming) {
        ConfirmDialog(
            title = tr("Eliminare la carta?"),
            message = card.name,
            confirmLabel = tr("Elimina"),
            destructive = true,
            onConfirm = onDelete,
            onDismiss = { confirming = false },
        )
    }
}

private val DefaultCategories get() = listOf(tr("Spesa"), tr("Sport"), tr("Salute"), tr("Casa"))

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

