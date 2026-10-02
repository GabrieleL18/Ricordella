package com.ricordella.app.domain.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import java.time.LocalDate
import java.time.LocalTime

/** Promemoria con persone, cose e ricorrenza collegate. */
data class ReminderWithLinks(
    @Embedded val reminder: Reminder,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(ReminderPersonCrossRef::class, parentColumn = "reminderId", entityColumn = "personId"),
    )
    val people: List<Person>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(ReminderItemCrossRef::class, parentColumn = "reminderId", entityColumn = "itemId"),
    )
    val items: List<Item>,
    @Relation(parentColumn = "recurrenceRuleId", entityColumn = "id")
    val recurrenceRule: RecurrenceRule?,
) {
    /** Primo veicolo collegato con chilometraggio noto: serve per le scadenze a km. */
    val odometerItem: Item? get() = items.firstOrNull { it.odometerKm != null }
}

data class ItemWithCategory(
    @Embedded val item: Item,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: Category?,
) {
    val group: ItemGroup get() = category?.itemGroup ?: ItemGroup.GENERIC
    val isVehicle: Boolean get() = group == ItemGroup.VEHICLES
}

data class PersonWithRole(
    @Embedded val person: Person,
    val role: PersonItemRole,
)

/** Dati necessari per salvare un promemoria insieme alle sue relazioni, in modo atomico. */
data class ReminderDraft(
    val reminder: Reminder,
    val recurrence: RecurrenceRule?,
    val personIds: Set<String>,
    val itemIds: Set<String>,
)

/** Proiezione minima usata per pianificare le notifiche senza caricare l'intero promemoria. */
data class SchedulingCandidate(
    val id: String,
    val dueDate: LocalDate,
    val dueTime: LocalTime?,
    val notifyOffsetMinutes: Int,
    val snoozedUntil: java.time.Instant?,
    val lastNotifiedAt: java.time.Instant?,
    /** Sveglia che si ripete: ha l'avviso silenzioso un po' prima. */
    val isRecurringAlarm: Boolean = false,
)

/** Un'occorrenza (reale o calcolata dalla ricorrenza) di un promemoria in una data. */
data class ReminderOccurrence(
    val entry: ReminderWithLinks,
    val date: LocalDate,
    /** Primo giorno dell'occorrenza: differisce da [date] nei giorni successivi di un evento lungo. */
    val start: LocalDate = date,
) {
    val reminder: Reminder get() = entry.reminder
    /** True se l'occorrenza non è quella corrente (proiettata dalla ricorrenza). */
    val isProjected: Boolean get() = start != entry.reminder.dueDate
    val end: LocalDate get() = start.plusDays(reminder.extraDays)
    val isMultiDay: Boolean get() = reminder.isMultiDay
    /** Es. 2 per il secondo giorno di una vacanza. */
    val dayNumber: Long get() = java.time.temporal.ChronoUnit.DAYS.between(start, date) + 1
}

data class SearchResults(
    val people: List<Person> = emptyList(),
    val items: List<ItemWithCategory> = emptyList(),
    val reminders: List<ReminderWithLinks> = emptyList(),
    val maintenance: List<MaintenanceRecord> = emptyList(),
) {
    val isEmpty: Boolean get() = people.isEmpty() && items.isEmpty() && reminders.isEmpty() && maintenance.isEmpty()
}
