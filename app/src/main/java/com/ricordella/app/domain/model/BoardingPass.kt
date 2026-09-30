package com.ricordella.app.domain.model

import java.time.LocalDate

/**
 * Legge il codice a barre di una carta d'imbarco (standard IATA BCBP, formato "M"), quello che
 * usa anche Google Wallet. Restituisce una tratta aerea per ogni volo, o null se non è una carta d'imbarco.
 * L'anno non è nel codice: si prende il primo giorno utile da una settimana fa in poi.
 */
object BoardingPass {
    private const val LEG_BLOCK = 37

    fun parse(raw: String, today: LocalDate): List<TripLeg>? {
        if (raw.length < 60 || raw[0] != 'M') return null
        val count = raw[1].digitToIntOrNull()?.takeIf { it in 1..4 } ?: return null
        val legs = mutableListOf<TripLeg>()
        var pos = 23
        repeat(count) {
            if (raw.length < pos + LEG_BLOCK) return legs.ifEmpty { null }
            val block = raw.substring(pos, pos + LEG_BLOCK)
            val carrier = block.substring(13, 16).trim()
            val number = block.substring(16, 21).trim().trimStart('0')
            legs += TripLeg(
                mode = TravelMode.PLANE,
                carrier = carrier.ifEmpty { null },
                code = (carrier + number).ifEmpty { null },
                from = block.substring(7, 10).trim().ifEmpty { null },
                to = block.substring(10, 13).trim().ifEmpty { null },
                date = block.substring(21, 24).trim().toIntOrNull()?.let { dayOfYear(it, today) },
                seat = block.substring(25, 29).trim().trimStart('0').ifEmpty { null },
                bookingCode = block.substring(0, 7).trim().ifEmpty { null },
            )
            val conditional = block.substring(35, 37).toIntOrNull(16) ?: 0
            pos += LEG_BLOCK + conditional
        }
        return legs
    }

    private fun dayOfYear(day: Int, today: LocalDate): LocalDate? {
        if (day !in 1..366) return null
        val from = today.minusDays(7)
        return (from.year..from.year + 4).firstNotNullOfOrNull { year ->
            runCatching { LocalDate.ofYearDay(year, day) }.getOrNull()?.takeIf { !it.isBefore(from) }
        }
    }
}
