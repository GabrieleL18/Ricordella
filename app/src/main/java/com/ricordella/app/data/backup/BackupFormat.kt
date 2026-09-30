package com.ricordella.app.data.backup

import com.ricordella.app.core.i18n.tr

import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.model.Attachment
import com.ricordella.app.domain.model.Category
import com.ricordella.app.domain.model.Item
import com.ricordella.app.domain.model.MaintenanceRecord
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.PersonItemCrossRef
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderCompletion
import com.ricordella.app.domain.model.ReminderItemCrossRef
import com.ricordella.app.domain.model.ReminderPersonCrossRef
import kotlinx.serialization.Serializable

/** Versione del formato di backup. Va incrementata a ogni modifica incompatibile. */
const val BACKUP_FORMAT_VERSION = 1

/** Contenuto completo del database, così come viene salvato in database.json. */
@Serializable
data class BackupDatabaseContent(
    val people: List<Person> = emptyList(),
    val categories: List<Category> = emptyList(),
    val items: List<Item> = emptyList(),
    val recurrenceRules: List<RecurrenceRule> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val completions: List<ReminderCompletion> = emptyList(),
    val maintenance: List<MaintenanceRecord> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val reminderPeople: List<ReminderPersonCrossRef> = emptyList(),
    val reminderItems: List<ReminderItemCrossRef> = emptyList(),
    val personItems: List<PersonItemCrossRef> = emptyList(),
) {
    /** Tutti i riferimenti a file presenti nei dati (foto, documenti, allegati). */
    fun fileUris(): Set<String> = buildSet {
        people.mapNotNullTo(this) { it.photoUri }
        items.mapNotNullTo(this) { it.photoUri }
        items.mapNotNullTo(this) { it.warrantyDocumentUri }
        attachments.mapTo(this) { it.uri }
    }

    /** Sostituisce i riferimenti ai file secondo [mapping] (URI originale → nuovo URI). */
    fun withFileUris(mapping: Map<String, String>): BackupDatabaseContent {
        if (mapping.isEmpty()) return this
        fun remap(uri: String?) = uri?.let { mapping[it] ?: it }
        return copy(
            people = people.map { it.copy(photoUri = remap(it.photoUri)) },
            items = items.map {
                it.copy(photoUri = remap(it.photoUri), warrantyDocumentUri = remap(it.warrantyDocumentUri))
            },
            attachments = attachments.map { it.copy(uri = remap(it.uri) ?: it.uri) },
        )
    }

    /**
     * Verifica che tutte le relazioni puntino a elementi esistenti.
     * Restituisce la descrizione del primo problema trovato, o null se i dati sono coerenti.
     */
    fun findIntegrityProblem(): String? {
        val personIds = people.mapTo(HashSet()) { it.id }
        val itemIds = items.mapTo(HashSet()) { it.id }
        val reminderIds = reminders.mapTo(HashSet()) { it.id }
        val ruleIds = recurrenceRules.mapTo(HashSet()) { it.id }
        val categoryIds = categories.mapTo(HashSet()) { it.id }
        val allIds = listOf(people.map { it.id }, items.map { it.id }, reminders.map { it.id })
        return when {
            allIds.any { it.size != it.toSet().size } -> tr("identificatori duplicati")
            items.any { it.categoryId != null && it.categoryId !in categoryIds } -> tr("categoria mancante")
            reminders.any { it.recurrenceRuleId != null && it.recurrenceRuleId !in ruleIds } -> tr("ricorrenza mancante")
            completions.any { it.reminderId !in reminderIds } -> tr("storico senza promemoria")
            maintenance.any { it.itemId !in itemIds } -> tr("manutenzione senza cosa")
            reminderPeople.any { it.reminderId !in reminderIds || it.personId !in personIds } -> tr("collegamento promemoria-persona non valido")
            reminderItems.any { it.reminderId !in reminderIds || it.itemId !in itemIds } -> tr("collegamento promemoria-cosa non valido")
            personItems.any { it.personId !in personIds || it.itemId !in itemIds } -> tr("collegamento persona-cosa non valido")
            else -> null
        }
    }
}

@Serializable
data class BackupFileEntry(
    /** Percorso del file all'interno dello zip. */
    val path: String,
    /** URI originale a cui il file era associato nei dati. */
    val originalUri: String,
)

@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val appVersion: String,
    val createdAt: String,
    /** SHA-256 esadecimale di database.json, per verificarne l'integrità. */
    val databaseSha256: String,
    val files: List<BackupFileEntry> = emptyList(),
)

/** Riepilogo mostrato all'utente prima di confermare il ripristino. */
data class BackupSummary(
    val createdAt: String,
    val people: Int,
    val items: Int,
    val reminders: Int,
    val maintenance: Int,
    val files: Int,
)

data class BackupContents(
    val manifest: BackupManifest,
    val database: BackupDatabaseContent,
    val settings: AppSettings?,
) {
    val summary: BackupSummary
        get() = BackupSummary(
            createdAt = manifest.createdAt,
            people = database.people.size,
            items = database.items.size,
            reminders = database.reminders.size,
            maintenance = database.maintenance.size,
            files = manifest.files.size,
        )
}

sealed interface BackupReadResult {
    data class Valid(val contents: BackupContents) : BackupReadResult
    /** Il file non è un backup di Ricordella. */
    data object NotABackup : BackupReadResult
    /** Il backup è stato creato da una versione più recente dell'app. */
    data class IncompatibleVersion(val formatVersion: Int) : BackupReadResult
    /** Il backup è danneggiato o incoerente. */
    data class Corrupted(val reason: String) : BackupReadResult
}
