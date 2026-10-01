package com.ricordella.app.data.backup

import com.ricordella.app.core.i18n.tr

import androidx.core.net.toUri
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import androidx.core.content.FileProvider
import com.ricordella.app.data.media.MediaStorage
import com.ricordella.app.BuildConfig
import com.ricordella.app.data.local.dao.BackupDao
import com.ricordella.app.data.local.database.BuiltInCategories
import com.ricordella.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
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

    /**
     * Crea il backup in un file temporaneo condivisibile (come "Condividi file") e ne
     * restituisce l'URI. Resta solo l'ultimo export: il precedente viene sovrascritto.
     */
    suspend fun exportForSharing(): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, EXPORT_DIR).apply {
            deleteRecursively()
            mkdirs()
        }
        val file = File(dir, "remindella-backup.zip")
        file.outputStream().buffered().use { writeBackup(it) }
        FileProvider.getUriForFile(context, fileProviderAuthority(), file)
    }

    private val reserveDir get() = File(context.filesDir, RESERVE_DIR).apply { mkdirs() }

    /** Copia di riserva dell'ultimo backup, sul telefono: serve se il file principale si rovina. */
    val reserveFile: File get() = File(reserveDir, "remindella-backup-riserva.zip")

    /** Quando è stata fatta la copia di riserva (millisecondi), null se non c'è. */
    fun reserveSavedAt(): Long? = reserveFile.takeIf { it.exists() }?.lastModified()

    /**
     * Scrive il backup nel documento scelto dall'utente, sovrascrivendone il contenuto.
     * Prima lo prepara sul telefono e controlla che si rilegga bene; poi lo copia nel file scelto
     * e ricontrolla anche quello. Il backup preparato diventa la nuova copia di riserva.
     */
    suspend fun exportTo(destination: Uri) = withContext(Dispatchers.IO) {
        val fresh = File(reserveDir, "in-preparazione.zip")
        fresh.outputStream().buffered().use { writeBackup(it) }
        if (!isReadable { fresh.inputStream() }) {
            fresh.delete()
            throw IOException(tr("Backup non valido"))
        }
        val output = runCatching { resolver.openOutputStream(destination, "wt") }.getOrNull()
            ?: resolver.openOutputStream(destination, "w")
            ?: throw IOException(tr("Destinazione non disponibile"))
        output.buffered().use { out -> fresh.inputStream().use { it.copyTo(out) } }
        // La copia di riserva si aggiorna solo con un backup sano: quella vecchia resta finché la nuova non è pronta.
        fresh.copyTo(reserveFile, overwrite = true)
        fresh.delete()
        if (!isReadable { resolver.openInputStream(destination) ?: throw IOException() }) throw IOException(tr("Il file di backup scritto non si rilegge"))
    }

    /** Il backup si rilegge per intero (manifest, dati e impronta)? */
    private fun isReadable(open: () -> java.io.InputStream): Boolean = runCatching {
        open().use { input -> codec.read(input) { _, content -> val buffer = ByteArray(8192); while (content.read(buffer) >= 0) Unit } } is BackupReadResult.Valid
    }.getOrDefault(false)

    /** Legge la copia di riserva per ripristinarla (stesso controllo e conferma di un backup scelto). */
    suspend fun readReserve(): Pair<BackupReadResult, PendingRestore?> = read(Uri.fromFile(reserveFile))

    /**
     * File per il backup automatico quando l'utente non ne ha scelto uno: Download/Remindella
     * (resta anche se l'app viene disinstallata, nessun permesso). Su Android 9 va nella cartella dell'app.
     */
    fun createAutoTarget(): Uri? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "remindella-backup.zip")
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/Remindella")
            }
            resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } else {
            Uri.fromFile(File(context.getExternalFilesDir(null), "remindella-backup.zip"))
        }
    }.getOrNull()

    /** Conserva l'accesso al file del backup anche dopo il riavvio, per poterlo sovrascrivere. */
    fun keepAccess(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    /**
     * Per pesare il meno possibile: dati JSON senza valori di default, zip alla massima
     * compressione e immagini ridotte e convertite in WebP (se così diventano più leggere).
     */
    private suspend fun writeBackup(output: OutputStream) {
        val staging = File(context.cacheDir, "backup-staging").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val database = backupDao.readAll()
            var index = 0
            val compacted = database.fileUris().associateWith { uri -> runCatching { compact(uri, File(staging, "${index++}")) }.getOrNull() }
            val webp = compacted.filterValues { it?.second == true }.keys
            val content = database.copy(
                attachments = database.attachments.map { if (it.uri in webp) it.copy(mimeType = "image/webp") else it },
            )
            val files = compacted.mapNotNull { (uri, result) -> result?.let { BackupArchiveCodec.FileSource(uri) { it.first.inputStream() } } }
            codec.write(output, content, settingsRepository.current(), files, BuildConfig.VERSION_NAME, Instant.now(clock).toString())
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Copia il file in [target], ricompresso se è un'immagine; restituisce il file e se è diventato WebP. */
    private fun compact(uri: String, target: File): Pair<File, Boolean>? {
        val original = resolver.openInputStream(uri.toUri())?.use { it.readBytes() } ?: return null
        val webp = MediaStorage.compress(original, MAX_IMAGE_SIDE, IMAGE_QUALITY)?.takeIf { it.size < original.size }
        target.writeBytes(webp ?: original)
        return target to (webp != null)
    }

    /** Legge e valida il backup; i file allegati vengono estratti in un'area temporanea. */
    suspend fun read(source: Uri): Pair<BackupReadResult, PendingRestore?> = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore-staging").apply {
            deleteRecursively()
            mkdirs()
        }
        val input = resolver.openInputStream(source) ?: throw IOException(tr("File non disponibile"))
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
        // L'accettazione dei termini è di chi usa questo telefono: il backup (magari di prima) non la tocca.
        contents.settings?.let { restored ->
            settingsRepository.update { current ->
                restored.copy(termsAcceptedVersion = current.termsAcceptedVersion, termsAcceptedEpochDay = current.termsAcceptedEpochDay)
            }
        }
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
        const val EXPORT_DIR = "exports"
        const val RESERVE_DIR = "backup-reserve"
        const val MAX_IMAGE_SIDE = 1280
        const val IMAGE_QUALITY = 70
    }
}
