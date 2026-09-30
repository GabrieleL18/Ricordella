package com.ricordella.app.domain.date

import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Calcola le occorrenze di una regola di ricorrenza senza mai salvarle nel database.
 *
 * Le occorrenze sono sempre derivate da [RecurrenceRule.startDate], così una ricorrenza
 * mensile iniziata il 31 cade il 28/29 febbraio e torna al 31 nei mesi lunghi.
 */
class RecurrenceCalculator {

    /** Prima occorrenza strettamente successiva ad [after], o null se la regola è terminata. */
    fun nextOccurrenceAfter(rule: RecurrenceRule, after: LocalDate): LocalDate? =
        occurrencesFrom(rule, after.plusDays(1)).firstOrNull()

    /** Occorrenze comprese tra [from] e [to] inclusi. */
    fun occurrencesBetween(rule: RecurrenceRule, from: LocalDate, to: LocalDate): List<LocalDate> =
        occurrencesFrom(rule, from).takeWhile { !it.isAfter(to) }.toList()

    /**
     * Date in cui un promemoria compare nel periodo [from]-[to]: l'occorrenza corrente e,
     * se è attivo e ricorrente, le occorrenze future calcolate dalla regola.
     */
    fun occurrencesInRange(reminder: Reminder, rule: RecurrenceRule?, from: LocalDate, to: LocalDate): List<LocalDate> {
        val current = reminder.dueDate.takeIf { !it.isBefore(from) && !it.isAfter(to) }
        if (rule == null || reminder.status != ReminderStatus.ACTIVE) return listOfNotNull(current)
        val projected = occurrencesBetween(rule, maxOf(from, reminder.dueDate.plusDays(1)), to)
        return listOfNotNull(current) + projected
    }

    /** Sequenza ordinata delle occorrenze a partire da [from] (incluso), limitata da endDate. */
    fun occurrencesFrom(rule: RecurrenceRule, from: LocalDate): Sequence<LocalDate> {
        val interval = rule.interval.coerceAtLeast(1).toLong()
        val lowerBound = maxOf(from, rule.startDate)
        val candidates = when (rule.frequency) {
            RecurrenceFrequency.DAILY -> dailyCandidates(rule.startDate, interval, lowerBound)
            RecurrenceFrequency.WEEKLY -> weeklyCandidates(rule, interval, lowerBound)
            RecurrenceFrequency.MONTHLY -> monthlyCandidates(rule, interval, lowerBound)
            RecurrenceFrequency.YEARLY -> yearlyCandidates(rule, interval, lowerBound)
        }
        return candidates
            .filter { !it.isBefore(lowerBound) }
            .takeWhile { rule.endDate == null || !it.isAfter(rule.endDate) }
    }

    private fun dailyCandidates(start: LocalDate, interval: Long, lowerBound: LocalDate): Sequence<LocalDate> {
        val firstStep = ChronoUnit.DAYS.between(start, lowerBound) / interval
        return generateSequence(firstStep) { it + 1 }.map { start.plusDays(it * interval) }
    }

    private fun weeklyCandidates(rule: RecurrenceRule, interval: Long, lowerBound: LocalDate): Sequence<LocalDate> {
        val start = rule.startDate
        if (rule.daysOfWeek.isEmpty()) {
            val firstStep = ChronoUnit.WEEKS.between(start, lowerBound) / interval
            return generateSequence(firstStep) { it + 1 }.map { start.plusWeeks(it * interval) }
        }
        val days: List<DayOfWeek> = rule.daysOfWeek.sortedBy { it.value }
        val anchorWeek = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstStep = ChronoUnit.WEEKS.between(anchorWeek, lowerBound) / interval
        return generateSequence(firstStep) { it + 1 }.flatMap { step ->
            val weekStart = anchorWeek.plusWeeks(step * interval)
            days.asSequence().map { weekStart.plusDays((it.value - 1).toLong()) }
        }
    }

    private fun monthlyCandidates(rule: RecurrenceRule, interval: Long, lowerBound: LocalDate): Sequence<LocalDate> {
        val startMonth = YearMonth.from(rule.startDate)
        val day = rule.dayOfMonth ?: rule.startDate.dayOfMonth
        val firstStep = (ChronoUnit.MONTHS.between(startMonth, YearMonth.from(lowerBound)) / interval - 1).coerceAtLeast(0)
        return generateSequence(firstStep) { it + 1 }.map { step ->
            startMonth.plusMonths(step * interval).atClampedDay(day)
        }
    }

    private fun yearlyCandidates(rule: RecurrenceRule, interval: Long, lowerBound: LocalDate): Sequence<LocalDate> {
        val start = rule.startDate
        val month = rule.monthOfYear ?: start.monthValue
        val day = rule.dayOfMonth ?: start.dayOfMonth
        val firstStep = ((lowerBound.year - start.year) / interval - 1).coerceAtLeast(0)
        return generateSequence(firstStep) { it + 1 }.map { step ->
            YearMonth.of(start.year + (step * interval).toInt(), month).atClampedDay(day)
        }
    }

    private fun YearMonth.atClampedDay(day: Int): LocalDate = atDay(day.coerceIn(1, lengthOfMonth()))
}
