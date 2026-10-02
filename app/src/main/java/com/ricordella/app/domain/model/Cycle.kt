@file:UseSerializers(LocalDateSerializer::class)

package com.ricordella.app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Ciclo mestruale di una persona: quanto dura la mestruazione e ogni quanti giorni torna.
 * Dato sensibile: vive solo nelle impostazioni del telefono (non nel file condiviso).
 * [notifyLate]: avviso se il ciclo è in ritardo (di default solo per il ciclo di chi usa l'app).
 */
@Serializable
data class CycleProfile(
    val personId: String,
    val periodDays: Int = 5,
    val cycleDays: Int = 28,
    val notifyLate: Boolean = false,
)

/** Una mestruazione vera: inizio e, quando finisce, fine. Senza fine vale [CycleProfile.periodDays]. */
@Serializable
data class CycleEntry(val personId: String, val start: LocalDate, val end: LocalDate? = null)

/** Un giorno di ciclo da mostrare: [predicted] = previsto, non ancora avvenuto. */
data class CycleDay(val personId: String, val predicted: Boolean)

/** Una riga della cronologia: la lunghezza del ciclo è la distanza dall'inizio del ciclo precedente. */
data class CycleHistoryRow(val entry: CycleEntry, val days: Int, val cycleLength: Int?)

object CycleCalendar {
    /** La cronologia si tiene per due anni. */
    const val HISTORY_YEARS = 2L

    /** Ultimo giorno della mestruazione [entry]. */
    fun lastDay(entry: CycleEntry, profile: CycleProfile): LocalDate = entry.end ?: entry.start.plusDays(profile.periodDays - 1L)

    private fun entriesOf(personId: String, log: List<CycleEntry>) = log.filter { it.personId == personId }.sortedBy { it.start }

    /** Prossimo inizio previsto: ultimo inizio vero + durata del ciclo. Null se non c'è ancora nessun inizio. */
    fun nextStart(profile: CycleProfile, log: List<CycleEntry>): LocalDate? =
        entriesOf(profile.personId, log).lastOrNull()?.start?.plusDays(profile.cycleDays.toLong())

    /** Giorni di ritardo rispetto alla previsione (0 se non in ritardo). */
    fun lateDays(profile: CycleProfile, log: List<CycleEntry>, today: LocalDate): Int =
        nextStart(profile, log)?.let { ChronoUnit.DAYS.between(it, today).toInt().coerceAtLeast(0) } ?: 0

    /** Giorno N della mestruazione in corso oggi, se c'è. */
    fun currentDay(profile: CycleProfile, log: List<CycleEntry>, today: LocalDate): Int? {
        val last = entriesOf(profile.personId, log).lastOrNull() ?: return null
        return if (today in last.start..lastDay(last, profile)) ChronoUnit.DAYS.between(last.start, today).toInt() + 1 else null
    }

    /** True se l'ultima mestruazione è iniziata e non è stata ancora chiusa. */
    fun isOpen(profile: CycleProfile, log: List<CycleEntry>, today: LocalDate): Boolean =
        entriesOf(profile.personId, log).lastOrNull()?.let { it.end == null && today <= lastDay(it, profile) } == true

    /** I giorni veri e previsti tra [from] e [to] (inclusi): le previsioni ripartono dall'ultimo inizio. */
    fun days(profile: CycleProfile, log: List<CycleEntry>, from: LocalDate, to: LocalDate): Map<LocalDate, CycleDay> {
        val result = HashMap<LocalDate, CycleDay>()
        val entries = entriesOf(profile.personId, log)
        entries.forEach { entry ->
            var day = maxOf(entry.start, from)
            val last = minOf(lastDay(entry, profile), to)
            while (!day.isAfter(last)) { result[day] = CycleDay(profile.personId, predicted = false); day = day.plusDays(1) }
        }
        val lastStart = entries.lastOrNull()?.start ?: return result
        var start = lastStart.plusDays(profile.cycleDays.toLong().coerceAtLeast(1))
        var guard = 0
        while (!start.isAfter(to) && guard++ < 60) {
            for (i in 0 until profile.periodDays) {
                val day = start.plusDays(i.toLong())
                if (day in from..to) result.putIfAbsent(day, CycleDay(profile.personId, predicted = true))
            }
            start = start.plusDays(profile.cycleDays.toLong().coerceAtLeast(1))
        }
        return result
    }

    /** Cronologia degli ultimi due anni, dalla più recente. */
    fun history(profile: CycleProfile, log: List<CycleEntry>, today: LocalDate): List<CycleHistoryRow> {
        val all = entriesOf(profile.personId, log)
        val since = today.minusYears(HISTORY_YEARS)
        return all.mapIndexedNotNull { i, entry ->
            if (entry.start.isBefore(since)) return@mapIndexedNotNull null
            val days = ChronoUnit.DAYS.between(entry.start, lastDay(entry, profile)).toInt() + 1
            val length = all.getOrNull(i - 1)?.let { ChronoUnit.DAYS.between(it.start, entry.start).toInt() }
            CycleHistoryRow(entry, days, length)
        }.reversed()
    }

    /** Registra l'inizio il [date] (se non c'è già una mestruazione che lo comprende) e toglie ciò che supera i due anni. */
    fun withStart(log: List<CycleEntry>, profile: CycleProfile, date: LocalDate): List<CycleEntry> {
        val covered = entriesOf(profile.personId, log).any { date in it.start..lastDay(it, profile) }
        val added = if (covered) log else log + CycleEntry(profile.personId, date)
        return added.filter { !it.start.isBefore(date.minusYears(HISTORY_YEARS)) }
    }

    /** Chiude l'ultima mestruazione già iniziata il [date]. */
    fun withEnd(log: List<CycleEntry>, personId: String, date: LocalDate): List<CycleEntry> {
        val open = log.filter { it.personId == personId && it.start <= date }.maxByOrNull { it.start } ?: return log
        return log.map { if (it == open) it.copy(end = date) else it }
    }
}
