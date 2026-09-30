package com.ricordella.app.feature.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.ricordella.app.core.navigation.ViewerRoute
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.openFile
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.data.documents.DocParagraph
import com.ricordella.app.data.documents.DocxReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private enum class DocKind { IMAGE, PDF, DOCX, OTHER }

private fun kindOf(context: Context, route: ViewerRoute): DocKind {
    val mime = route.mimeType ?: runCatching { context.contentResolver.getType(route.uri.toUri()) }.getOrNull()
    val name = route.name.lowercase()
    return when {
        mime?.startsWith("image/") == true -> DocKind.IMAGE
        mime == "application/pdf" || name.endsWith(".pdf") -> DocKind.PDF
        mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || name.endsWith(".docx") -> DocKind.DOCX
        else -> DocKind.OTHER
    }
}

/** Visualizzatore interno: foto con zoom, PDF pagina per pagina, documenti Word come testo. */
@Composable
fun DocumentViewerScreen(route: ViewerRoute, onBack: () -> Unit) {
    val context = LocalContext.current
    val kind = remember(route) { kindOf(context, route) }
    DetailScaffold(
        title = route.name,
        onBack = onBack,
        actions = {
            IconButton(onClick = { openFile(context, route.uri, route.mimeType) }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Apri con un'altra app")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (kind) {
                DocKind.IMAGE -> ImageViewer(route.uri.toUri())
                DocKind.PDF -> PdfViewer(route.uri.toUri())
                DocKind.DOCX -> DocxViewer(route.uri.toUri())
                DocKind.OTHER -> Unsupported()
            }
        }
    }
}

/** Pizzica per ingrandire, trascina per spostarti, doppio tocco per tornare alla dimensione normale. */
@Composable
private fun Modifier.zoomable(): Modifier {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    return this
        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) }
        // Lo spostamento con un dito vale solo da ingranditi: altrimenti si scorre normalmente.
        .transformable(state, canPan = { scale > 1f })
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationX = offset.x
            translationY = offset.y
        }
}

@Composable
private fun ImageViewer(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val side = maxOf(info.size.width, info.size.height)
                    if (side > 2400) decoder.setTargetSampleSize(side / 2400 + 1)
                }
            }.getOrNull()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image == null) CircularProgressIndicator()
        else Image(image.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().zoomable())
    }
}

/** PDF reso con PdfRenderer di Android, una pagina alla volta man mano che si scorre. */
@Composable
private fun PdfViewer(uri: Uri) {
    val context = LocalContext.current
    val renderer = remember(uri) {
        runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.let(::PdfRenderer) }.getOrNull()
    }
    DisposableEffect(renderer) { onDispose { runCatching { renderer?.close() } } }
    if (renderer == null) {
        Unsupported("Non riesco ad aprire questo PDF.")
        return
    }
    val mutex = remember { Mutex() }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        LazyColumn(
            Modifier.fillMaxSize().zoomable(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(renderer.pageCount) { index ->
                val page by produceState<Bitmap?>(null, index) {
                    value = withContext(Dispatchers.IO) {
                        mutex.withLock {
                            renderer.openPage(index).use { pdfPage ->
                                val height = (widthPx.toFloat() * pdfPage.height / pdfPage.width).toInt().coerceAtLeast(1)
                                Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                                    bitmap.eraseColor(AndroidColor.WHITE)
                                    pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }
                    }
                }
                val bitmap = page
                if (bitmap == null) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color.White), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    Image(bitmap.asImageBitmap(), contentDescription = "Pagina ${index + 1}", modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DocxViewer(uri: Uri) {
    val context = LocalContext.current
    val paragraphs by produceState<List<DocParagraph>?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use(DocxReader::read) }.getOrNull() ?: emptyList()
        }
    }
    val list = paragraphs
    when {
        list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        list.isEmpty() -> Unsupported("Il documento è vuoto o non si riesce a leggere.")
        else -> SelectionContainer {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(RicordellaDimensions.screenPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list) { paragraph ->
                    val text = buildAnnotatedString {
                        if (paragraph.bullet) append("•  ")
                        paragraph.runs.forEach { run ->
                            withStyle(SpanStyle(fontWeight = if (run.bold) FontWeight.Bold else null, fontStyle = if (run.italic) FontStyle.Italic else null)) {
                                append(run.text)
                            }
                        }
                    }
                    val style = when (paragraph.headingLevel) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        in 3..9 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.bodyLarge
                    }
                    Text(text, style = style, modifier = if (paragraph.bullet) Modifier.padding(start = 12.dp) else Modifier)
                }
            }
        }
    }
}

@Composable
private fun Unsupported(message: String = "Questo tipo di file non si può vedere nell'app.") {
    Column(
        Modifier.fillMaxSize().padding(RicordellaDimensions.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.padding(bottom = 12.dp))
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Text(
            "Usa il pulsante in alto a destra per aprirlo con un'altra app.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
