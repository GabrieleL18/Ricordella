package com.ricordella.app.data.backup

import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.model.Attachment
import com.ricordella.app.domain.model.AttachmentOwnerType
import com.ricordella.app.domain.model.Category
import com.ricordella.app.domain.model.Item
import com.ricordella.app.domain.model.ItemGroup
import com.ricordella.app.domain.model.ItemKind
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.PersonItemCrossRef
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderItemCrossRef
import com.ricordella.app.domain.model.ReminderPersonCrossRef
import com.ricordella.app.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupArchiveCodecTest {

    private val codec = BackupArchiveCodec()
    private val now = Instant.parse("2026-09-29T10:00:00Z")

    private fun sampleDatabase(): BackupDatabaseContent {
        val category = Category(id = "builtin-car", name = "Auto", itemGroup = ItemGroup.VEHICLES, kind = ItemKind.CAR, isBuiltIn = true)
        val person = Person(name = "Gabriele", createdAt = now, updatedAt = now)
        val car = Item(name = "Fiat Panda", categoryId = category.id, odometerKm = 84_230, photoUri = "content://photos/1", createdAt = now, updatedAt = now)
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY, startDate = LocalDate.of(2026, 10, 23), daysOfWeek = setOf(DayOfWeek.MONDAY))
        val reminder = Reminder(
            title = "Tagliando",
            dueDate = LocalDate.of(2026, 10, 23),
            dueTime = LocalTime.of(9, 30),
            recurrenceRuleId = rule.id,
            createdAt = now,
            updatedAt = now,
        )
        return BackupDatabaseContent(
            people = listOf(person),
            categories = listOf(category),
            items = listOf(car),
            recurrenceRules = listOf(rule),
            reminders = listOf(reminder),
            attachments = listOf(
                Attachment(ownerType = AttachmentOwnerType.ITEM, ownerId = car.id, uri = "content://docs/libretto", displayName = "Libretto.pdf", createdAt = now),
            ),
            reminderPeople = listOf(ReminderPersonCrossRef(reminder.id, person.id)),
            reminderItems = listOf(ReminderItemCrossRef(reminder.id, car.id)),
            personItems = listOf(PersonItemCrossRef(person.id, car.id)),
        )
    }

    private fun write(database: BackupDatabaseContent, files: Map<String, ByteArray> = emptyMap()): ByteArray {
        val output = ByteArrayOutputStream()
        codec.write(
            output,
            database,
            AppSettings(themeMode = ThemeMode.DARK),
            files.map { (uri, bytes) -> BackupArchiveCodec.FileSource(uri) { ByteArrayInputStream(bytes) } },
            appVersion = "test",
            createdAt = now.toString(),
        )
        return output.toByteArray()
    }

    private fun read(bytes: ByteArray, stored: MutableMap<String, ByteArray> = mutableMapOf()) =
        codec.read(ByteArrayInputStream(bytes)) { path, content -> stored[path] = content.readBytes() }

    @Test
    fun `export e import restituiscono gli stessi dati`() {
        val database = sampleDatabase()
        val stored = mutableMapOf<String, ByteArray>()
        val result = read(write(database, mapOf("content://photos/1" to byteArrayOf(1, 2, 3))), stored)

        assertTrue(result is BackupReadResult.Valid)
        val contents = (result as BackupReadResult.Valid).contents
        assertEquals(database, contents.database)
        assertEquals(ThemeMode.DARK, contents.settings?.themeMode)
        assertEquals(1, contents.manifest.files.size)
        assertEquals("content://photos/1", contents.manifest.files.single().originalUri)
        assertEquals(listOf<Byte>(1, 2, 3), stored.getValue(contents.manifest.files.single().path).toList())
        assertEquals(1, contents.summary.reminders)
    }

    @Test
    fun `i file non più leggibili vengono saltati senza bloccare il backup`() {
        val output = ByteArrayOutputStream()
        codec.write(output, sampleDatabase(), AppSettings(), listOf(BackupArchiveCodec.FileSource("content://gone") { null }), "test", now.toString())
        val result = read(output.toByteArray())
        assertTrue((result as BackupReadResult.Valid).contents.manifest.files.isEmpty())
    }

    @Test
    fun `un file qualsiasi non è un backup`() {
        assertEquals(BackupReadResult.NotABackup, read("non sono uno zip".toByteArray()))
    }

    @Test
    fun `backup corrotto - dati modificati rispetto alla firma`() {
        val tampered = rewriteEntry(write(sampleDatabase()), BackupArchiveCodec.DATABASE_PATH) { it.replace("Tagliando", "Manomesso") }
        assertTrue(read(tampered) is BackupReadResult.Corrupted)
    }

    @Test
    fun `backup con dati mancanti`() {
        val withoutDatabase = removeEntry(write(sampleDatabase()), BackupArchiveCodec.DATABASE_PATH)
        assertTrue(read(withoutDatabase) is BackupReadResult.Corrupted)
    }

    @Test
    fun `backup di una versione futura non è compatibile`() {
        val future = rewriteEntry(write(sampleDatabase()), BackupArchiveCodec.MANIFEST_PATH) {
            it.replace("\"formatVersion\":$BACKUP_FORMAT_VERSION", "\"formatVersion\":99")
        }
        assertEquals(BackupReadResult.IncompatibleVersion(99), read(future))
    }

    @Test
    fun `relazioni incoerenti vengono rifiutate`() {
        val broken = sampleDatabase().let { it.copy(reminderPeople = it.reminderPeople + ReminderPersonCrossRef("missing", "missing")) }
        val result = read(write(broken))
        assertTrue(result is BackupReadResult.Corrupted)
    }

    @Test
    fun `i riferimenti ai file vengono riscritti dopo il ripristino`() {
        val remapped = sampleDatabase().withFileUris(mapOf("content://photos/1" to "content://restored/1"))
        assertEquals("content://restored/1", remapped.items.single().photoUri)
        assertEquals("content://docs/libretto", remapped.attachments.single().uri)
    }

    private fun entries(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes()
            }
        }
        return result
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun rewriteEntry(bytes: ByteArray, name: String, transform: (String) -> String): ByteArray {
        val all = entries(bytes)
        all[name] = transform(all.getValue(name).decodeToString()).toByteArray()
        return zip(all)
    }

    private fun removeEntry(bytes: ByteArray, name: String): ByteArray = zip(entries(bytes).apply { remove(name) })
}
