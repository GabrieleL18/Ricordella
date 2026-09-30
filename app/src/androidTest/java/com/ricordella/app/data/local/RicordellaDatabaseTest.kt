package com.ricordella.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ricordella.app.data.backup.BackupDatabaseContent
import com.ricordella.app.data.local.database.BuiltInCategories
import com.ricordella.app.data.local.database.RicordellaDatabase
import com.ricordella.app.data.repository.RoomReminderRepository
import com.ricordella.app.domain.model.Item
import com.ricordella.app.domain.model.ItemKind
import com.ricordella.app.domain.model.MaintenanceRecord
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.PersonItemCrossRef
import com.ricordella.app.domain.model.PersonItemRole
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderFilter
import com.ricordella.app.domain.model.ReminderListScope
import com.ricordella.app.domain.model.ReminderType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class RicordellaDatabaseTest {

    private lateinit var db: RicordellaDatabase
    private val now = Instant.parse("2026-09-29T10:00:00Z")
    private val today = LocalDate.of(2026, 9, 29)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RicordellaDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(): Triple<Person, Item, Reminder> {
        db.backupDao().insertCategories(BuiltInCategories.all)
        val person = Person(name = "Gabriele", createdAt = now, updatedAt = now)
        val car = Item(name = "Fiat Panda", categoryId = BuiltInCategories.idFor(ItemKind.CAR), odometerKm = 84_000, createdAt = now, updatedAt = now)
        db.personDao().upsert(person)
        db.itemDao().saveWithPeople(car, listOf(PersonItemCrossRef(person.id, car.id, PersonItemRole.OWNER)))
        val reminder = Reminder(
            title = "Tagliando",
            type = ReminderType.MAINTENANCE,
            dueDate = today.plusDays(24),
            dueTime = LocalTime.of(9, 0),
            createdAt = now,
            updatedAt = now,
        )
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.YEARLY, startDate = reminder.dueDate)
        db.reminderDao().save(reminder, rule, setOf(person.id), setOf(car.id))
        return Triple(person, car, reminder)
    }

    @Test
    fun relazioniPromemoriaPersonaCosa() = runTest {
        val (person, car, reminder) = seed()
        val loaded = db.reminderDao().getWithLinks(reminder.id)!!
        assertEquals(listOf(person.id), loaded.people.map { it.id })
        assertEquals(listOf(car.id), loaded.items.map { it.id })
        assertEquals(RecurrenceFrequency.YEARLY, loaded.recurrenceRule?.frequency)
        assertEquals(LocalTime.of(9, 0), loaded.reminder.dueTime)

        assertEquals(listOf(reminder.id), db.reminderDao().observeForPerson(person.id).first().map { it.reminder.id })
        assertEquals(listOf(reminder.id), db.reminderDao().observeForItem(car.id).first().map { it.reminder.id })
    }

    @Test
    fun relazionePersonaCosaConRuolo() = runTest {
        val (person, car, _) = seed()
        val owners = db.personDao().getPeopleForItem(car.id)
        assertEquals(PersonItemRole.OWNER, owners.single().role)
        assertEquals(listOf(car.id), db.itemDao().observeItemsForPerson(person.id).first().map { it.item.id })
        assertEquals(ItemKind.CAR, db.itemDao().getItem(car.id)?.category?.kind)
    }

    @Test
    fun eliminareUnPromemoriaNonEliminaLaCosa() = runTest {
        val (_, car, reminder) = seed()
        db.reminderDao().deleteWithDependencies(reminder.id)
        assertNull(db.reminderDao().getById(reminder.id))
        assertTrue(db.backupDao().recurrenceRules().isEmpty())
        assertEquals(car.id, db.itemDao().getItem(car.id)?.item?.id)
    }

    @Test
    fun eliminareUnaCosaRimuoveStoricoMaNonIPromemoria() = runTest {
        val (_, car, reminder) = seed()
        db.maintenanceDao().upsert(MaintenanceRecord(itemId = car.id, title = "Tagliando", date = today, createdAt = now))
        db.itemDao().deleteWithDependencies(car.id)
        assertTrue(db.backupDao().maintenance().isEmpty())
        assertTrue(db.backupDao().reminderItems().isEmpty())
        assertEquals(reminder.id, db.reminderDao().getById(reminder.id)?.id)
    }

    @Test
    fun queryFiltrataPerAmbitoERicerca() = runTest {
        val (_, car, _) = seed()
        val repository = RoomReminderRepository(db.reminderDao())
        val upcoming = repository.observeFiltered(ReminderFilter(scope = ReminderListScope.UPCOMING), today).first()
        assertEquals(1, upcoming.size)
        val byItemName = repository.observeFiltered(ReminderFilter(query = "panda"), today).first()
        assertEquals(1, byItemName.size)
        val overdue = repository.observeFiltered(ReminderFilter(scope = ReminderListScope.OVERDUE), today).first()
        assertTrue(overdue.isEmpty())
        val byItem = repository.observeFiltered(ReminderFilter(itemId = car.id), today).first()
        assertEquals(1, byItem.size)
        val wildcard = repository.observeFiltered(ReminderFilter(query = "%"), today).first()
        assertTrue(wildcard.isEmpty())
    }

    @Test
    fun ripristinoSostituisceTuttiIDati() = runTest {
        seed()
        val snapshot = db.backupDao().readAll()
        db.backupDao().replaceAll(BackupDatabaseContent(categories = BuiltInCategories.all))
        assertTrue(db.backupDao().reminders().isEmpty())
        db.backupDao().replaceAll(snapshot)
        assertEquals(snapshot, db.backupDao().readAll())
    }
}
