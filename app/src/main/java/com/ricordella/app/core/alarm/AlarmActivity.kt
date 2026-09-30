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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
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
            RicordellaTheme(themeMode = ThemeMode.DARK) {
                AlarmScreen(
                    title = shown!!.title,
                    ringing = ringing != null,
                    onStop = { AlarmRingService.stop(this) },
                    onSnooze = { AlarmRingService.snooze(this) },
                    onDone = ::finish,
                )
            }
        }
    }
}

@Composable
private fun AlarmScreen(title: String, ringing: Boolean, onStop: () -> Unit, onSnooze: () -> Unit, onDone: () -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    val calm = remember { Animatable(0f) }
    val thrown = remember { Animatable(0f) }
    val night = remember { Animatable(0f) }
    LaunchedEffect(ringing) {
        if (ringing) return@LaunchedEffect
        if (AlarmRingService.endedBySnooze) {
            // Posticipata: il maghetto lancia via la sveglia e fa calare la notte.
            thrown.animateTo(1f, tween(900, easing = RicordellaMotion.EaseIn))
            night.animateTo(1f, tween(1400, easing = RicordellaMotion.EaseInOut))
            delay(1_200)
        } else {
            // Fermata: la sveglia si quieta ed esplode di stelle.
            calm.animateTo(1f, tween(1400, easing = RicordellaMotion.EaseOut))
            delay(300)
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
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1036), Color(0xFF2E1A5C), Color(0xFF111319))))
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LiveTime(now, MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold), Color.White, Modifier.padding(top = 16.dp))
        Text(
            DateTexts.weekdayAndDay(now.toLocalDate()).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.75f),
        )
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.bolt,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 20.dp),
        )
        AlarmScene(calm = calm.value, thrown = thrown.value, modifier = Modifier.fillMaxWidth().weight(1f))
        OutlinedButton(onClick = onSnooze, enabled = ringing, modifier = Modifier.padding(bottom = 16.dp)) {
            Icon(Icons.Rounded.Snooze, contentDescription = null, tint = Color.White)
            Text(trf("Posticipa %1\$s min", AlarmRingService.SNOOZE_MINUTES), color = Color.White, modifier = Modifier.padding(start = 8.dp))
        }
        SwipeToStop(enabled = ringing, onStop = onStop)
    }
    if (night.value > 0f) Nightfall(night.value)
    }
}

/** La notte cala sulla schermata: cielo blu scuro, luna calante, stelle che si accendono e un "a dopo". */
@Composable
private fun Nightfall(progress: Float) {
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
            trf("Buonanotte… ti risveglio tra %1\$s minuti", AlarmRingService.SNOOZE_MINUTES),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White.copy(alpha = ((progress - 0.5f) * 2f).coerceIn(0f, 1f)),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
        )
    }
}

/**
 * Il maghetto incanta la sveglia che trema; [calm] da 0 a 1 la ferma con un'esplosione di stelle,
 * [thrown] da 0 a 1 la lancia fuori dallo schermo con un colpo di bacchetta.
 */
@Composable
private fun AlarmScene(calm: Float, thrown: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.ricordellaColors
    val reduced = rememberReducedMotion()
    val loop = rememberInfiniteTransition(label = "alarm")
    val shake = if (reduced) 0f else loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(70, easing = LinearEasing), RepeatMode.Reverse), label = "shake").value
    val wave = if (reduced) 0f else loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(600, easing = RicordellaMotion.EaseInOut), RepeatMode.Reverse), label = "wave").value
    val twinkle = if (reduced) 0.5f else loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "twinkle").value
    val time = LocalDateTime.now()
    Canvas(modifier.semantics { contentDescription = tr("Un maghetto incanta la sveglia con la bacchetta") }) {
        val w = size.width
        val h = size.height
        val side = minOf(w * 0.5f, h * 0.8f)
        val top = (h - side) / 2
        // Maghetto a sinistra: finita la sveglia, smette di agitare la bacchetta e annuisce.
        inset(left = w * 0.02f, top = top, right = w - side - w * 0.02f, bottom = h - top - side) {
            // Lancio: la bacchetta carica all'indietro e poi scatta in avanti.
            val swing = if (thrown > 0f) (thrown * 3f - 1f).coerceIn(-1f, 1.6f) else wave * (1f - calm * 0.8f)
            drawWizard(colors, nod = wave * calm, wave = swing, twinkle = twinkle)
        }
        // La sveglia lanciata vola in alto a destra, girando su se stessa, fino a sparire.
        val clock = Offset(w * 0.74f + w * 0.8f * thrown, h * 0.5f - h * 1.5f * thrown + h * 0.4f * thrown * thrown)
        val radius = minOf(w * 0.18f, h * 0.26f)
        // Incantesimo: stelline che volano dalla bacchetta alla sveglia.
        val wand = Offset(side * 0.95f, top + side * 0.45f)
        if (calm < 1f && thrown == 0f) repeat(5) { i ->
            val t = (twinkle + i / 5f) % 1f
            val x = wand.x + (clock.x - radius - wand.x) * t
            val y = wand.y + (clock.y - wand.y) * t - sin(t * PI.toFloat()) * h * 0.12f
            drawFourPointStar(Offset(x, y), radius * 0.12f * (1f - t * 0.5f), listOf(colors.bolt, colors.cyan.solid, colors.coral.solid)[i % 3].copy(alpha = 1f - calm), rotation = t * 180f)
        }
        rotate(thrown * 540f, clock) {
            drawAlarmClock(colors, clock, radius * (1f - 0.4f * thrown), if (thrown > 0f) 0f else shake * (1f - calm), time)
        }
        // Stop: anello di stelle che esplode dalla sveglia.
        if (calm > 0f && calm < 1f) repeat(10) { i ->
            val angle = (2 * PI * i / 10).toFloat()
            val distance = radius * (0.8f + 1.4f * calm)
            drawFourPointStar(
                Offset(clock.x + cos(angle) * distance, clock.y + sin(angle) * distance),
                radius * 0.16f * (1f - calm),
                listOf(colors.bolt, colors.cyan.solid, colors.coral.solid, colors.mint.solid)[i % 4],
                rotation = calm * 180f,
            )
        }
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
private fun SwipeToStop(enabled: Boolean, onStop: () -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val thumb = 64.dp
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(thumb)
            .background(Color.White.copy(alpha = 0.12f), CircleShape)
            .semantics { onClick(label = tr("Ferma la sveglia")) { onStop(); true } },
    ) {
        val max = with(LocalDensity.current) { (maxWidth - thumb).toPx() }
        Text(
            tr("Scorri per fermare"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.8f * (1f - offset.value / max.coerceAtLeast(1f))),
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
