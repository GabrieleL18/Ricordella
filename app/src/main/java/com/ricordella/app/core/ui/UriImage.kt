package com.ricordella.app.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Mostra un'immagine locale (content URI) ridimensionata, decodificata fuori dal main thread.
 * Se l'immagine non è leggibile mostra [fallback].
 */
@Composable
fun UriImage(
    uri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxSizePx: Int = 512,
    fallback: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { loadThumbnail(context, Uri.parse(uri), maxSizePx) }
    }
    val image = bitmap
    if (image == null) {
        fallback()
    } else {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

private fun loadThumbnail(context: Context, uri: Uri, maxSizePx: Int): Bitmap? = try {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val size = info.size
        val scale = maxOf(1f, maxOf(size.width, size.height) / maxSizePx.toFloat())
        decoder.setTargetSize((size.width / scale).toInt().coerceAtLeast(1), (size.height / scale).toInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
} catch (_: IOException) {
    null
} catch (_: SecurityException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

