package com.ricordella.app.data.trash

import android.content.Context
import com.ricordella.app.data.backup.BackupDatabaseContent
import com.ricordella.app.data.local.dao.BackupDao
import com.ricordella.app.domain.model.AttachmentOwnerType
import com.ricordella.app.domain.model.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

enum class TrashKind { REMINDER, ITEM, PERSON, MAINTENANCE }

/** Una cosa eliminata, con tutto ciò che serve per rimetterla com'era. Resta [KEEP_DAYS] giorni. */
@Serializable
data class TrashEntry(
    val id: String,
    val kind: TrashKind,
    val title: String,
    /** Quanti elementi (più di uno se viene dalla pulizia annuale). */
    val count: Int = 1,
    val deletedAtMillis: Long,
    val content: BackupDatabaseContent,
) {
    val deletedAt: Instant get() = Instant.ofEpochMilli(deletedAtMillis)
    fun daysLeft(now: Instant): Int = (KEEP_DAYS - java.time.Duration.between(deletedAt, now).toDays()).toInt().coerceAtLeast(0)
}

const val KEEP_DAYS = 7L

/**
 * Cestino: prima di eliminare un promemoria, una cosa, una persona o un intervento si salva una copia
 * in un file (con collegamenti, storico e ricorrenza). Per sette giorni si può recuperare dalle Impostazioni;
 * poi il file viene cancellato e l'eliminazione diventa definitiva.
 */
class Trash(private val context: Context, private val backupDao: BackupDao) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val dir get() = File(context.filesDir, "trash").apply { mkdirs() }
    private val _entries = MutableStateFlow<List<TrashEntry>>(emptyList())
    val entries: StateFlow<List<TrashEntry>> = _entries.asStateFlow()

    /** Da chiamare prima di eliminare i promemoria [ids] (anche tanti, dalla pulizia annuale). */
    suspend fun saveReminders(ids: Collection<String>) {
        val all = runCatching { backupDao.readAll() }.getOrNull() ?: return
        val reminders = all.reminders.filter { it.id in ids }
        if (reminders.isEmpty()) return
        val ruleIds = reminders.mapNotNullTo(HashSet()) { it.recurrenceRuleId }
        put(
            TrashKind.REMINDER, reminders.first().title, reminders.size,
            BackupDatabaseContent(
                reminders = reminders,
                recurrenceRules = all.recurrenceRules.filter { it.id in ruleIds },
                completions = all.completions.filter { it.reminderId in ids },
                attachments = all.attachments.filter { it.ownerType == AttachmentOwnerType.REMINDER && it.ownerId in ids },
                reminderPeople = all.reminderPeople.filter { it.reminderId in ids },
                reminderItems = all.reminderItems.filter { it.reminderId in ids },
            ),
        )
    }

    suspend fun saveItem(id: String) {
        val all = runCatching { backupDao.readAll() }.getOrNull() ?: return
        val item = all.items.firstOrNull { it.id == id } ?: return
        // Le garanzie generate dalla cosa vengono eliminate con lei.
        val warranty = all.reminders.filter { r -> r.type == com.ricordella.app.domain.model.ReminderType.WARRANTY && all.reminderItems.any { it.reminderId == r.id && it.itemId == id } }
        val warrantyIds = warranty.mapTo(HashSet()) { it.id }
        val ruleIds = warranty.mapNotNullTo(HashSet()) { it.recurrenceRuleId }
        put(
            TrashKind.ITEM, item.name, 1,
            BackupDatabaseContent(
                items = listOf(item),
                maintenance = all.maintenance.filter { it.itemId == id },
                reminders = warranty,
                recurrenceRules = all.recurrenceRules.filter { it.id in ruleIds },
                completions = all.completions.filter { it.reminderId in warrantyIds },
                attachments = all.attachments.filter { (it.ownerType == AttachmentOwnerType.ITEM && it.ownerId == id) || (it.ownerType == AttachmentOwnerType.REMINDER && it.ownerId in warrantyIds) },
                reminderItems = all.reminderItems.filter { it.itemId == id },
                personItems = all.personItems.filter { it.itemId == id },
            ),
        )
    }

    suspend fun savePerson(id: String) {
        val all = runCatching { backupDao.readAll() }.getOrNull() ?: return
        val person = all.people.firstOrNull { it.id == id } ?: return
        put(
            TrashKind.PERSON, person.name, 1,
            BackupDatabaseContent(
                people = listOf(person),
                reminderPeople = all.reminderPeople.filter { it.personId == id },
                personItems = all.personItems.filter { it.personId == id },
            ),
        )
    }

    suspend fun saveMaintenance(id: String) {
        val all = runCatching { backupDao.readAll() }.getOrNull() ?: return
        val record = all.maintenance.firstOrNull { it.id == id } ?: return
        put(TrashKind.MAINTENANCE, record.title, 1, BackupDatabaseContent(maintenance = listOf(record)))
    }

    private suspend fun put(kind: TrashKind, title: String, count: Int, content: BackupDatabaseContent) = withContext(Dispatchers.IO) {
        val entry = TrashEntry(newId(), kind, title, count, System.currentTimeMillis(), content)
        runCatching { File(dir, entry.id + ".json").writeText(json.encodeToString(TrashEntry.serializer(), entry)) }
        reload()
    }

    /** Rilegge i file e toglie quelli scaduti. */
    suspend fun reload() = withContext(Dispatchers.IO) {
        val now = Instant.now()
        val list = dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { file ->
            runCatching { json.decodeFromString(TrashEntry.serializer(), file.readText()) }.getOrNull()
                ?.takeIf { it.daysLeft(now) > 0 }
                ?: run { file.delete(); null }
        }.sortedByDescending { it.deletedAtMillis }
        _entries.value = list
    }

    /** Rimette com'era quello che c'era nel cestino. I collegamenti verso cose ormai sparite si saltano. */
    suspend fun restore(entry: TrashEntry) {
        val now = Instant.now()
        val c = entry.content
        // Modificati "adesso": nel file condiviso un elemento ripristinato batte la sua cancellazione.
        val fresh = c.copy(
            people = c.people.map { it.copy(updatedAt = now) },
            items = c.items.map { it.copy(updatedAt = now) },
            reminders = c.reminders.map { it.copy(updatedAt = now) },
            completions = c.completions.map { it.copy(id = newId()) },
            maintenance = c.maintenance.map { it.copy(id = newId()) },
        )
        backupDao.restoreSubset(fresh)
        discard(entry)
    }

    suspend fun discard(entry: TrashEntry) {
        withContext(Dispatchers.IO) { File(dir, entry.id + ".json").delete() }
        reload()
    }
}
