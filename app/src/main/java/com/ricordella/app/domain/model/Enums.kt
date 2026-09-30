package com.ricordella.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ReminderType {
    TASK, EVENT, VACATION, MEDICAL_VISIT, HOLIDAY, DEADLINE, BIRTHDAY, WARRANTY, MAINTENANCE, PAYMENT, RENEWAL, OTHER,

    /** Sveglia a tutto schermo: orario obbligatorio, suona finché non la si ferma. */
    ALARM;

    /** Le feste non si "fanno": non si completano e non diventano mai scadute. */
    val isCompletable: Boolean get() = this != HOLIDAY


    /** Tipi che rappresentano una scadenza vera e propria (evidenziati in Home e Calendario). */
    val isDeadlineLike: Boolean
        get() = this == DEADLINE || this == WARRANTY || this == PAYMENT || this == RENEWAL || this == MAINTENANCE
}

@Serializable
enum class ReminderStatus { ACTIVE, COMPLETED, CANCELLED }

@Serializable
enum class Priority { NORMAL, IMPORTANT, URGENT }

/** Stato temporale derivato: non viene mai salvato nel database. */
enum class ReminderTimeStatus { OVERDUE, TODAY, UPCOMING, COMPLETED, CANCELLED }

@Serializable
enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }

@Serializable
enum class PersonItemRole { OWNER, USER, OTHER }

@Serializable
enum class AttachmentOwnerType { ITEM, REMINDER }

@Serializable
enum class ItemGroup { VEHICLES, HOME, ELECTRONICS, DOCUMENTS, GENERIC }

/** Tipologie predefinite di "Cosa". Guidano campi e promemoria suggeriti in fase di creazione. */
@Serializable
enum class ItemKind(val group: ItemGroup) {
    CAR(ItemGroup.VEHICLES),
    MOTORBIKE(ItemGroup.VEHICLES),
    SCOOTER(ItemGroup.VEHICLES),
    OTHER_VEHICLE(ItemGroup.VEHICLES),
    WASHING_MACHINE(ItemGroup.HOME),
    DISHWASHER(ItemGroup.HOME),
    FRIDGE(ItemGroup.HOME),
    OVEN(ItemGroup.HOME),
    AIR_CONDITIONER(ItemGroup.HOME),
    BOILER(ItemGroup.HOME),
    TV(ItemGroup.HOME),
    OTHER_HOME(ItemGroup.HOME),
    SMARTPHONE(ItemGroup.ELECTRONICS),
    TABLET(ItemGroup.ELECTRONICS),
    COMPUTER(ItemGroup.ELECTRONICS),
    CONSOLE(ItemGroup.ELECTRONICS),
    CAMERA(ItemGroup.ELECTRONICS),
    OTHER_ELECTRONICS(ItemGroup.ELECTRONICS),
    PERSONAL_DOCUMENT(ItemGroup.DOCUMENTS),
    CONTRACT(ItemGroup.DOCUMENTS),
    WARRANTY_DOCUMENT(ItemGroup.DOCUMENTS),
    OTHER_DOCUMENT(ItemGroup.DOCUMENTS),
    EQUIPMENT(ItemGroup.GENERIC),
    OBJECT(ItemGroup.GENERIC),
    OTHER(ItemGroup.GENERIC),
}

/** Stato di una manutenzione basata sui chilometri. */
enum class OdometerStatus { FAR, NEAR, DUE, OVERDUE }
