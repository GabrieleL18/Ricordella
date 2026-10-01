package com.ricordella.app.core

import com.ricordella.app.core.i18n.tr

import android.content.Context
import com.ricordella.app.core.alarm.AlarmRingService
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
import kotlinx.coroutines.flow.MutableStateFlow
import com.ricordella.app.domain.model.Resolution
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import com.ricordella.app.domain.date.Holidays
import com.ricordella.app.domain.model.AutoMode

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
    private val databaseFileName: String,
) {
    /** Mostra una notifica finta (non legata a un promemoria reale), subito o dopo [delaySeconds]. */
    fun sendTestNotification(type: ReminderType = ReminderType.EVENT, delaySeconds: Long = 0) {
        scope.launch {
            if (delaySeconds > 0) delay(delaySeconds * 1000)
            val now = time.now()
            val reminder = Reminder(
                id = "developer-test",
                title = tr("Notifica di prova"),
                description = tr("Se leggi questo messaggio le notifiche di Remindella funzionano."),
                type = type,
                dueDate = time.today(),
                dueTime = LocalTime.now().withSecond(0).withNano(0),
                createdAt = now,
                updatedAt = now,
            )
            notifier.show(ReminderWithLinks(reminder, emptyList(), emptyList(), null), time.today())
        }
    }

    /** Fa suonare una sveglia finta (nessun promemoria reale da completare) tra 10 secondi, di giorno o di notte. */
    fun ringTestAlarm(night: Boolean) {
        scope.launch {
            AlarmRingService.scheduleTest(context, settings.current(), if (night) AlarmRingService.SCENE_NIGHT else AlarmRingService.SCENE_DAY)
        }
    }

    suspend fun restartOnboarding() = settings.update { it.copy(onboardingDone = false) }

    /** Fa comparire subito in Home l'invito ad aggiornare il backup. */
    suspend fun forceBackupReminder() {
        val today = time.today().toEpochDay()
        settings.update { it.copy(backupCheckEpochDay = today - it.backupIntervalDays, lastBackupMillis = null) }
    }

    /**
     * Fa comparire in Home le proposte di inizio anno (feste da spostare e pulizia), come al primo
     * avvio in un anno nuovo. Le modalità "Mai" tornano a "Chiedimi". Restituisce quante feste e
     * quanti promemoria sono coinvolti: se sono zero non c'è nulla da proporre.
     */
    suspend fun simulateNewYear(): Pair<Int, Int> {
        settings.update {
            it.copy(
                lastCleanupYear = time.today().year - 1,
                holidayMode = if (it.holidayMode == AutoMode.OFF) AutoMode.ASK else it.holidayMode,
                cleanupMode = if (it.cleanupMode == AutoMode.OFF) AutoMode.ASK else it.cleanupMode,
            )
        }
        return housekeeping.holidaysToRoll() to housekeeping.countCleanupCandidates()
    }

    /**
     * Giorno finto usato dalla Home per gli inviti dei buoni propositi: permette di provarli
     * senza aspettare gennaio o dicembre. Resta in memoria fino alla chiusura dell'app.
     */
    val resolutionsDay = MutableStateFlow<LocalDate?>(null)

    /** La Home fa come se fosse il 2 gennaio dell'anno prossimo: recap di quest'anno (se ci sono propositi), poi l'invito a scriverne di nuovi. */
    suspend fun simulateResolutionsNewYear() {
        val next = time.today().year + 1
        settings.update { it.copy(resolutionsAskedYear = null, resolutionsRecapYear = null, resolutions = it.resolutions.filterNot { r -> r.year == next }) }
        resolutionsDay.value = LocalDate.of(next, 1, 2)
    }

    /** La Home fa come se fosse il 28 dicembre: recap di quest'anno, con tre propositi di esempio se non ce ne sono. */
    suspend fun simulateResolutionsRecap() {
        val year = time.today().year
        settings.update { current ->
            val samples = if (current.resolutions.any { it.year == year }) emptyList() else listOf(
                Resolution(year = year, text = tr("Bere più acqua"), kept = true),
                Resolution(year = year, text = tr("Leggere un libro al mese")),
                Resolution(year = year, text = tr("Fare una passeggiata ogni domenica"), kept = true),
            )
            current.copy(resolutionsRecapYear = null, resolutions = current.resolutions + samples)
        }
        resolutionsDay.value = LocalDate.of(year, 12, 28)
    }

    suspend fun rescheduleAlarms() = scheduler.refresh()

    fun refreshWidget() = com.ricordella.app.core.widget.HomeWidgets.updateAll(context)

    suspend fun info(): DeveloperInfo = withContext(Dispatchers.IO) {
        val media = File(context.filesDir, "media").listFiles().orEmpty()
        DeveloperInfo(
            databaseBytes = context.getDatabasePath(databaseFileName).length(),
            mediaBytes = media.sumOf { it.length() },
            mediaFiles = media.size,
            cacheBytes = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
        )
    }

    fun clearCache() {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /**
     * Dati di esempio per provare tutte le schermate: persona, cosa, vacanza, visita, compleanno, scaduto,
     * più due feste e una cena dell'anno scorso per provare le operazioni di inizio anno.
     */
    suspend fun createDemoData() {
        val now = time.now()
        val today = time.today()
        val anna = Person(name = tr("Anna"), surname = tr("Esempio"), notes = tr("Persona di esempio"), createdAt = now, updatedAt = now)
        people.save(anna)
        val car = Item(name = tr("Auto di esempio"), categoryId = BuiltInCategories.idFor(ItemKind.CAR), licensePlate = "AB123CD", odometerKm = 54_000, createdAt = now, updatedAt = now)
        items.save(car, mapOf(anna.id to PersonItemRole.OWNER))

        fun draft(reminder: Reminder, rule: RecurrenceRule? = null, person: Boolean = false, item: Boolean = false) =
            ReminderDraft(reminder, rule, if (person) setOf(anna.id) else emptySet(), if (item) setOf(car.id) else emptySet())

        listOf(
            draft(Reminder(title = tr("Compleanno di Anna"), type = ReminderType.BIRTHDAY, dueDate = today.plusDays(3), createdAt = now, updatedAt = now),
                RecurrenceRule(frequency = RecurrenceFrequency.YEARLY, startDate = today.plusDays(3)), person = true),
            draft(Reminder(title = tr("Visita dal dentista"), type = ReminderType.MEDICAL_VISIT, dueDate = today.plusDays(1), dueTime = LocalTime.of(10, 30), createdAt = now, updatedAt = now)),
            draft(Reminder(title = tr("Pagare la bolletta"), type = ReminderType.PAYMENT, dueDate = today.minusDays(2), priority = Priority.URGENT, createdAt = now, updatedAt = now)),
            draft(Reminder(title = tr("Revisione auto"), type = ReminderType.MAINTENANCE, dueDate = today.plusDays(20), createdAt = now, updatedAt = now), item = true),
            draft(Reminder(title = tr("Riunione"), type = ReminderType.EVENT, dueDate = today, dueTime = LocalTime.of(15, 0), createdAt = now, updatedAt = now)),
            draft(
                Reminder(
                    title = tr("Vacanza al mare"),
                    type = ReminderType.VACATION,
                    dueDate = today.plusDays(10),
                    endDate = today.plusDays(17),
                    trip = TripInfo(
                        destination = tr("Palermo"),
                        legs = listOf(
                            TripLeg(TravelMode.PLANE, tr("ITA Airways"), "AZ1781", tr("Roma FCO"), tr("Palermo PMO"), today.plusDays(10), LocalTime.of(8, 40), "14A"),
                            TripLeg(TravelMode.SHIP, "GNV", tr("La Suprema"), tr("Palermo"), tr("Napoli"), today.plusDays(17), LocalTime.of(20, 0), tr("Cabina 204")),
                        ),
                        stay = TripStay(tr("Hotel Esempio"), tr("Via Roma 1, Palermo"), today.plusDays(10), today.plusDays(17), "XYZ123", "+39 091 000000"),
                    ),
                    createdAt = now,
                    updatedAt = now,
                ),
                person = true,
            ),
            // Anno scorso: feste da spostare (Pasqua cambia data) e un evento da pulire.
            draft(Reminder(title = tr("Natale"), type = ReminderType.HOLIDAY, dueDate = LocalDate.of(today.year - 1, 12, 25), notificationsEnabled = false, createdAt = now, updatedAt = now)),
            draft(Reminder(title = tr("Pasqua"), type = ReminderType.HOLIDAY, dueDate = Holidays.easter(today.year - 1), notificationsEnabled = false, createdAt = now, updatedAt = now)),
            draft(Reminder(title = tr("Cena dell'anno scorso"), type = ReminderType.EVENT, dueDate = today.minusYears(1).minusMonths(2), createdAt = now, updatedAt = now)),
        ).forEach { saveReminder(it) }
    }
}
