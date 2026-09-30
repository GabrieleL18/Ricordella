@file:UseSerializers(
    InstantSerializer::class,
    LocalDateSerializer::class,
    LocalTimeSerializer::class,
    DayOfWeekSerializer::class,
)

package com.ricordella.app.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/*
 * Modelli dell'app. Sono usati direttamente come entità Room e come formato del backup,
 * così da non duplicare classi identiche tra livelli diversi.
 * Gli identificatori sono UUID stabili (necessari per backup e ripristino).
 */

fun newId(): String = UUID.randomUUID().toString()

@Serializable
@Entity(
    tableName = "person",
    indices = [Index("name"), Index("isArchived")],
)
data class Person(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val surname: String? = null,
    val photoUri: String? = null,
    val notes: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

val Person.displayName: String
    get() = listOfNotNull(name.trim(), surname?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" ")

@Serializable
@Entity(
    tableName = "category",
    indices = [Index("itemGroup")],
)
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val itemGroup: ItemGroup,
    /** Tipologia predefinita; null per categorie create dall'utente. */
    val kind: ItemKind? = null,
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
@Entity(
    tableName = "item",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId"), Index("name"), Index("isArchived")],
)
data class Item(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val categoryId: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val licensePlate: String? = null,
    val productionYear: Int? = null,
    /** Chilometraggio attuale, solo per i veicoli. */
    val odometerKm: Int? = null,
    val purchaseDate: LocalDate? = null,
    val purchasePriceCents: Long? = null,
    val warrantyStartDate: LocalDate? = null,
    val warrantyEndDate: LocalDate? = null,
    val warrantySeller: String? = null,
    val warrantyDocumentUri: String? = null,
    val notes: String? = null,
    val photoUri: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

val Item.hasWarranty: Boolean get() = warrantyEndDate != null

@Serializable
@Entity(tableName = "recurrence_rule")
data class RecurrenceRule(
    @PrimaryKey val id: String = newId(),
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: Int? = null,
    val monthOfYear: Int? = null,
)

@Serializable
@Entity(
    tableName = "reminder",
    foreignKeys = [
        ForeignKey(
            entity = RecurrenceRule::class,
            parentColumns = ["id"],
            childColumns = ["recurrenceRuleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("dueDate"),
        Index("status"),
        Index("type"),
        Index("category"),
        Index("priority"),
        Index("isArchived"),
        Index("recurrenceRuleId"),
    ],
)
data class Reminder(
    @PrimaryKey val id: String = newId(),
    val title: String,
    val description: String? = null,
    val notes: String? = null,
    val type: ReminderType = ReminderType.TASK,
    /** Data dell'occorrenza corrente (per i ricorrenti avanza a ogni completamento). */
    val dueDate: LocalDate,
    /** Null = promemoria per tutto il giorno. */
    val dueTime: LocalTime? = null,
    /** Ultimo giorno per gli eventi che durano più giorni (es. una vacanza); null = un giorno solo. */
    val endDate: LocalDate? = null,
    /** Dettagli di viaggio, solo per le vacanze. */
    val trip: TripInfo? = null,
    /** Anno di nascita, solo per i compleanni: serve a mostrare quanti anni compie. */
    val birthYear: Int? = null,
    val status: ReminderStatus = ReminderStatus.ACTIVE,
    val priority: Priority = Priority.NORMAL,
    val category: String? = null,
    val recurrenceRuleId: String? = null,
    /** Chilometraggio al quale il promemoria è dovuto (solo veicoli). */
    val dueOdometerKm: Int? = null,
    /** Intervallo in km usato per calcolare la scadenza successiva dopo il completamento. */
    val odometerIntervalKm: Int? = null,
    val notificationsEnabled: Boolean = true,
    /** Anticipo della notifica rispetto alla scadenza, in minuti. */
    val notifyOffsetMinutes: Int = 0,
    val snoozedUntil: Instant? = null,
    val lastNotifiedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant? = null,
    val isArchived: Boolean = false,
)

val Reminder.isAllDay: Boolean get() = dueTime == null

/** Anni compiuti in [date] per i compleanni con l'anno di nascita, altrimenti null. */
fun Reminder.ageOn(date: LocalDate): Int? =
    birthYear?.takeIf { type == ReminderType.BIRTHDAY }?.let { date.year - it }?.takeIf { it > 0 }

/** Giorni in più oltre al primo per gli eventi di più giorni (0 = un giorno solo). */
val Reminder.extraDays: Long get() = endDate?.let { ChronoUnit.DAYS.between(dueDate, it).coerceAtLeast(0) } ?: 0L

val Reminder.isMultiDay: Boolean get() = extraDays > 0

@Serializable
@Entity(
    tableName = "reminder_completion",
    foreignKeys = [
        ForeignKey(
            entity = Reminder::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("reminderId")],
)
data class ReminderCompletion(
    @PrimaryKey val id: String = newId(),
    val reminderId: String,
    val occurrenceDate: LocalDate,
    val completedAt: Instant,
)

@Serializable
@Entity(
    tableName = "maintenance_record",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId"), Index("date")],
)
data class MaintenanceRecord(
    @PrimaryKey val id: String = newId(),
    val itemId: String,
    /** Tipo di intervento, es. "Tagliando" o "Pneumatici". */
    val title: String,
    val date: LocalDate,
    val odometerKm: Int? = null,
    val costCents: Long? = null,
    val description: String? = null,
    val notes: String? = null,
    val createdAt: Instant,
)

@Serializable
@Entity(
    tableName = "attachment",
    indices = [Index("ownerType", "ownerId")],
)
data class Attachment(
    @PrimaryKey val id: String = newId(),
    val ownerType: AttachmentOwnerType,
    val ownerId: String,
    /** Riferimento al file (content URI), il file non viene duplicato. */
    val uri: String,
    val displayName: String,
    val mimeType: String? = null,
    val createdAt: Instant,
)

@Serializable
@Entity(
    tableName = "reminder_person",
    primaryKeys = ["reminderId", "personId"],
    foreignKeys = [
        ForeignKey(Reminder::class, ["id"], ["reminderId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("personId")],
)
data class ReminderPersonCrossRef(val reminderId: String, val personId: String)

@Serializable
@Entity(
    tableName = "reminder_item",
    primaryKeys = ["reminderId", "itemId"],
    foreignKeys = [
        ForeignKey(Reminder::class, ["id"], ["reminderId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Item::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("itemId")],
)
data class ReminderItemCrossRef(val reminderId: String, val itemId: String)

@Serializable
@Entity(
    tableName = "person_item",
    primaryKeys = ["personId", "itemId"],
    foreignKeys = [
        ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Item::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("itemId")],
)
data class PersonItemCrossRef(
    val personId: String,
    val itemId: String,
    val role: PersonItemRole = PersonItemRole.OWNER,
)
