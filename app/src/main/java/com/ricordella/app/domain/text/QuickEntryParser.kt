package com.ricordella.app.domain.text

import com.ricordella.app.data.calendar.CalendarImporter
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.model.Priority
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Promemoria ricavato da una frase scritta al volo. */
data class QuickEntry(
    val title: String,
    val date: LocalDate,
    val time: LocalTime?,
    val type: ReminderType,
    val recurrence: RecurrenceRule?,
    /** Il testo conteneva una data/ora esplicita (altrimenti si è usato oggi). */
    val dateFound: Boolean,
    /** "urgente", "importante", "!!", "!". */
    val priority: Priority = Priority.NORMAL,
    /** Ultimo giorno per "dal 10 al 15 agosto" o "per 3 giorni". */
    val endDate: LocalDate? = null,
)

/**
 * Capisce frasi come "dentista domani alle 9", "bollo auto 31 gennaio", "ogni lunedì palestra
 * alle 18:30", "compleanno di Marco il 12 marzo", "pagare affitto ogni mese il 5",
 * "call with Anna tomorrow at 3pm". Italiano e inglese, più le parole principali di tedesco,
 * francese e spagnolo ("Zahnarzt morgen um 9", "dentiste demain à 9h", "dentista mañana a las 9"). Tutto sul telefono.
 * Le parti riconosciute (data, ora, ripetizione) vengono tolte: quello che resta è il titolo.
 */
object QuickEntryParser {

    private val months = mapOf(
        "gennaio" to 1, "gen" to 1, "febbraio" to 2, "feb" to 2, "marzo" to 3, "mar" to 3, "aprile" to 4, "apr" to 4,
        "maggio" to 5, "mag" to 5, "giugno" to 6, "giu" to 6, "luglio" to 7, "lug" to 7, "agosto" to 8, "ago" to 8,
        "settembre" to 9, "set" to 9, "ottobre" to 10, "ott" to 10, "novembre" to 11, "nov" to 11, "dicembre" to 12, "dic" to 12,
        "january" to 1, "jan" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6, "jun" to 6,
        "july" to 7, "jul" to 7, "august" to 8, "aug" to 8, "september" to 9, "sep" to 9, "october" to 10, "oct" to 10,
        "november" to 11, "december" to 12, "dec" to 12,
        // Tedesco, francese, spagnolo.
        "januar" to 1, "februar" to 2, "märz" to 3, "maerz" to 3, "mai" to 5, "juni" to 6, "juli" to 7,
        "oktober" to 10, "okt" to 10, "dezember" to 12, "dez" to 12,
        "janvier" to 1, "février" to 2, "fevrier" to 2, "mars" to 3, "avril" to 4, "juin" to 6, "juillet" to 7,
        "août" to 8, "aout" to 8, "septembre" to 9, "octobre" to 10, "novembre" to 11, "décembre" to 12, "decembre" to 12,
        "enero" to 1, "febrero" to 2, "abril" to 4, "mayo" to 5, "junio" to 6, "julio" to 7,
        "septiembre" to 9, "setiembre" to 9, "octubre" to 10, "noviembre" to 11, "diciembre" to 12,
    )
    private val weekdays = mapOf(
        "lunedì" to DayOfWeek.MONDAY, "lunedi" to DayOfWeek.MONDAY, "martedì" to DayOfWeek.TUESDAY, "martedi" to DayOfWeek.TUESDAY,
        "mercoledì" to DayOfWeek.WEDNESDAY, "mercoledi" to DayOfWeek.WEDNESDAY, "giovedì" to DayOfWeek.THURSDAY, "giovedi" to DayOfWeek.THURSDAY,
        "venerdì" to DayOfWeek.FRIDAY, "venerdi" to DayOfWeek.FRIDAY, "sabato" to DayOfWeek.SATURDAY, "domenica" to DayOfWeek.SUNDAY,
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY,
        "montag" to DayOfWeek.MONDAY, "dienstag" to DayOfWeek.TUESDAY, "mittwoch" to DayOfWeek.WEDNESDAY, "donnerstag" to DayOfWeek.THURSDAY,
        "freitag" to DayOfWeek.FRIDAY, "samstag" to DayOfWeek.SATURDAY, "sonntag" to DayOfWeek.SUNDAY,
        "lundi" to DayOfWeek.MONDAY, "mardi" to DayOfWeek.TUESDAY, "mercredi" to DayOfWeek.WEDNESDAY, "jeudi" to DayOfWeek.THURSDAY,
        "vendredi" to DayOfWeek.FRIDAY, "samedi" to DayOfWeek.SATURDAY, "dimanche" to DayOfWeek.SUNDAY,
        "lunes" to DayOfWeek.MONDAY, "martes" to DayOfWeek.TUESDAY, "miércoles" to DayOfWeek.WEDNESDAY, "miercoles" to DayOfWeek.WEDNESDAY,
        "jueves" to DayOfWeek.THURSDAY, "viernes" to DayOfWeek.FRIDAY, "sábado" to DayOfWeek.SATURDAY, "sabado" to DayOfWeek.SATURDAY, "domingo" to DayOfWeek.SUNDAY,
    )
    private val units = mapOf(
        "giorno" to RecurrenceFrequency.DAILY, "giorni" to RecurrenceFrequency.DAILY, "day" to RecurrenceFrequency.DAILY, "days" to RecurrenceFrequency.DAILY,
        "settimana" to RecurrenceFrequency.WEEKLY, "settimane" to RecurrenceFrequency.WEEKLY, "week" to RecurrenceFrequency.WEEKLY, "weeks" to RecurrenceFrequency.WEEKLY,
        "mese" to RecurrenceFrequency.MONTHLY, "mesi" to RecurrenceFrequency.MONTHLY, "month" to RecurrenceFrequency.MONTHLY, "months" to RecurrenceFrequency.MONTHLY,
        "anno" to RecurrenceFrequency.YEARLY, "anni" to RecurrenceFrequency.YEARLY, "year" to RecurrenceFrequency.YEARLY, "years" to RecurrenceFrequency.YEARLY,
        "tag" to RecurrenceFrequency.DAILY, "tage" to RecurrenceFrequency.DAILY, "tagen" to RecurrenceFrequency.DAILY,
        "woche" to RecurrenceFrequency.WEEKLY, "wochen" to RecurrenceFrequency.WEEKLY,
        "monat" to RecurrenceFrequency.MONTHLY, "monate" to RecurrenceFrequency.MONTHLY, "monaten" to RecurrenceFrequency.MONTHLY,
        "jahr" to RecurrenceFrequency.YEARLY, "jahre" to RecurrenceFrequency.YEARLY, "jahren" to RecurrenceFrequency.YEARLY,
        "jour" to RecurrenceFrequency.DAILY, "jours" to RecurrenceFrequency.DAILY, "semaine" to RecurrenceFrequency.WEEKLY, "semaines" to RecurrenceFrequency.WEEKLY,
        "mois" to RecurrenceFrequency.MONTHLY, "an" to RecurrenceFrequency.YEARLY, "ans" to RecurrenceFrequency.YEARLY,
        "année" to RecurrenceFrequency.YEARLY, "années" to RecurrenceFrequency.YEARLY,
        "día" to RecurrenceFrequency.DAILY, "dia" to RecurrenceFrequency.DAILY, "días" to RecurrenceFrequency.DAILY, "dias" to RecurrenceFrequency.DAILY,
        "semana" to RecurrenceFrequency.WEEKLY, "semanas" to RecurrenceFrequency.WEEKLY, "mes" to RecurrenceFrequency.MONTHLY, "meses" to RecurrenceFrequency.MONTHLY,
        "año" to RecurrenceFrequency.YEARLY, "años" to RecurrenceFrequency.YEARLY,
    )

    /** "ogni", "every" e i loro equivalenti in tedesco, francese e spagnolo. */
    private const val EVERY = """(?:ogni|every|jeden|jede|jedes|chaque|tous\s+les|toutes\s+les|cada|todos\s+los|todas\s+las)"""

    private val monthNames = months.keys.sortedByDescending { it.length }.joinToString("|")
    private val dayNames = weekdays.keys.sortedByDescending { it.length }.joinToString("|")
    private val unitNames = units.keys.sortedByDescending { it.length }.joinToString("|")

    /** Parole che restano "appese" al titolo dopo aver tolto data e ora. */
    private val dangling = setOf(
        "il", "lo", "la", "le", "l'", "alle", "all'", "ore", "per", "di", "del", "della", "entro", "a", "ad", "e", "da", "dal",
        "at", "on", "the", "by", "in", "for", "of", "and", "from", "every", "ogni",
        "um", "am", "an", "der", "die", "das", "den", "dem", "zum", "zur", "und", "von", "bis", "jeden", "jede",
        "à", "le", "les", "de", "du", "des", "au", "et", "chaque", "el", "los", "las", "del", "para", "por", "y", "cada",
    )

    fun parse(text: String, now: LocalDateTime): QuickEntry? {
        if (text.isBlank()) return null
        val today = now.toLocalDate()
        val work = Work(text)

        var date: LocalDate? = null
        var time: LocalTime? = null
        var frequency: RecurrenceFrequency? = null
        var interval = 1
        var recurringDays = emptySet<DayOfWeek>()
        var monthDay: Int? = null
        var priority = Priority.NORMAL
        var endDate: LocalDate? = null

        // 0. Importanza: "urgente", "importante", "!!", "!".
        work.take("""\b(urgente|urgent|dringend)\b""")?.let { priority = Priority.URGENT }
        if (priority == Priority.NORMAL) work.take("""\b(importante|important|wichtig)\b""")?.let { priority = Priority.IMPORTANT }
        work.take("""(?<!\S)(!{1,3})(?!\S)""")?.let { m ->
            if (priority == Priority.NORMAL) priority = if (m.groupValues[1].length > 1) Priority.URGENT else Priority.IMPORTANT
        }

        // 1. Ripetizioni: "ogni 2 settimane", "ogni mese il 5", "ogni lunedì", "tutti i giorni", "every week".
        work.take("""\b(?:$EVERY|alle)\s+(\d{1,2})\s+($unitNames)\b""")?.let { m ->
            interval = m.groupValues[1].toInt().coerceAtLeast(1)
            frequency = units.getValue(m.groupValues[2])
        }
        work.take("""\bogni\s+mese\s+il\s+(\d{1,2})\b|\bevery\s+month\s+on\s+the\s+(\d{1,2})(?:st|nd|rd|th)?\b""")?.let { m ->
            frequency = RecurrenceFrequency.MONTHLY
            monthDay = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toInt().coerceIn(1, 31)
        }
        work.take("""\b(?:ogni\s+giorno\s+feriale|(?:nei\s+)?giorni\s+feriali|dal\s+luned[iì]\s+al\s+venerd[iì]|every\s+weekday|on\s+weekdays|weekdays)\b""")?.let {
            frequency = RecurrenceFrequency.WEEKLY
            recurringDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        }
        work.take("""\b(?:ogni\s+(?:fine\s+settimana|weekend)|every\s+weekend)\b""")?.let {
            frequency = RecurrenceFrequency.WEEKLY
            recurringDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }
        // "ogni lunedì", "ogni lunedì e giovedì", "every monday, wednesday and friday".
        work.take("""\b$EVERY\s+($dayNames)(?:\s*(?:,|e|and|und|et|y)\s*($dayNames))?(?:\s*(?:,|e|and|und|et|y)\s*($dayNames))?\b""")?.let { m ->
            frequency = RecurrenceFrequency.WEEKLY
            recurringDays = m.groupValues.drop(1).filter { it.isNotEmpty() }.mapTo(mutableSetOf()) { weekdays.getValue(it) }
        }
        // "ogni sera", "ogni mattina": tutti i giorni, a quell'ora se non ce n'è un'altra.
        var dailyMoment: LocalTime? = null
        if (frequency == null) {
            work.take("""\b(?:ogni|every)\s+(mattina|sera|notte|morning|evening|night)\b""")?.let { m ->
                frequency = RecurrenceFrequency.DAILY
                dailyMoment = when (m.groupValues[1]) {
                    "mattina", "morning" -> LocalTime.of(9, 0)
                    "notte", "night" -> LocalTime.of(23, 0)
                    else -> LocalTime.of(20, 0)
                }
            }
        }
        if (frequency == null) {
            work.take("""\b(?:tutti\s+i|tutte\s+le|$EVERY)\s+($unitNames)\b""")?.let { m -> frequency = units.getValue(m.groupValues[1]) }
        }
        if (frequency == null) {
            work.take("""\b(daily|weekly|monthly|yearly|annually|täglich|taeglich|wöchentlich|woechentlich|monatlich|jährlich|jaehrlich)\b""")?.let { m ->
                frequency = when (m.groupValues[1]) {
                    "daily", "täglich", "taeglich" -> RecurrenceFrequency.DAILY
                    "weekly", "wöchentlich", "woechentlich" -> RecurrenceFrequency.WEEKLY
                    "monthly", "monatlich" -> RecurrenceFrequency.MONTHLY
                    else -> RecurrenceFrequency.YEARLY
                }
            }
        }

        // 2. Ora: "alle 9", "alle 18:30", "ore 15", "at 3pm", "9:30", momenti del giorno.
        work.take("""\b(?:tra|fra|in|dans|en)\s+(?:(\d{1,3})\s*(ore|ora|minuti|minuto|min|hours|hour|minutes|minute|mins|stunden|stunde|minuten|heures|heure|horas|hora|minutos)|(mezz'ora|mezzora|half\s+an\s+hour)|(un'ora|un\s+ora|an\s+hour))\b""")?.let { m ->
            val minutes = when {
                m.groupValues[3].isNotEmpty() -> 30L
                m.groupValues[4].isNotEmpty() -> 60L
                listOf("or", "hour", "stund", "heure", "hora").any { m.groupValues[2].startsWith(it) } -> m.groupValues[1].toLong() * 60
                else -> m.groupValues[1].toLong()
            }
            val at = now.plusMinutes(minutes).withSecond(0).withNano(0)
            date = at.toLocalDate()
            time = at.toLocalTime()
        }
        if (time == null) work.take("""\b(?:alle\s+ore|alle|all'|ore|at|um|à|a\s+las|a\s+la)\s*(\d{1,2})(?:[:.](\d{2}))?(?:\s+(e\s+mezz[ao]|e\s+un\s+quarto|e\s+tre\s+quarti|meno\s+un\s+quarto))?\s*(am|pm)?\b""")?.let { m ->
            val fraction = m.groupValues[3]
            time = clock(m.groupValues[1], m.groupValues[2], m.groupValues[4])?.let { base ->
                when {
                    fraction.startsWith("e mezz") -> base.withMinute(30)
                    fraction == "e un quarto" -> base.withMinute(15)
                    fraction == "e tre quarti" -> base.withMinute(45)
                    fraction.startsWith("meno") -> base.minusHours(1).withMinute(45)
                    else -> base
                }
            }
        }
        if (time == null) work.take("""\b(\d{1,2})\s*(am|pm)\b""")?.let { m -> time = clock(m.groupValues[1], "", m.groupValues[2]) }
        // "9 Uhr", "18 Uhr 30" (tedesco) e "9h", "18h30" (francese).
        if (time == null) work.take("""\b(\d{1,2})\s*uhr(?:\s*(\d{2}))?\b""")?.let { m -> time = clock(m.groupValues[1], m.groupValues[2], "") }
        if (time == null) work.take("""\b(\d{1,2})h(\d{2})?\b""")?.let { m -> time = clock(m.groupValues[1], m.groupValues[2], "") }
        if (time == null) work.take("""\b(\d{1,2})[:.](\d{2})\b""")?.let { m -> time = clock(m.groupValues[1], m.groupValues[2], "") }
        work.take("""\b(stasera|tonight|heute\s+abend|ce\s+soir|esta\s+noche)\b""")?.let { date = today; if (time == null) time = LocalTime.of(20, 0) }
        work.take("""\b(stamattina|stamani|heute\s+morgen|ce\s+matin|esta\s+mañana)\b""")?.let { date = today; if (time == null) time = LocalTime.of(9, 0) }
        work.take("""\b(stanotte)\b""")?.let { date = today; if (time == null) time = LocalTime.of(23, 0) }
        work.take("""\b(mezzogiorno|noon)\b""")?.let { if (time == null) time = LocalTime.NOON }
        // Solo "a cena", non "cena con Luca": la parola resta nel titolo se non c'è la preposizione.
        work.take("""\b(?:a|at|for)\s+(colazione|pranzo|cena|breakfast|lunch|dinner)\b""")?.let { m ->
            if (time == null) time = when (m.groupValues[1]) {
                "colazione", "breakfast" -> LocalTime.of(8, 0)
                "pranzo", "lunch" -> LocalTime.of(13, 0)
                else -> LocalTime.of(20, 0)
            }
        }
        work.take("""\b(?:di\s+|in\s+the\s+)?(mattina|morning|morgens|matin)\b|\bpor\s+la\s+mañana\b""")?.let { if (time == null) time = LocalTime.of(9, 0) }
        work.take("""\b(?:di\s+|nel\s+|in\s+the\s+|l'|por\s+la\s+)?(pomeriggio|afternoon|nachmittags|nachmittag|après-midi|apres-midi|tarde)\b""")?.let { if (time == null) time = LocalTime.of(15, 0) }
        work.take("""\b(?:di\s+|in\s+the\s+|le\s+)?(sera|evening|abends|soir)\b""")?.let { if (time == null) time = LocalTime.of(20, 0) }

        // 3. Data. Prima gli intervalli di più giorni: "dal 10 al 15 agosto", "from 10 to 15 august".
        work.take("""\b(?:dal|from|vom|du|del)\s+(\d{1,2})\.?\s+(?:al|to|until|bis\s+(?:zum\s+)?|au)\s*(\d{1,2})\.?\s+(?:di\s+|of\s+|de\s+)?($monthNames)\.?(?:\s+(\d{4}))?\b""")?.let { m ->
            val month = months.getValue(m.groupValues[3])
            val from = dayMonth(m.groupValues[1].toInt(), month, m.groupValues[4], today)
            val to = from?.let { dayMonth(m.groupValues[2].toInt(), month, it.year.toString(), today) }
            if (from != null && to != null) {
                date = from
                endDate = if (to.isBefore(from)) to.plusMonths(1) else to
            }
        }
        work.take("""\b(fine\s+(?:del\s+)?mese|end\s+of\s+(?:the\s+)?month)\b""")?.let { date = today.with(TemporalAdjusters.lastDayOfMonth()) }
        work.take("""\b(?:nel\s+|questo\s+|this\s+|on\s+the\s+)?(weekend|fine\s+settimana)\b""")?.let {
            date = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        }
        work.take("""\b(?:la\s+)?(prossima\s+settimana|settimana\s+prossima|next\s+week|nächste\s+woche|naechste\s+woche|semaine\s+prochaine|próxima\s+semana|proxima\s+semana)\b""")?.let {
            date = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        }
        work.take("""\b(?:il\s+|le\s+|el\s+)?(prossimo\s+mese|mese\s+prossimo|next\s+month|nächsten\s+monat|naechsten\s+monat|mois\s+prochain|próximo\s+mes|proximo\s+mes)\b""")?.let { date = today.plusMonths(1) }
        work.take("""\b(?:l'|the\s+)?(prossimo\s+anno|anno\s+prossimo|next\s+year)\b""")?.let { date = today.plusYears(1) }
        work.take("""\b(dopodomani|day\s+after\s+tomorrow|übermorgen|uebermorgen|après-demain|apres-demain|pasado\s+mañana|pasado\s+manana)\b""")?.let { date = today.plusDays(2) }
        work.take("""\b(domani|tomorrow|morgen|demain|mañana|manana)\b""")?.let { date = today.plusDays(1) }
        work.take("""\b(oggi|today|heute|aujourd'hui|hoy)\b""")?.let { date = today }
        work.take("""\b(?:tra|fra|in|dans|en)\s+(\d{1,3}|un|una|a|an|einer|einem|une|uno)\s+($unitNames)\b""")?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: 1
            date = when (units.getValue(m.groupValues[2])) {
                RecurrenceFrequency.DAILY -> today.plusDays(n.toLong())
                RecurrenceFrequency.WEEKLY -> today.plusWeeks(n.toLong())
                RecurrenceFrequency.MONTHLY -> today.plusMonths(n.toLong())
                RecurrenceFrequency.YEARLY -> today.plusYears(n.toLong())
            }
        }
        work.take("""\b(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?\b""")?.let { m ->
            date = dayMonth(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3], today)
        }
        if (date == null) {
            work.take("""\b(?:il\s+|l'|on\s+|am\s+|le\s+|el\s+)?(\d{1,2})(?:st|nd|rd|th|\.)?\s+(?:di\s+|of\s+|de\s+)?($monthNames)\.?(?:\s+(?:de\s+)?(\d{4}))?\b""")?.let { m ->
                date = dayMonth(m.groupValues[1].toInt(), months.getValue(m.groupValues[2]), m.groupValues[3], today)
            }
        }
        if (date == null) {
            work.take("""\b($monthNames)\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?\b""")?.let { m ->
                date = dayMonth(m.groupValues[2].toInt(), months.getValue(m.groupValues[1]), m.groupValues[3], today)
            }
        }
        if (date == null) {
            work.take("""\b(?:(?:il|la|this|next|on|am|nächsten|naechsten|le|el)\s+)?(?:prossim[oa]\s+|próximo\s+|proximo\s+)?($dayNames)(?:\s+prossim[oa]|\s+prochain|\s+que\s+viene)?\b""")?.let { m ->
                date = today.with(TemporalAdjusters.next(weekdays.getValue(m.groupValues[1])))
            }
        }
        if (date == null && monthDay == null) {
            work.take("""\b(?:il|the)\s+(\d{1,2})(?:st|nd|rd|th)?\b""")?.let { m ->
                val day = m.groupValues[1].toInt()
                if (day in 1..31) date = nextMonthDay(day, today)
            }
        }

        // "per 3 giorni", "for 3 days": quanto dura a partire dal giorno trovato.
        val lasting = work.take("""\b(?:per|for|für|pour|durante)\s+(\d{1,2})\s+(giorni|days|tage|jours|días|dias)\b""")?.groupValues?.get(1)?.toLong()
        if (time == null) time = dailyMoment

        val dateFound = date != null || time != null || recurringDays.isNotEmpty() || monthDay != null
        val start = date
            ?: recurringDays.takeIf { it.isNotEmpty() }?.minOf { today.with(TemporalAdjusters.nextOrSame(it)) }
            ?: monthDay?.let { nextMonthDay(it, today) }
            // Solo l'ora: oggi se non è ancora passata, altrimenti domani.
            ?: time?.let { if (it.isAfter(now.toLocalTime())) today else today.plusDays(1) }
            ?: today

        val title = work.remaining()
            .split(Regex("\\s+")).filter { it.isNotBlank() }
            .let { words -> words.dropWhile { it.lowercase() in dangling }.dropLastWhile { it.lowercase() in dangling } }
            .joinToString(" ")
            .trim(' ', ',', '.', ';', ':', '-')
            .replaceFirstChar { it.uppercase() }
            .ifEmpty { "Promemoria" }

        val lower = text.lowercase()
        val type = when {
            listOf("sveglia", "svegliami", "wake me", "alarm", "wecker", "weck mich", "réveil", "reveil", "despertador", "despiértame").any { it in lower } && time != null -> ReminderType.ALARM
            else -> CalendarImporter.guessType(title)
        }
        val finalFrequency = frequency ?: if (type == ReminderType.BIRTHDAY) RecurrenceFrequency.YEARLY else null
        val recurrence = finalFrequency?.let { freq ->
            RecurrenceRule(
                frequency = freq,
                interval = interval,
                startDate = start,
                daysOfWeek = recurringDays,
                dayOfMonth = monthDay ?: if (freq == RecurrenceFrequency.MONTHLY || freq == RecurrenceFrequency.YEARLY) start.dayOfMonth else null,
                monthOfYear = if (freq == RecurrenceFrequency.YEARLY) start.monthValue else null,
            )
        }
        val end = endDate ?: lasting?.takeIf { it > 1 }?.let { start.plusDays(it - 1) }
        return QuickEntry(title, start, time, type, recurrence, dateFound, priority, end)
    }

    /** Parole e modi di dire proposti mentre si scrive, per lingua. */
    private val vocabularyIt = listOf(
        "oggi", "domani", "dopodomani", "stamattina", "stasera", "stanotte", "a colazione", "a pranzo", "a cena", "mezzogiorno",
        "alle 9", "alle 18:30", "e mezza", "e un quarto", "tra mezz'ora", "tra 2 ore", "tra 3 giorni", "tra 2 settimane",
        "lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica", "prossima settimana", "nel weekend", "fine mese",
        "mese prossimo", "anno prossimo", "ogni giorno", "ogni settimana", "ogni 2 settimane", "ogni mese", "ogni mese il 5", "ogni anno",
        "ogni lunedì", "ogni sera", "ogni mattina", "giorni feriali", "ogni weekend", "dal 10 al 15 agosto", "per 3 giorni",
        "urgente", "importante", "sveglia", "compleanno", "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio",
        "agosto", "settembre", "ottobre", "novembre", "dicembre",
    )
    private val vocabularyEn = listOf(
        "today", "tomorrow", "tonight", "this morning", "at breakfast", "at lunch", "at dinner", "noon", "at 9", "at 6:30pm",
        "in half an hour", "in 2 hours", "in 3 days", "in 2 weeks", "monday", "tuesday", "wednesday", "thursday", "friday",
        "saturday", "sunday", "next week", "this weekend", "end of month", "next month", "next year", "every day", "every week",
        "every 2 weeks", "every month", "every month on the 5th", "every year", "every monday", "every evening", "every morning",
        "weekdays", "every weekend", "from 10 to 15 august", "for 3 days", "urgent", "important", "alarm", "birthday",
        "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december",
    )

    private val vocabularyDe = listOf(
        "heute", "morgen", "übermorgen", "heute abend", "um 9", "um 18:30", "in 2 Stunden", "in 3 Tagen", "montag", "dienstag",
        "mittwoch", "donnerstag", "freitag", "samstag", "sonntag", "nächste woche", "jeden Tag", "jede Woche", "jeden Monat",
        "jedes Jahr", "jeden montag", "dringend", "wichtig", "wecker", "geburtstag", "januar", "februar", "märz", "april", "mai",
        "juni", "juli", "august", "september", "oktober", "november", "dezember",
    )
    private val vocabularyFr = listOf(
        "aujourd'hui", "demain", "après-demain", "ce soir", "ce matin", "à 9h", "à 18h30", "dans 2 heures", "dans 3 jours", "lundi",
        "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche", "semaine prochaine", "chaque jour", "chaque semaine",
        "chaque mois", "chaque année", "chaque lundi", "urgent", "important", "réveil", "anniversaire", "janvier", "février", "mars",
        "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre",
    )
    private val vocabularyEs = listOf(
        "hoy", "mañana", "pasado mañana", "esta noche", "a las 9", "a las 18:30", "en 2 horas", "en 3 días", "lunes", "martes",
        "miércoles", "jueves", "viernes", "sábado", "domingo", "próxima semana", "cada día", "cada semana", "cada mes", "cada año",
        "cada lunes", "urgente", "importante", "despertador", "cumpleaños", "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    /** Parole proposte quando alla frase manca qualcosa: quando, a che ora, se si ripete, importanza. */
    private fun missingWords(language: String): List<String> = when (language) {
        "en" -> listOf("tomorrow", "monday", "at 9", "every week", "important")
        "de" -> listOf("morgen", "montag", "um 9", "jede Woche", "wichtig")
        "fr" -> listOf("demain", "lundi", "à 9h", "chaque semaine", "important")
        "es" -> listOf("mañana", "lunes", "a las 9", "cada semana", "importante")
        else -> listOf("domani", "lunedì", "alle 9", "ogni settimana", "importante")
    }

    /** Compatibilità: italiano o inglese. */
    fun suggestions(text: String, now: LocalDateTime, english: Boolean, max: Int = 4): List<String> =
        suggestions(text, now, if (english) "en" else "it", max)

    /**
     * Parole da proporre mentre si scrive [text]: prima i completamenti dell'ultima parola
     * ("dom" → "domani", "domenica"; "ogni se" → "ogni settimana", "ogni sera"), poi, a parola
     * finita, quello che manca alla frase (quando, a che ora, se si ripete).
     */
    fun suggestions(text: String, now: LocalDateTime, language: String, max: Int = 4): List<String> {
        val vocabulary = when (language) {
            "en" -> vocabularyEn
            "de" -> vocabularyDe
            "fr" -> vocabularyFr
            "es" -> vocabularyEs
            else -> vocabularyIt
        }
        val lower = text.lowercase().replace('\u2019', '\'')
        if (lower.isNotBlank() && !lower.endsWith(' ')) {
            val words = lower.trimEnd().split(' ')
            // Si prova con le ultime 3, 2, 1 parole: "ogni se" completa meglio di "se".
            val completions = (minOf(3, words.size) downTo 1).flatMap { n ->
                val tail = words.takeLast(n).joinToString(" ")
                if (tail.length < 2) emptyList() else vocabulary.filter { it.lowercase().startsWith(tail) && it.lowercase() != tail }
            }.distinct()
            if (completions.isNotEmpty()) return completions.take(max)
        }
        val entry = parse(text, now) ?: return emptyList()
        val words = missingWords(language)
        return buildList {
            if (entry.date == now.toLocalDate() && !entry.dateFound) addAll(words.subList(0, 2))
            if (entry.time == null) add(words[2])
            if (entry.recurrence == null) add(words[3])
            if (entry.priority == Priority.NORMAL) add(words[4])
        }.take(max)
    }

    /** Inserisce [suggestion]: sostituisce la parte già scritta che completa, altrimenti la aggiunge in fondo. */
    fun applySuggestion(text: String, suggestion: String): String {
        val lower = text.lowercase().replace('\u2019', '\'')
        if (!lower.endsWith(' ')) {
            val words = lower.trimEnd().split(' ')
            for (n in minOf(3, words.size) downTo 1) {
                val tail = words.takeLast(n).joinToString(" ")
                if (tail.isNotEmpty() && suggestion.startsWith(tail)) return text.dropLast(tail.length) + suggestion + " "
            }
        }
        return text.trimEnd().let { if (it.isEmpty()) "" else "$it " } + suggestion + " "
    }

    private fun clock(hours: String, minutes: String, ampm: String): LocalTime? {
        var h = hours.toIntOrNull() ?: return null
        val m = minutes.toIntOrNull() ?: 0
        if (ampm == "pm" && h < 12) h += 12
        if (ampm == "am" && h == 12) h = 0
        return if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
    }

    /** Giorno e mese; senza anno si sceglie la prossima volta che capita (oggi compreso). */
    private fun dayMonth(day: Int, month: Int, year: String, today: LocalDate): LocalDate? {
        if (month !in 1..12) return null
        val explicitYear = year.toIntOrNull()?.let { if (it < 100) 2000 + it else it }
        val base = runCatching { LocalDate.of(explicitYear ?: today.year, month, 1) }.getOrNull() ?: return null
        val date = base.withDayOfMonth(day.coerceIn(1, base.lengthOfMonth()))
        return if (explicitYear == null && date.isBefore(today)) date.plusYears(1) else date
    }

    private fun nextMonthDay(day: Int, today: LocalDate): LocalDate {
        val thisMonth = today.withDayOfMonth(day.coerceAtMost(today.lengthOfMonth()))
        if (!thisMonth.isBefore(today)) return thisMonth
        val next = today.plusMonths(1)
        return next.withDayOfMonth(day.coerceAtMost(next.lengthOfMonth()))
    }

    /** Testo originale da cui si "tolgono" le parti riconosciute (la ricerca avviene sul minuscolo). */
    private class Work(private val original: String) {
        private val removed = BooleanArray(original.length)
        // L'apostrofo curvo delle tastiere (’) vale come quello dritto: stessa lunghezza, indici invariati.
        private val lower = original.lowercase().replace('\u2019', '\'')

        fun take(pattern: String): MatchResult? {
            val regex = cache.getOrPut(pattern) { Regex(pattern.replace("""\b""", WORD_BOUNDARY)) }
            var match = regex.find(lower)
            while (match != null && match.range.any { removed[it] }) match = match.next()
            match?.range?.forEach { removed[it] = true }
            return match
        }

        fun remaining(): String = buildString {
            original.forEachIndexed { i, c -> append(if (removed[i]) ' ' else c) }
        }

        private companion object {
            /**
             * Confine di parola che conta anche le lettere accentate ("lunedì"). Il flag (?U) farebbe
             * lo stesso sul PC, ma le regex di Android non lo supportano e l'app andava in crash.
             */
            const val WORD_BOUNDARY = """(?:(?<![\p{L}\p{N}_])(?=[\p{L}\p{N}_])|(?<=[\p{L}\p{N}_])(?![\p{L}\p{N}_]))"""

            /** Le regex si compilano una volta sola: il testo si analizza a ogni lettera digitata. */
            val cache = java.util.concurrent.ConcurrentHashMap<String, Regex>()
        }
    }
}
