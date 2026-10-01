package com.ricordella.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ricordella.app.domain.model.MaintenanceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceDao {

    @Query("SELECT * FROM maintenance_record WHERE itemId = :itemId ORDER BY date DESC, createdAt DESC")
    fun observeForItem(itemId: String): Flow<List<MaintenanceRecord>>

    /** Tutte le spese da [from] in poi (per il totale annuo di tutte le cose). */
    @Query("SELECT * FROM maintenance_record WHERE date >= :from")
    fun observeSince(from: java.time.LocalDate): Flow<List<MaintenanceRecord>>

    @Upsert
    suspend fun upsert(record: MaintenanceRecord)

    @Query("DELETE FROM maintenance_record WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        """
        SELECT * FROM maintenance_record
        WHERE title LIKE :pattern ESCAPE '\' OR description LIKE :pattern ESCAPE '\' OR notes LIKE :pattern ESCAPE '\'
        ORDER BY date DESC
        LIMIT :limit
        """,
    )
    suspend fun search(pattern: String, limit: Int): List<MaintenanceRecord>
}
