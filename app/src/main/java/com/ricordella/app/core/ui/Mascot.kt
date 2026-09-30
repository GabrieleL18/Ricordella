package com.ricordella.app.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.ui.theme.RicordellaColors
import com.ricordella.app.core.ui.theme.ricordellaColors
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Stato della mascotte: ogni [celebrate] fa brillare la saetta e scintillare la palla. */
@Stable
class MascotState {
    var sparks by mutableIntStateOf(0)
        private set

    fun celebrate() {
        sparks++
    }
}

@Composable
fun rememberMascotState(): MascotState = remember { MascotState() }

private val SmokeRed = Color(0xFFE53935)
private val SmokeDeep = Color(0xFF9E1B1B)

/**
 * Ricordella: una palla di vetro (stile flat) con dentro una saetta che gira su se stessa.
 * Se c'è qualcosa di scaduto e non completato ([overdue]) la palla si riempie di fumo rosso;
 * quando si torna in pari il fumo svanisce verso l'alto e la saetta ricompare con un lampo.
 */
@Composable
fun CrystalBallMascot(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    state: MascotState = rememberMascotState(),
    idle: Boolean = true,
    interactive: Boolean = true,
    overdue: Boolean = false,
) {
    val colors = MaterialTheme.ricordellaColors
    val reduced = rememberReducedMotion()
    val haptics = LocalHapticFeedback.current

    // Ingresso: la palla "atterra" con una piccola molla (unico rimbalzo concesso: è il personaggio).
    val entrance = remember { Animatable(if (reduced) 1f else 0.6f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }

    val loop = rememberInfiniteTransition(label = "mascot")
    val bob = if (idle && !reduced) {
        loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(1800, easing = RicordellaMotion.EaseInOut), RepeatMode.Reverse), label = "bob").value
    } else {
        0f
    }
    // La saetta gira sul proprio asse verticale; il fumo vortica. Con animazioni ridotte stanno fermi.
    val spin = if (!reduced) loop.animateFloat(0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "spin").value else 0f
    val swirl = if (!reduced) loop.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "swirl").value else 0f

    // 1 = palla piena di fumo, 0 = saetta visibile.
    val smoke = remember { Animatable(if (overdue) 1f else 0f) }
    LaunchedEffect(overdue) {
        val target = if (overdue) 1f else 0f
        if (smoke.value == target) return@LaunchedEffect
        if (!overdue) state.celebrate()
        smoke.animateTo(target, tween(if (overdue) 900 else 1100, easing = RicordellaMotion.EaseInOut))
    }

    val wobble = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(state.sparks) {
        if (state.sparks == 0 || reduced) return@LaunchedEffect
        launch {
            wobble.animateTo(
                0f,
                keyframes {
                    durationMillis = 560
                    -10f at 90
                    8f at 200
                    -4f at 320
                    2f at 430
                },
            )
        }
        launch {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(700, easing = RicordellaMotion.EaseOut))
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = if (overdue) "Remindella: c'è qualcosa di scaduto" else "Remindella, la palla di vetro"
            }
            .then(
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        state.celebrate()
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = entrance.value
                    scaleY = entrance.value
                    translationY = bob * size.toPx() * 0.03f
                    rotationZ = wobble.value
                },
        ) {
            drawCrystalBall(colors, flash.value, spin, smoke.value, swirl, clearing = !overdue)
        }
        StarBurst(
            trigger = state.sparks,
            colors = listOf(colors.bolt, colors.coral.solid, colors.cyan.solid),
            modifier = Modifier.requiredSize(size * 1.6f),
        )
    }
}

private fun DrawScope.drawCrystalBall(
    colors: RicordellaColors,
    flash: Float,
    spin: Float,
    smoke: Float,
    swirl: Float,
    clearing: Boolean,
) {
    val w = size.width
    val h = size.height
    val r = w * 0.36f
    val center = Offset(w / 2f, h * 0.43f)

    // Piedistallo: due trapezi pieni, nessuna sfumatura.
    val standTop = center.y + r * 0.72f
    val standBottom = h * 0.97f
    val stand = Path().apply {
        moveTo(center.x - r * 0.62f, standTop)
        lineTo(center.x + r * 0.62f, standTop)
        lineTo(center.x + r * 0.9f, standBottom)
        lineTo(center.x - r * 0.9f, standBottom)
        close()
    }
    drawPath(stand, colors.stand)
    drawRoundRect(
        color = colors.standLight,
        topLeft = Offset(center.x - r * 0.7f, standTop - r * 0.06f),
        size = Size(r * 1.4f, r * 0.2f),
        cornerRadius = CornerRadius(r * 0.1f),
    )

    // Vetro flat: disco pieno + mezzaluna più chiara.
    drawCircle(colors.glass, radius = r, center = center)
    drawCircle(colors.glassLight.copy(alpha = 0.35f), radius = r * 0.78f, center = Offset(center.x - r * 0.12f, center.y - r * 0.12f))

    val glass = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
    clipPath(glass) {
        // Alone della saetta, spento dal fumo.
        val boltVisible = 1f - smoke
        if (boltVisible > 0f) {
            drawCircle(
                color = colors.bolt.copy(alpha = (0.25f + 0.45f * flash) * boltVisible),
                radius = r * (0.55f + 0.25f * flash),
                center = center,
            )
            drawSpinningBolt(colors, center, r * (0.85f + 0.3f * flash) * (0.4f + 0.6f * boltVisible), spin)
        }
        if (smoke > 0f) drawSmoke(center, r, smoke, swirl, clearing)
    }

    // Riflesso flat e bordo.
    drawArc(
        color = colors.glassHighlight.copy(alpha = 0.85f),
        startAngle = 200f,
        sweepAngle = 55f,
        useCenter = false,
        topLeft = Offset(center.x - r * 0.72f, center.y - r * 0.72f),
        size = Size(r * 1.44f, r * 1.44f),
        style = Stroke(width = r * 0.1f, cap = StrokeCap.Round),
    )
    drawCircle(colors.glassHighlight, radius = r * 0.06f, center = Offset(center.x - r * 0.05f, center.y - r * 0.75f))
    drawCircle(colors.glassRim, radius = r, center = center, style = Stroke(width = r * 0.06f))
}

/** Saetta che ruota sull'asse verticale: la larghezza segue il coseno, il retro è più scuro. */
private fun DrawScope.drawSpinningBolt(colors: RicordellaColors, center: Offset, s: Float, spin: Float) {
    val radians = spin * PI.toFloat() / 180f
    val facing = cos(radians)
    val squash = abs(facing).coerceAtLeast(0.08f)
    val bolt = Path().apply {
        moveTo(center.x + 0.16f * s, center.y - 0.62f * s)
        lineTo(center.x - 0.33f * s, center.y + 0.1f * s)
        lineTo(center.x - 0.02f * s, center.y + 0.1f * s)
        lineTo(center.x - 0.16f * s, center.y + 0.62f * s)
        lineTo(center.x + 0.36f * s, center.y - 0.16f * s)
        lineTo(center.x + 0.05f * s, center.y - 0.16f * s)
        close()
    }
    withTransform({ scale(scaleX = if (facing >= 0f) squash else -squash, scaleY = 1f, pivot = center) }) {
        drawPath(bolt, if (facing >= 0f) colors.bolt else colors.boltEdge)
    }
}

/**
 * Fumo rosso: sbuffi che vorticano dentro la palla. In arrivo crescono dal fondo;
 * quando si dissolve ([clearing]) salgono, si allargano e svaniscono.
 */
private fun DrawScope.drawSmoke(center: Offset, r: Float, amount: Float, swirl: Float, clearing: Boolean) {
    val puffs = 7
    val gone = 1f - amount
    repeat(puffs) { i ->
        val phase = (swirl + i.toFloat() / puffs) * 2f * PI.toFloat()
        val orbit = r * (0.18f + 0.26f * ((i % 3) / 2f))
        val rise = if (clearing) gone * r * 0.9f else (1f - amount) * -r * 0.4f
        val position = Offset(
            center.x + cos(phase) * orbit,
            center.y + sin(phase * 0.8f) * orbit * 0.7f - rise + r * 0.1f,
        )
        val grow = if (clearing) 1f + gone * 0.8f else amount
        val radius = r * (0.3f + 0.08f * (i % 2)) * grow
        val color = if (i % 2 == 0) SmokeRed else SmokeDeep
        drawCircle(color.copy(alpha = 0.55f * amount), radius = radius, center = position)
    }
    // Velo di fondo che tinge tutta la palla.
    drawCircle(SmokeRed.copy(alpha = 0.35f * amount), radius = r, center = center)
}
