package com.ricordella.app.feature.reminders

import android.content.Context
import android.content.Intent
import android.net.Uri
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
        TravelMode.PLANE -> "Aereo"
        TravelMode.SHIP -> "Nave"
        TravelMode.TRAIN -> "Treno"
        TravelMode.CAR -> "Auto"
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
        TravelMode.PLANE -> "Numero volo"
        TravelMode.SHIP -> "Nave / traghetto"
        TravelMode.TRAIN -> "Numero treno"
        TravelMode.CAR -> "Targa / noleggio"
    }

private val TravelMode.seatLabel: String
    get() = when (this) {
        TravelMode.PLANE -> "Posto"
        TravelMode.SHIP -> "Cabina / posto"
        TravelMode.TRAIN -> "Carrozza e posto"
        TravelMode.CAR -> "Note"
    }

/* ---------- Modulo ---------- */

/** Campi della vacanza: destinazione, tratte (aereo, nave, treno, auto) e alloggio. */
@Composable
fun TripFields(trip: TripInfo, onChange: (TripInfo) -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    SectionHeader("Viaggio", icon = Icons.Rounded.Luggage, tone = colors.mint)
    Text_(
        value = trip.destination,
        label = "Luogo / destinazione",
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
    StayCard(trip.stay ?: TripStay(), onChange = { onChange(trip.copy(stay = it)) })
}

@Composable
private fun LegCard(leg: TripLeg, number: Int, onChange: (TripLeg) -> Unit, onRemove: () -> Unit) {
    FormCard(title = "${leg.mode.label} $number", icon = leg.mode.icon, tone = MaterialTheme.ricordellaColors.cyan, onRemove = onRemove) {
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            Text_(leg.carrier, "Compagnia", Modifier.weight(1f)) { onChange(leg.copy(carrier = it)) }
            Text_(leg.code, leg.mode.codeLabel, Modifier.weight(1f), KeyboardCapitalization.Characters) { onChange(leg.copy(code = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            Text_(leg.from, "Da", Modifier.weight(1f)) { onChange(leg.copy(from = it)) }
            Text_(leg.to, "A", Modifier.weight(1f)) { onChange(leg.copy(to = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            DateField("Partenza", leg.date, { onChange(leg.copy(date = it)) }, Modifier.weight(1.6f), clearable = true)
            TimeField("Ora", leg.time, { onChange(leg.copy(time = it)) }, Modifier.weight(1f))
        }
        Text_(leg.seat, leg.mode.seatLabel) { onChange(leg.copy(seat = it)) }
    }
}

@Composable
private fun StayCard(stay: TripStay, onChange: (TripStay) -> Unit) {
    FormCard(title = "Alloggio", icon = Icons.Rounded.Hotel, tone = MaterialTheme.ricordellaColors.lavender) {
        Text_(stay.name, "Nome (hotel, casa, campeggio...)") { onChange(stay.copy(name = it)) }
        Text_(stay.address, "Indirizzo") { onChange(stay.copy(address = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            DateField("Check-in", stay.checkIn, { onChange(stay.copy(checkIn = it)) }, Modifier.weight(1f), clearable = true)
            DateField("Check-out", stay.checkOut, { onChange(stay.copy(checkOut = it)) }, Modifier.weight(1f), clearable = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            Text_(stay.bookingCode, "Codice prenotazione", Modifier.weight(1f), KeyboardCapitalization.Characters) { onChange(stay.copy(bookingCode = it)) }
            Text_(stay.phone, "Telefono", Modifier.weight(1f), keyboardType = KeyboardType.Phone) { onChange(stay.copy(phone = it)) }
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
                    IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = "Rimuovi $title") }
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
    SectionHeader("Viaggio", icon = Icons.Rounded.Luggage, tone = colors.mint)
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
    trip.legs.forEach { leg ->
        FormCard(title = listOfNotNull(leg.from, leg.to).joinToString(" → ").ifEmpty { leg.mode.label }, icon = leg.mode.icon, tone = colors.cyan) {
            val whenText = listOfNotNull(leg.date?.let { DateTexts.date(it, format) }, leg.time?.let(DateTexts::time)).joinToString(" · ")
            if (whenText.isNotEmpty()) InfoRow("Partenza", whenText)
            listOfNotNull(leg.carrier, leg.code).joinToString(" ").takeIf { it.isNotBlank() }?.let { InfoRow(leg.mode.label, it) }
            leg.seat?.let { InfoRow(leg.mode.seatLabel, it) }
        }
    }
    trip.stay?.takeIf { !it.isEmpty }?.let { stay ->
        FormCard(title = stay.name ?: "Alloggio", icon = Icons.Rounded.Hotel, tone = colors.lavender) {
            stay.address?.let { address ->
                Text(
                    address,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { openMaps(context, address) },
                )
            }
            stay.checkIn?.let { InfoRow("Check-in", DateTexts.date(it, format)) }
            stay.checkOut?.let { InfoRow("Check-out", DateTexts.date(it, format)) }
            stay.bookingCode?.let { InfoRow("Prenotazione", it) }
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

private fun openMaps(context: Context, place: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
