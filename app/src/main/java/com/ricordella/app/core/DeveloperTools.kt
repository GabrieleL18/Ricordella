package com.ricordella.app.core

import android.content.Context
import com.ricordella.app.core.notifications.ReminderNotifier
import com.ricordella.app.core.widget.CalendarWidgetProvider
import com.ricordella.app.data.local.database.BuiltInCategories
import com.ricordella.app.domain.ReminderScheduler
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.Item
import com.ricordella.app.domain.model.ItemKind
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.PersonItemRole
import com.ricordella.app.domain.model.Priority
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderDraft
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.model.TravelMode
import com.ricordella.app.domain.model.TripInfo
import com.ricordella.app.domain.model.TripLeg
import com.ricordella.app.domain.model.TripStay
import com.ricordella.app.domain.repository.ItemRepository
import com.ricordella.app.domain.repository.PersonRepository
import com.ricordella.app.domain.repository.SettingsRepository
import com.ricordella.app.domain.usecase.Housekeeping
import com.ricordella.app.domain.usecase.SaveReminderUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalTime

/** Informazioni tecniche mostrate nella sezione Sviluppatore. */
data class DeveloperInfo(val databaseBytes: Long, val mediaBytes: Long, val mediaFiles: Int, val cacheBytes: Long)

/** Comandi della sezione Sviluppatore: servono a provare l'app senza aspettare date reali. */
class DeveloperTools(
    private val context: Context,
    private val scope: CoroutineScope,
    private val notifier: ReminderNotifier,
    private val scheduler: ReminderScheduler,
    private val settings: SettingsRepository,
    private val housekeeping: Housekeeping,
    private val saveReminder: SaveReminderUseCase,
    private val people: PersonRepository,
    private val items: ItemRepository,
    private val time: TimeSource,
) {
    /** Mostra una notifica finta (non legata a un promemoria reale), subito o dopo [delaySeconds]. */
    fun sendTestNotification(type: ReminderType = ReminderType.EVENT, delaySeconds: Long = 0) {
        scope.launch {
            if (delaySeconds > 0) delay(delaySeconds * 1000)
            val now = time.now()
            val reminder = Reminder(
                id = "developer-test",
                title = "Notifica di prova",
                description = "Se leggi questo messaggio le notifiche di Remindella funzionano.",
                type = type,
                dueDate = time.today(),
                dueTime = LocalTime.now().withSecond(0).withNano(0),
                createdAt = now,
                updatedAt = now,
            )
            notifier.show(ReminderWithLinks(reminder, emptyList(), emptyList(), null), time.today())
        }
    }

    suspend fun restartOnboarding() = settings.update { it.copy(onboardingDone = false) }

    /** Fa comparire subito in Home l'invito ad aggiornare il backup. */
    suspend fun forceBackupReminder() {
        val today = time.today().toEpochDay()
        settings.update { it.copy(backupCheckEpochDay = today - it.backupIntervalDays) }
    }

    /** Fa proporre la pulizia annuale alla prossima apertura della Home. */
    suspend fun forceYearlyCleanup() = settings.update { it.copy(lastCleanupYear = time.today().year - 1) }

    suspend fun rollHolidays() = housekeeping.rollHolidays()

    suspend fun rescheduleAlarms() = scheduler.refresh()

    fun refreshWidget() = CalendarWidgetProvider.requestUpdate(context)

    suspend fun info(): DeveloperInfo = withContext(Dispatchers.IO) {
        val media = File(context.filesDir, "media").listFiles().orEmpty()
        DeveloperInfo(
            databaseBytes = context.getDatabasePath("ricordella.db").length(),
            mediaBytes = media.sumOf { it.length() },
            mediaFiles = media.size,
            cacheBytes = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
        )
    }

    fun clearCache() {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Dati di esempio per provare tutte le schermate: persona, cosa, vacanza, visita, compleanno, scaduto. */
    suspend fun createDemoData() {
        val now = time.now()
        val today = time.today()
        val anna = Person(name = "Anna", surname = "Esempio", notes = "Persona di esempio", createdAt = now, updatedAt = now)
        people.save(anna)
        val car = Item(name = "Auto di esempio", categoryId = BuiltInCategories.idFor(ItemKind.CAR), licensePlate = "AB123CD", odometerKm = 54_000, createdAt = now, updatedAt = now)
        items.save(car, mapOf(anna.id to PersonItemRole.OWNER))

        fun draft(reminder: Reminder, rule: RecurrenceRule? = null, person: Boolean = false, item: Boolean = false) =
            ReminderDraft(reminder, rule, if (person) setOf(anna.id) else emptySet(), if (item) setOf(car.id) else emptySet())

        listOf(
            draft(Reminder(title = "Compleanno di Anna", type = ReminderType.BIRTHDAY, dueDate = today.plusDays(3), createdAt = now, updatedAt = now),
                RecurrenceRule(frequency = RecurrenceFrequency.YEARLY, startDate = today.plusDays(3)), person = true),
            draft(Reminder(title = "Visita dal dentista", type = ReminderType.MEDICAL_VISIT, dueDate = today.plusDays(1), dueTime = LocalTime.of(10, 30), createdAt = now, updatedAt = now)),
            draft(Reminder(title = "Pagare la bolletta", type = ReminderType.PAYMENT, dueDate = today.minusDays(2), priority = Priority.URGENT, createdAt = now, updatedAt = now)),
            draft(Reminder(title = "Revisione auto", type = ReminderType.MAINTENANCE, dueDate = today.plusDays(20), createdAt = now, updatedAt = now), item = true),
            draft(Reminder(title = "Riunione", type = ReminderType.EVENT, dueDate = today, dueTime = LocalTime.of(15, 0), createdAt = now, updatedAt = now)),
            draft(
                Reminder(
                    title = "Vacanza al mare",
                    type = ReminderType.VACATION,
                    dueDate = today.plusDays(10),
                    endDate = today.plusDays(17),
                    trip = TripInfo(
                        destination = "Palermo",
                        legs = listOf(
                            TripLeg(TravelMode.PLANE, "ITA Airways", "AZ1781", "Roma FCO", "Palermo PMO", today.plusDays(10), LocalTime.of(8, 40), "14A"),
                            TripLeg(TravelMode.SHIP, "GNV", "La Suprema", "Palermo", "Napoli", today.plusDays(17), LocalTime.of(20, 0), "Cabina 204"),
                        ),
                        stay = TripStay("Hotel Esempio", "Via Roma 1, Palermo", today.plusDays(10), today.plusDays(17), "XYZ123", "+39 091 000000"),
                    ),
                    createdAt = now,
                    updatedAt = now,
                ),
                person = true,
            ),
        ).forEach { saveReminder(it) }
    }
}
