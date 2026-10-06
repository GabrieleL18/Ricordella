package com.ricordella.app.data.calendar

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.data.local.dao.ReminderDao
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderDraft
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.usecase.SaveReminderUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Importa eventi, feste, compleanni e promemoria di un account Google dal calendario del
 * telefono (CalendarContract): i dati sono già sincronizzati sul dispositivo, quindi non
 * serve Internet. Richiede il permesso READ_CALENDAR, chiesto dalla UI.
 */
class CalendarImporter(
    private val context: Context,
    private val saveReminder: SaveReminderUseCase,
    private val reminderDao: ReminderDao,
    private val time: TimeSource,
    private val trash: com.ricordella.app.data.trash.Trash? = null,
) {
    /** Cosa fare quando un evento c'è già (stesso titolo, stesso giorno). */
    enum class ConflictMode { SKIP, REPLACE, KEEP_BOTH }

    private val resolver get() = context.contentResolver

    /** Tipo di calendario Google, riconosciuto dall'indirizzo del proprietario. */
    private class SourceCalendar(val id: Long, owner: String, name: String) {
        val isHoliday = "#holiday@" in owner
        val isItalian = owner.startsWith("it.") || "ital" in name.lowercase()
        val isBirthdays = "#contacts@" in owner
        val isReminders = listOf("promemoria", "reminder", "task", "attività").any { it in name.lowercase() }
    }

    /** Evento letto, prima della conversione (serve per scartare i doppioni delle feste). */
    private class Found(val draft: ReminderDraft, val calendar: SourceCalendar)

    /** Account (Google, Samsung, calendari locali del telefono...) con almeno un calendario sul dispositivo. */
    data class Account(val name: String, val type: String) {
        val isGoogle get() = type == GOOGLE_ACCOUNT_TYPE

        /** Come lo si mostra: Google = l'indirizzo; Samsung e calendari locali dicono da dove vengono. */
        val label: String
            get() = when {
                isGoogle -> name
                type.contains("samsung", ignoreCase = true) || type == "com.osp.app.signin" -> "Samsung · $name"
                type.contains("local", ignoreCase = true) -> tr("Calendario del telefono") + " · $name"
                else -> name
            }
    }

    suspend fun accounts(): List<Account> = withContext(Dispatchers.IO) {
        val accounts = linkedSetOf<Account>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE),
            null,
            null,
            "${CalendarContract.Calendars.ACCOUNT_TYPE}, ${CalendarContract.Calendars.ACCOUNT_NAME}",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                accounts += Account(name, cursor.getString(1).orEmpty())
            }
        }
        accounts.toList()
    }

    /** Importa i prossimi 12 mesi; restituisce quanti promemoria sono stati creati. */
    suspend fun import(accounts: List<Account>, mode: ConflictMode): Int =
        save(withContext(Dispatchers.IO) { accounts.flatMap { readEvents(it) } }, mode)

    /** Importa gli eventi di un file .ics (esportato da qualsiasi calendario); niente permessi. */
    suspend fun importIcs(uri: Uri, mode: ConflictMode): Int {
        val drafts = withContext(Dispatchers.IO) {
            val text = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("file illeggibile")
            parseIcs(text, time.zone, time.today(), time.now())
        }
        return save(drafts, mode)
    }

    private suspend fun save(drafts: List<ReminderDraft>, mode: ConflictMode): Int {
        var count = 0
        for (draft in drafts) {
            val same = reminderDao.findSameIds(draft.reminder.title, draft.reminder.dueDate)
            when {
                same.isEmpty() || mode == ConflictMode.KEEP_BOTH -> Unit
                mode == ConflictMode.SKIP -> continue
                else -> {
                    // Sostituisce: quello che c'era finisce nel Cestino, si può recuperare.
                    trash?.saveReminders(same)
                    reminderDao.deleteAllWithDependencies(same)
                }
            }
            saveReminder(draft)
            count++
        }
        return count
    }

    private fun readCalendars(account: Account): Map<Long, SourceCalendar> {
        val calendars = mutableMapOf<Long, SourceCalendar>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.OWNER_ACCOUNT, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(account.name, account.type),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                calendars[id] = SourceCalendar(id, cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty())
            }
        }
        return calendars
    }

    /** Anticipo della prima notifica impostata sull'evento in Google Calendar, se c'è. */
    private fun alertMinutes(eventIds: Collection<Long>): Map<Long, Int> {
        if (eventIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<Long, Int>()
        resolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.EVENT_ID, CalendarContract.Reminders.MINUTES),
            "${CalendarContract.Reminders.EVENT_ID} IN (${eventIds.joinToString()})",
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val minutes = cursor.getInt(1).takeIf { it >= 0 } ?: continue
                result.merge(cursor.getLong(0), minutes, ::maxOf)
            }
        }
        return result
    }

    private fun readEvents(account: Account): List<ReminderDraft> {
        val calendars = readCalendars(account)
        if (calendars.isEmpty()) return emptyList()

        val start = time.today().atStartOfDay(time.zone).toInstant().toEpochMilli()
        val end = time.today().plusYears(1).atStartOfDay(time.zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.END,
        )
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN (${calendars.keys.joinToString()}) AND " +
            "${CalendarContract.Instances.STATUS} != ${CalendarContract.Instances.STATUS_CANCELED}"

        // Un evento ricorrente ha molte istanze: si tiene solo la prima, con la sua regola.
        // Le feste invece si prendono tutte: ognuna ha la sua data e ogni gennaio passa all'anno dopo.
        val seen = HashSet<Long>()
        val found = mutableListOf<Found>()
        val eventIds = mutableMapOf<Found, Long>()
        val now = time.now()
        resolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val calendar = calendars[cursor.getLong(7)] ?: continue
                val eventId = cursor.getLong(0)
                if (!seen.add(eventId) && !calendar.isHoliday) continue
                val title = cursor.getString(1)?.trim().orEmpty().ifEmpty { "Evento" }
                val notes = cursor.getString(2)?.trim()?.takeIf { it.isNotEmpty() }
                val begin = Instant.ofEpochMilli(cursor.getLong(3))
                val allDay = cursor.getInt(4) == 1
                // Gli eventi "tutto il giorno" sono salvati a mezzanotte UTC.
                val dateTime = if (allDay) LocalDateTime.ofInstant(begin, ZoneOffset.UTC) else LocalDateTime.ofInstant(begin, time.zone)
                val location = cursor.getString(6)?.takeIf { it.isNotBlank() }
                // Eventi di più giorni: la fine "tutto il giorno" è esclusa (mezzanotte del giorno dopo).
                val endInstant = Instant.ofEpochMilli(cursor.getLong(8))
                val lastDay = if (allDay) LocalDateTime.ofInstant(endInstant, ZoneOffset.UTC).toLocalDate().minusDays(1)
                else LocalDateTime.ofInstant(endInstant, time.zone).toLocalDate()
                val type = when {
                    calendar.isHoliday -> ReminderType.HOLIDAY
                    calendar.isBirthdays -> ReminderType.BIRTHDAY
                    else -> guessType(title, notes).let { if (it == ReminderType.EVENT && calendar.isReminders) ReminderType.TASK else it }
                }
                val reminder = Reminder(
                    title = title,
                    description = listOfNotNull(notes, location?.let { "Luogo: $it" }).joinToString("\n").ifEmpty { null },
                    type = type,
                    dueDate = dateTime.toLocalDate(),
                    dueTime = if (allDay) null else dateTime.toLocalTime(),
                    endDate = lastDay.takeIf { it.isAfter(dateTime.toLocalDate()) },
                    category = if (account.isGoogle) CATEGORY else CATEGORY_OTHER,
                    // Le feste si vedono nel calendario ma non disturbano con notifiche.
                    notificationsEnabled = type != ReminderType.HOLIDAY,
                    createdAt = now,
                    updatedAt = now,
                )
                val recurrence = when (type) {
                    ReminderType.HOLIDAY -> null
                    // Un compleanno si ripete ogni anno anche se su Google non era impostato.
                    ReminderType.BIRTHDAY -> parseRRule(cursor.getString(5), reminder.dueDate)
                        ?: RecurrenceRule(frequency = RecurrenceFrequency.YEARLY, startDate = reminder.dueDate)
                    else -> parseRRule(cursor.getString(5), reminder.dueDate)
                }
                val item = Found(ReminderDraft(reminder, recurrence, emptySet(), emptySet()), calendar)
                found += item
                eventIds[item] = eventId
            }
        }

        val alerts = alertMinutes(eventIds.values.toSet())
        return keepItalianHolidays(found).map { item ->
            val minutes = alerts[eventIds.getValue(item)]
            if (minutes == null || item.draft.reminder.type == ReminderType.HOLIDAY) item.draft
            else item.draft.copy(reminder = item.draft.reminder.copy(notifyOffsetMinutes = minutes))
        }
    }

    /**
     * Più calendari di festività possono riportare la stessa festa nello stesso giorno in lingue
     * diverse: in quel giorno si tiene solo la versione italiana (e mai due volte lo stesso titolo).
     */
    private fun keepItalianHolidays(found: List<Found>): List<Found> {
        val (holidays, others) = found.partition { it.calendar.isHoliday }
        val kept = holidays.groupBy { it.draft.reminder.dueDate }.values.flatMap { sameDay ->
            val italian = sameDay.filter { it.calendar.isItalian }
            italian.ifEmpty { sameDay }.distinctBy { it.draft.reminder.title.lowercase() }
        }
        return others + kept
    }

    companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        const val CATEGORY = "Google Calendar"
        const val CATEGORY_OTHER = "Calendario importato"

        // Italiano e inglese, più le parole più comuni di tedesco, francese e spagnolo.
        private val typeKeywords = listOf(
            ReminderType.BIRTHDAY to listOf("compleanno", "birthday", "buon compleanno", "geburtstag", "anniversaire", "cumpleaños", "cumpleanos"),
            ReminderType.VACATION to listOf("vacanza", "vacanze", "ferie", "viaggio", "vacation", "holiday trip", "urlaub", "vacances", "voyage", "vacaciones", "viaje"),
            ReminderType.MEDICAL_VISIT to listOf(
                "visita", "medico", "dott.", "dottor", "dentista", "analisi", "oculista", "esami del sangue",
                "doctor", "dentist", "arzt", "médecin", "medecin", "dentiste", "médico", "cita médica",
            ),
            ReminderType.PAYMENT to listOf(
                "pagamento", "pagare", "bolletta", "rata", "bonifico", "f24",
                "pay ", "bill", "bezahlen", "rechnung", "miete", "payer", "facture", "loyer", "pagar", "factura", "alquiler",
            ),
            ReminderType.RENEWAL to listOf("rinnovo", "rinnovare", "abbonamento", "renew", "subscription", "verlängern", "renouveler", "abonnement", "renovar", "suscripción"),
            ReminderType.MAINTENANCE to listOf("tagliando", "revisione", "manutenzione", "cambio gomme", "service", "inspektion", "wartung", "entretien", "mantenimiento"),
            ReminderType.WARRANTY to listOf("garanzia", "warranty", "garantie", "garantía"),
            ReminderType.DEADLINE to listOf("scadenza", "scade", "deadline", "frist", "échéance", "vencimiento"),
        )

        /** Tipo dedotto da titolo e descrizione (es. "compleanno" nella descrizione → compleanno). */
        fun guessType(title: String, description: String? = null): ReminderType {
            val text = (title + " " + description.orEmpty()).lowercase()
            return typeKeywords.firstOrNull { (_, words) -> words.any { it in text } }?.first ?: ReminderType.EVENT
        }

        /**
         * Legge i VEVENT di un file iCalendar. Si tengono gli eventi futuri e quelli ricorrenti;
         * gli eventi singoli già passati si scartano (come l'import dal telefono, che parte da oggi).
         * ponytail: niente VTIMEZONE (si usa il TZID se è un fuso Java valido, altrimenti il fuso
         * del telefono), niente EXDATE/RDATE; estendere se serve.
         */
        fun parseIcs(text: String, zone: ZoneId, today: LocalDate, now: Instant): List<ReminderDraft> {
            val result = mutableListOf<ReminderDraft>()
            var event: MutableMap<String, Pair<Map<String, String>, String>>? = null
            var alarmMinutes: Int? = null
            for (line in text.replace(Regex("\r?\n[ \t]"), "").lines()) {
                val colon = line.indexOf(':')
                if (colon < 0) continue
                val head = line.substring(0, colon).split(';')
                val name = head[0].uppercase()
                val value = line.substring(colon + 1)
                when {
                    name == "BEGIN" && value == "VEVENT" -> { event = mutableMapOf(); alarmMinutes = null }
                    name == "END" && value == "VEVENT" -> {
                        event?.let { icsDraft(it, alarmMinutes, zone, today, now) }?.let(result::add)
                        event = null
                    }
                    event == null -> Unit
                    name == "TRIGGER" -> alarmMinutes = parseTrigger(value)
                    else -> event.putIfAbsent(name, head.drop(1).mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 } }.associate { it[0].uppercase() to it[1] } to value)
                }
            }
            return result
        }

        private fun icsDraft(p: Map<String, Pair<Map<String, String>, String>>, alert: Int?, zone: ZoneId, today: LocalDate, now: Instant): ReminderDraft? {
            if (p["STATUS"]?.second.equals("CANCELLED", true)) return null
            val (startParams, startValue) = p["DTSTART"] ?: return null
            val allDay = startParams["VALUE"] == "DATE" || startValue.length == 8
            val start = icsDateTime(startValue, startParams["TZID"], zone) ?: return null
            val end = p["DTEND"]?.let { icsDateTime(it.second, it.first["TZID"], zone) }
            val first = start.toLocalDate()
            // Un evento "tutto il giorno" finisce il giorno prima della data di DTEND.
            val lastDay = end?.toLocalDate()?.let { if (allDay) it.minusDays(1) else it } ?: first
            val rrule = p["RRULE"]?.second
            if (rrule == null && lastDay.isBefore(today)) return null

            fun text(key: String) = p[key]?.second?.let(::unescapeIcs)?.trim()?.takeIf { it.isNotEmpty() }
            val title = text("SUMMARY") ?: "Evento"
            val notes = text("DESCRIPTION")
            val location = text("LOCATION")
            val type = guessType(title, notes)
            val reminder = Reminder(
                title = title,
                description = listOfNotNull(notes, location?.let { "Luogo: $it" }).joinToString("\n").ifEmpty { null },
                type = type,
                dueDate = first,
                dueTime = if (allDay) null else start.toLocalTime(),
                endDate = lastDay.takeIf { it.isAfter(first) },
                category = CATEGORY_OTHER,
                notifyOffsetMinutes = alert ?: 0,
                createdAt = now,
                updatedAt = now,
            )
            val recurrence = parseRRule(rrule, first)
                ?: RecurrenceRule(frequency = RecurrenceFrequency.YEARLY, startDate = first).takeIf { type == ReminderType.BIRTHDAY }
            return ReminderDraft(reminder, recurrence, emptySet(), emptySet())
        }

        /** "20261225", "20261225T100000" (ora locale) o "...Z" (UTC); TZID se è un fuso valido. */
        private fun icsDateTime(value: String, tzid: String?, phoneZone: ZoneId): LocalDateTime? = runCatching {
            if (value.length == 8) return@runCatching LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay()
            val local = LocalDateTime.parse(value.removeSuffix("Z"), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            val source = if (value.endsWith("Z")) ZoneOffset.UTC else tzid?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: phoneZone
            local.atZone(source).withZoneSameInstant(phoneZone).toLocalDateTime()
        }.getOrNull()

        /** "-PT15M", "-PT1H", "-P1D" → minuti di anticipo; nulla se l'allarme è dopo l'evento. */
        private fun parseTrigger(value: String): Int? {
            val m = Regex("^-P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?)?$").find(value.trim()) ?: return null
            val (d, h, min) = m.destructured
            return (d.toIntOrNull() ?: 0) * 1440 + (h.toIntOrNull() ?: 0) * 60 + (min.toIntOrNull() ?: 0)
        }

        private fun unescapeIcs(s: String) = s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

        /**
         * Converte la parte semplice di una RRULE (FREQ, INTERVAL, BYDAY, UNTIL).
         * ponytail: COUNT e regole complesse (BYSETPOS, BYMONTHDAY multipli) vengono approssimate
         * dalla sola frequenza; estendere se gli utenti importano calendari con regole articolate.
         */
        fun parseRRule(rrule: String?, start: LocalDate): RecurrenceRule? {
            if (rrule.isNullOrBlank()) return null
            val parts = rrule.removePrefix("RRULE:").split(';').mapNotNull {
                val (key, value) = it.split('=', limit = 2).takeIf { kv -> kv.size == 2 } ?: return@mapNotNull null
                key.uppercase() to value
            }.toMap()
            val frequency = when (parts["FREQ"]?.uppercase()) {
                "DAILY" -> RecurrenceFrequency.DAILY
                "WEEKLY" -> RecurrenceFrequency.WEEKLY
                "MONTHLY" -> RecurrenceFrequency.MONTHLY
                "YEARLY" -> RecurrenceFrequency.YEARLY
                else -> return null
            }
            val days = if (frequency == RecurrenceFrequency.WEEKLY) {
                parts["BYDAY"]?.split(',')?.mapNotNull { WEEKDAYS[it.takeLast(2).uppercase()] }?.toSet().orEmpty()
            } else {
                emptySet()
            }
            val until = parts["UNTIL"]?.take(8)?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull() }
            return RecurrenceRule(
                frequency = frequency,
                interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                startDate = start,
                endDate = until,
                daysOfWeek = days,
                dayOfMonth = if (frequency == RecurrenceFrequency.MONTHLY || frequency == RecurrenceFrequency.YEARLY) start.dayOfMonth else null,
                monthOfYear = if (frequency == RecurrenceFrequency.YEARLY) start.monthValue else null,
            )
        }

        private val WEEKDAYS = mapOf(
            "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY, "TH" to DayOfWeek.THURSDAY,
            "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
        )
    }
}
