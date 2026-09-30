package com.ricordella.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ricordella.app.domain.model.Attachment
import com.ricordella.app.domain.model.AttachmentOwnerType
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachment WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt")
    fun observeFor(ownerType: AttachmentOwnerType, ownerId: String): Flow<List<Attachment>>

    @Insert
    suspend fun insert(attachment: Attachment)

    @Query("DELETE FROM attachment WHERE id = :id")
    suspend fun delete(id: String)
}
