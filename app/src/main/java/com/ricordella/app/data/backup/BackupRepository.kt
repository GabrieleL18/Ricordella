package com.ricordella.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.ricordella.app.BuildConfig
import com.ricordella.app.data.local.dao.BackupDao
import com.ricordella.app.data.local.database.BuiltInCategories
import com.ricordella.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Un backup letto e validato, pronto per essere ripristinato dopo la conferma dell'utente. */
class PendingRestore internal constructor(
    val contents: BackupContents,
    internal val stagingDir: File,
)

/**
 * Esportazione e ripristino del backup manuale, e cancellazione di tutti i dati.
 * I file vengono letti/scritti tramite Storage Access Framework (URI scelti dall'utente).
 */
class BackupRepository(
    private val context: Context,
    private val backupDao: BackupDao,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val codec: BackupArchiveCodec = BackupArchiveCodec(),
) {
    private val resolver get() = context.contentResolver
    private val restoredDir get() = File(context.filesDir, RESTORED_DIR)

    suspend fun export(destination: Uri) = withContext(Dispatchers.IO) {
        val database = backupDao.readAll()
        val settings = settingsRepository.current()
        val files = database.fileUris().map { uri ->
            BackupArchiveCodec.FileSource(uri) { resolver.openInputStream(Uri.parse(uri)) }
        }
        val output = resolver.openOutputStream(destination, "wt") ?: throw IOException("Destinazione non disponibile")
        output.use {
            codec.write(it, database, settings, files, BuildConfig.VERSION_NAME, Instant.now(clock).toString())
        }
    }

    /** Legge e valida il backup; i file allegati vengono estratti in un'area temporanea. */
    suspend fun read(source: Uri): Pair<BackupReadResult, PendingRestore?> = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore-staging").apply {
            deleteRecursively()
            mkdirs()
        }
        val input = resolver.openInputStream(source) ?: throw IOException("File non disponibile")
        val result = input.use {
            codec.read(it) { path, content ->
                File(staging, path.substringAfterLast('/')).outputStream().use { out -> content.copyTo(out) }
            }
        }
        val pending = (result as? BackupReadResult.Valid)?.let { PendingRestore(it.contents, staging) }
        if (pending == null) staging.deleteRecursively()
        result to pending
    }

    /** Sostituisce tutti i dati attuali con quelli del backup, in un'unica transazione. */
    suspend fun restore(pending: PendingRestore) = withContext(Dispatchers.IO) {
        val contents = pending.contents
        restoredDir.mkdirs()
        val mapping = mutableMapOf<String, String>()
        val newFiles = mutableSetOf<String>()
        contents.manifest.files.forEach { entry ->
            val staged = File(pending.stagingDir, entry.path.substringAfterLast('/'))
            if (!staged.exists()) return@forEach
            val target = File(restoredDir, UUID.randomUUID().toString())
            staged.copyTo(target)
            newFiles += target.name
            mapping[entry.originalUri] = FileProvider.getUriForFile(context, fileProviderAuthority(), target).toString()
        }
        try {
            backupDao.replaceAll(contents.database.withFileUris(mapping))
        } catch (error: Exception) {
            newFiles.forEach { File(restoredDir, it).delete() }
            throw error
        }
        contents.settings?.let { restored -> settingsRepository.update { restored } }
        // I file ripristinati in precedenza e non più referenziati vengono rimossi.
        restoredDir.listFiles()?.filter { it.name !in newFiles }?.forEach { it.delete() }
        pending.stagingDir.deleteRecursively()
    }

    fun discard(pending: PendingRestore) {
        pending.stagingDir.deleteRecursively()
    }

    suspend fun deleteAllData() = withContext(Dispatchers.IO) {
        backupDao.replaceAll(BackupDatabaseContent(categories = BuiltInCategories.all))
        restoredDir.deleteRecursively()
    }

    private fun fileProviderAuthority() = "${context.packageName}.files"

    private companion object {
        const val RESTORED_DIR = "restored"
    }
}
