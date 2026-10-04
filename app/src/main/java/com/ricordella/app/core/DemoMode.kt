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
import com.ricordella.app.domain.model.Note
import com.ricordella.app.domain.model.NoteLine
import com.ricordella.app.domain.date.nextAlarmDate
import java.time.DayOfWeek
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

/** Versione dei dati demo: da alzare ogni volta che si aggiungono o cambiano gli esempi. */
private const val DEMO_DATA_VERSION = 5

/** Riempie il database demo al primo avvio in modalità demo (se è già pieno non fa nulla). */
suspend fun AppContainer.seedDemoDataIfEmpty() {
    // Quando i dati demo cambiano si alza DEMO_DATA_VERSION: al prossimo avvio demo si svuota e si rifà tutto.
    val prefs = appContext.getSharedPreferences("demo_mode", Context.MODE_PRIVATE)
    if (personRepository.observePeople(archived = false).first().isNotEmpty()) {
        if (prefs.getInt("seed_version", 0) == DEMO_DATA_VERSION) return
        clearAllTables()
        settingsRepository.update { com.ricordella.app.domain.model.AppSettings() }
    }
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
    // Profilo principale già scelto e tutorial delle sezioni già visti: niente finestre sopra gli screenshot (si riaprono da Sviluppatore › tutorial).
    settingsRepository.update {
        it.copy(
            sharedMeId = giulia.id,
            sharedMeName = giulia.name,
            sectionTutorialsSeen = setOf("home", "calendar", "reminders", "items", "people", "potions", "resolutions"),
        )
    }

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
    // Le sveglie non hanno una data: suonano alla prossima occasione nei giorni scelti.
    fun alarm(title: String, ring: LocalTime, days: Set<DayOfWeek>, people: List<Person>, enabled: Boolean = true): ReminderDraft {
        val date = nextAlarmDate(ring, days, time.localNow())
        return draft(
            at(date, title, ReminderType.ALARM, ring) { copy(notificationsEnabled = enabled) },
            RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY, startDate = date, daysOfWeek = days),
            people,
        )
    }
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
        draft(at(trip, tr("Sveglia per il volo"), ReminderType.ALARM, LocalTime.of(5, 45)), people = listOf(marco)),
        alarm(tr("Sveglia"), LocalTime.of(6, 45), DayOfWeek.entries.take(5).toSet(), listOf(giulia)),
        alarm(tr("Sveglia"), LocalTime.of(7, 30), DayOfWeek.entries.take(5).toSet(), listOf(marco)),
        alarm(tr("Weekend"), LocalTime.of(9, 0), setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), listOf(giulia), enabled = false),
        // Feste per colorare il calendario.
        draft(at(LocalDate.of(today.year, 12, 25), tr("Natale"), ReminderType.HOLIDAY) { copy(notificationsEnabled = false) }),
        draft(at(Holidays.easter(today.year + 1), tr("Pasqua"), ReminderType.HOLIDAY) { copy(notificationsEnabled = false) }),
        draft(at(LocalDate.of(today.year, 11, 1), tr("Ognissanti"), ReminderType.HOLIDAY) { copy(notificationsEnabled = false) }),
    ).forEach { saveReminder(it) }

    // Note: una lista della spesa coi prezzi, una lista semplice e una nota di testo.
    fun line(text: String, cents: Long? = null, qty: Int = 1, done: Boolean = false) = NoteLine(text = text, priceCents = cents, qty = qty, done = done)
    val nowMillis = System.currentTimeMillis()
    settingsRepository.update {
        it.copy(
            notes = listOf(
                Note(
                    title = tr("Spesa del sabato"), isList = true, prices = true, pinned = true, updatedAt = nowMillis,
                    lines = listOf(
                        line(tr("Latte"), 140, 2, done = true), line(tr("Pane"), 220, done = true), line(tr("Pasta"), 110, 3),
                        line(tr("Mele"), 290), line(tr("Parmigiano"), 780), line(tr("Detersivo"), 450),
                    ),
                ),
                Note(
                    title = tr("Valigia per la Sicilia"), isList = true, updatedAt = nowMillis - 3_600_000,
                    lines = listOf(line(tr("Passaporti"), done = true), line(tr("Costumi"), done = true), line(tr("Crema solare")), line(tr("Caricatori"))),
                ),
                Note(
                    title = tr("Idee regalo per Sofia"), updatedAt = nowMillis - 7_200_000,
                    text = tr("Un libro di avventure, il set per i braccialetti e le cuffie per la piscina."),
                ),
            ),
        )
    }

    // Ciclo di Giulia: in corso da qualche giorno, con la cronologia dei mesi scorsi.
    val period = com.ricordella.app.domain.model.CycleProfile(giulia.id, periodDays = 5, cycleDays = 28, notifyLate = false)
    settingsRepository.update {
        it.copy(
            cycleProfiles = listOf(period),
            // Un anno di storia con cicli e durate che variano un po', fino a quello in corso.
            cycleLog = run {
                val gaps = listOf(28, 30, 27, 29, 28, 31, 27, 28, 29, 30, 28)
                val lengths = listOf(5, 4, 5, 6, 5, 4, 5, 5, 6, 4, 5)
                var start = today.minusDays(3)
                listOf(com.ricordella.app.domain.model.CycleEntry(giulia.id, start)) + gaps.indices.map { i ->
                    start = start.minusDays(gaps[i].toLong())
                    com.ricordella.app.domain.model.CycleEntry(giulia.id, start, start.plusDays(lengths[i] - 1L))
                }
            },
        )
    }

    // Spese: mutuo a rate, abbonamento, spese divise e qualche entrata.
    val couple = listOf(giulia, marco)
    val mortgageFirst = today.minusMonths(6).withDayOfMonth(5)
    var mortgage = com.ricordella.app.domain.model.InstallmentPlan.create(mortgageFirst, 240, 65_000, 1, false, false)
    (0 until 6).forEach { mortgage = mortgage.pay(it, mortgage.dueDate(it)) }
    val netflixStart = today.minusMonths(7).withDayOfMonth(14)
    val netflixNext = generateSequence(netflixStart) { it.plusMonths(1) }.first { !it.isBefore(today) }
    listOf(
        draft(
            at(mortgage.dueDate(mortgage.nextUnpaid() ?: 0), tr("Mutuo casa"), ReminderType.PAYMENT) {
                copy(plan = mortgage, money = com.ricordella.app.domain.model.MoneyInfo(com.ricordella.app.domain.model.MoneyKind.INSTALLMENTS))
            },
            RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, startDate = mortgage.dueDate(mortgage.nextUnpaid() ?: 0), endDate = mortgage.lastDate, dayOfMonth = 5),
            couple,
        ),
        draft(
            at(netflixNext, "Netflix", ReminderType.PAYMENT) {
                copy(money = com.ricordella.app.domain.model.MoneyInfo(com.ricordella.app.domain.model.MoneyKind.SUBSCRIPTION, 1_399))
            },
            RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, startDate = netflixStart),
            couple,
        ),
        draft(
            at(today.plusDays(10), tr("Palestra di Marco"), ReminderType.PAYMENT) {
                copy(money = com.ricordella.app.domain.model.MoneyInfo(com.ricordella.app.domain.model.MoneyKind.SUBSCRIPTION, 4_500))
            },
            RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, startDate = today.plusDays(10).minusMonths(5)),
            listOf(marco),
        ),
    ).forEach { saveReminder(it) }
    // Casi più complessi: più persone, quote diverse, rate trimestrali variabili, abbonamento annuale.
    fun next(start: LocalDate, months: Long) = generateSequence(start) { it.plusMonths(months) }.first { !it.isBefore(today) }
    fun info(kind: com.ricordella.app.domain.model.MoneyKind, cents: Long, shares: Map<String, Long> = emptyMap()) =
        com.ricordella.app.domain.model.MoneyInfo(kind, cents, shares)
    val expense = com.ricordella.app.domain.model.MoneyKind.EXPENSE
    val subscription = com.ricordella.app.domain.model.MoneyKind.SUBSCRIPTION
    val income = com.ricordella.app.domain.model.MoneyKind.INCOME
    val yearStart = LocalDate.of(today.year, 1, 1)
    // Affitto dello studio diviso in tre con quote diverse (50% / 30% / 20%).
    val studioStart = yearStart.withDayOfMonth(3)
    // Condominio a rate trimestrali con importi che cambiano: le prime 3 pagate, le altre da pagare.
    var condo = com.ricordella.app.domain.model.InstallmentPlan.create(yearStart.withDayOfMonth(20), 8, 28_000, 3, true, false)
    condo = condo.withAmount(2, 31_500, following = true).withAmount(5, 29_000, following = false)
    (0 until 3).forEach { condo = condo.pay(it, condo.dueDate(it)) }
    val condoDue = condo.dueDate(condo.nextUnpaid() ?: 0)
    val insuranceStart = yearStart.withDayOfMonth(22).plusMonths(4)
    listOf(
        draft(
            at(next(studioStart, 1), tr("Affitto studio"), ReminderType.PAYMENT) { copy(money = info(subscription, 60_000, mapOf(marco.id to 50L, giulia.id to 30L, luca.id to 20L))) },
            RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, startDate = studioStart),
            listOf(marco, giulia, luca),
        ),
        draft(
            at(condoDue, tr("Condominio"), ReminderType.PAYMENT) { copy(plan = condo, money = info(com.ricordella.app.domain.model.MoneyKind.INSTALLMENTS, 0)) },
            RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, interval = 3, startDate = condoDue, endDate = condo.lastDate, dayOfMonth = 20),
            couple,
        ),
        draft(
            at(next(insuranceStart, 12), tr("Assicurazione auto"), ReminderType.PAYMENT) { copy(money = info(subscription, 48_000)) },
            RecurrenceRule(frequency = RecurrenceFrequency.YEARLY, startDate = insuranceStart),
            listOf(giulia),
        ),
        draft(
            at(next(yearStart.withDayOfMonth(8), 1), tr("Corso di nuoto di Sofia"), ReminderType.PAYMENT) { copy(money = info(subscription, 4_000)) },
            RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, startDate = yearStart.withDayOfMonth(8)),
            listOf(sofia),
        ),
    ).forEach { saveReminder(it) }
    listOf(
        // Cena di gruppo in quattro con quote diverse.
        draft(
            at(today.minusDays(14), tr("Cena di gruppo"), ReminderType.PAYMENT) { copy(money = info(expense, 18_700, mapOf(giulia.id to 5_000L, marco.id to 5_000L, luca.id to 5_000L, nonna.id to 3_700L))) },
            people = listOf(giulia, marco, luca, nonna),
        ),
        draft(
            at(today.minusMonths(1).minusDays(5), tr("Regalo per la nonna"), ReminderType.PAYMENT) { copy(money = info(expense, 12_000)) },
            people = listOf(giulia, marco, luca),
        ),
        draft(
            at(today.minusMonths(4), tr("Rimborso del dentista"), ReminderType.PAYMENT) { copy(money = info(income, 24_000, mapOf(giulia.id to 1L, marco.id to 1L))) },
            people = couple,
        ),
        draft(
            at(today.minusMonths(5).withDayOfMonth(10), tr("Libri e zaino di Sofia"), ReminderType.PAYMENT) { copy(money = info(expense, 15_650)) },
            people = listOf(sofia, giulia),
        ),
        draft(
            at(today.minusMonths(2).minusDays(8), tr("Stipendio extra"), ReminderType.PAYMENT) { copy(money = info(income, 120_000)) },
            people = listOf(marco),
        ),
    ).forEach { entry ->
        saveReminder(entry)
        completeReminder(entry.reminder.id)
    }
    // Già pagate o incassate, così il riepilogo dell'anno ha qualcosa da mostrare.
    listOf(
        draft(
            at(today.minusDays(20), tr("Vacanza in montagna"), ReminderType.PAYMENT) {
                copy(money = com.ricordella.app.domain.model.MoneyInfo(com.ricordella.app.domain.model.MoneyKind.EXPENSE, 90_000, mapOf(giulia.id to 60L, marco.id to 40L)))
            },
            people = couple,
        ),
        draft(
            at(today.minusMonths(2), tr("Spese condominiali"), ReminderType.PAYMENT) {
                copy(money = com.ricordella.app.domain.model.MoneyInfo(com.ricordella.app.domain.model.MoneyKind.EXPENSE, 31_000))
            },
            people = couple,
        ),
        draft(
            at(today.minusMonths(3), tr("Regalo di compleanno"), ReminderType.PAYMENT) {
                copy(money = com.ricordella.app.domain.model.MoneyInfo(com.ricordella.app.domain.model.MoneyKind.INCOME, 10_000))
            },
            people = listOf(sofia),
        ),
        draft(
            at(today.minusDays(9), tr("Bonifico dalla nonna"), ReminderType.PAYMENT) {
                copy(money = com.ricordella.app.domain.model.MoneyInfo(com.ricordella.app.domain.model.MoneyKind.INCOME, 5_000))
            },
            people = listOf(sofia),
        ),
    ).forEach { entry ->
        saveReminder(entry)
        completeReminder(entry.reminder.id)
    }

    // Qualcosa di già fatto, così la cronologia non è vuota.
    listOf(
        at(today.minusDays(1), tr("Spesa settimanale"), ReminderType.TASK),
        at(today.minusDays(3), tr("Pagare la mensa di Sofia"), ReminderType.PAYMENT),
    ).forEach { reminder ->
        saveReminder(draft(reminder))
        completeReminder(reminder.id)
    }
    // Solo a lavoro finito: se qualcosa si interrompe a metà, al prossimo avvio si rifà tutto.
    prefs.edit().putInt("seed_version", DEMO_DATA_VERSION).commit()
}
