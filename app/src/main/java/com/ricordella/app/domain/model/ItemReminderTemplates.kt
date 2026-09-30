package com.ricordella.app.domain.model

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
        ReminderSuggestion("Assicurazione", ReminderType.RENEWAL, RecurrenceFrequency.YEARLY),
        ReminderSuggestion("Revisione", ReminderType.DEADLINE, RecurrenceFrequency.YEARLY, interval = 2),
        ReminderSuggestion("Tagliando", ReminderType.MAINTENANCE, null, odometerIntervalKm = 15_000),
        ReminderSuggestion("Bollo", ReminderType.PAYMENT, RecurrenceFrequency.YEARLY, selectedByDefault = false),
    )

    private val byKind: Map<ItemKind, List<ReminderSuggestion>> = mapOf(
        ItemKind.CAR to vehicle + ReminderSuggestion("Cambio gomme", ReminderType.MAINTENANCE, RecurrenceFrequency.MONTHLY, 6),
        ItemKind.MOTORBIKE to vehicle,
        ItemKind.SCOOTER to vehicle,
        ItemKind.OTHER_VEHICLE to vehicle,
        ItemKind.WASHING_MACHINE to listOf(
            ReminderSuggestion("Pulizia filtro", ReminderType.MAINTENANCE, RecurrenceFrequency.MONTHLY, 3),
            ReminderSuggestion("Pulizia guarnizione", ReminderType.MAINTENANCE, RecurrenceFrequency.MONTHLY, 1),
            ReminderSuggestion("Manutenzione", ReminderType.MAINTENANCE, RecurrenceFrequency.YEARLY, selectedByDefault = false),
        ),
        ItemKind.DISHWASHER to listOf(
            ReminderSuggestion("Pulizia filtro", ReminderType.MAINTENANCE, RecurrenceFrequency.MONTHLY, 1),
            ReminderSuggestion("Sale e brillantante", ReminderType.TASK, RecurrenceFrequency.MONTHLY, 1, selectedByDefault = false),
        ),
        ItemKind.FRIDGE to listOf(
            ReminderSuggestion("Pulizia frigorifero", ReminderType.MAINTENANCE, RecurrenceFrequency.MONTHLY, 3),
        ),
        ItemKind.OVEN to listOf(
            ReminderSuggestion("Pulizia forno", ReminderType.MAINTENANCE, RecurrenceFrequency.MONTHLY, 3, selectedByDefault = false),
        ),
        ItemKind.AIR_CONDITIONER to listOf(
            ReminderSuggestion("Pulizia filtri", ReminderType.MAINTENANCE, RecurrenceFrequency.MONTHLY, 6),
            ReminderSuggestion("Sanificazione", ReminderType.MAINTENANCE, RecurrenceFrequency.YEARLY, selectedByDefault = false),
        ),
        ItemKind.BOILER to listOf(
            ReminderSuggestion("Manutenzione caldaia", ReminderType.MAINTENANCE, RecurrenceFrequency.YEARLY),
            ReminderSuggestion("Controllo fumi", ReminderType.DEADLINE, RecurrenceFrequency.YEARLY, 2, selectedByDefault = false),
        ),
        ItemKind.SMARTPHONE to listOf(
            ReminderSuggestion("Backup", ReminderType.TASK, RecurrenceFrequency.MONTHLY, 1, selectedByDefault = false),
        ),
        ItemKind.COMPUTER to listOf(
            ReminderSuggestion("Backup", ReminderType.TASK, RecurrenceFrequency.MONTHLY, 1, selectedByDefault = false),
        ),
        ItemKind.PERSONAL_DOCUMENT to listOf(
            ReminderSuggestion("Rinnovo documento", ReminderType.RENEWAL, null, interval = 10, selectedByDefault = false),
        ),
        ItemKind.CONTRACT to listOf(
            ReminderSuggestion("Rinnovo contratto", ReminderType.RENEWAL, RecurrenceFrequency.YEARLY, selectedByDefault = false),
        ),
    )

    fun suggestionsFor(kind: ItemKind?): List<ReminderSuggestion> = kind?.let { byKind[it] }.orEmpty()

    /** Le cose "acquistabili" possono avere prezzo e garanzia. */
    fun supportsWarranty(group: ItemGroup): Boolean = group != ItemGroup.DOCUMENTS
}
