package com.ricordella.app.core.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Linguaggio del movimento: tre easing, tre durate. Niente easing di default. */
object RicordellaMotion {
    /** Elementi che entrano: rallentano arrivando. */
    val EaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    /** Elementi che escono: accelerano andando via. */
    val EaseIn = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)
    /** Cambi di stato. */
    val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    /** Pressione dei pulsanti "push": secca, senza rimbalzo. */
    val Snappy = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)

    const val MICRO = 120
    const val SHORT = 220
    const val LONG = 420
}

/**
 * True se l'utente ha disattivato le animazioni di sistema (Opzioni sviluppatore o
 * accessibilità "Rimuovi animazioni"): le animazioni decorative vengono saltate.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Leggera compressione al tocco: rende tattili card e riquadri. */
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(if (pressed) RicordellaMotion.MICRO else RicordellaMotion.SHORT, easing = RicordellaMotion.Snappy),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Ricorda quali elementi di una lista sono già comparsi, per animarli una volta sola. */
@Stable
class RevealTracker {
    private val revealed = mutableStateMapOf<Any, Boolean>()
    fun isRevealed(key: Any) = revealed[key] == true
    fun markRevealed(key: Any) {
        revealed[key] = true
    }
}

@Composable
fun rememberRevealTracker(): RevealTracker = remember { RevealTracker() }

/**
 * Comparsa a cascata: dissolvenza + risalita di pochi dp, ritardata in base alla posizione.
 * Ogni elemento compare una sola volta; la cascata totale resta sotto il mezzo secondo.
 */
fun Modifier.reveal(tracker: RevealTracker, key: Any, index: Int): Modifier = composed {
    val reduced = rememberReducedMotion()
    val alreadyShown = remember(key) { reduced || tracker.isRevealed(key) }
    val progress = remember(key) { Animatable(if (alreadyShown) 1f else 0f) }
    val offsetPx = with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(key) {
        if (!alreadyShown) {
            delay((index.coerceIn(0, 8) * 45L))
            progress.animateTo(1f, tween(RicordellaMotion.LONG, easing = RicordellaMotion.EaseOut))
        }
        tracker.markRevealed(key)
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}

/**
 * Micro-festa: stelle a quattro punte che esplodono dal centro e svaniscono.
 * Si attiva a ogni cambio di [trigger] (> 0); non va mai in loop.
 */
@Composable
fun StarBurst(
    trigger: Int,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stars: Int = 8,
) {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0 && !reduced) {
            progress.snapTo(0f)
            launch { progress.animateTo(1f, tween(560, easing = RicordellaMotion.EaseOut)) }
        }
    }
    val p = progress.value
    if (p >= 1f || colors.isEmpty()) return
    Canvas(modifier.size(size)) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val maxRadius = this.size.minDimension / 2
        val fade = 1f - FastOutLinearInEasing.transform(p)
        repeat(stars) { i ->
            val angle = (2 * PI * i / stars + PI / stars).toFloat()
            val distance = maxRadius * (0.25f + 0.75f * p)
            val position = Offset(center.x + cos(angle) * distance, center.y + sin(angle) * distance)
            val starSize = maxRadius * 0.22f * (if (p < 0.4f) p / 0.4f else 1f) * (if (i % 2 == 0) 1f else 0.7f)
            drawFourPointStar(position, starSize, colors[i % colors.size].copy(alpha = fade), rotation = p * 90f)
        }
    }
}

fun DrawScope.drawFourPointStar(center: Offset, radius: Float, color: Color, rotation: Float = 0f) {
    if (radius <= 0f) return
    val inner = radius * 0.32f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + inner, center.y - inner)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + inner, center.y + inner)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - inner, center.y + inner)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - inner, center.y - inner)
        close()
    }
    rotate(rotation, center) { drawPath(path, color) }
}
