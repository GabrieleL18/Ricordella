package com.ricordella.app.domain.model

import kotlinx.serialization.Serializable

/** Tipo di voce nel registro di una cosa: intervento di manutenzione, rifornimento o altra spesa. */
@Serializable
enum class ExpenseKind { SERVICE, FUEL, OTHER }

/** Riepilogo dei costi di una cosa (o di tutte) per l'anno [year]. */
data class ExpenseStats(
    val year: Int,
    val totalCents: Long,
    val previousYearCents: Long,
    val byKind: Map<ExpenseKind, Long>,
    /** Consumo medio in km/l calcolato dai rifornimenti con chilometri e litri; null se non calcolabile. */
    val kmPerLiter: Double?,
    /** Costo per km nell'anno (spese / km percorsi tra il primo e l'ultimo dato), null se non calcolabile. */
    val centsPerKm: Double?,
) {
    companion object {
        fun of(records: List<MaintenanceRecord>, year: Int): ExpenseStats {
            val thisYear = records.filter { it.date.year == year }
            val total = thisYear.sumOf { it.costCents ?: 0 }
            val byKind = thisYear.groupBy { it.kind }.mapValues { (_, list) -> list.sumOf { it.costCents ?: 0 } }.filterValues { it > 0 }

            // Consumo: tra due rifornimenti consecutivi (in ordine di km) i km percorsi divisi
            // per i litri messi nel secondo, il classico metodo "a serbatoio pieno".
            val fills = records.filter { it.kind == ExpenseKind.FUEL && it.odometerKm != null && (it.liters ?: 0.0) > 0 }
                .sortedBy { it.odometerKm }
            val pairs = fills.zipWithNext().filter { (a, b) -> b.odometerKm!! > a.odometerKm!! }
            val km = pairs.sumOf { (a, b) -> b.odometerKm!! - a.odometerKm!! }
            val liters = pairs.sumOf { (_, b) -> b.liters!! }
            val kmPerLiter = if (km > 0 && liters > 0) km / liters else null

            val kms = thisYear.mapNotNull { it.odometerKm }
            val driven = if (kms.size >= 2) kms.max() - kms.min() else 0
            return ExpenseStats(
                year = year,
                totalCents = total,
                previousYearCents = records.filter { it.date.year == year - 1 }.sumOf { it.costCents ?: 0 },
                byKind = byKind,
                kmPerLiter = kmPerLiter,
                centsPerKm = if (driven > 0 && total > 0) total.toDouble() / driven else null,
            )
        }
    }
}
