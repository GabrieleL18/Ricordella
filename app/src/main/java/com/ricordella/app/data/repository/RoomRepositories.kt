package com.ricordella.app.data.repository

import com.ricordella.app.data.local.dao.AttachmentDao
import com.ricordella.app.data.local.dao.ItemDao
import com.ricordella.app.data.local.dao.MaintenanceDao
import com.ricordella.app.data.local.dao.PersonDao
import com.ricordella.app.domain.model.Attachment
import com.ricordella.app.domain.model.AttachmentOwnerType
import com.ricordella.app.domain.model.Item
import com.ricordella.app.domain.model.MaintenanceRecord
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.PersonItemCrossRef
import com.ricordella.app.domain.model.PersonItemRole
import com.ricordella.app.domain.repository.AttachmentRepository
import com.ricordella.app.domain.repository.ItemRepository
import com.ricordella.app.domain.repository.MaintenanceRepository
import com.ricordella.app.domain.repository.PersonRepository

class RoomPersonRepository(private val dao: PersonDao, private val trash: com.ricordella.app.data.trash.Trash) : PersonRepository {
    override fun observePeople(archived: Boolean) = dao.observePeople(archived)
    override fun observePerson(id: String) = dao.observePerson(id)
    override fun observePeopleForItem(itemId: String) = dao.observePeopleForItem(itemId)
    override suspend fun getPerson(id: String) = dao.getPerson(id)
    override suspend fun save(person: Person) = dao.upsert(person)
    override suspend fun delete(id: String) {
        trash.savePerson(id)
        dao.delete(id)
    }
    override suspend fun search(query: String, limit: Int) = dao.search(likePattern(query), limit)
}

class RoomItemRepository(
    private val itemDao: ItemDao,
    private val personDao: PersonDao,
) : ItemRepository {
    override fun observeItems(archived: Boolean) = itemDao.observeItems(archived)
    override fun observeItem(id: String) = itemDao.observeItem(id)
    override fun observeItemsForPerson(personId: String) = itemDao.observeItemsForPerson(personId)
    override fun observeCategories() = itemDao.observeCategories()
    override suspend fun getItem(id: String) = itemDao.getItem(id)
    override suspend fun getOwners(itemId: String) = personDao.getPeopleForItem(itemId)

    override suspend fun save(item: Item, people: Map<String, PersonItemRole>) =
        itemDao.saveWithPeople(item, people.map { (personId, role) -> PersonItemCrossRef(personId, item.id, role) })

    override suspend fun update(item: Item) = itemDao.update(item)
    override suspend fun delete(id: String) = itemDao.deleteWithDependencies(id)
    override suspend fun search(query: String, limit: Int) = itemDao.search(likePattern(query), limit)
}

class RoomMaintenanceRepository(private val dao: MaintenanceDao, private val trash: com.ricordella.app.data.trash.Trash) : MaintenanceRepository {
    override fun observeForItem(itemId: String) = dao.observeForItem(itemId)
    override fun observeSince(from: java.time.LocalDate) = dao.observeSince(from)
    override suspend fun save(record: MaintenanceRecord) = dao.upsert(record)
    override suspend fun delete(id: String) {
        trash.saveMaintenance(id)
        dao.delete(id)
    }
    override suspend fun search(query: String, limit: Int) = dao.search(likePattern(query), limit)
}

class RoomAttachmentRepository(private val dao: AttachmentDao) : AttachmentRepository {
    override fun observeFor(ownerType: AttachmentOwnerType, ownerId: String) = dao.observeFor(ownerType, ownerId)
    override suspend fun add(attachment: Attachment) = dao.insert(attachment)
    override suspend fun delete(id: String) = dao.delete(id)
}
