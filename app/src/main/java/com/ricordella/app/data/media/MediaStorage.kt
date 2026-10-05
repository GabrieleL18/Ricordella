package com.ricordella.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Foto e immagini allegate occupano il meno possibile: se ne salva una copia ridotta
 * (lato massimo [MAX_SIDE] px) in WebP, di solito 20-40 volte più leggera dello scatto originale.
 * I documenti (PDF, Word...) non vengono copiati: si conserva solo il riferimento, 0 byte in più.
 */
object MediaStorage {
    private const val DIR = "media"
    private const val MAX_SIDE = 1600
    private const val QUALITY = 75

    /** Copia compressa dell'immagine [source]; restituisce il nuovo URI, o null se non è un'immagine leggibile. */
    fun storeCompressedImage(context: Context, source: Uri): String? = runCatching {
        val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() } ?: return null
        val compact = compress(bytes, MAX_SIDE, QUALITY) ?: return null
        val file = File(File(context.filesDir, DIR).apply { mkdirs() }, "${UUID.randomUUID()}.webp")
        file.writeBytes(if (compact.size < bytes.size) compact else bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
    }.getOrNull()

    /** Nuovo file vuoto nella cartella dei media (per audio e disegni) e il suo URI. */
    fun newFile(context: Context, name: String): Pair<File, String> {
        val file = File(File(context.filesDir, DIR).apply { mkdirs() }, name)
        return file to FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
    }

    /** Elimina un file dell'app (media o ripristinato) dato il suo URI; gli altri URI non si toccano. */
    fun delete(context: Context, uri: String) {
        val u = Uri.parse(uri)
        if (u.authority != "${context.packageName}.files") return
        val name = u.lastPathSegment ?: return
        listOf(DIR, "restored").forEach { File(File(context.filesDir, it), name).takeIf(File::isFile)?.delete() }
    }

    /**
     * Ridimensiona e ricomprime un'immagine in WebP. ImageDecoder applica l'orientamento EXIF,
     * che altrimenti andrebbe perso. Restituisce null se i byte non sono un'immagine.
     */
    fun compress(bytes: ByteArray, maxSide: Int, quality: Int): ByteArray? = runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val side = maxOf(info.size.width, info.size.height)
            if (side > maxSide) {
                val scale = maxSide.toFloat() / side
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        ByteArrayOutputStream().use { out ->
            bitmap.compress(format, quality, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }.getOrNull()
}
