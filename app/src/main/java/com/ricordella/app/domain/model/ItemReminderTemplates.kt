package com.ricordella.app.domain.model

import com.ricordella.app.core.i18n.tr

import java.time.LocalDate

/** Promemoria proposto durante la creazione guidata di una "Cosa". */
data class ReminderSuggestion(
    val title: String,
    val type: ReminderType,
    val frequency: RecurrenceFrequency?,
    val interval: Int = 1,
    val odometerIntervalKm: Int? = null,
    val selectedByDefault: Boolean = true,
) {
    /** Prima scadenza proposta: oggi più un intervallo. */
    fun firstDueDate(today: LocalDate): LocalDate = when (frequency) {
        RecurrenceFrequency.DAILY -> today.plusDays(interval.toLong())
        RecurrenceFrequency.WEEKLY -> today.plusWeeks(interval.toLong())
        RecurrenceFrequency.MONTHLY -> today.plusMonths(interval.toLong())
        RecurrenceFrequency.YEARLY, null -> today.plusYears(interval.toLong())
    }
}

/** Catalogo dei promemoria suggeriti per tipologia di cosa. */
object ItemReminderTemplates {

    private val vehicle = listOf(
        ReminderSuggestion(tr("Assicurazione"), ReminderType.RENEWAL, RecurrenceFrequency.YEARLY),
        ReminderSuggestion(tr("Revisione"), ReminderType.DEADLINE, RecurrenceFrequency.YEARLY, interval = 2),
        ReminderSuggestion(tr("Tagliando"), ReminderType.MAINTENANCE, null, odometerIntervalKm = 15_000),
        ReminderSuggestion(tr("Bollo"), ReminderType.PAYMENT, RecurrenceFrequency.YEARLY, selectedByDefault = false),
    )

    private val byKind: Map<ItemKind, List<ReminderSuggestion>> = mapOf(
        ItemKind.CAR to vehicle + ReminderSuggestion(tr("Cambio gomme"), ReminderType.MAINTENANCE, RecurrenceFrequency.MONTHLY, 6),
        ItemKind.MOTORBIKE to vehicle,
        ItemKind.SCOOTER to vehicle,
        ItemKind.OTHER_VEHICLE to vehicle,
        ItemKind.WASHING_MACHINE to listOf(
            ReminderSuggestion(tr("Pulizia filtro"), ReminderType.MAINTENANCE, RecurrenceFrequency.MONTHLY, 3),
            ReminderSuggestion(tr("Pulizia guarnizione"), ReminderType.MAINTENANCE, RecurrenceFrequency.MONTHLY, 1),
            ReminderSuggestion(tr("Manutenzione"), ReminderType.MAINTENANCE, RecurrenceFrequency.YEARLY, selectedByDefault = false),
        ),
        ItemKind.DISHWASHER to listOf(
            ReminderSuggestion(tr("Pulizia filtro"), ReminderType.MAINTENANCE, RecurrenceFrequency.MONTHLY, 1),
            ReminderSuggestion(tr("Sale e brillantante"), ReminderType.TASK, RecurrenceFrequency.MONTHLY, 1, selectedByDefault = false),
        ),
        ItemKind.FRIDGE to listOf(
            ReminderSuggestion(tr("Pulizia frigorifero"), ReminderType.MAINTENANCE, RecurrenceFrequency.MONTHLY, 3),
        ),
        ItemKind.OVEN to listOf(
            ReminderSuggestion(tr("Pulizia forno"), ReminderType.MAINTENANCE, RecurrenceFrequency.MONTHLY, 3, selectedByDefault = false),
        ),
        ItemKind.AIR_CONDITIONER to listOf(
            ReminderSuggestion(tr("Pulizia filtri"), ReminderType.MAINTENANCE, RecurrenceFrequency.MONTHLY, 6),
            ReminderSuggestion(tr("Sanificazione"), ReminderType.MAINTENANCE, RecurrenceFrequency.YEARLY, selectedByDefault = false),
        ),
        ItemKind.BOILER to listOf(
            ReminderSuggestion(tr("Manutenzione caldaia"), ReminderType.MAINTENANCE, RecurrenceFrequency.YEARLY),
            ReminderSuggestion(tr("Controllo fumi"), ReminderType.DEADLINE, RecurrenceFrequency.YEARLY, 2, selectedByDefault = false),
        ),
        ItemKind.SMARTPHONE to listOf(
            ReminderSuggestion(tr("Backup"), ReminderType.TASK, RecurrenceFrequency.MONTHLY, 1, selectedByDefault = false),
        ),
        ItemKind.COMPUTER to listOf(
            ReminderSuggestion(tr("Backup"), ReminderType.TASK, RecurrenceFrequency.MONTHLY, 1, selectedByDefault = false),
        ),
        ItemKind.PERSONAL_DOCUMENT to listOf(
            ReminderSuggestion(tr("Rinnovo documento"), ReminderType.RENEWAL, null, interval = 10, selectedByDefault = false),
        ),
        ItemKind.CONTRACT to listOf(
            ReminderSuggestion(tr("Rinnovo contratto"), ReminderType.RENEWAL, RecurrenceFrequency.YEARLY, selectedByDefault = false),
        ),
    )

    fun suggestionsFor(kind: ItemKind?): List<ReminderSuggestion> = kind?.let { byKind[it] }.orEmpty()

    /** Le cose "acquistabili" possono avere prezzo e garanzia. */
    fun supportsWarranty(group: ItemGroup): Boolean = group != ItemGroup.DOCUMENTS
}
