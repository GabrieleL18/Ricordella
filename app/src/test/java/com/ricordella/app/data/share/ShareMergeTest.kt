package com.ricordella.app.data.share

import com.ricordella.app.data.backup.BackupDatabaseContent
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderPersonCrossRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ShareMergeTest {

    private val t0 = Instant.parse("2026-10-01T08:00:00Z")
    private fun at(minutes: Long) = t0.plusSeconds(minutes * 60)
    private fun reminder(id: String, title: String, updated: Instant) =
        Reminder(id = id, title = title, dueDate = LocalDate.of(2026, 10, 2), createdAt = t0, updatedAt = updated)

    @Test
    fun firstSyncKeepsBothSides() {
        val mine = BackupDatabaseContent(reminders = listOf(reminder("a", "Mio", t0)))
        val theirs = BackupDatabaseContent(reminders = listOf(reminder("b", "Suo", t0)))
        val merged = ShareMerge.merge(null, mine, theirs)
        assertEquals(setOf("a", "b"), merged.reminders.map { it.id }.toSet())
    }

    @Test
    fun newestEditWins() {
        val base = BackupDatabaseContent(reminders = listOf(reminder("a", "Prima", t0)))
        val mine = BackupDatabaseContent(reminders = listOf(reminder("a", "Mia modifica", at(5))))
        val theirs = BackupDatabaseContent(reminders = listOf(reminder("a", "Sua modifica", at(10))))
        assertEquals("Sua modifica", ShareMerge.merge(base, mine, theirs).reminders.single().title)
    }

    @Test
    fun deletionsPropagateUnlessEditedAfterwards() {
        val base = BackupDatabaseContent(reminders = listOf(reminder("a", "A", t0), reminder("b", "B", t0)))
        // Io ho cancellato "a"; l'altra persona ha cancellato "b" (e lo ha scritto nel file) ma io l'avevo modificato dopo.
        val mine = BackupDatabaseContent(reminders = listOf(reminder("b", "B modificato", at(3))))
        val theirs = BackupDatabaseContent(reminders = listOf(reminder("a", "A", t0)))
        val deleted = ShareMerge.localDeletions(base, mine) + mapOf("b" to t0)
        val merged = ShareMerge.merge(base, mine, theirs, deleted)
        assertEquals(listOf("b"), merged.reminders.map { it.id })
    }

    @Test
    fun missingFromFileWithoutDeletionIsKept() {
        // Il file è stato sovrascritto da una copia vecchia (Drive offline): "a" manca ma nessuno l'ha cancellato.
        val base = BackupDatabaseContent(reminders = listOf(reminder("a", "A", t0)))
        val mine = BackupDatabaseContent(reminders = listOf(reminder("a", "A", t0)))
        val merged = ShareMerge.merge(base, mine, BackupDatabaseContent(), ShareMerge.localDeletions(base, mine))
        assertEquals(listOf("a"), merged.reminders.map { it.id })
    }

    @Test
    fun bothEditedDifferentFieldsKeepsBoth() {
        val start = reminder("a", "Dentista", t0)
        val base = BackupDatabaseContent(reminders = listOf(start))
        // Offline: io cambio il titolo, l'altra persona la data. Nessuna delle due modifiche va persa.
        val mine = BackupDatabaseContent(reminders = listOf(start.copy(title = "Dentista Sofia", updatedAt = at(5))))
        val theirs = BackupDatabaseContent(reminders = listOf(start.copy(dueDate = LocalDate.of(2026, 10, 9), updatedAt = at(8))))
        val merged = ShareMerge.merge(base, mine, theirs).reminders.single()
        assertEquals("Dentista Sofia", merged.title)
        assertEquals(LocalDate.of(2026, 10, 9), merged.dueDate)
    }

    @Test
    fun editedOnlyThereWinsEvenIfOlderClock() {
        // Solo l'altra parte ha cambiato la voce rispetto all'ultima sincronizzazione: vince, qualunque sia l'orologio.
        val base = BackupDatabaseContent(reminders = listOf(reminder("a", "A", at(10))))
        val mine = BackupDatabaseContent(reminders = listOf(reminder("a", "A", at(10))))
        val theirs = BackupDatabaseContent(reminders = listOf(reminder("a", "A cambiato", at(9))))
        assertEquals("A cambiato", ShareMerge.merge(base, mine, theirs).reminders.single().title)
    }

    @Test
    fun linksFollowTheWinningSideAndPhotosStayLocal() {
        val anna = Person(id = "p", name = "Anna", photoUri = "content://mia/foto", createdAt = t0, updatedAt = t0)
        val mine = BackupDatabaseContent(people = listOf(anna), reminders = listOf(reminder("a", "A", t0)))
        val theirs = BackupDatabaseContent(
            people = listOf(anna.copy(photoUri = null, name = "Anna B", updatedAt = at(2))),
            reminders = listOf(reminder("a", "A", at(1))),
            reminderPeople = listOf(ReminderPersonCrossRef("a", "p")),
        )
        val merged = ShareMerge.merge(null, mine, theirs)
        assertEquals("Anna B", merged.people.single().name)
        assertEquals("content://mia/foto", merged.people.single().photoUri)
        assertTrue(ReminderPersonCrossRef("a", "p") in merged.reminderPeople)
        assertNull(ShareMerge.forFile(merged).people.single().photoUri)
    }
}
