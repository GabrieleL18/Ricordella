package com.ricordella.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.ricordella.app.domain.model.AttachmentOwnerType
import com.ricordella.app.domain.model.Category
import com.ricordella.app.domain.model.Item
import com.ricordella.app.domain.model.ItemWithCategory
import com.ricordella.app.domain.model.PersonItemCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ItemDao {

    @Transaction
    @Query("SELECT * FROM item WHERE isArchived = :archived ORDER BY name COLLATE NOCASE")
    abstract fun observeItems(archived: Boolean): Flow<List<ItemWithCategory>>

    @Transaction
    @Query("SELECT * FROM item WHERE id = :id")
    abstract fun observeItem(id: String): Flow<ItemWithCategory?>

    @Transaction
    @Query("SELECT * FROM item WHERE id = :id")
    abstract suspend fun getItem(id: String): ItemWithCategory?

    @Transaction
    @Query(
        """
        SELECT item.* FROM item
        JOIN person_item ON person_item.itemId = item.id
        WHERE person_item.personId = :personId AND item.isArchived = 0
        ORDER BY item.name COLLATE NOCASE
        """,
    )
    abstract fun observeItemsForPerson(personId: String): Flow<List<ItemWithCategory>>

    @Query("SELECT * FROM category ORDER BY sortOrder, name COLLATE NOCASE")
    abstract fun observeCategories(): Flow<List<Category>>

    @Transaction
    @Query(
        """
        SELECT * FROM item
        WHERE name LIKE :pattern ESCAPE '\' OR brand LIKE :pattern ESCAPE '\' OR model LIKE :pattern ESCAPE '\'
            OR serialNumber LIKE :pattern ESCAPE '\' OR licensePlate LIKE :pattern ESCAPE '\'
            OR notes LIKE :pattern ESCAPE '\' OR warrantySeller LIKE :pattern ESCAPE '\'
        ORDER BY isArchived, name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    abstract suspend fun search(pattern: String, limit: Int): List<ItemWithCategory>

    @Upsert
    abstract suspend fun upsert(item: Item)

    @Update
    abstract suspend fun update(item: Item)

    @Query("DELETE FROM person_item WHERE itemId = :itemId")
    abstract suspend fun deletePeopleLinks(itemId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPeopleLinks(links: List<PersonItemCrossRef>)

    @Query("DELETE FROM item WHERE id = :id")
    abstract suspend fun deleteItem(id: String)

    @Query("DELETE FROM attachment WHERE ownerType = :ownerType AND ownerId = :ownerId")
    abstract suspend fun deleteAttachments(ownerType: AttachmentOwnerType, ownerId: String)

    /** Elimina le garanzie generate automaticamente per questa cosa. Gli altri promemoria restano. */
    @Query(
        """
        DELETE FROM reminder WHERE type = 'WARRANTY'
            AND id IN (SELECT reminderId FROM reminder_item WHERE itemId = :itemId)
        """,
    )
    abstract suspend fun deleteWarrantyReminders(itemId: String)

    @Transaction
    open suspend fun saveWithPeople(item: Item, links: List<PersonItemCrossRef>) {
        upsert(item)
        deletePeopleLinks(item.id)
        insertPeopleLinks(links)
    }

    @Transaction
    open suspend fun deleteWithDependencies(id: String) {
        deleteWarrantyReminders(id)
        deleteAttachments(AttachmentOwnerType.ITEM, id)
        deleteItem(id)
    }
}
