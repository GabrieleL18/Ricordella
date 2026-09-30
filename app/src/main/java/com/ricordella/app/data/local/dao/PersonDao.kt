package com.ricordella.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.PersonWithRole
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Query("SELECT * FROM person WHERE isArchived = :archived ORDER BY name COLLATE NOCASE, surname COLLATE NOCASE")
    fun observePeople(archived: Boolean): Flow<List<Person>>

    @Query("SELECT * FROM person WHERE id = :id")
    fun observePerson(id: String): Flow<Person?>

    @Query("SELECT * FROM person WHERE id = :id")
    suspend fun getPerson(id: String): Person?

    @Query(
        """
        SELECT person.*, person_item.role AS role FROM person
        JOIN person_item ON person_item.personId = person.id
        WHERE person_item.itemId = :itemId
        ORDER BY person.name COLLATE NOCASE
        """,
    )
    fun observePeopleForItem(itemId: String): Flow<List<PersonWithRole>>

    @Query(
        """
        SELECT person.*, person_item.role AS role FROM person
        JOIN person_item ON person_item.personId = person.id
        WHERE person_item.itemId = :itemId
        """,
    )
    suspend fun getPeopleForItem(itemId: String): List<PersonWithRole>

    @Upsert
    suspend fun upsert(person: Person)

    @Query("DELETE FROM person WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        """
        SELECT * FROM person
        WHERE name LIKE :pattern ESCAPE '\' OR surname LIKE :pattern ESCAPE '\' OR notes LIKE :pattern ESCAPE '\'
        ORDER BY isArchived, name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    suspend fun search(pattern: String, limit: Int): List<Person>
}
