package com.ricordella.app.domain.date

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

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
            days == 0L -> tr("oggi")
            days == 1L -> tr("domani")
            days == -1L -> tr("ieri")
            days > 0 -> trf("tra %1\$s", amount(date, today, days))
            else -> trf("%1\$s fa", amount(date, today, days))
        }
    }

    /** Es. "Scade oggi", "Scade tra 5 giorni", "Scaduto da 2 giorni". */
    fun describeDeadline(date: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> tr("Scade oggi")
            days == 1L -> tr("Scade domani")
            days > 0 -> trf("Scade tra %1\$s", amount(date, today, days))
            days == -1L -> tr("Scaduto ieri")
            else -> trf("Scaduto da %1\$s", amount(date, today, days))
        }
    }

    private fun amount(date: LocalDate, today: LocalDate, days: Long): String {
        if (abs(days) < MONTHS_THRESHOLD_DAYS) return plural(abs(days), tr("giorno"), tr("giorni"))
        val months = abs(ChronoUnit.MONTHS.between(today, date))
        return if (months >= 24) plural(months / 12, tr("anno"), tr("anni")) else plural(months, tr("mese"), tr("mesi"))
    }

    private fun plural(count: Long, singular: String, pluralForm: String) =
        if (count == 1L) "1 $singular" else "$count $pluralForm"
}
