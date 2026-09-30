package com.ricordella.app.domain.date

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Descrizioni testuali relative ("tra 5 giorni", "scaduto da 2 giorni"), in italiano. */
object RelativeDateDescriber {

    private const val MONTHS_THRESHOLD_DAYS = 60

    /** Es. "oggi", "domani", "tra 5 giorni", "3 giorni fa", "tra 4 mesi". */
    fun describe(date: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> "oggi"
            days == 1L -> "domani"
            days == -1L -> "ieri"
            days > 0 -> "tra ${amount(date, today, days)}"
            else -> "${amount(date, today, days)} fa"
        }
    }

    /** Es. "Scade oggi", "Scade tra 5 giorni", "Scaduto da 2 giorni". */
    fun describeDeadline(date: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> "Scade oggi"
            days == 1L -> "Scade domani"
            days > 0 -> "Scade tra ${amount(date, today, days)}"
            days == -1L -> "Scaduto ieri"
            else -> "Scaduto da ${amount(date, today, days)}"
        }
    }

    private fun amount(date: LocalDate, today: LocalDate, days: Long): String {
        if (abs(days) < MONTHS_THRESHOLD_DAYS) return plural(abs(days), "giorno", "giorni")
        val months = abs(ChronoUnit.MONTHS.between(today, date))
        return if (months >= 24) plural(months / 12, "anno", "anni") else plural(months, "mese", "mesi")
    }

    private fun plural(count: Long, singular: String, pluralForm: String) =
        if (count == 1L) "1 $singular" else "$count $pluralForm"
}
