package com.ricordella.app.domain.date

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Spostamento delle feste all'anno successivo. Le feste fisse restano nello stesso giorno;
 * quelle legate alla Pasqua ne seguono la data; quelle "n-esima domenica del mese"
 * (festa della mamma, cambio d'ora) mantengono la posizione nel mese.
 */
object Holidays {

    private val easterRelated = listOf(
        "pasqua", "pasquetta", "lunedì dell'angelo", "lunedi dell'angelo", "ceneri", "pentecoste", "ascensione", "corpus domini", "palme",
        "venerdì santo", "venerdi santo", "giovedì santo", "giovedi santo", "sabato santo",
        "carnevale", "martedì grasso", "martedi grasso", "giovedì grasso", "giovedi grasso",
        "easter", "good friday", "ash wednesday", "pentecost", "ascension",
    )

    // ponytail: solo le feste mobili "a settimana" più comuni in Italia; aggiungere parole se ne compaiono altre.
    private val weekdayBased = listOf("festa della mamma", "ora legale", "ora solare", "mother's day", "daylight saving")

    /** Domenica di Pasqua (calendario gregoriano, algoritmo di Meeus/Jones/Butcher). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    /** Data della festa [title], che cadeva in [date], nell'anno [year]. */
    fun moveToYear(title: String, date: LocalDate, year: Int): LocalDate {
        val t = title.lowercase()
        return when {
            easterRelated.any { it in t } -> {
                val offset = date.toEpochDay() - easter(date.year).toEpochDay()
                easter(year).plusDays(offset)
            }
            weekdayBased.any { it in t } -> {
                val month = date.withYear(year).withDayOfMonth(1)
                if (date.dayOfMonth > date.lengthOfMonth() - 7) {
                    month.with(TemporalAdjusters.lastInMonth(date.dayOfWeek))
                } else {
                    month.with(TemporalAdjusters.dayOfWeekInMonth((date.dayOfMonth - 1) / 7 + 1, date.dayOfWeek))
                }
            }
            else -> date.withYear(year) // il 29 febbraio diventa 28 negli anni non bisestili
        }
    }
}
