package com.ricordella.app.core.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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

/**
 * Ricordella: una palla di vetro con dentro una saetta. È l'unico "personaggio" dell'app:
 * ondeggia piano, e quando la tocchi o completi qualcosa la saetta si accende.
 */
@Composable
fun CrystalBallMascot(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    state: MascotState = rememberMascotState(),
    idle: Boolean = true,
    interactive: Boolean = true,
) {
    val colors = MaterialTheme.ricordellaColors
    val reduced = rememberReducedMotion()
    val haptics = LocalHapticFeedback.current

    // Ingresso: la palla "atterra" con una piccola molla (unico rimbalzo concesso: è il personaggio).
    val entrance = remember { Animatable(if (reduced) 1f else 0.6f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }

    val bob = if (idle && !reduced) {
        rememberInfiniteTransition(label = "bob").animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1800, easing = RicordellaMotion.EaseInOut), RepeatMode.Reverse),
            label = "bobValue",
        ).value
    } else {
        0f
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
            .semantics { contentDescription = "Ricordella, la palla di vetro" }
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
            drawCrystalBall(colors, flash.value)
        }
        StarBurst(
            trigger = state.sparks,
            colors = listOf(colors.bolt, colors.coral.solid, colors.cyan.solid),
            modifier = Modifier.requiredSize(size * 1.6f),
        )
    }
}

private fun DrawScope.drawCrystalBall(colors: RicordellaColors, flash: Float) {
    val w = size.width
    val h = size.height
    val r = w * 0.36f
    val center = Offset(w / 2f, h * 0.43f)

    // Piedistallo.
    val standTop = center.y + r * 0.72f
    val standBottom = h * 0.97f
    val stand = Path().apply {
        moveTo(center.x - r * 0.62f, standTop)
        lineTo(center.x + r * 0.62f, standTop)
        lineTo(center.x + r * 0.9f, standBottom - r * 0.08f)
        quadraticTo(center.x + r * 0.92f, standBottom, center.x + r * 0.8f, standBottom)
        lineTo(center.x - r * 0.8f, standBottom)
        quadraticTo(center.x - r * 0.92f, standBottom, center.x - r * 0.9f, standBottom - r * 0.08f)
        close()
    }
    drawPath(stand, colors.stand)
    drawRoundRect(
        color = colors.standLight,
        topLeft = Offset(center.x - r * 0.68f, standTop - r * 0.06f),
        size = Size(r * 1.36f, r * 0.2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.1f),
    )

    // Vetro.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colors.glassLight, colors.glass, colors.glassDeep),
            center = Offset(center.x - r * 0.35f, center.y - r * 0.4f),
            radius = r * 1.55f,
        ),
        radius = r,
        center = center,
    )

    // Bagliore della saetta: si accende quando la mascotte festeggia.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colors.bolt.copy(alpha = 0.45f + 0.5f * flash), Color.Transparent),
            center = center,
            radius = r * (0.62f + 0.25f * flash),
        ),
        radius = r * (0.62f + 0.25f * flash),
        center = center,
    )

    // Saetta.
    val s = r * (1f + 0.15f * flash)
    val bolt = Path().apply {
        moveTo(center.x + 0.16f * s, center.y - 0.62f * s)
        lineTo(center.x - 0.33f * s, center.y + 0.1f * s)
        lineTo(center.x - 0.02f * s, center.y + 0.1f * s)
        lineTo(center.x - 0.16f * s, center.y + 0.62f * s)
        lineTo(center.x + 0.36f * s, center.y - 0.16f * s)
        lineTo(center.x + 0.05f * s, center.y - 0.16f * s)
        close()
    }
    drawPath(bolt, colors.bolt)
    drawPath(bolt, colors.boltEdge, style = Stroke(width = r * 0.06f, join = StrokeJoin.Round))

    // Riflessi sul vetro.
    rotate(-35f, Offset(center.x - r * 0.42f, center.y - r * 0.5f)) {
        drawOval(
            color = colors.glassHighlight.copy(alpha = 0.8f),
            topLeft = Offset(center.x - r * 0.64f, center.y - r * 0.6f),
            size = Size(r * 0.44f, r * 0.2f),
        )
    }
    drawCircle(colors.glassHighlight.copy(alpha = 0.7f), radius = r * 0.06f, center = Offset(center.x - r * 0.12f, center.y - r * 0.72f))
    drawCircle(colors.glassRim.copy(alpha = 0.55f), radius = r, center = center, style = Stroke(width = r * 0.05f))
}
