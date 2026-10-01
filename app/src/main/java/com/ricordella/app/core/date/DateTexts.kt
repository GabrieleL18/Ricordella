package com.ricordella.app.core.date

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import com.ricordella.app.domain.model.DateFormatStyle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import com.ricordella.app.core.i18n.Lang

/** Formattazione di date e orari per l'interfaccia, nella lingua dell'app. */
object DateTexts {

    private val locale: Locale get() = Lang.locale
    private val formatters = java.util.concurrent.ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()
    private fun pattern(value: String): DateTimeFormatter = formatters.getOrPut(value to locale) { DateTimeFormatter.ofPattern(value, locale) }
    /** Formato per lingua: inglese, tedesco e spagnolo ordinano giorno e mese in modo diverso dall'italiano. */
    private fun byLanguage(it: String, en: String, de: String, fr: String = it, es: String = it): String =
        when (Lang.code) { "en" -> en; "de" -> de; "fr" -> fr; "es" -> es; else -> it }
    private val numeric get() = pattern(byLanguage("dd/MM/yyyy", "MM/dd/yyyy", "dd.MM.yyyy"))
    private val extendedWithYear get() = pattern(byLanguage("d MMMM yyyy", "MMMM d, yyyy", "d. MMMM yyyy", es = "d 'de' MMMM 'de' yyyy"))
    private val extendedNoYear get() = pattern(byLanguage("d MMMM", "MMMM d", "d. MMMM", es = "d 'de' MMMM"))
    private val dayHeader get() = pattern(byLanguage("EEEE d MMMM", "EEEE, MMMM d", "EEEE, d. MMMM", es = "EEEE, d 'de' MMMM"))
    private val fullDate get() = pattern(byLanguage("EEEE d MMMM yyyy", "EEEE, MMMM d, yyyy", "EEEE, d. MMMM yyyy", es = "EEEE, d 'de' MMMM 'de' yyyy"))
    /** "29 settembre" senza anno, nella lingua dell'app. */
    fun dayMonth(date: LocalDate): String = extendedNoYear.format(date)
    private val time get() = pattern("HH:mm")

    fun date(date: LocalDate, style: DateFormatStyle, today: LocalDate? = null): String = when (style) {
        DateFormatStyle.NUMERIC -> numeric.format(date)
        DateFormatStyle.EXTENDED ->
            if (today != null && today.year == date.year) extendedNoYear.format(date) else extendedWithYear.format(date)
    }

    fun time(value: LocalTime): String = time.format(value)

    fun dateWithTime(date: LocalDate, time: LocalTime?, style: DateFormatStyle, today: LocalDate? = null): String =
        listOfNotNull(date(date, style, today), time?.let(::time)).joinToString(" · ")

    /** Es. "Oggi alle 18:00", "Domani", "Tra 3 giorni alle 09:00". */
    fun relativeWithTime(date: LocalDate, time: LocalTime?, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, date)
        val day = when {
            days == 0L -> tr("Oggi")
            days == 1L -> tr("Domani")
            days == -1L -> tr("Ieri")
            days in 2..6 -> date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() }
            else -> extendedWithYear.format(date)
        }
        return if (time == null) day else trf("%1\$s alle %2\$s", day, time(time))
    }

    /** Intestazione di un giorno in agenda: "OGGI", "DOMANI" o "LUNEDÌ 29 SETTEMBRE". */
    fun dayHeader(date: LocalDate, today: LocalDate): String = when (date) {
        today -> tr("Oggi · ") + dayHeader.format(date)
        today.plusDays(1) -> tr("Domani · ") + dayHeader.format(date)
        else -> dayHeader.format(date)
    }.uppercase(locale)

    /** Es. "lunedì 29 settembre 2026", per descrizioni accessibili. */
    fun fullDate(date: LocalDate): String = fullDate.format(date)

    /** "mercoledì 30 settembre", senza anno. */
    fun weekdayAndDay(date: LocalDate): String = dayHeader.format(date)

    fun monthTitle(month: YearMonth): String =
        month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.uppercase() } + " " + month.year

    fun weekdayShort(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW_STANDALONE, locale).uppercase(locale)

    fun weekdayFull(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() }

    fun money(cents: Long): String = "€ " + String.format(locale, "%,.2f", cents / 100.0)

    fun kilometers(km: Int): String = String.format(locale, tr("%,d km"), km)
}
