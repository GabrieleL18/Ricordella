@file:UseSerializers(LocalDateSerializer::class, LocalTimeSerializer::class)

package com.ricordella.app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate
import java.time.LocalTime

/**
 * Dettagli di una vacanza: destinazione, tratte (aereo, nave, treno, auto) e alloggio.
 * È salvato come JSON nella colonna `trip` del promemoria e viaggia così anche nel backup.
 */
@Serializable
data class TripInfo(
    val destination: String? = null,
    val legs: List<TripLeg> = emptyList(),
    val stay: TripStay? = null,
) {
    val isEmpty: Boolean get() = destination.isNullOrBlank() && legs.isEmpty() && (stay == null || stay.isEmpty)
}

@Serializable
enum class TravelMode { PLANE, SHIP, TRAIN, CAR }

@Serializable
data class TripLeg(
    val mode: TravelMode = TravelMode.PLANE,
    /** Compagnia aerea o di navigazione. */
    val carrier: String? = null,
    /** Numero del volo, della nave o del treno. */
    val code: String? = null,
    val from: String? = null,
    val to: String? = null,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    /** Posto, cabina o carrozza. */
    val seat: String? = null,
)

@Serializable
data class TripStay(
    val name: String? = null,
    val address: String? = null,
    val checkIn: LocalDate? = null,
    val checkOut: LocalDate? = null,
    val bookingCode: String? = null,
    val phone: String? = null,
) {
    val isEmpty: Boolean get() = listOf(name, address, bookingCode, phone).all { it.isNullOrBlank() } && checkIn == null && checkOut == null
}
