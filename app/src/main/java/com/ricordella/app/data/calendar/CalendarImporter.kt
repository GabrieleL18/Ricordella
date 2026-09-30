package com.ricordella.app.data.calendar

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
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
) {
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

    /** Account Google con almeno un calendario sul dispositivo. */
    suspend fun googleAccounts(): List<String> = withContext(Dispatchers.IO) {
        val accounts = sortedSetOf<String>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars.ACCOUNT_NAME),
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(GOOGLE_ACCOUNT_TYPE),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) cursor.getString(0)?.let(accounts::add)
        }
        accounts.toList()
    }

    /** Importa i prossimi 12 mesi; restituisce quanti promemoria sono stati creati. */
    suspend fun import(account: String): Int {
        val drafts = withContext(Dispatchers.IO) { readEvents(account) }
            .filter { reminderDao.countSame(it.reminder.title, it.reminder.dueDate) == 0 }
        drafts.forEach { saveReminder(it) }
        return drafts.size
    }

    private fun readCalendars(account: String): Map<Long, SourceCalendar> {
        val calendars = mutableMapOf<Long, SourceCalendar>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.OWNER_ACCOUNT, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(account, GOOGLE_ACCOUNT_TYPE),
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

    private fun readEvents(account: String): List<ReminderDraft> {
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
                    category = CATEGORY,
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

        private val typeKeywords = listOf(
            ReminderType.BIRTHDAY to listOf("compleanno", "birthday", "buon compleanno"),
            ReminderType.MEDICAL_VISIT to listOf("visita", "medico", "dott.", "dottor", "dentista", "analisi", "oculista", "esami del sangue"),
            ReminderType.PAYMENT to listOf("pagamento", "pagare", "bolletta", "rata", "bonifico", "f24"),
            ReminderType.RENEWAL to listOf("rinnovo", "rinnovare", "abbonamento"),
            ReminderType.MAINTENANCE to listOf("tagliando", "revisione", "manutenzione", "cambio gomme"),
            ReminderType.WARRANTY to listOf("garanzia"),
            ReminderType.DEADLINE to listOf("scadenza", "scade"),
        )

        /** Tipo dedotto da titolo e descrizione (es. "compleanno" nella descrizione → compleanno). */
        fun guessType(title: String, description: String? = null): ReminderType {
            val text = (title + " " + description.orEmpty()).lowercase()
            return typeKeywords.firstOrNull { (_, words) -> words.any { it in text } }?.first ?: ReminderType.EVENT
        }

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
