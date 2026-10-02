package com.ricordella.app.domain.model

import com.ricordella.app.domain.date.RecurrenceCalculator
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Che cosa è un movimento di denaro: una spesa, un abbonamento, un pagamento a rate o un'entrata (bonifico, regalo). */
@Serializable
enum class MoneyKind { EXPENSE, SUBSCRIPTION, INSTALLMENTS, INCOME }

/**
 * Dati di denaro di un promemoria di tipo [ReminderType.PAYMENT]. [amountCents] è l'importo di ogni pagamento
 * (per le rate stanno nel [InstallmentPlan]). [shares] sono le quote per persona: servono solo come proporzioni,
 * vuoto = parti uguali tra le persone collegate.
 */
@Serializable
data class MoneyInfo(
    val kind: MoneyKind = MoneyKind.EXPENSE,
    val amountCents: Long = 0,
    val shares: Map<String, Long> = emptyMap(),
)

/** Un pagamento (o un'entrata) con data, importo totale e come si divide tra le persone. */
data class MoneyEntry(
    val reminderId: String,
    val title: String,
    val date: LocalDate,
    val kind: MoneyKind,
    val cents: Long,
    val shares: Map<String, Long>,
)

/** Quanto una persona ha speso (o incassato) per un pagamento nell'anno. */
data class PersonLine(val reminderId: String, val title: String, val kind: MoneyKind, val count: Int, val cents: Long)

data class PersonRecap(val personId: String, val spentCents: Long, val receivedCents: Long, val lines: List<PersonLine>)

object MoneyLedger {
    /** Chi non ha persone collegate finisce qui. */
    const val NOBODY = ""

    /** Divide [total] tra [personIds] secondo le proporzioni di [info]; i centesimi che avanzano vanno ai primi. */
    fun split(info: MoneyInfo, total: Long, personIds: List<String>): Map<String, Long> {
        val ids = personIds.ifEmpty { listOf(NOBODY) }
        val weights = ids.map { info.shares[it] ?: 0L }
        val useShares = info.shares.isNotEmpty() && weights.sum() > 0
        val w = if (useShares) weights else ids.map { 1L }
        val sum = w.sum()
        val parts = w.map { total * it / sum }.toMutableList()
        var left = total - parts.sum()
        var i = 0
        while (left > 0) { parts[i % parts.size]++; left--; i++ }
        return ids.zip(parts).toMap()
    }

    /** Tutti i pagamenti dei promemoria [reminders] che cadono tra [from] e [to] (inclusi). */
    fun entries(reminders: List<ReminderWithLinks>, from: LocalDate, to: LocalDate, calculator: RecurrenceCalculator): List<MoneyEntry> =
        reminders.filter { it.reminder.type == ReminderType.PAYMENT }.flatMap { entry ->
            val r = entry.reminder
            val plan = r.plan
            val info = r.money ?: if (plan != null) MoneyInfo(MoneyKind.INSTALLMENTS) else return@flatMap emptyList()
            val people = entry.people.map { it.id }
            fun at(date: LocalDate, cents: Long) = MoneyEntry(r.id, r.title, date, info.kind, cents, split(info, cents, people))
            when {
                plan != null -> (0 until plan.count).filter { plan.dueDate(it) in from..to }.map { at(plan.dueDate(it), plan.cents(it)) }
                info.amountCents <= 0 -> emptyList()
                entry.recurrenceRule != null -> calculator.occurrencesBetween(entry.recurrenceRule, from, to).map { at(it, info.amountCents) }
                r.dueDate in from..to -> listOf(at(r.dueDate, info.amountCents))
                else -> emptyList()
            }
        }.sortedBy { it.date }

    /** Per ogni persona: totale speso, totale ricevuto e un rigo per pagamento. */
    fun recap(entries: List<MoneyEntry>): List<PersonRecap> =
        entries.flatMap { e -> e.shares.map { (person, cents) -> person to (e to cents) } }
            .groupBy({ it.first }, { it.second })
            .map { (person, list) ->
                val lines = list.groupBy { it.first.reminderId }.map { (id, rows) ->
                    val first = rows.first().first
                    PersonLine(id, first.title, first.kind, rows.size, rows.sumOf { it.second })
                }.sortedByDescending { it.cents }
                PersonRecap(
                    person,
                    spentCents = lines.filter { it.kind != MoneyKind.INCOME }.sumOf { it.cents },
                    receivedCents = lines.filter { it.kind == MoneyKind.INCOME }.sumOf { it.cents },
                    lines = lines,
                )
            }
            .sortedByDescending { it.spentCents + it.receivedCents }
}
