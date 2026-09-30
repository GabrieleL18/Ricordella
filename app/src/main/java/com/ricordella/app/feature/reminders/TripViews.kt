package com.ricordella.app.feature.reminders

import java.time.LocalTime
import com.ricordella.app.core.ui.currentMinute
import com.ricordella.app.core.ui.rememberReducedMotion
import com.ricordella.app.core.ui.pressScale
import com.ricordella.app.core.ui.RicordellaMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.ricordella.app.domain.model.BoardingPass
import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.ui.DateField
import com.ricordella.app.core.ui.InfoRow
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.TimeField
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.TravelMode
import com.ricordella.app.domain.model.TripInfo
import com.ricordella.app.domain.model.TripLeg
import com.ricordella.app.domain.model.TripStay

val TravelMode.label: String
    get() = when (this) {
        TravelMode.PLANE -> tr("Aereo")
        TravelMode.SHIP -> tr("Nave")
        TravelMode.TRAIN -> tr("Treno")
        TravelMode.CAR -> tr("Auto")
    }

val TravelMode.icon: ImageVector
    get() = when (this) {
        TravelMode.PLANE -> Icons.Rounded.Flight
        TravelMode.SHIP -> Icons.Rounded.DirectionsBoat
        TravelMode.TRAIN -> Icons.Rounded.Train
        TravelMode.CAR -> Icons.Rounded.DirectionsCar
    }

private val TravelMode.codeLabel: String
    get() = when (this) {
        TravelMode.PLANE -> tr("Numero volo")
        TravelMode.SHIP -> tr("Nave / traghetto")
        TravelMode.TRAIN -> tr("Numero treno")
        TravelMode.CAR -> tr("Targa / noleggio")
    }

private val TravelMode.seatLabel: String
    get() = when (this) {
        TravelMode.PLANE -> tr("Posto")
        TravelMode.SHIP -> tr("Cabina / posto")
        TravelMode.TRAIN -> tr("Carrozza e posto")
        TravelMode.CAR -> tr("Note")
    }

/* ---------- Modulo ---------- */

/** Campi della vacanza: destinazione, tratte (aereo, nave, treno, auto) e alloggio. */
@Composable
fun TripFields(trip: TripInfo, onChange: (TripInfo) -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    SectionHeader(tr("Viaggio"), icon = Icons.Rounded.Luggage, tone = colors.mint)
    Text_(
        value = trip.destination,
        label = tr("Luogo / destinazione"),
        onValue = { onChange(trip.copy(destination = it)) },
    )
    trip.legs.forEachIndexed { index, leg ->
        LegCard(
            leg = leg,
            number = trip.legs.take(index + 1).count { it.mode == leg.mode },
            onChange = { updated -> onChange(trip.copy(legs = trip.legs.toMutableList().also { it[index] = updated })) },
            onRemove = { onChange(trip.copy(legs = trip.legs.filterIndexed { i, _ -> i != index })) },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS), modifier = Modifier.fillMaxWidth()) {
        TravelMode.entries.forEach { mode ->
            AssistChip(
                onClick = { onChange(trip.copy(legs = trip.legs + TripLeg(mode = mode))) },
                label = { Text(mode.label) },
                leadingIcon = { Icon(mode.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
    BoardingPassImport { legs -> onChange(trip.copy(legs = trip.legs + legs)) }
    StayCard(trip.stay ?: TripStay(), onChange = { onChange(trip.copy(stay = it)) })
}

/** Come Google Wallet: la carta d'imbarco (fotocamera, foto o PDF) compila da sola le tratte aeree. */
@Composable
private fun BoardingPassImport(onLegs: (List<TripLeg>) -> Unit) {
    val context = LocalContext.current
    val onCodes: (List<String>) -> Unit = { codes ->
        val legs = codes.firstNotNullOfOrNull { BoardingPass.parse(it, LocalDate.now()) }
        if (legs == null) Toast.makeText(context, tr("Nessuna carta d'imbarco trovata"), Toast.LENGTH_LONG).show() else onLegs(legs)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readBarcodes(context, it, onCodes) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS), modifier = Modifier.fillMaxWidth()) {
        AssistChip(
            onClick = {
                val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(BARCODE_FORMAT, *OTHER_BARCODE_FORMATS).build()
                GmsBarcodeScanning.getClient(context, options).startScan()
                    .addOnSuccessListener { onCodes(listOfNotNull(it.rawValue)) }
                    .addOnFailureListener { Toast.makeText(context, tr("Scanner non disponibile"), Toast.LENGTH_LONG).show() }
            },
            label = { Text(tr("Scansiona carta d'imbarco")) },
            leadingIcon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        AssistChip(
            onClick = { picker.launch(arrayOf("image/*", "application/pdf")) },
            label = { Text(tr("Da foto o PDF")) },
            leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
}

/** Le carte d'imbarco usano PDF417 o Aztec; QR e Data Matrix per sicurezza. */
private const val BARCODE_FORMAT = Barcode.FORMAT_PDF417
private val OTHER_BARCODE_FORMATS = intArrayOf(Barcode.FORMAT_AZTEC, Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX)

private fun readBarcodes(context: Context, uri: Uri, onCodes: (List<String>) -> Unit) {
    val image = runCatching {
        if (context.contentResolver.getType(uri) == "application/pdf") InputImage.fromBitmap(renderFirstPage(context, uri), 0)
        else InputImage.fromFilePath(context, uri)
    }.getOrElse { return onCodes(emptyList()) }
    val options = BarcodeScannerOptions.Builder().setBarcodeFormats(BARCODE_FORMAT, *OTHER_BARCODE_FORMATS).build()
    BarcodeScanning.getClient(options).process(image)
        .addOnSuccessListener { codes -> onCodes(codes.mapNotNull { it.rawValue }) }
        .addOnFailureListener { onCodes(emptyList()) }
}

// ponytail: legge solo la prima pagina del PDF, dove c'è quasi sempre il codice; scorrere le pagine se servisse.
private fun renderFirstPage(context: Context, uri: Uri): Bitmap =
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

@Composable
private fun LegCard(leg: TripLeg, number: Int, onChange: (TripLeg) -> Unit, onRemove: () -> Unit) {
    FormCard(title = "${leg.mode.label} $number", icon = leg.mode.icon, tone = MaterialTheme.ricordellaColors.cyan, onRemove = onRemove) {
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            Text_(leg.carrier, tr("Compagnia"), Modifier.weight(1f)) { onChange(leg.copy(carrier = it)) }
            Text_(leg.code, leg.mode.codeLabel, Modifier.weight(1f), KeyboardCapitalization.Characters) { onChange(leg.copy(code = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            Text_(leg.from, tr("Da"), Modifier.weight(1f)) { onChange(leg.copy(from = it)) }
            Text_(leg.to, "A", Modifier.weight(1f)) { onChange(leg.copy(to = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            DateField(tr("Partenza"), leg.date, { onChange(leg.copy(date = it)) }, Modifier.weight(1.6f), clearable = true)
            TimeField(
                tr("Ora"),
                leg.time,
                { onChange(leg.copy(time = it)) },
                Modifier.weight(1f),
                defaultTime = if (leg.date == LocalDate.now()) currentMinute() else LocalTime.of(9, 0),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            Text_(leg.seat, leg.mode.seatLabel, Modifier.weight(1f)) { onChange(leg.copy(seat = it)) }
            Text_(leg.bookingCode, tr("Codice prenotazione"), Modifier.weight(1f), KeyboardCapitalization.Characters) { onChange(leg.copy(bookingCode = it)) }
        }
    }
}

@Composable
private fun StayCard(stay: TripStay, onChange: (TripStay) -> Unit) {
    FormCard(title = tr("Alloggio"), icon = Icons.Rounded.Hotel, tone = MaterialTheme.ricordellaColors.lavender) {
        Text_(stay.name, tr("Nome (hotel, casa, campeggio...)")) { onChange(stay.copy(name = it)) }
        Text_(stay.address, tr("Indirizzo")) { onChange(stay.copy(address = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            DateField(tr("Check-in"), stay.checkIn, { onChange(stay.copy(checkIn = it)) }, Modifier.weight(1f), clearable = true)
            DateField(tr("Check-out"), stay.checkOut, { onChange(stay.copy(checkOut = it)) }, Modifier.weight(1f), clearable = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            Text_(stay.bookingCode, tr("Codice prenotazione"), Modifier.weight(1f), KeyboardCapitalization.Characters) { onChange(stay.copy(bookingCode = it)) }
            Text_(stay.phone, tr("Telefono"), Modifier.weight(1f), keyboardType = KeyboardType.Phone) { onChange(stay.copy(phone = it)) }
        }
    }
}

@Composable
private fun FormCard(title: String, icon: ImageVector, tone: Tone, onRemove: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = tone.container.copy(alpha = 0.45f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(RicordellaDimensions.spaceM), verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(18.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 10.dp))
                if (onRemove != null) {
                    IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = trf("Rimuovi %1\$s", title)) }
                }
            }
            content()
        }
    }
}

/** Campo di testo che salva null quando è vuoto. */
@Composable
private fun Text_(
    value: String?,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValue: (String?) -> Unit,
) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = { onValue(it.ifEmpty { null }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
        modifier = modifier,
    )
}

/* ---------- Dettaglio ---------- */

/** Sezione "Viaggio" nel dettaglio: tratte con icona e alloggio; indirizzo → mappe, telefono → chiamata. */
@Composable
fun TripSection(trip: TripInfo) {
    val context = LocalContext.current
    val format = LocalAppSettings.current.dateFormat
    val colors = MaterialTheme.ricordellaColors
    SectionHeader(tr("Viaggio"), icon = Icons.Rounded.Luggage, tone = colors.mint)
    trip.destination?.let { destination ->
        Row(
            Modifier.fillMaxWidth().clickable { openMaps(context, destination) }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Place, contentDescription = null, tint = colors.coral.solid)
            Text(destination, style = MaterialTheme.typography.titleMedium, textDecoration = TextDecoration.Underline)
        }
    }
    (trip.stay?.address ?: trip.destination)?.let { place ->
        TakeMeThere(place, name = trip.stay?.name?.takeIf { trip.stay.address != null }, onClick = { openDirections(context, place) })
    }
    trip.legs.forEach { leg ->
        FormCard(title = listOfNotNull(leg.from, leg.to).joinToString(" → ").ifEmpty { leg.mode.label }, icon = leg.mode.icon, tone = colors.cyan) {
            val whenText = listOfNotNull(leg.date?.let { DateTexts.date(it, format) }, leg.time?.let(DateTexts::time)).joinToString(" · ")
            if (whenText.isNotEmpty()) InfoRow(tr("Partenza"), whenText)
            listOfNotNull(leg.carrier, leg.code).joinToString(" ").takeIf { it.isNotBlank() }?.let { InfoRow(leg.mode.label, it) }
            leg.seat?.let { InfoRow(leg.mode.seatLabel, it) }
            leg.bookingCode?.let { InfoRow(tr("Prenotazione"), it) }
        }
    }
    trip.stay?.takeIf { !it.isEmpty }?.let { stay ->
        FormCard(title = stay.name ?: tr("Alloggio"), icon = Icons.Rounded.Hotel, tone = colors.lavender) {
            stay.address?.let { address ->
                Text(
                    address,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { openMaps(context, address) },
                )
            }
            stay.checkIn?.let { InfoRow(tr("Check-in"), DateTexts.date(it, format)) }
            stay.checkOut?.let { InfoRow(tr("Check-out"), DateTexts.date(it, format)) }
            stay.bookingCode?.let { InfoRow(tr("Prenotazione"), it) }
            stay.phone?.let { phone ->
                Text(
                    "📞 $phone",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                )
            }
        }
    }
}

/**
 * "Portami lì": pulsante grande a sfumatura verde-azzurra con la freccia di navigazione che
 * oscilla piano, il nome del posto e l'indirizzo. Apre Google Maps con il percorso già pronto.
 */
@Composable
private fun TakeMeThere(place: String, name: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    val interaction = remember { MutableInteractionSource() }
    val nudge = if (rememberReducedMotion()) 0f else rememberInfiniteTransition(label = "nudge").animateFloat(
        -1f,
        1f,
        infiniteRepeatable(tween(1100, easing = RicordellaMotion.EaseInOut), RepeatMode.Reverse),
        label = "nudgeX",
    ).value
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.horizontalGradient(listOf(colors.mint.solid, colors.cyan.solid)))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = tr("Apri il percorso in Google Maps"), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(48.dp).background(Color.White.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Navigation,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = 45f + nudge * 8f },
            )
        }
        Column(Modifier.weight(1f)) {
            Text(tr("Portami lì"), style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                listOfNotNull(name, place).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.graphicsLayer { translationX = nudge * 4.dp.toPx() },
        )
    }
}

/** Apre Google Maps già con il percorso verso il luogo (o il browser se Maps non c'è). */
private fun openDirections(context: Context, place: String) {
    runCatching {
        val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(place))
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openMaps(context: Context, place: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
