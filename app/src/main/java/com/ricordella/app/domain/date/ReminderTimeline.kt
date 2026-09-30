package com.ricordella.app.domain.date

import com.ricordella.app.domain.model.OdometerStatus
import com.ricordella.app.domain.model.Priority
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.ReminderTimeStatus
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.model.isMultiDay
import com.ricordella.app.domain.model.odometerStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Fascia temporale usata per raggruppare i promemoria (Home, Agenda). */
enum class TimeHorizon { OVERDUE, TODAY, NEXT_DAYS, NEXT_WEEKS, LATER }

/**
 * Logica temporale condivisa da Home, Calendario e Promemoria.
 * Lo stato temporale è sempre calcolato e mai memorizzato.
 */
object ReminderTimeline {

    const val NEXT_DAYS_LIMIT = 7L
    const val NEXT_WEEKS_LIMIT = 30L
    private const val URGENT_ATTENTION_DAYS = 7L
    private const val DEADLINE_ATTENTION_DAYS = 14L

    fun timeStatus(reminder: Reminder, now: LocalDateTime): ReminderTimeStatus = when (reminder.status) {
        ReminderStatus.COMPLETED -> ReminderTimeStatus.COMPLETED
        ReminderStatus.CANCELLED -> ReminderTimeStatus.CANCELLED
        ReminderStatus.ACTIVE -> when {
            isOverdue(reminder, now) -> ReminderTimeStatus.OVERDUE
            isOngoing(reminder, now.toLocalDate()) -> ReminderTimeStatus.TODAY
            else -> ReminderTimeStatus.UPCOMING
        }
    }

    fun isOverdue(reminder: Reminder, now: LocalDateTime): Boolean {
        if (reminder.status != ReminderStatus.ACTIVE || !reminder.type.isCompletable) return false
        // Un evento di più giorni è scaduto solo quando è finito l'ultimo giorno.
        reminder.endDate?.let { if (reminder.isMultiDay) return it.isBefore(now.toLocalDate()) }
        val time = reminder.dueTime
        return if (time == null) reminder.dueDate.isBefore(now.toLocalDate())
        else reminder.dueDate.atTime(time).isBefore(now)
    }

    /** Oggi cade dentro il promemoria (il suo giorno, o uno dei giorni di un evento lungo). */
    fun isOngoing(reminder: Reminder, today: LocalDate): Boolean =
        !today.isBefore(reminder.dueDate) && !today.isAfter(reminder.endDate?.takeIf { reminder.isMultiDay } ?: reminder.dueDate)

    fun horizon(reminder: Reminder, now: LocalDateTime): TimeHorizon {
        if (isOverdue(reminder, now)) return TimeHorizon.OVERDUE
        val days = daysUntil(reminder.dueDate, now.toLocalDate())
        return when {
            days <= 0 -> TimeHorizon.TODAY
            days <= NEXT_DAYS_LIMIT -> TimeHorizon.NEXT_DAYS
            days <= NEXT_WEEKS_LIMIT -> TimeHorizon.NEXT_WEEKS
            else -> TimeHorizon.LATER
        }
    }

    /**
     * Un promemoria "richiede attenzione" se è scaduto, se è urgente e vicino,
     * se è una scadenza (garanzia, pagamento, ...) vicina, o se la scadenza a km è prossima.
     */
    fun needsAttention(entry: ReminderWithLinks, now: LocalDateTime): Boolean {
        val reminder = entry.reminder
        if (reminder.status != ReminderStatus.ACTIVE) return false
        if (isOverdue(reminder, now)) return true
        val days = daysUntil(reminder.dueDate, now.toLocalDate())
        val odometer = entry.odometerStatus
        return (reminder.priority == Priority.URGENT && days <= URGENT_ATTENTION_DAYS) ||
            (reminder.type.isDeadlineLike && days in 1..DEADLINE_ATTENTION_DAYS) ||
            (odometer != null && odometer != OdometerStatus.FAR)
    }

    fun daysUntil(date: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, date)

    /** Ordine cronologico: prima la data, poi l'ora (gli "tutto il giorno" prima degli orari). */
    val chronologicalOrder: Comparator<Reminder> =
        compareBy<Reminder> { it.dueDate }.thenBy(nullsFirst()) { it.dueTime }.thenBy { it.title.lowercase() }
}
