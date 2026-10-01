package com.ricordella.app.feature.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.ricordella.app.core.i18n.Lang
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.ui.CrystalBallMascot
import com.ricordella.app.core.ui.HappyWizard
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.UiSound
import com.ricordella.app.core.ui.WizardScene
import com.ricordella.app.core.ui.rememberReducedMotion
import com.ricordella.app.core.ui.rememberUiSounds
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Il microfono di "Scrivi al volo". La prima volta spiega a cosa serve e chiede il consenso
 * (poi il permesso di Android); poi apre la sfera che ascolta e passa la frase capita a [onText].
 * Il riconoscimento lo fa il servizio vocale del telefono: Remindella non salva l'audio.
 */
@Composable
fun VoiceButton(onText: (String) -> Unit) {
    val context = LocalContext.current
    var asking by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listening = true
        else Toast.makeText(context, tr("Senza il microfono puoi sempre scrivere. Puoi attivarlo nelle impostazioni di Android."), Toast.LENGTH_LONG).show()
    }
    IconButton(onClick = {
        when {
            !SpeechRecognizer.isRecognitionAvailable(context) ->
                Toast.makeText(context, tr("Il riconoscimento vocale non è disponibile su questo telefono"), Toast.LENGTH_LONG).show()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> listening = true
            else -> asking = true
        }
    }) {
        Icon(Icons.Rounded.Mic, contentDescription = tr("Dillo a voce"), tint = MaterialTheme.colorScheme.primary)
    }
    if (asking) {
        MicConsent(
            onAllow = { asking = false; permission.launch(Manifest.permission.RECORD_AUDIO) },
            onDismiss = { asking = false },
        )
    }
    if (listening) {
        VoiceListening(onResult = { onText(it.take(200)); listening = false }, onDismiss = { listening = false })
    }
}

/** Prima del permesso di Android: cosa ascolto, quando e cosa ne faccio. */
@Composable
private fun MicConsent(onAllow: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.widthIn(max = 520.dp)) {
            Column(
                Modifier.padding(RicordellaDimensions.spaceXl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
            ) {
                HappyWizard(size = 96.dp, scene = WizardScene.PHONE_CALL)
                Text(tr("Posso ascoltarti?"), style = MaterialTheme.typography.headlineSmall)
                Text(
                    tr("Uso il microfono solo mentre la sfera è aperta, per capire cosa vuoi ricordare. La voce la trascrive il riconoscimento vocale del telefono: Remindella non registra né salva l'audio."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(tr("Non ora")) }
                    PushButton(tr("Consenti"), onClick = onAllow)
                }
            }
        }
    }
}

private val NightTop = Color(0xFF2A1450)
private val NightBottom = Color(0xFF1F4E8C)

/**
 * La sfera che ascolta: cielo notturno, anelli di magia che si allargano con la voce,
 * stelline che girano e la frase che compare mentre parli.
 */
@Composable
private fun VoiceListening(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sounds = rememberUiSounds()
    var attempt by remember { mutableIntStateOf(0) }
    var heard by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var rms by remember { mutableFloatStateOf(0f) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    // Prima si prova offline; se manca il pacchetto della lingua si riprova col servizio normale.
    var offline by remember { mutableStateOf(true) }
    // La prima volta (permesso appena dato, servizio vocale ancora freddo) il riconoscitore può
    // fallire per un attimo: si riprova una volta di nascosto prima di mostrare l'errore.
    var retried by remember { mutableStateOf(false) }

    // Modalità demo (screenshot): solo l'animazione con una frase d'esempio, senza microfono.
    val demo = remember { com.ricordella.app.core.DemoMode.isOn(context) }
    if (demo) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val words = tr("cena con Luca sabato alle 20").split(' ')
            words.indices.forEach { i ->
                kotlinx.coroutines.delay(350)
                heard = words.take(i + 1).joinToString(" ")
                rms = 0.4f + 0.5f * kotlin.random.Random.nextFloat()
            }
        }
    }
    DisposableEffect(attempt) {
        error = null
        heard = ""
        if (demo) return@DisposableEffect onDispose { }
        val speech = SpeechRecognizer.createSpeechRecognizer(context)
        // Gli eventi di un riconoscitore già chiuso (o dopo il primo errore) non contano più.
        var active = true
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = sounds(UiSound.POP)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) { rms = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { rms = 0f }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { heard = it }
            }
            override fun onResults(results: Bundle?) {
                if (!active) return
                active = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() } ?: heard
                if (text.isBlank()) error = tr("Non ho sentito niente.") else onResult(text)
            }
            override fun onError(code: Int) {
                if (!active) return
                active = false
                rms = 0f
                // 12 e 13: lingua non supportata / pacchetto offline mancante (costanti solo da Android 12).
                if (offline && (code == 12 || code == 13)) {
                    offline = false
                    attempt++
                    return
                }
                if (!retried && code in listOf(SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY)) {
                    retried = true
                    attempt++
                    return
                }
                error = when (code) {
                    12, 13 -> tr("Il riconoscimento vocale del telefono non ha questa lingua: scaricala nelle impostazioni della tastiera o di Google (Voce › Riconoscimento offline).")
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> tr("Non ho sentito niente.")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                        tr("Il riconoscimento vocale ha bisogno di internet su questo telefono.")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> tr("Manca il permesso del microfono.")
                    else -> tr("Non sono riuscita ad ascoltare.")
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        // Un attimo di respiro: subito dopo il permesso la finestra non ha ancora il microfono libero.
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val start = Runnable {
            speech.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLanguageTag())
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offline),
            )
        }
        handler.postDelayed(start, 350)
        recognizer = speech
        onDispose {
            handler.removeCallbacks(start)
            active = false
            speech.destroy()
            recognizer = null
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .padding(RicordellaDimensions.spaceL)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(NightTop, NightBottom)), RoundedCornerShape(32.dp))
                .padding(RicordellaDimensions.spaceXl),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceL)) {
                Text(
                    if (error == null) tr("Ti ascolto…") else tr("Ops!"),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                ListeningOrb(level = if (error == null) rms else 0f, active = error == null)
                AnimatedContent(
                    targetState = error ?: heard,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "heard",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                ) { text ->
                    Text(
                        text.ifBlank { tr("Dimmi cosa ricordare, ad esempio «dentista domani alle 9»") },
                        style = if (text.isBlank() || error != null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineSmall,
                        color = if (text.isBlank()) Color.White.copy(alpha = 0.7f) else Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(tr("Annulla"), color = Color.White) }
                    if (error != null) PushButton(tr("Riprova"), onClick = { attempt++ })
                    else PushButton(tr("Fatto"), onClick = { recognizer?.stopListening() })
                }
            }
        }
    }
}

/** La sfera di Remindella con gli anelli che pulsano al ritmo della voce e le stelline in orbita. */
@Composable
private fun ListeningOrb(level: Float, active: Boolean) {
    val colors = MaterialTheme.ricordellaColors
    val reduced = rememberReducedMotion()
    val voice by animateFloatAsState(level, tween(120), label = "voice")
    val loop = rememberInfiniteTransition(label = "orb")
    val wave = if (active && !reduced) loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "wave").value else 0f
    val orbit = if (active && !reduced) loop.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "orbit").value else 0f
    val glow = colors.pear.solid
    val ring = colors.cyan.solid
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val base = size.minDimension * 0.26f
            // Alone dietro la sfera: più forte quando parli.
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.25f + 0.35f * voice), Color.Transparent), c, base * (1.8f + voice)), base * (1.8f + voice), c)
            // Tre anelli che si allargano e svaniscono, spinti dalla voce.
            if (active) repeat(3) { i ->
                val t = (wave + i / 3f) % 1f
                val r = base * (1.05f + t * (0.7f + 0.9f * voice))
                drawCircle(ring.copy(alpha = (1f - t) * (0.35f + 0.5f * voice)), r, c, style = Stroke(width = 3.dp.toPx() * (1f - t) + 1f))
            }
            // Stelline in orbita.
            repeat(6) { i ->
                val a = 2 * PI * (orbit + i / 6f)
                val r = base * (1.55f + 0.12f * sin(a * 3).toFloat())
                val p = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                drawCircle(Color.White.copy(alpha = 0.5f + 0.5f * voice), (2.dp.toPx() + 2.dp.toPx() * voice) * (if (i % 2 == 0) 1.4f else 1f), p)
            }
        }
        CrystalBallMascot(size = 112.dp, idle = active, interactive = false)
    }
}

/**
 * Lingua per il riconoscimento vocale, con la regione (it-IT, de-DE…): quella del telefono se è la
 * stessa lingua dell'app (così un tedesco svizzero resta de-CH), altrimenti la più comune.
 */
private fun speechLanguageTag(): String {
    val system = android.content.res.Resources.getSystem().configuration.locales[0]
    if (system != null && system.language == Lang.code && system.country.isNotEmpty()) return system.toLanguageTag()
    return when (Lang.code) {
        "en" -> "en-US"
        "de" -> "de-DE"
        "fr" -> "fr-FR"
        "es" -> "es-ES"
        else -> "it-IT"
    }
}

