package com.ricordella.app.data.backup

import com.ricordella.app.domain.model.AppSettings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Scrive e legge l'archivio di backup:
 *
 * ```
 * backup/manifest.json
 * backup/database.json
 * backup/settings.json
 * backup/attachments/...
 * ```
 *
 * Non dipende da Android: è testabile con semplici stream.
 */
class BackupArchiveCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /** Un file da includere nel backup; [open] restituisce null se il file non è più leggibile. */
    class FileSource(val originalUri: String, val open: () -> InputStream?)

    fun write(
        output: OutputStream,
        database: BackupDatabaseContent,
        settings: AppSettings,
        files: List<FileSource>,
        appVersion: String,
        createdAt: String,
    ) {
        val databaseBytes = json.encodeToString(BackupDatabaseContent.serializer(), database).toByteArray()
        ZipOutputStream(output).use { zip ->
            zip.putBytes(DATABASE_PATH, databaseBytes)
            zip.putBytes(SETTINGS_PATH, json.encodeToString(AppSettings.serializer(), settings).toByteArray())

            val entries = mutableListOf<BackupFileEntry>()
            files.forEachIndexed { index, file ->
                val input = runCatching { file.open() }.getOrNull() ?: return@forEachIndexed
                val path = "$ATTACHMENTS_DIR$index"
                input.use {
                    zip.putNextEntry(ZipEntry(path))
                    it.copyTo(zip)
                    zip.closeEntry()
                }
                entries += BackupFileEntry(path, file.originalUri)
            }

            val manifest = BackupManifest(
                formatVersion = BACKUP_FORMAT_VERSION,
                appVersion = appVersion,
                createdAt = createdAt,
                databaseSha256 = sha256(databaseBytes),
                files = entries,
            )
            zip.putBytes(MANIFEST_PATH, json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
        }
    }

    /**
     * Legge e valida un backup. I file allegati vengono passati a [storeFile]
     * (percorso nello zip, contenuto) man mano che vengono letti.
     */
    fun read(input: InputStream, storeFile: (path: String, content: InputStream) -> Unit): BackupReadResult {
        var manifestBytes: ByteArray? = null
        var databaseBytes: ByteArray? = null
        var settingsBytes: ByteArray? = null
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.isDirectory -> Unit
                        entry.name == MANIFEST_PATH -> manifestBytes = zip.readBytes()
                        entry.name == DATABASE_PATH -> databaseBytes = zip.readBytes()
                        entry.name == SETTINGS_PATH -> settingsBytes = zip.readBytes()
                        entry.name.startsWith(ATTACHMENTS_DIR) && !entry.name.contains("..") ->
                            storeFile(entry.name, zip)
                    }
                    zip.closeEntry()
                }
            }
        } catch (_: ZipException) {
            return if (manifestBytes == null) BackupReadResult.NotABackup else BackupReadResult.Corrupted("archivio danneggiato")
        } catch (_: IOException) {
            return BackupReadResult.Corrupted("lettura del file non riuscita")
        }

        val manifestData = manifestBytes ?: return BackupReadResult.NotABackup
        val manifest = decode(manifestData, BackupManifest.serializer())
            ?: return BackupReadResult.Corrupted("manifest non leggibile")
        if (manifest.formatVersion > BACKUP_FORMAT_VERSION || manifest.formatVersion < 1) {
            return BackupReadResult.IncompatibleVersion(manifest.formatVersion)
        }
        val databaseData = databaseBytes ?: return BackupReadResult.Corrupted("dati mancanti")
        if (sha256(databaseData) != manifest.databaseSha256) {
            return BackupReadResult.Corrupted("i dati non corrispondono alla firma del backup")
        }
        val database = decode(databaseData, BackupDatabaseContent.serializer())
            ?: return BackupReadResult.Corrupted("dati non leggibili")
        database.findIntegrityProblem()?.let { return BackupReadResult.Corrupted(it) }
        val settings = settingsBytes?.let { decode(it, AppSettings.serializer()) }
        return BackupReadResult.Valid(BackupContents(manifest, database, settings))
    }

    private fun <T> decode(bytes: ByteArray, serializer: kotlinx.serialization.KSerializer<T>): T? = try {
        json.decodeFromString(serializer, bytes.decodeToString())
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: java.time.DateTimeException) {
        null
    }

    private fun ZipOutputStream.putBytes(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(bytes)
        closeEntry()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val MANIFEST_PATH = "backup/manifest.json"
        const val DATABASE_PATH = "backup/database.json"
        const val SETTINGS_PATH = "backup/settings.json"
        const val ATTACHMENTS_DIR = "backup/attachments/"
    }
}
