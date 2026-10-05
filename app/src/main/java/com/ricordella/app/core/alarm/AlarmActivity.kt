package com.ricordella.app.core.alarm

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import com.ricordella.app.domain.model.AlarmStyle
import com.ricordella.app.core.ui.alarmStyleLabel
import androidx.compose.ui.draw.clip
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.ui.RicordellaMotion
import com.ricordella.app.core.ui.drawFourPointStar
import com.ricordella.app.core.ui.drawWizard
import com.ricordella.app.core.ui.rememberReducedMotion
import com.ricordella.app.core.ui.theme.RicordellaColors
import com.ricordella.app.core.ui.theme.RicordellaTheme
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.ThemeMode
import com.ricordella.app.feature.calendar.LiveTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Schermata della sveglia: appare anche sopra il blocco schermo e accende il display. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val ringing by AlarmRingService.ringing.collectAsStateWithLifecycle()
            // Il titolo resta anche dopo lo stop, durante l'animazione finale.
            var shown by remember { mutableStateOf(ringing) }
            if (ringing != null) shown = ringing
            if (shown == null) {
                LaunchedEffect(Unit) { finish() }
                return@setContent
            }
            val alarm = shown!!
            RicordellaTheme(themeMode = if (alarm.night || alarm.classic) ThemeMode.DARK else ThemeMode.LIGHT) {
                if (alarm.classic) ClassicAlarmScreen(
                    alarm = alarm,
                    ringing = ringing != null,
                    onStop = { AlarmRingService.stop(this) },
                    onSnooze = { AlarmRingService.snooze(this) },
                    onDone = ::finish,
                ) else AlarmScreen(
                    alarm = alarm,
                    ringing = ringing != null,
                    onStop = { AlarmRingService.stop(this) },
                    onSnooze = { AlarmRingService.snooze(this) },
                    onDone = ::finish,
                )
            }
        }
    }
}

private val DayInk = Color(0xFF1F2340)

/** L'arancio del titolo dell'app (la fine della sfumatura dorata di MagicTitle). */
private val BrandOrange = Color(0xFFFF8A00)

/**
 * Sveglia "classica": solo l'ora. Una sfumatura dell'arancio del titolo dell'app parte dal centro e svanisce nel nero
 * ai bordi; mentre suona si espande e si comprime, come un respiro.
 */
@Composable
private fun ClassicAlarmScreen(alarm: AlarmRingService.Ringing, ringing: Boolean, onStop: () -> Unit, onSnooze: () -> Unit, onDone: () -> Unit) {
    val reduced = rememberReducedMotion()
    val breath = if (reduced) 0.5f else rememberInfiniteTransition(label = "classic").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = RicordellaMotion.EaseInOut), RepeatMode.Reverse), label = "breath",
    ).value
    val sparkle = if (reduced) 0.5f else rememberInfiniteTransition(label = "classicSparkle").animateFloat(
        0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "sparkleT",
    ).value
    val fade = remember { Animatable(1f) }
    // Fermata o posticipata: il bagliore si spegne e la schermata si chiude.
    LaunchedEffect(ringing) {
        if (ringing) return@LaunchedEffect
        fade.animateTo(0f, tween(500))
        onDone()
    }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    val colors = MaterialTheme.ricordellaColors
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) {
            val radius = minOf(size.width, size.height) * (0.62f + 0.38f * breath) + size.height * 0.1f
            drawRect(
                Brush.radialGradient(
                    0f to BrandOrange.copy(alpha = 0.85f),
                    0.3f to BrandOrange.copy(alpha = 0.55f),
                    0.65f to BrandOrange.copy(alpha = 0.14f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
            )
            // Polvere magica: stelline a quattro punte che salgono piano e brillano ognuna col suo ritmo.
            val random = java.util.Random(5)
            repeat(32) {
                val x = random.nextFloat() * size.width
                val y0 = random.nextFloat()
                val phase = random.nextFloat()
                val big = random.nextFloat() < 0.3f
                val rise = (y0 - sparkle * (0.3f + phase * 0.4f) + 1f) % 1f
                val glow = 0.25f + 0.75f * (0.5f + 0.5f * sin((sparkle * 2f + phase) * 2 * PI).toFloat())
                val tint = if (it % 3 == 0) Color(0xFFFFF6B0) else Color.White
                drawFourPointStar(Offset(x, rise * size.height), size.width * (if (big) 0.022f else 0.012f), tint.copy(alpha = glow), rotation = sparkle * 90f)
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            val landscape = maxWidth > maxHeight
            // Come i fogli delle note: un riquadro arrotondato e scuro che tiene l'ora leggibile sopra il bagliore.
            val clock: @Composable (Modifier) -> Unit = { modifier ->
                Column(
                    modifier
                        .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.extraLarge)
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    LiveTime(now, MaterialTheme.typography.displayLarge.copy(fontSize = if (landscape) 72.sp else 88.sp, fontWeight = FontWeight.SemiBold), Color.White)
                    Text(
                        DateTexts.weekdayAndDay(now.toLocalDate()).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    if (alarm.title.isNotBlank()) Text(
                        alarm.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.bolt,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            val controls: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledTonalButton(onClick = onSnooze, enabled = ringing) {
                        Icon(Icons.Rounded.Snooze, contentDescription = null)
                        Text(trf("Posticipa %1\$s min", alarm.snoozeMinutes), modifier = Modifier.padding(start = 8.dp))
                    }
                    SwipeToStop(enabled = ringing, ink = Color.White, onStop = onStop)
                }
            }
            if (landscape) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                clock(Modifier.weight(1f))
                controls(Modifier.weight(1f))
            } else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { clock(Modifier) }
                controls(Modifier.fillMaxWidth().padding(top = 16.dp))
            }
        }
    }
}

@Composable
private fun AlarmScreen(alarm: AlarmRingService.Ringing, ringing: Boolean, onStop: () -> Unit, onSnooze: () -> Unit, onDone: () -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    val calm = remember { Animatable(0f) }
    val thrown = remember { Animatable(0f) }
    val night = remember { Animatable(0f) }
    LaunchedEffect(ringing) {
        if (ringing) return@LaunchedEffect
        when {
            !AlarmRingService.endedBySnooze -> {
                // Fermata: la sveglia si quieta ed esplode di stelle (di giorno l'orso continua a dormire).
                calm.animateTo(1f, tween(1600, easing = RicordellaMotion.EaseOut))
                delay(400)
            }
            alarm.night -> {
                // Posticipata di notte: il maghetto lancia via la sveglia e fa calare la notte.
                thrown.animateTo(1f, tween(900, easing = RicordellaMotion.EaseIn))
                night.animateTo(1f, tween(1400, easing = RicordellaMotion.EaseInOut))
                delay(1_200)
            }
            else -> {
                // Posticipata di giorno: l'orso si sveglia e lancia via sveglia e maghetto.
                thrown.animateTo(1f, tween(2000, easing = LinearEasing))
                delay(1_400)
            }
        }
        onDone()
    }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    val ink = if (alarm.night) Color.White else DayInk
    Box(Modifier.fillMaxSize()) {
        if (alarm.night) {
            NightSky()
        } else {
            DaySky()
        }
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LiveTime(now, MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold), ink, Modifier.padding(top = 16.dp))
            Text(
                DateTexts.weekdayAndDay(now.toLocalDate()).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                color = ink.copy(alpha = 0.75f),
            )
            Text(
                alarm.title,
                style = MaterialTheme.typography.headlineMedium,
                color = if (alarm.night) colors.bolt else colors.coral.content,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 20.dp),
            )
            val sceneModifier = Modifier.fillMaxWidth().weight(1f)
            if (alarm.night) NightScene(calm.value, thrown.value, sceneModifier) else DayScene(calm.value, thrown.value, sceneModifier)
            OutlinedButton(onClick = onSnooze, enabled = ringing, modifier = Modifier.padding(bottom = 16.dp)) {
                Icon(Icons.Rounded.Snooze, contentDescription = null, tint = ink)
                Text(trf("Posticipa %1\$s min", alarm.snoozeMinutes), color = ink, modifier = Modifier.padding(start = 8.dp))
            }
            SwipeToStop(enabled = ringing, ink = ink, onStop = onStop)
        }
        if (night.value > 0f) Nightfall(night.value, alarm.snoozeMinutes)
        // Di giorno, dopo il lancio dell'orso: "ok... ci riprovo".
        val retry = ((thrown.value - 0.75f) / 0.25f).coerceIn(0f, 1f)
        if (!alarm.night && retry > 0f) {
            Text(
                trf("Ok… ci riprovo tra %1\$s minuti", alarm.snoozeMinutes),
                style = MaterialTheme.typography.titleLarge,
                color = DayInk,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .graphicsLayer { alpha = retry; translationY = (1f - retry) * 40f }
                    .background(Color.White.copy(alpha = 0.85f), CircleShape)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
    }
}

/** Anteprima animata di uno stile di sveglia, nella stessa proporzione della schermata vera. */
@Composable
private fun AlarmStylePreview(style: AlarmStyle, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
        when (style) {
            AlarmStyle.CLASSIC -> {
                val reduced = rememberReducedMotion()
                val breath = if (reduced) 0.5f else rememberInfiniteTransition(label = "previewBreath").animateFloat(
                    0f, 1f, infiniteRepeatable(tween(1800, easing = RicordellaMotion.EaseInOut), RepeatMode.Reverse), label = "previewBreathT",
                ).value
                val sparkle = if (reduced) 0.5f else rememberInfiniteTransition(label = "previewSparkle").animateFloat(
                    0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "previewSparkleT",
                ).value
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                Canvas(Modifier.fillMaxSize()) {
                    val radius = minOf(size.width, size.height) * (0.62f + 0.38f * breath) + size.height * 0.1f
                    drawRect(
                        Brush.radialGradient(
                            0f to BrandOrange.copy(alpha = 0.85f), 0.3f to BrandOrange.copy(alpha = 0.55f), 0.65f to BrandOrange.copy(alpha = 0.14f), 1f to Color.Transparent,
                            center = center, radius = radius,
                        ),
                    )
                    val random = java.util.Random(5)
                    repeat(14) {
                        val x = random.nextFloat() * size.width
                        val y0 = random.nextFloat()
                        val phase = random.nextFloat()
                        val rise = (y0 - sparkle * (0.3f + phase * 0.4f) + 1f) % 1f
                        val glow = 0.25f + 0.75f * (0.5f + 0.5f * sin((sparkle * 2f + phase) * 2 * PI).toFloat())
                        drawFourPointStar(Offset(x, rise * size.height), size.width * 0.03f, (if (it % 3 == 0) Color(0xFFFFF6B0) else Color.White).copy(alpha = glow), rotation = sparkle * 90f)
                    }
                }
                Box(
                    Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    LiveTime(remember { LocalDateTime.now() }, MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), Color.White)
                }
            }
            AlarmStyle.MAGIC -> {
                val hour = LocalDateTime.now().hour
                val night = hour < AlarmRingService.DAY_STARTS || hour >= AlarmRingService.NIGHT_STARTS
                if (night) NightSky() else DaySky()
                if (night) NightScene(0f, 0f, Modifier.fillMaxSize()) else DayScene(0f, 0f, Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Scelta dello stile con le anteprime. Con [allowDefault] c'è anche "Come nelle Impostazioni": [selected] null
 * segue [defaultStyle]; toccando un'anteprima si sceglie quello stile.
 */
@Composable
fun AlarmStylePicker(
    selected: AlarmStyle?,
    defaultStyle: AlarmStyle,
    allowDefault: Boolean,
    onSelected: (AlarmStyle?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.ricordellaColors
    val effective = selected ?: defaultStyle
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Schermata della sveglia"), style = MaterialTheme.typography.bodyLarge)
        if (allowDefault) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelected(if (selected == null) defaultStyle else null) },
                label = { Text(trf("Come nelle Impostazioni (%1\$s)", alarmStyleLabel(defaultStyle))) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AlarmStyle.entries.forEach { style ->
                val chosen = effective == style
                Column(
                    Modifier.weight(1f).clickable { onSelected(style) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AlarmStylePreview(
                        style,
                        Modifier.fillMaxWidth().aspectRatio(0.62f)
                            .border(if (chosen) 3.dp else 1.dp, if (chosen) colors.bolt else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .graphicsLayer { alpha = if (selected == null && allowDefault) 0.6f else 1f },
                    )
                    Text(
                        alarmStyleLabel(style),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/** Cielo di giorno: azzurro che scalda verso l'orizzonte, sole con raggi che girano piano e nuvole che scorrono. */
@Composable
private fun DaySky() {
    val colors = MaterialTheme.ricordellaColors
    val reduced = rememberReducedMotion()
    val t = if (reduced) 0f else rememberInfiniteTransition(label = "sky").animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "skyT").value
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF7CC8FF), Color(0xFFBFE6FF), Color(0xFFFFF1C9))))
        val sun = Offset(w * 0.84f, h * 0.1f)
        rotate(t * 360f, sun) {
            repeat(12) { i ->
                val a = (2 * PI * i / 12).toFloat()
                drawLine(colors.bolt.copy(alpha = 0.8f), Offset(sun.x + cos(a) * w * 0.12f, sun.y + sin(a) * w * 0.12f), Offset(sun.x + cos(a) * w * 0.17f, sun.y + sin(a) * w * 0.17f), strokeWidth = w * 0.015f, cap = StrokeCap.Round)
            }
        }
        drawCircle(colors.bolt, radius = w * 0.09f, center = sun)
        listOf(0.18f to 0.2f, 0.55f to 0.33f, 0.9f to 0.45f).forEachIndexed { i, (x, y) ->
            val cx = ((x + t * (0.3f + i * 0.1f)) % 1.3f - 0.15f) * w
            val c = Offset(cx, h * y)
            val r = w * 0.05f
            listOf(-1.2f to 0.3f, 0f to 0f, 1.2f to 0.3f, 0.6f to 0.5f, -0.6f to 0.5f).forEach { (dx, dy) ->
                drawCircle(Color.White.copy(alpha = 0.9f), radius = r, center = Offset(c.x + dx * r, c.y + dy * r))
            }
        }
    }
}

/** Cielo di notte: blu profondo, luna calante che respira, stelle che brillano a turno e una stella cadente. */
@Composable
private fun NightSky() {
    val colors = MaterialTheme.ricordellaColors
    val reduced = rememberReducedMotion()
    val t = if (reduced) 0.5f else rememberInfiniteTransition(label = "nightSky").animateFloat(0f, 1f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "nightT").value
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val top = Color(0xFF0B0A2A)
        drawRect(Brush.verticalGradient(listOf(top, Color(0xFF231456), Color(0xFF3A1F6E), Color(0xFF16122E))))
        // Luna calante con alone che respira: la falce è un cerchio meno un cerchio spostato, così l'alone resta visibile.
        val moon = Offset(w * 0.85f, h * 0.1f)
        val r = w * 0.07f
        val glow = 0.22f + 0.08f * sin(t * 2 * PI).toFloat()
        drawCircle(Brush.radialGradient(listOf(colors.bolt.copy(alpha = glow), Color.Transparent), moon, r * 2.2f), radius = r * 2.2f, center = moon)
        val crescent = Path().apply {
            op(
                Path().apply { addOval(Rect(moon, r)) },
                Path().apply { addOval(Rect(Offset(moon.x - r * 0.45f, moon.y - r * 0.15f), r * 0.85f)) },
                PathOperation.Difference,
            )
        }
        drawPath(crescent, colors.bolt)
        // Stelle fisse che brillano ognuna con il suo ritmo; le più grandi a quattro punte.
        val random = java.util.Random(11)
        repeat(70) {
            val star = Offset(random.nextFloat() * w, random.nextFloat() * h * 0.85f)
            val phase = random.nextFloat()
            val big = random.nextFloat() < 0.18f
            val glow = 0.35f + 0.65f * (0.5f + 0.5f * sin(((t + phase) * 2 * PI)).toFloat())
            if (big) {
                drawFourPointStar(star, w * (0.012f + random.nextFloat() * 0.01f), Color.White.copy(alpha = glow), rotation = t * 90f)
            } else {
                drawCircle(Color.White.copy(alpha = glow * 0.8f), radius = w * (0.002f + random.nextFloat() * 0.003f), center = star)
            }
        }
        // Stella cadente: attraversa il cielo nella prima parte di ogni giro.
        val fall = (t / 0.25f).coerceIn(0f, 1f)
        if (!reduced && fall in 0.01f..0.99f) {
            val head = Offset(w * (0.1f + 0.5f * fall), h * (0.05f + 0.15f * fall))
            val tail = Offset(head.x - w * 0.14f, head.y - h * 0.042f)
            drawLine(
                Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.9f * sin(fall * PI).toFloat())), tail, head),
                tail, head, strokeWidth = w * 0.006f, cap = StrokeCap.Round,
            )
        }
        // Colline scure all'orizzonte, come l'erba della scena di giorno ma addormentata.
        drawOval(Color(0xFF1A1440), topLeft = Offset(-w * 0.3f, h * 0.84f), size = Size(w * 1.6f, h * 0.4f))
    }
}

/** La notte cala sulla schermata: cielo blu scuro, luna calante, stelle che si accendono e un "a dopo". */
@Composable
private fun Nightfall(progress: Float, snoozeMinutes: Int) {
    val colors = MaterialTheme.ricordellaColors
    val sky = Color(0xFF060A1F)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Il buio scende dall'alto come un sipario.
            drawRect(sky.copy(alpha = 0.97f), size = Size(w, h * minOf(1f, progress * 1.6f)))
            val light = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
            val moon = Offset(w * 0.72f, h * 0.22f)
            val r = w * 0.11f
            drawCircle(colors.bolt.copy(alpha = light), radius = r, center = moon)
            drawCircle(sky, radius = r * 0.9f, center = Offset(moon.x + r * 0.45f, moon.y - r * 0.2f), alpha = light)
            val random = java.util.Random(7)
            repeat(34) { i ->
                val star = Offset(random.nextFloat() * w, random.nextFloat() * h * 0.75f)
                val appear = ((light - i / 60f) * 2f).coerceIn(0f, 1f)
                drawFourPointStar(star, w * (0.006f + random.nextFloat() * 0.012f), Color.White.copy(alpha = appear))
            }
        }
        Text(
            trf("Va bene… ci vediamo tra %1\$s minuti", snoozeMinutes),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White.copy(alpha = ((progress - 0.5f) * 2f).coerceIn(0f, 1f)),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
        )
    }
}

/** Animazioni in loop comuni alle due scene. */
private class SceneLoop(val shake: Float, val wave: Float, val twinkle: Float, val breath: Float)

@Composable
private fun rememberSceneLoop(): SceneLoop {
    val reduced = rememberReducedMotion()
    val loop = rememberInfiniteTransition(label = "alarm")
    if (reduced) return SceneLoop(0f, 0f, 0.5f, 0.5f)
    return SceneLoop(
        shake = loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(70, easing = LinearEasing), RepeatMode.Reverse), label = "shake").value,
        wave = loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(600, easing = RicordellaMotion.EaseInOut), RepeatMode.Reverse), label = "wave").value,
        twinkle = loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "twinkle").value,
        breath = loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = RicordellaMotion.EaseInOut), RepeatMode.Reverse), label = "breath").value,
    )
}

/** Stelline che volano ad arco dalla bacchetta alla sveglia: l'incantesimo in corso. */
private fun DrawScope.drawSpell(colors: RicordellaColors, from: Offset, to: Offset, lift: Float, size: Float, twinkle: Float, alpha: Float) {
    repeat(5) { i ->
        val t = (twinkle + i / 5f) % 1f
        val x = from.x + (to.x - from.x) * t
        val y = from.y + (to.y - from.y) * t - sin(t * PI.toFloat()) * lift
        drawFourPointStar(Offset(x, y), size * (1f - t * 0.5f), listOf(colors.bolt, colors.cyan.solid, colors.coral.solid)[i % 3].copy(alpha = alpha), rotation = t * 180f)
    }
}

/** Anello di stelle che esplode dalla sveglia quando la si ferma. */
private fun DrawScope.drawStopBurst(colors: RicordellaColors, center: Offset, radius: Float, calm: Float) {
    if (calm <= 0f || calm >= 1f) return
    repeat(10) { i ->
        val angle = (2 * PI * i / 10).toFloat()
        val distance = radius * (0.8f + 1.4f * calm)
        drawFourPointStar(
            Offset(center.x + cos(angle) * distance, center.y + sin(angle) * distance),
            radius * 0.16f * (1f - calm),
            listOf(colors.bolt, colors.cyan.solid, colors.coral.solid, colors.mint.solid)[i % 4],
            rotation = calm * 180f,
        )
    }
}

/**
 * Notte: il maghetto incanta la sveglia che trema; [calm] da 0 a 1 la ferma con un'esplosione di stelle,
 * [thrown] da 0 a 1 la lancia fuori dallo schermo con un colpo di bacchetta.
 */
@Composable
private fun NightScene(calm: Float, thrown: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.ricordellaColors
    val loop = rememberSceneLoop()
    val time = LocalDateTime.now()
    Canvas(modifier.semantics { contentDescription = tr("Un maghetto incanta la sveglia con la bacchetta") }) {
        val w = size.width
        val h = size.height
        val side = minOf(w * 0.5f, h * 0.8f)
        val top = (h - side) / 2
        // Maghetto a sinistra: finita la sveglia, smette di agitare la bacchetta e annuisce.
        inset(left = w * 0.02f, top = top, right = w - side - w * 0.02f, bottom = h - top - side) {
            // Lancio: la bacchetta carica all'indietro e poi scatta in avanti.
            val swing = if (thrown > 0f) (thrown * 3f - 1f).coerceIn(-1f, 1.6f) else loop.wave * (1f - calm * 0.8f)
            drawWizard(colors, nod = loop.wave * calm, wave = swing, twinkle = loop.twinkle)
        }
        // La sveglia lanciata vola in alto a destra, girando su se stessa, fino a sparire.
        val clock = Offset(w * 0.74f + w * 0.8f * thrown, h * 0.5f - h * 1.5f * thrown + h * 0.4f * thrown * thrown)
        val radius = minOf(w * 0.18f, h * 0.26f)
        if (calm < 1f && thrown == 0f) {
            drawSpell(colors, Offset(side * 0.95f, top + side * 0.45f), Offset(clock.x - radius, clock.y), h * 0.12f, radius * 0.12f, loop.twinkle, 1f - calm)
        }
        rotate(thrown * 540f, clock) {
            drawAlarmClock(colors, clock, radius * (1f - 0.4f * thrown), if (thrown > 0f) 0f else loop.shake * (1f - calm), time)
        }
        drawStopBurst(colors, clock, radius, calm)
    }
}

/**
 * Giorno: il maghetto cerca di zittire la sveglia prima che svegli l'orso che dorme.
 * Fermata ([calm]): l'orso continua a dormire e il maghetto è contento. Posticipata ([thrown]):
 * l'orso si sveglia arrabbiato e con una zampata lancia fuori dallo schermo sveglia e maghetto.
 */
@Composable
private fun DayScene(calm: Float, thrown: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.ricordellaColors
    val loop = rememberSceneLoop()
    val measurer = rememberTextMeasurer()
    val time = LocalDateTime.now()
    val wake = (thrown / 0.35f).coerceIn(0f, 1f)
    val fly = RicordellaMotion.EaseIn.transform(((thrown - 0.35f) / 0.4f).coerceIn(0f, 1f))
    Canvas(modifier.semantics { contentDescription = tr("Un maghetto cerca di zittire la sveglia per non svegliare l'orso che dorme") }) {
        val w = size.width
        val h = size.height
        // Prato su cui dorme l'orso.
        drawOval(colors.mint.solid.copy(alpha = 0.55f), topLeft = Offset(-w * 0.2f, h * 0.78f), size = Size(w * 1.4f, h * 0.5f))

        val side = minOf(w * 0.42f, h * 0.62f)
        val top = h * 0.18f
        val wizardCenter = Offset(side / 2, top + side / 2)
        // Il maghetto vola via in alto a sinistra facendo le capriole.
        withTransform({
            translate(-w * 0.9f * fly, -h * 0.9f * fly)
            rotate(-540f * fly, wizardCenter)
        }) {
            inset(left = 0f, top = top, right = w - side, bottom = h - top - side) {
                val swing = if (thrown > 0f) 0.6f else loop.wave * (1f - calm * 0.8f)
                drawWizard(colors, nod = loop.wave * calm, wave = swing, twinkle = loop.twinkle)
            }
        }
        val radius = minOf(w * 0.12f, h * 0.17f)
        val clockRest = Offset(w * 0.52f, h * 0.36f)
        val clock = Offset(clockRest.x - w * 1.1f * fly, clockRest.y - h * 0.7f * fly)
        if (calm < 1f && thrown == 0f) {
            drawSpell(colors, Offset(side * 0.95f, top + side * 0.45f), Offset(clock.x - radius, clock.y), h * 0.08f, radius * 0.16f, loop.twinkle, 1f - calm)
        }
        rotate(-720f * fly, clock) {
            // Appena l'orso apre gli occhi la sveglia smette di tremare: è troppo spaventata.
            drawAlarmClock(colors, clock, radius, if (thrown > 0f) 0f else loop.shake * (1f - calm), time)
        }
        drawStopBurst(colors, clock, radius, calm)

        val bear = Offset(w * 0.76f, h * 0.8f)
        drawBear(bear, w * 0.4f, wake = wake, swipe = ((thrown - 0.35f) / 0.2f).coerceIn(0f, 1f) * (1f - fly * 0.3f), breath = loop.breath)
        // Zzz che salgono dal muso finché l'orso dorme (più grandi se la sveglia è stata fermata).
        if (wake == 0f) repeat(3) { i ->
            val t = (loop.twinkle + i / 3f) % 1f
            val letter = measurer.measure("z", TextStyle(fontSize = (14 + i * 6 + calm * 6).sp, fontWeight = FontWeight.Bold, color = DayInk.copy(alpha = 1f - t)))
            drawText(letter, topLeft = Offset(bear.x - w * 0.14f + t * w * 0.08f, bear.y - h * 0.12f - t * h * 0.22f))
        }
    }
}

private val Fur = Color(0xFF8B5A3C)
private val FurLight = Color(0xFFD9A77A)
private val FurDark = Color(0xFF5A3825)
private val BearInk = Color(0xFF2B2140)

/**
 * Orso sdraiato a pancia in giù con la testa verso la sveglia. [wake] da 0 a 1: alza la testa e apre
 * gli occhi arrabbiato; [swipe] da 0 a 1: zampata verso l'alto a sinistra; [breath] il respiro del sonno.
 */
private fun DrawScope.drawBear(center: Offset, width: Float, wake: Float, swipe: Float, breath: Float) {
    val bodyH = width * 0.42f * (1f + 0.04f * breath * (1f - wake))
    // Corpo e zampe posteriori.
    drawOval(Fur, topLeft = Offset(center.x - width * 0.45f, center.y - bodyH / 2), size = Size(width * 0.9f, bodyH))
    drawCircle(FurDark, radius = width * 0.07f, center = Offset(center.x + width * 0.42f, center.y + bodyH * 0.3f))
    drawCircle(Fur, radius = width * 0.09f, center = Offset(center.x + width * 0.33f, center.y + bodyH * 0.38f))

    val r = width * 0.2f
    val head = Offset(center.x - width * 0.42f, center.y - bodyH * 0.05f - wake * width * 0.25f)
    // Zampata: il braccio parte dalla spalla e ruota verso l'alto a sinistra.
    val shoulder = Offset(center.x - width * 0.28f, center.y)
    val armAngle = (200f - 110f * swipe) * PI.toFloat() / 180f
    val paw = Offset(shoulder.x + cos(armAngle) * width * 0.38f, shoulder.y - sin(armAngle) * width * 0.38f * (0.3f + swipe))
    drawLine(Fur, shoulder, paw, strokeWidth = width * 0.13f, cap = StrokeCap.Round)
    drawCircle(FurLight, radius = width * 0.07f, center = paw)

    // Orecchie, testa e muso.
    listOf(-1f, 1f).forEach { side ->
        drawCircle(Fur, radius = r * 0.36f, center = Offset(head.x + side * r * 0.72f, head.y - r * 0.78f))
        drawCircle(FurLight, radius = r * 0.18f, center = Offset(head.x + side * r * 0.72f, head.y - r * 0.78f))
    }
    drawCircle(Fur, radius = r, center = head)
    drawOval(FurLight, topLeft = Offset(head.x - r * 0.5f, head.y + r * 0.05f), size = Size(r, r * 0.7f))
    drawOval(BearInk, topLeft = Offset(head.x - r * 0.18f, head.y + r * 0.1f), size = Size(r * 0.36f, r * 0.22f))

    val eyeY = head.y - r * 0.25f
    listOf(-1f, 1f).forEach { side ->
        val eye = Offset(head.x + side * r * 0.42f, eyeY)
        if (wake < 0.5f) {
            // Occhi chiusi: archetti all'ingiù del sonno profondo.
            drawArc(BearInk, 20f, 140f, false, topLeft = Offset(eye.x - r * 0.18f, eye.y - r * 0.12f), size = Size(r * 0.36f, r * 0.24f), style = Stroke(width = r * 0.08f, cap = StrokeCap.Round))
        } else {
            // Sveglio e arrabbiato: occhi aperti e sopracciglia a V.
            drawCircle(Color.White, radius = r * 0.16f, center = eye)
            drawCircle(BearInk, radius = r * 0.08f, center = eye)
            drawLine(BearInk, Offset(eye.x - side * r * 0.22f, eye.y - r * 0.3f), Offset(eye.x + side * r * 0.12f, eye.y - r * 0.18f), strokeWidth = r * 0.08f, cap = StrokeCap.Round)
        }
    }
    if (wake >= 0.5f) {
        // Bocca spalancata in un ruggito.
        drawOval(BearInk, topLeft = Offset(head.x - r * 0.16f, head.y + r * 0.38f), size = Size(r * 0.32f, r * 0.26f * wake))
    }
}

/** Sveglia a campane: trema a destra e sinistra, il martelletto batte e le lancette segnano l'ora vera. */
private fun DrawScope.drawAlarmClock(colors: RicordellaColors, center: Offset, r: Float, shake: Float, time: LocalDateTime) {
    val pivot = Offset(center.x, center.y + r)
    rotate(shake * 9f, pivot) {
        // Zampette.
        listOf(-1f, 1f).forEach { side ->
            drawLine(colors.coral.solid, Offset(center.x + side * r * 0.55f, center.y + r * 0.7f), Offset(center.x + side * r * 0.8f, center.y + r * 1.15f), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
        }
        // Campane e martelletto che batte tra le due.
        listOf(-1f, 1f).forEach { side ->
            val bell = Offset(center.x + side * r * 0.72f, center.y - r * 0.82f)
            rotate(side * 35f, bell) { drawArc(colors.bolt, 180f, 180f, true, topLeft = Offset(bell.x - r * 0.38f, bell.y - r * 0.3f), size = Size(r * 0.76f, r * 0.6f)) }
        }
        drawLine(colors.stand, Offset(center.x, center.y - r * 0.95f), Offset(center.x + shake * r * 0.35f, center.y - r * 1.3f), strokeWidth = r * 0.08f, cap = StrokeCap.Round)
        drawCircle(colors.stand, radius = r * 0.09f, center = Offset(center.x + shake * r * 0.35f, center.y - r * 1.3f))
        // Cassa e quadrante.
        drawCircle(colors.coral.solid, radius = r, center = center)
        drawCircle(Color.White, radius = r * 0.8f, center = center)
        repeat(12) { i ->
            val a = (2 * PI * i / 12).toFloat()
            val outer = r * 0.72f
            val inner = if (i % 3 == 0) r * 0.58f else r * 0.64f
            drawLine(Color(0xFF2B2140), Offset(center.x + cos(a) * inner, center.y + sin(a) * inner), Offset(center.x + cos(a) * outer, center.y + sin(a) * outer), strokeWidth = r * 0.04f, cap = StrokeCap.Round)
        }
        val hourAngle = ((time.hour % 12 + time.minute / 60f) / 12f * 2 * PI - PI / 2).toFloat()
        val minuteAngle = (time.minute / 60f * 2 * PI - PI / 2).toFloat()
        drawLine(Color(0xFF2B2140), center, Offset(center.x + cos(hourAngle) * r * 0.38f, center.y + sin(hourAngle) * r * 0.38f), strokeWidth = r * 0.08f, cap = StrokeCap.Round)
        drawLine(colors.coral.solid, center, Offset(center.x + cos(minuteAngle) * r * 0.58f, center.y + sin(minuteAngle) * r * 0.58f), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
        drawCircle(colors.bolt, radius = r * 0.08f, center = center)
    }
    // Onde del suono ai lati, solo mentre trema.
    if (kotlin.math.abs(shake) > 0.01f) listOf(-1f, 1f).forEach { side ->
        repeat(2) { k ->
            val rr = r * (1.25f + k * 0.2f)
            drawArc(
                Color.White.copy(alpha = 0.5f - k * 0.2f),
                startAngle = if (side < 0) 160f else -20f,
                sweepAngle = 40f,
                useCenter = false,
                topLeft = Offset(center.x - rr, center.y - rr - r * 0.4f),
                size = Size(rr * 2, rr * 2),
                style = Stroke(width = r * 0.06f, cap = StrokeCap.Round),
            )
        }
    }
}

/** Cursore da trascinare fino in fondo per fermare la sveglia (con TalkBack basta un doppio tocco). */
@Composable
private fun SwipeToStop(enabled: Boolean, ink: Color, onStop: () -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val thumb = 64.dp
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(thumb)
            .background(ink.copy(alpha = 0.12f), CircleShape)
            .semantics { onClick(label = tr("Ferma la sveglia")) { onStop(); true } },
    ) {
        val max = with(LocalDensity.current) { (maxWidth - thumb).toPx() }
        Text(
            tr("Scorri per fermare"),
            style = MaterialTheme.typography.titleMedium,
            color = ink.copy(alpha = 0.8f * (1f - offset.value / max.coerceAtLeast(1f))),
            modifier = Modifier.align(Alignment.Center),
        )
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(thumb)
                .background(colors.bolt, CircleShape)
                .pointerInput(enabled, max) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value > max * 0.8f) {
                                    offset.animateTo(max)
                                    onStop()
                                } else {
                                    offset.animateTo(0f, spring())
                                }
                            }
                        },
                    ) { _, delta -> scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, max)) } }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.onBolt, modifier = Modifier.size(32.dp))
        }
    }
}
