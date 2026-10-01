package com.ricordella.app.data.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.ricordella.app.data.backup.BackupDatabaseContent
import com.ricordella.app.data.local.dao.BackupDao
import com.ricordella.app.domain.ReminderScheduler
import com.ricordella.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Contenuto del file condiviso (JSON compresso). */
@Serializable
private data class SharedFile(val formatVersion: Int = FORMAT_VERSION, val savedAt: String = "", val content: BackupDatabaseContent = BackupDatabaseContent())

private const val FORMAT_VERSION = 1

/**
 * Condivisione con un'altra persona tramite un file su Google Drive (o qualsiasi app di file):
 * entrambi i telefoni leggono e scrivono lo stesso file, scelto con il selettore di Android.
 * Remindella non va su Internet: è l'app di Drive a sincronizzare il file tra i telefoni.
 *
 * A ogni apertura e chiusura dell'app (e su richiesta) si legge il file, si uniscono i dati con
 * [ShareMerge] e si riscrive il file con il risultato. La "base" (ultimo stato unito) resta sul
 * telefono e serve a capire cosa è stato cancellato da una parte o dall'altra.
 */
class SharedSpace(
    private val context: Context,
    private val backupDao: BackupDao,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val scope: CoroutineScope,
    /** Spento in modalità demo: i dati finti non devono arrivare all'altra persona. */
    private val enabled: Boolean = true,
) {
    sealed interface Status {
        data object Off : Status
        data object Syncing : Status
        data class Synced(val at: Instant, val changedHere: Boolean) : Status
        data class Failed(val reason: String) : Status
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
    private val mutex = Mutex()
    private val baseFile get() = File(context.filesDir, "shared-base.json.gz")
    private val resolver get() = context.contentResolver

    private val _status = MutableStateFlow<Status>(Status.Off)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Nuovo file appena creato con il selettore: ci si scrivono i dati di questo telefono. */
    suspend fun create(uri: Uri) = connect(uri)

    /** File già esistente (creato dall'altra persona): al primo giro si uniscono i dati di entrambi. */
    suspend fun join(uri: Uri) = connect(uri)

    private suspend fun connect(uri: Uri): Boolean {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        withContext(Dispatchers.IO) { baseFile.delete() }
        settings.update { it.copy(sharedFileUri = uri.toString()) }
        return sync()
    }

    suspend fun leave() {
        val uri = settings.current().sharedFileUri?.toUri()
        settings.update { it.copy(sharedFileUri = null, sharedLastSync = null) }
        withContext(Dispatchers.IO) { baseFile.delete() }
        uri?.let { runCatching { resolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        _status.value = Status.Off
    }

    /** Sincronizza senza bloccare chi chiama; se una sincronizzazione è già in corso non ne parte un'altra. */
    fun syncInBackground() {
        if (mutex.isLocked) return
        scope.launch { sync() }
    }

    suspend fun sync(): Boolean = mutex.withLock {
        val uri = settings.current().sharedFileUri?.toUri()?.takeIf { enabled } ?: run { _status.value = Status.Off; return false }
        _status.value = Status.Syncing
        val result = runCatching { withContext(Dispatchers.IO) { syncWith(uri) } }
        _status.value = result.fold(
            onSuccess = { changed -> Status.Synced(Instant.now(), changed) },
            onFailure = { Status.Failed(it.message ?: "") },
        )
        if (result.isSuccess) settings.update { it.copy(sharedLastSync = Instant.now().toEpochMilli()) }
        result.isSuccess
    }

    /** Restituisce true se i dati di questo telefono sono cambiati. */
    private suspend fun syncWith(uri: Uri): Boolean {
        val remote = read(uri)
        val local = backupDao.readAll()
        val base = readBase()
        val merged = ShareMerge.merge(base, local, remote)
        val forFile = ShareMerge.forFile(merged)
        if (!sameData(forFile, ShareMerge.forFile(remote))) write(uri, forFile)
        val changedHere = !sameData(merged, local)
        // ponytail: tra la lettura e la sostituzione passano pochi millisecondi; una modifica fatta
        // proprio in quell'istante verrebbe ripresa alla sincronizzazione successiva solo se ancora presente.
        if (changedHere) {
            backupDao.replaceAll(merged)
            scheduler.refresh()
        }
        writeBase(forFile)
        return changedHere
    }

    private fun read(uri: Uri): BackupDatabaseContent {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("File condiviso non disponibile")
        if (bytes.isEmpty()) return BackupDatabaseContent()
        val text = runCatching { GZIPInputStream(bytes.inputStream()).use { it.readBytes() }.decodeToString() }
            .getOrElse { throw IOException("Il file scelto non è un file condiviso di Remindella") }
        val file = json.decodeFromString(SharedFile.serializer(), text)
        if (file.formatVersion > FORMAT_VERSION) throw IOException("File creato con una versione più recente di Remindella")
        return file.content
    }

    private fun write(uri: Uri, content: BackupDatabaseContent) {
        val output = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
            ?: throw IOException("File condiviso non scrivibile")
        output.use { out ->
            GZIPOutputStream(out).use { gz ->
                gz.write(json.encodeToString(SharedFile.serializer(), SharedFile(savedAt = Instant.now().toString(), content = content)).toByteArray())
            }
        }
    }

    private fun readBase(): BackupDatabaseContent? = runCatching {
        if (!baseFile.exists()) return null
        json.decodeFromString(BackupDatabaseContent.serializer(), GZIPInputStream(baseFile.inputStream()).use { it.readBytes() }.decodeToString())
    }.getOrNull()

    private fun writeBase(content: BackupDatabaseContent) {
        GZIPOutputStream(baseFile.outputStream()).use { it.write(json.encodeToString(BackupDatabaseContent.serializer(), content).toByteArray()) }
    }

    /** Stessi dati, indipendentemente dall'ordine. */
    private fun sameData(a: BackupDatabaseContent, b: BackupDatabaseContent): Boolean =
        a.people.toSet() == b.people.toSet() && a.items.toSet() == b.items.toSet() &&
            a.reminders.toSet() == b.reminders.toSet() && a.recurrenceRules.toSet() == b.recurrenceRules.toSet() &&
            a.completions.toSet() == b.completions.toSet() && a.maintenance.toSet() == b.maintenance.toSet() &&
            a.categories.toSet() == b.categories.toSet() && a.reminderPeople.toSet() == b.reminderPeople.toSet() &&
            a.reminderItems.toSet() == b.reminderItems.toSet() && a.personItems.toSet() == b.personItems.toSet() &&
            a.attachments.toSet() == b.attachments.toSet()
}
