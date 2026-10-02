@file:UseSerializers(LocalDateSerializer::class)

package com.ricordella.app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate
import java.time.YearMonth

/** Una rata pagata: quale (da 0), quando e quanto (può essere diverso dal previsto). */
@Serializable
data class PaidInstallment(val index: Int, val on: LocalDate, val cents: Long)

/**
 * Piano a rate di un pagamento (condominio, mutuo, finanziamento): [amounts] ha un importo per ogni rata,
 * uguali o diversi ([variable]); ogni rata pagata resta registrata in [paid] con data e importo.
 * Le scadenze sono ogni [everyMonths] mesi dalla [firstDate], sempre nello stesso giorno del mese
 * (a fine mese si accorcia: il 31 diventa 30 o 28 e poi torna 31).
 * Con [deleteAfter] l'evento viene eliminato un anno dopo l'ultima rata (altrimenti resta nello storico).
 */
@Serializable
data class InstallmentPlan(
    val firstDate: LocalDate,
    val everyMonths: Int = 1,
    val amounts: List<Long>,
    val paid: List<PaidInstallment> = emptyList(),
    val variable: Boolean = false,
    val deleteAfter: Boolean = false,
) {
    val count: Int get() = amounts.size

    fun dueDate(index: Int): LocalDate {
        val month = YearMonth.from(firstDate).plusMonths(index.toLong() * everyMonths)
        return month.atDay(minOf(firstDate.dayOfMonth, month.lengthOfMonth()))
    }

    val lastDate: LocalDate get() = dueDate(count - 1)

    fun isPaid(index: Int): Boolean = paid.any { it.index == index }

    /** La prima rata ancora da pagare, null se sono finite. */
    fun nextUnpaid(): Int? = (0 until count).firstOrNull { !isPaid(it) }

    val paidCount: Int get() = paid.count { it.index in 0 until count }
    val totalCents: Long get() = (0 until count).sumOf { i -> paid.firstOrNull { it.index == i }?.cents ?: amounts[i] }
    val paidCents: Long get() = paid.filter { it.index in 0 until count }.sumOf { it.cents }
    val finished: Boolean get() = nextUnpaid() == null

    /** Importo della rata: quello pagato, o quello previsto. */
    fun cents(index: Int): Long = paid.firstOrNull { it.index == index }?.cents ?: amounts[index]

    fun pay(index: Int, on: LocalDate, cents: Long? = null): InstallmentPlan =
        copy(paid = (paid.filter { it.index != index } + PaidInstallment(index, on, cents ?: amounts[index])).sortedBy { it.index })

    fun unpay(index: Int): InstallmentPlan = copy(paid = paid.filter { it.index != index })

    /** Cambia l'importo della rata [index] e, con [following], anche delle successive ancora da pagare. */
    fun withAmount(index: Int, cents: Long, following: Boolean): InstallmentPlan =
        copy(amounts = amounts.mapIndexed { i, old -> if (i == index || (following && i > index && !isPaid(i))) cents else old })

    /** Nuovo numero di rate e importo (per le non pagate); le pagate restano com'erano. */
    fun resized(newCount: Int, newAmount: Long?): InstallmentPlan {
        val base = newAmount ?: amounts.lastOrNull() ?: 0L
        val list = List(newCount) { i ->
            when {
                isPaid(i) -> amounts.getOrNull(i) ?: base
                newAmount != null -> newAmount
                else -> amounts.getOrNull(i) ?: base
            }
        }
        return copy(amounts = list, paid = paid.filter { it.index < newCount })
    }

    companion object {
        fun create(first: LocalDate, count: Int, cents: Long, everyMonths: Int, variable: Boolean, deleteAfter: Boolean) =
            InstallmentPlan(first, everyMonths, List(count) { cents }, emptyList(), variable, deleteAfter)
    }
}
