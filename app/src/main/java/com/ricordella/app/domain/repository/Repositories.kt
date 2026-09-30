package com.ricordella.app.domain.repository

import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.model.Attachment
import com.ricordella.app.domain.model.AttachmentOwnerType
import com.ricordella.app.domain.model.Category
import com.ricordella.app.domain.model.Item
import com.ricordella.app.domain.model.ItemWithCategory
import com.ricordella.app.domain.model.MaintenanceRecord
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.PersonItemRole
import com.ricordella.app.domain.model.PersonWithRole
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderCompletion
import com.ricordella.app.domain.model.ReminderDraft
import com.ricordella.app.domain.model.ReminderFilter
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.model.SchedulingCandidate
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface ReminderRepository {
    fun observeReminder(id: String): Flow<ReminderWithLinks?>
    /** Promemoria attivi con scadenza entro [until] (inclusi gli scaduti), in ordine cronologico. */
    fun observeActiveUntil(until: LocalDate, limit: Int): Flow<List<ReminderWithLinks>>
    /** Promemoria utili per mostrare il periodo [from]-[to], inclusi i ricorrenti che vi proiettano occorrenze. */
    fun observeForRange(from: LocalDate, to: LocalDate): Flow<List<ReminderWithLinks>>
    fun observeFiltered(filter: ReminderFilter, today: LocalDate): Flow<List<ReminderWithLinks>>
    fun observeForPerson(personId: String): Flow<List<ReminderWithLinks>>
    fun observeForItem(itemId: String): Flow<List<ReminderWithLinks>>
    fun observeCompletions(reminderId: String): Flow<List<ReminderCompletion>>
    fun observeCategories(): Flow<List<String>>
    suspend fun getReminder(id: String): ReminderWithLinks?
    suspend fun getRemindersForItem(itemId: String): List<ReminderWithLinks>
    /** Crea o aggiorna promemoria, ricorrenza e collegamenti in un'unica transazione. */
    suspend fun save(draft: ReminderDraft)
    suspend fun update(reminder: Reminder)
    /** Aggiorna il promemoria e registra il completamento nello storico, atomicamente. */
    suspend fun recordCompletion(updated: Reminder, completion: ReminderCompletion)
    suspend fun delete(id: String)
    suspend fun getSchedulingCandidates(): List<SchedulingCandidate>
    suspend fun markNotified(ids: List<String>, at: Instant)
    suspend fun search(query: String, limit: Int): List<ReminderWithLinks>
}

interface PersonRepository {
    fun observePeople(archived: Boolean): Flow<List<Person>>
    fun observePerson(id: String): Flow<Person?>
    fun observePeopleForItem(itemId: String): Flow<List<PersonWithRole>>
    suspend fun getPerson(id: String): Person?
    suspend fun save(person: Person)
    suspend fun delete(id: String)
    suspend fun search(query: String, limit: Int): List<Person>
}

interface ItemRepository {
    fun observeItems(archived: Boolean): Flow<List<ItemWithCategory>>
    fun observeItem(id: String): Flow<ItemWithCategory?>
    fun observeItemsForPerson(personId: String): Flow<List<ItemWithCategory>>
    fun observeCategories(): Flow<List<Category>>
    suspend fun getItem(id: String): ItemWithCategory?
    suspend fun getOwners(itemId: String): List<PersonWithRole>
    /** Salva la cosa e sostituisce le persone associate, in un'unica transazione. */
    suspend fun save(item: Item, people: Map<String, PersonItemRole>)
    suspend fun update(item: Item)
    suspend fun delete(id: String)
    suspend fun search(query: String, limit: Int): List<ItemWithCategory>
}

interface MaintenanceRepository {
    fun observeForItem(itemId: String): Flow<List<MaintenanceRecord>>
    suspend fun save(record: MaintenanceRecord)
    suspend fun delete(id: String)
    suspend fun search(query: String, limit: Int): List<MaintenanceRecord>
}

interface AttachmentRepository {
    fun observeFor(ownerType: AttachmentOwnerType, ownerId: String): Flow<List<Attachment>>
    suspend fun add(attachment: Attachment)
    suspend fun delete(id: String)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
