package com.ricordella.app.feature.notes

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.ui.UriImage
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.data.media.MediaStorage
import com.ricordella.app.domain.model.Note
import com.ricordella.app.domain.model.NoteAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/*
 * Allegati delle note: immagini dalla galleria, registrazioni audio e la tela del disegno.
 * I file stanno nella cartella dei media dell'app (come le foto) e finiscono nel backup.
 */

/** Immagini e audio di una nota, con i pulsanti per aggiungerne. [edit] applica una modifica alla nota. */
@Composable
fun NoteMediaSection(note: Note, tone: Tone, edit: ((Note) -> Note) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val stored = withContext(Dispatchers.IO) { uris.mapNotNull { MediaStorage.storeCompressedImage(context, it) } }
            if (stored.isNotEmpty()) edit { it.copy(images = it.images + stored) }
        }
    }
    val player = remember { NotePlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Column(Modifier.padding(horizontal = RicordellaDimensions.spaceS), verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
        if (note.images.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                note.images.forEach { uri ->
                    Box {
                        UriImage(
                            uri = uri,
                            contentDescription = tr("Immagine"),
                            maxSizePx = 600,
                            modifier = Modifier.size(112.dp).clip(RoundedCornerShape(16.dp)).clickable { openImage(context, uri) },
                        )
                        RemoveBadge(tr("Togli l'immagine"), Modifier.align(Alignment.TopEnd)) {
                            edit { it.copy(images = it.images - uri) }
                            MediaStorage.delete(context, uri)
                        }
                    }
                }
            }
        }
        note.audios.forEach { audio ->
            AudioRow(audio, tone, player) {
                if (player.playing == audio.id) player.stop()
                edit { it.copy(audios = it.audios.filterNot { a -> a.id == audio.id }) }
                MediaStorage.delete(context, audio.uri)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS), verticalAlignment = Alignment.CenterVertically) {
            MediaButton(Icons.Rounded.AddPhotoAlternate, tr("Immagine"), tone) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            RecorderButton(tone) { audio -> edit { it.copy(audios = it.audios + audio) } }
        }
    }
}

@Composable
private fun RemoveBadge(description: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.padding(4.dp).size(24.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Close, contentDescription = description, tint = Color.White, modifier = Modifier.size(16.dp)) }
}

@Composable
private fun MediaButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tone: Tone, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(tone.solid).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.surfaceContainerLowest)
    }
}

private fun openImage(context: Context, uri: String) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, tr("Nessuna app per aprire l'immagine."), Toast.LENGTH_SHORT).show()
    }
}

// ---------------------------------------------------------------------------------------------
// Audio

private fun formatDuration(ms: Long): String = (ms / 1000).let { "%d:%02d".format(it / 60, it % 60) }

/** Riproduce una registrazione alla volta. */
private class NotePlayer {
    var playing by mutableStateOf<String?>(null)
        private set
    private var player: MediaPlayer? = null

    fun toggle(context: Context, audio: NoteAudio) {
        if (playing == audio.id) return stop()
        stop()
        val p = MediaPlayer()
        try {
            p.setDataSource(context, Uri.parse(audio.uri))
            p.setOnCompletionListener { stop() }
            p.prepare()
            p.start()
            player = p
            playing = audio.id
        } catch (_: Exception) {
            p.release()
            Toast.makeText(context, tr("Impossibile riprodurre l'audio."), Toast.LENGTH_SHORT).show()
        }
    }

    fun stop() {
        runCatching { player?.release() }
        player = null
        playing = null
    }

    fun release() = stop()
}

@Composable
private fun AudioRow(audio: NoteAudio, tone: Tone, player: NotePlayer, onRemove: () -> Unit) {
    val context = LocalContext.current
    val active = player.playing == audio.id
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(tone.solid.copy(alpha = 0.16f)).padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { player.toggle(context, audio) }) {
            Icon(if (active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (active) tr("Pausa") else tr("Riproduci"), tint = tone.content)
        }
        Text(tr("Registrazione") + " · " + formatDuration(audio.durationMs), style = MaterialTheme.typography.bodyMedium, color = tone.content, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = tr("Elimina la registrazione"), tint = tone.content) }
    }
}

/** Registra dal microfono in un file m4a: un tocco avvia, un tocco ferma e la registrazione si aggiunge alla nota. */
@Composable
private fun RecorderButton(tone: Tone, onRecorded: (NoteAudio) -> Unit) {
    val context = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var target by remember { mutableStateOf<Pair<java.io.File, String>?>(null) }
    var elapsed by remember { mutableLongStateOf(0L) }

    fun finish(save: Boolean) {
        val r = recorder ?: return
        val file = target
        recorder = null
        target = null
        val ok = runCatching { r.stop() }.isSuccess // troppo breve: stop() fallisce e il file non vale
        runCatching { r.release() }
        if (file == null) return
        if (save && ok) onRecorded(NoteAudio(uri = file.second, durationMs = elapsed)) else file.first.delete()
    }

    fun start() {
        val (file, uri) = MediaStorage.newFile(context, "${UUID.randomUUID()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            target = file to uri
            elapsed = 0
        } catch (_: Exception) {
            runCatching { r.release() }
            file.delete()
            Toast.makeText(context, tr("Registrazione non riuscita."), Toast.LENGTH_SHORT).show()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else Toast.makeText(context, tr("Serve il permesso del microfono per registrare."), Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(recorder) {
        val began = System.currentTimeMillis()
        while (recorder != null) { elapsed = System.currentTimeMillis() - began; delay(250) }
    }
    // Uscendo dalla nota durante la registrazione, la registrazione viene scartata.
    DisposableEffect(Unit) { onDispose { finish(save = false) } }

    if (recorder == null) {
        MediaButton(Icons.Rounded.Mic, tr("Registra"), tone) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    } else {
        Row(
            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.error).clickable { finish(save = true) }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(20.dp))
            Text(tr("Ferma") + " · " + formatDuration(elapsed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onError)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Disegno

/** Un tratto: punti e spessore sono relativi alla larghezza della tela, così il disegno non dipende dallo schermo. */
private class InkStroke(val points: List<Offset>, val color: Color, val width: Float)

private val Pens = listOf(Color(0xFF1B1B1F), Color(0xFFD32F2F), Color(0xFF1976D2), Color(0xFF2E7D32), Color(0xFFF57C00), Color(0xFF7B1FA2))
private val Widths = listOf(0.004f, 0.01f, 0.022f)
private const val PAPER_W = 1200
private const val PAPER_H = 1600

private fun inkPath(points: List<Offset>, w: Float, h: Float): Path = Path().apply {
    val p = points.map { Offset(it.x * w, it.y * h) }
    moveTo(p[0].x, p[0].y)
    if (p.size == 1) lineTo(p[0].x + 0.1f, p[0].y) // un tocco: un punto
    else {
        for (i in 1 until p.size) quadraticTo(p[i - 1].x, p[i - 1].y, (p[i - 1].x + p[i].x) / 2, (p[i - 1].y + p[i].y) / 2)
        lineTo(p.last().x, p.last().y)
    }
}

/**
 * Tela per disegnare su carta bianca: penna in 6 colori e 3 spessori, gomma, annulla e cancella tutto.
 * Si salva da sola come PNG (un attimo dopo ogni tratto); [onSaved] riceve l'URI del file.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun DrawingPad(noteId: String, existing: String?, tone: Tone, header: (@Composable () -> Unit)? = null, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val strokes = remember { mutableStateListOf<InkStroke>() }
    val current = remember { mutableStateListOf<Offset>() }
    var base by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var pen by remember { mutableIntStateOf(0) }
    var width by remember { mutableIntStateOf(1) }
    var eraser by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }

    // Il disegno già salvato fa da sfondo: i nuovi tratti ci si aggiungono sopra.
    val initial = remember { existing }
    LaunchedEffect(Unit) {
        if (initial != null) {
            base = withContext(Dispatchers.IO) {
                runCatching { ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(initial))) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }.asImageBitmap() }.getOrNull()
            }
        }
    }
    // Salvataggio dopo una breve pausa dall'ultima modifica.
    LaunchedEffect(revision) {
        if (revision == 0) return@LaunchedEffect
        delay(300)
        val snapshot = strokes.toList()
        val background = base
        val uri = withContext(Dispatchers.Default) {
            val bitmap = Bitmap.createBitmap(PAPER_W, PAPER_H, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            background?.let { canvas.drawBitmap(it.asAndroidBitmap(), null, android.graphics.RectF(0f, 0f, PAPER_W.toFloat(), PAPER_H.toFloat()), null) }
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            snapshot.forEach {
                paint.color = it.color.toArgb()
                paint.strokeWidth = it.width * PAPER_W
                canvas.drawPath(inkPath(it.points, PAPER_W.toFloat(), PAPER_H.toFloat()).asAndroidPath(), paint)
            }
            val (file, fileUri) = MediaStorage.newFile(context, "drawing-$noteId.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            fileUri
        }
        onSaved(uri)
    }

    fun norm(o: Offset) = Offset((o.x / size.width).coerceIn(0f, 1f), (o.y / size.height).coerceIn(0f, 1f))
    fun commit() {
        if (current.isEmpty()) return
        strokes += InkStroke(current.toList(), if (eraser) Color.White else Pens[pen], Widths[width] * if (eraser) 3 else 1)
        current.clear()
        revision++
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // In orizzontale il foglio prende tutta l'altezza disponibile e gli strumenti stanno di fianco.
        val landscape = maxWidth > maxHeight && maxHeight != androidx.compose.ui.unit.Dp.Infinity
        val tools: @Composable () -> Unit = {
            Pens.forEachIndexed { i, color ->
                Box(
                    Modifier.size(32.dp).background(color, CircleShape)
                        .border(BorderStroke(if (!eraser && pen == i) 3.dp else 1.dp, if (!eraser && pen == i) tone.content else tone.content.copy(alpha = 0.3f)), CircleShape)
                        .clickable { pen = i; eraser = false },
                )
            }
            Widths.forEachIndexed { i, _ ->
                Box(Modifier.size(32.dp).clip(CircleShape).background(if (width == i) tone.solid.copy(alpha = 0.25f) else Color.Transparent).clickable { width = i }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size((6 + i * 6).dp).background(tone.content, CircleShape))
                }
            }
            IconButton(onClick = { eraser = !eraser }) {
                Icon(Icons.Rounded.AutoFixNormal, contentDescription = tr("Gomma"), tint = if (eraser) tone.solid else tone.content)
            }
            IconButton(onClick = { if (strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex); revision++ } }, enabled = strokes.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = tr("Annulla"), tint = tone.content.copy(alpha = if (strokes.isNotEmpty()) 1f else 0.3f))
            }
            IconButton(onClick = { strokes.clear(); base = null; revision++ }) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = tr("Cancella tutto"), tint = tone.content)
            }
        }
        val paper: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier.aspectRatio(PAPER_W.toFloat() / PAPER_H).clip(RoundedCornerShape(20.dp)).background(Color.White).onSizeChanged { size = it },
        ) {
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(eraser, pen, width) {
                        detectTapGestures { o -> current.clear(); current += norm(o); commit() }
                    }
                    .pointerInput(eraser, pen, width) {
                        detectDragGestures(
                            onDragStart = { o -> current.clear(); current += norm(o) },
                            onDrag = { change, _ -> current += norm(change.position); change.consume() },
                            onDragEnd = { commit() },
                            onDragCancel = { commit() },
                        )
                    },
            ) {
                base?.let { drawImage(it, dstSize = androidx.compose.ui.unit.IntSize(this.size.width.toInt(), this.size.height.toInt())) }
                fun ink(points: List<Offset>, color: Color, w: Float) = drawPath(
                    inkPath(points, this.size.width, this.size.height), color,
                    style = Stroke(width = w * this.size.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                strokes.forEach { ink(it.points, it.color, it.width) }
                if (current.isNotEmpty()) ink(current.toList(), if (eraser) Color.White else Pens[pen], Widths[width] * if (eraser) 3 else 1)
            }
        }
        }
        if (landscape) Row(Modifier.fillMaxWidth().height(maxHeight), horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS), verticalAlignment = Alignment.CenterVertically) {
            paper(Modifier.fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                header?.invoke()
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { tools() }
            }
        } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            paper(Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { tools() }
        }
    }
}
