package com.ricordella.app.core

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import android.content.Context
import android.content.Intent
import com.ricordella.app.data.local.database.BuiltInCategories
import com.ricordella.app.domain.date.Holidays
import com.ricordella.app.domain.model.ExpenseKind
import com.ricordella.app.domain.model.Item
import com.ricordella.app.domain.model.ItemKind
import com.ricordella.app.domain.model.MaintenanceRecord
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.PersonItemRole
import com.ricordella.app.domain.model.Priority
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderDraft
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.model.TravelMode
import com.ricordella.app.domain.model.TripInfo
import com.ricordella.app.domain.model.TripLeg
import com.ricordella.app.domain.model.TripStay
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import kotlin.system.exitProcess

/**
 * Modalità demo per gli screenshot del Play Store: l'app usa un database e impostazioni a parte,
 * pieni di dati finti ma coerenti. I dati veri non vengono toccati e tornano uscendo dalla modalità.
 */
object DemoMode {
    private const val PREFS = "demo_mode"
    private const val KEY_ON = "on"

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)

    /** Cambia modalità e riavvia l'app: database e impostazioni si scelgono solo all'avvio. */
    fun switch(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).commit()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
        exitProcess(0)
    }
}

/** Riempie il database demo al primo avvio in modalità demo (se è già pieno non fa nulla). */
suspend fun AppContainer.seedDemoDataIfEmpty() {
    if (personRepository.observePeople(archived = false).first().isNotEmpty()) return
    // Niente notifiche vere dai dati finti, niente tutorial né invito al backup sopra gli screenshot.
    settingsRepository.update {
        it.copy(
            onboardingDone = true,
            termsAcceptedVersion = com.ricordella.app.feature.legal.TERMS_VERSION,
            termsAcceptedEpochDay = time.today().toEpochDay(),
            backupTutorialSeen = true,
            quickEntryTutorialSeen = true,
            newsSeenVersion = com.ricordella.app.BuildConfig.VERSION_CODE,
            developerMode = true,
            notificationsEnabled = false,
            backupCheckEpochDay = time.today().toEpochDay(),
            lastCleanupYear = time.today().year,
            potions = it.potions.copy(goalMl = 2000, potionMl = 250, day = time.today().toEpochDay(), drankMl = 1250),
        )
    }
    val now = time.now()
    val today = time.today()
    fun person(name: String, surname: String) = Person(name = name, surname = surname, createdAt = now, updatedAt = now)
    val giulia = person("Giulia", "Bianchi")
    val marco = person("Marco", "Bianchi")
    val sofia = person("Sofia", "Bianchi")
    val nonna = person(tr("Nonna Rosa"), "Ferri")
    val luca = person("Luca", "Martini")
    listOf(giulia, marco, sofia, nonna, luca).forEach { personRepository.save(it) }

    fun item(name: String, kind: ItemKind, build: Item.() -> Item = { this }) =
        Item(name = name, categoryId = BuiltInCategories.idFor(kind), createdAt = now, updatedAt = now).build()
    val car = item("Fiat Panda", ItemKind.CAR) {
        copy(brand = "Fiat", model = "Panda Hybrid", licensePlate = "GH 482 KL", productionYear = 2021, odometerKm = 48_350, purchaseDate = today.minusYears(3).minusMonths(2))
    }
    val scooter = item("Vespa", ItemKind.SCOOTER) { copy(brand = "Piaggio", model = "Vespa Primavera 125", licensePlate = "EX 71204", productionYear = 2019, odometerKm = 12_800) }
    val washer = item(tr("Lavatrice"), ItemKind.WASHING_MACHINE) {
        copy(brand = "Bosch", model = "Serie 6", purchaseDate = today.minusMonths(20), purchasePriceCents = 54_900, warrantyStartDate = today.minusMonths(20), warrantyEndDate = today.plusMonths(4), warrantySeller = "MediaWorld")
    }
    val boiler = item(tr("Caldaia"), ItemKind.BOILER) { copy(brand = "Vaillant", model = "ecoTEC plus", productionYear = 2018) }
    val phone = item("Pixel 9", ItemKind.SMARTPHONE) {
        copy(brand = "Google", model = "Pixel 9", purchaseDate = today.minusMonths(9), purchasePriceCents = 89_900, warrantyStartDate = today.minusMonths(9), warrantyEndDate = today.plusMonths(15), warrantySeller = "Google Store")
    }
    val idCard = item(tr("Carta d'identità di Giulia"), ItemKind.PERSONAL_DOCUMENT) { copy(serialNumber = "CA 00000 XY") }
    val passport = item(tr("Passaporto di Marco"), ItemKind.PERSONAL_DOCUMENT) { copy(serialNumber = "YA0000000") }
    val rent = item(tr("Contratto luce e gas"), ItemKind.CONTRACT) { copy(notes = tr("Offerta a prezzo fisso, 24 mesi")) }
    // saveItem crea da solo i promemoria di fine garanzia.
    saveItem(car, mapOf(giulia.id to PersonItemRole.OWNER, marco.id to PersonItemRole.USER))
    saveItem(scooter, mapOf(marco.id to PersonItemRole.OWNER))
    saveItem(washer, emptyMap())
    saveItem(boiler, emptyMap())
    saveItem(phone, mapOf(marco.id to PersonItemRole.OWNER))
    saveItem(idCard, mapOf(giulia.id to PersonItemRole.OWNER))
    saveItem(passport, mapOf(marco.id to PersonItemRole.OWNER))
    saveItem(rent, mapOf(giulia.id to PersonItemRole.OWNER))

    listOf(
        MaintenanceRecord(itemId = car.id, title = tr("Tagliando"), date = today.minusMonths(7), odometerKm = 41_200, costCents = 23_000, description = tr("Olio, filtri e controllo freni"), createdAt = now),
        MaintenanceRecord(itemId = car.id, title = tr("Pneumatici"), date = today.minusMonths(11), odometerKm = 37_900, costCents = 48_000, description = tr("Cambio gomme invernali"), createdAt = now),
        MaintenanceRecord(itemId = boiler.id, title = tr("Controllo fumi"), date = today.minusMonths(10), costCents = 9_000, createdAt = now),
        // Rifornimenti e altre spese dell'anno, per un riepilogo delle spese credibile.
        MaintenanceRecord(itemId = car.id, title = tr("Rifornimento"), date = today.minusMonths(1).withDayOfMonth(12), odometerKm = 47_900, costCents = 6_200, liters = 34.0, kind = ExpenseKind.FUEL, createdAt = now),
        MaintenanceRecord(itemId = car.id, title = tr("Rifornimento"), date = today.minusMonths(2).withDayOfMonth(20), odometerKm = 47_250, costCents = 5_800, liters = 32.5, kind = ExpenseKind.FUEL, createdAt = now),
        MaintenanceRecord(itemId = car.id, title = tr("Rifornimento"), date = today.minusMonths(4).withDayOfMonth(3), odometerKm = 46_600, costCents = 6_500, liters = 35.0, kind = ExpenseKind.FUEL, createdAt = now),
        MaintenanceRecord(itemId = car.id, title = tr("Assicurazione"), date = today.minusMonths(5).withDayOfMonth(15), costCents = 41_000, kind = ExpenseKind.OTHER, createdAt = now),
        MaintenanceRecord(itemId = car.id, title = tr("Bollo auto"), date = today.minusMonths(8).withDayOfMonth(28), costCents = 18_700, kind = ExpenseKind.OTHER, createdAt = now),
        MaintenanceRecord(itemId = boiler.id, title = tr("Manutenzione annuale"), date = today.minusMonths(3).withDayOfMonth(9), costCents = 12_000, createdAt = now),
    ).forEach { maintenanceRepository.save(it) }

    fun at(date: LocalDate, title: String, type: ReminderType, time: LocalTime? = null, build: Reminder.() -> Reminder = { this }) =
        Reminder(title = title, type = type, dueDate = date, dueTime = time, createdAt = now, updatedAt = now).build()
    fun yearly(date: LocalDate) = RecurrenceRule(frequency = RecurrenceFrequency.YEARLY, startDate = date)
    fun draft(reminder: Reminder, rule: RecurrenceRule? = null, people: List<Person> = emptyList(), items: List<Item> = emptyList()) =
        ReminderDraft(reminder, rule, people.map { it.id }.toSet(), items.map { it.id }.toSet())
    fun birthday(p: Person, date: LocalDate, year: Int) =
        draft(at(date, trf("Compleanno di %1\$s", p.name), ReminderType.BIRTHDAY) { copy(birthYear = year) }, yearly(date), listOf(p))

    val trip = today.plusDays(12)
    // Gli orari di oggi seguono l'ora attuale, così negli screenshot sono "in arrivo" e non scaduti.
    val hour = LocalTime.now().hour
    fun later(hours: Int, minute: Int = 0) = LocalTime.of((hour + hours).coerceAtMost(23), minute)
    listOf(
        // Oggi: una giornata piena ma credibile.
        draft(at(today, tr("Portare Sofia in piscina"), ReminderType.TASK, later(3, 30)), people = listOf(sofia)),
        draft(at(today, tr("Call con il commercialista"), ReminderType.EVENT, later(1))),
        draft(at(today, tr("Ritirare il pacco in tabaccheria"), ReminderType.TASK)),
        // Scaduti: pochi, per mostrare il colore senza allarmare.
        draft(at(today.minusDays(2), tr("Pagare la bolletta della luce"), ReminderType.PAYMENT) { copy(priority = Priority.URGENT) }, items = listOf(rent)),
        // In arrivo.
        birthday(sofia, today.plusDays(3), today.year - 9),
        draft(at(today.plusDays(1), tr("Visita dal dentista"), ReminderType.MEDICAL_VISIT, LocalTime.of(9, 30)) { copy(notes = tr("Studio dott.ssa Conti, portare la tessera sanitaria")) }, people = listOf(giulia)),
        draft(at(today.plusDays(2), tr("Cena da Luca"), ReminderType.EVENT, LocalTime.of(20, 30)), people = listOf(luca)),
        draft(at(today.plusDays(5), tr("Riunione con le maestre"), ReminderType.EVENT, LocalTime.of(16, 45)), people = listOf(sofia, giulia)),
        draft(at(today.plusDays(6), tr("Tagliando Panda"), ReminderType.MAINTENANCE) { copy(dueOdometerKm = 50_000, odometerIntervalKm = 15_000) }, items = listOf(car)),
        draft(at(today.plusDays(8), tr("Bollo auto"), ReminderType.PAYMENT) { copy(priority = Priority.IMPORTANT) }, yearly(today.plusDays(8)), items = listOf(car)),
        draft(at(today.plusDays(9), tr("Controllo pressione nonna"), ReminderType.MEDICAL_VISIT, LocalTime.of(10, 0)), people = listOf(nonna)),
        draft(at(today.plusDays(15), tr("Assicurazione Vespa"), ReminderType.RENEWAL), yearly(today.plusDays(15)), items = listOf(scooter)),
        draft(at(today.plusDays(18), tr("Revisione caldaia"), ReminderType.DEADLINE), items = listOf(boiler)),
        draft(at(today.plusDays(26), tr("Rinnovo passaporto"), ReminderType.RENEWAL) { copy(priority = Priority.IMPORTANT) }, people = listOf(marco), items = listOf(passport)),
        birthday(nonna, today.plusDays(21), 1948),
        birthday(giulia, today.plusMonths(2).plusDays(4), 1987),
        birthday(marco, today.plusMonths(4).minusDays(3), 1985),
        birthday(luca, today.plusMonths(6).plusDays(10), 1986),
        draft(
            at(trip, tr("Weekend in Sicilia"), ReminderType.VACATION) {
                copy(
                    endDate = trip.plusDays(4),
                    trip = TripInfo(
                        destination = "Palermo",
                        legs = listOf(
                            TripLeg(TravelMode.PLANE, "ITA Airways", "AZ1781", tr("Roma FCO"), "Palermo PMO", trip, LocalTime.of(8, 40), "14A"),
                            TripLeg(TravelMode.PLANE, "ITA Airways", "AZ1790", "Palermo PMO", tr("Roma FCO"), trip.plusDays(4), LocalTime.of(19, 15), "14A"),
                        ),
                        stay = TripStay("Hotel Porta Felice", tr("Via Butera 45, Palermo"), trip, trip.plusDays(4), "HPF-58213", "+39 091 000 0000"),
                    ),
                )
            },
            people = listOf(giulia, marco, sofia),
        ),
        draft(at(trip, tr("Sveglia per il volo"), ReminderType.ALARM, LocalTime.of(5, 45))),
        // Feste per colorare il calendario.
        draft(at(LocalDate.of(today.year, 12, 25), tr("Natale"), ReminderType.HOLIDAY) { copy(notificationsEnabled = false) }),
        draft(at(Holidays.easter(today.year + 1), tr("Pasqua"), ReminderType.HOLIDAY) { copy(notificationsEnabled = false) }),
        draft(at(LocalDate.of(today.year, 11, 1), tr("Ognissanti"), ReminderType.HOLIDAY) { copy(notificationsEnabled = false) }),
    ).forEach { saveReminder(it) }

    // Qualcosa di già fatto, così la cronologia non è vuota.
    listOf(
        at(today.minusDays(1), tr("Spesa settimanale"), ReminderType.TASK),
        at(today.minusDays(3), tr("Pagare la mensa di Sofia"), ReminderType.PAYMENT),
    ).forEach { reminder ->
        saveReminder(draft(reminder))
        completeReminder(reminder.id)
    }
}
