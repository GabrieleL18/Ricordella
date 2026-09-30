package com.ricordella.app.core.ui

import com.ricordella.app.core.i18n.tr

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.ui.theme.RicordellaColors
import com.ricordella.app.core.ui.theme.ricordellaColors
import kotlin.math.PI
import kotlin.math.sin

private val Skin = Color(0xFFFFD7B5)
private val Blush = Color(0xFFFF9E9E)
private val Beard = Color(0xFFFFFFFF)
private val BeardShade = Color(0xFFE6E4EF)
private val Ink = Color(0xFF2B2140)
private val Wood = Color(0xFF8A5A3B)

/**
 * Il mago felice di "Niente all'orizzonte": sorride a occhi chiusi, annuisce soddisfatto
 * e fa scintillare la bacchetta. Disegnato in Canvas, stile flat come la mascotte.
 */
@Composable
fun HappyWizard(modifier: Modifier = Modifier, size: Dp = 140.dp) {
    val colors = MaterialTheme.ricordellaColors
    val reduced = rememberReducedMotion()
    val entrance = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }

    val loop = rememberInfiniteTransition(label = "wizard")
    val nod = if (reduced) 0f else loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(1400, easing = RicordellaMotion.EaseInOut), RepeatMode.Reverse), label = "nod").value
    val wave = if (reduced) 0f else loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(900, easing = RicordellaMotion.EaseInOut), RepeatMode.Reverse), label = "wave").value
    val twinkle = if (reduced) 0.5f else loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "twinkle").value

    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = tr("Un mago felice e soddisfatto") }
            .graphicsLayer {
                scaleX = entrance.value
                scaleY = entrance.value
                translationY = (1f - entrance.value) * 30f
            },
    ) {
        drawWizard(colors, nod, wave, twinkle)
    }
}

internal fun DrawScope.drawWizard(colors: RicordellaColors, nod: Float, wave: Float, twinkle: Float) {
    val w = size.width
    val h = size.height
    val cx = w * 0.46f

    // Ombra a terra.
    drawOval(Ink.copy(alpha = 0.10f), topLeft = Offset(cx - w * 0.26f, h * 0.9f), size = Size(w * 0.52f, h * 0.07f))

    // Tunica: trapezio lavanda con orlo.
    val robe = Path().apply {
        moveTo(cx - w * 0.12f, h * 0.5f)
        lineTo(cx + w * 0.12f, h * 0.5f)
        lineTo(cx + w * 0.26f, h * 0.93f)
        lineTo(cx - w * 0.26f, h * 0.93f)
        close()
    }
    drawPath(robe, colors.lavender.solid)
    drawRect(colors.bolt, topLeft = Offset(cx - w * 0.26f, h * 0.88f), size = Size(w * 0.52f, h * 0.05f))

    // Braccio con bacchetta: oscilla come per un incantesimo.
    val shoulder = Offset(cx + w * 0.1f, h * 0.58f)
    rotate(-35f + wave * 18f, shoulder) {
        drawLine(colors.lavender.solid, shoulder, Offset(shoulder.x + w * 0.2f, shoulder.y), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
        drawCircle(Skin, radius = w * 0.04f, center = Offset(shoulder.x + w * 0.22f, shoulder.y))
        val wandBase = Offset(shoulder.x + w * 0.22f, shoulder.y)
        val wandTip = Offset(wandBase.x + w * 0.2f, wandBase.y - w * 0.02f)
        drawLine(Wood, wandBase, wandTip, strokeWidth = w * 0.025f, cap = StrokeCap.Round)
        drawFourPointStar(wandTip, w * (0.05f + 0.025f * sin(twinkle * 2 * PI.toFloat())), colors.bolt, rotation = twinkle * 90f)
        // Scintille che si allontanano dalla punta.
        repeat(3) { i ->
            val t = (twinkle + i / 3f) % 1f
            val spark = Offset(wandTip.x + w * 0.12f * t, wandTip.y - w * 0.14f * t + (i - 1) * w * 0.05f * t)
            drawFourPointStar(spark, w * 0.03f * (1f - t), listOf(colors.bolt, colors.cyan.solid, colors.coral.solid)[i].copy(alpha = 1f - t))
        }
    }

    // Testa che annuisce, con barba e cappello.
    val head = Offset(cx, h * 0.4f + nod * h * 0.012f)
    rotate(nod * 4f, Offset(cx, h * 0.5f)) {
        val faceR = w * 0.12f
        drawCircle(Skin, radius = faceR, center = head)
        // Barba a nuvola.
        val beardTop = head.y + faceR * 0.35f
        listOf(-0.7f to 0.1f, 0f to 0.35f, 0.7f to 0.1f, -0.35f to 0.6f, 0.35f to 0.6f, 0f to 0.95f).forEach { (dx, dy) ->
            drawCircle(if (dy > 0.5f) BeardShade else Beard, radius = faceR * 0.55f, center = Offset(head.x + dx * faceR, beardTop + dy * faceR))
        }
        // Occhi chiusi felici "^ ^" e guance rosse.
        val eyeY = head.y - faceR * 0.1f
        listOf(-1f, 1f).forEach { side ->
            val eye = Offset(head.x + side * faceR * 0.42f, eyeY)
            drawArc(
                Ink,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(eye.x - faceR * 0.2f, eye.y - faceR * 0.12f),
                size = Size(faceR * 0.4f, faceR * 0.32f),
                style = Stroke(width = faceR * 0.12f, cap = StrokeCap.Round),
            )
            drawCircle(Blush.copy(alpha = 0.7f), radius = faceR * 0.16f, center = Offset(head.x + side * faceR * 0.62f, eyeY + faceR * 0.32f))
        }
        // Sorriso che spunta dalla barba.
        drawArc(
            Ink,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(head.x - faceR * 0.28f, head.y + faceR * 0.05f),
            size = Size(faceR * 0.56f, faceR * 0.4f),
            style = Stroke(width = faceR * 0.1f, cap = StrokeCap.Round),
        )
        // Cappello a punta, un po' storto, con stella e falda.
        val brimY = head.y - faceR * 0.7f
        val hat = Path().apply {
            moveTo(head.x - faceR * 1.05f, brimY)
            quadraticTo(head.x - faceR * 0.2f, brimY - faceR * 1.2f, head.x + faceR * 0.9f, brimY - faceR * 2.4f)
            quadraticTo(head.x + faceR * 0.4f, brimY - faceR * 1.0f, head.x + faceR * 1.05f, brimY)
            close()
        }
        drawPath(hat, colors.cyan.solid)
        drawRoundRect(
            colors.stand,
            topLeft = Offset(head.x - faceR * 1.35f, brimY - faceR * 0.12f),
            size = Size(faceR * 2.7f, faceR * 0.3f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(faceR * 0.15f),
        )
        drawFourPointStar(Offset(head.x + faceR * 0.05f, brimY - faceR * 0.8f), faceR * (0.28f + 0.06f * sin(twinkle * 4 * PI.toFloat())), colors.bolt)
    }
}

private val SkinTones = listOf(Color(0xFFFFD7B5), Color(0xFFF1C27D), Color(0xFFD9A066), Color(0xFFA56B46), Color(0xFFFFE0CC))
private val HairTones = listOf(Color(0xFFFFFFFF), Color(0xFF6B4226), Color(0xFF2B2140), Color(0xFFE0A030), Color(0xFFB0413E))

/**
 * Avatar "maghetto" per chi non ha una foto: cappello del colore della persona, stella,
 * barba o capelli, occhiali a volte. Tutto deriva dal [seed], così ognuno ha il suo mago.
 * Sbatte le palpebre, la punta del cappello ondeggia e la stella luccica (ognuno col suo ritmo).
 */
@Composable
fun WizardAvatar(seed: String, size: Dp, modifier: Modifier = Modifier) {
    val tone = toneFor(seed)
    val colors = MaterialTheme.ricordellaColors
    val hash = seed.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
    val skin = SkinTones[hash % SkinTones.size]
    val hair = HairTones[(hash / 7) % HairTones.size]
    val beard = (hash / 3) % 3 != 0
    val glasses = (hash / 11) % 4 == 0
    val offset = (hash % 1000) / 1000f
    val reduced = rememberReducedMotion()
    val t = if (reduced) 0.3f else rememberInfiniteTransition(label = "avatar").animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(3600 + (hash % 900), easing = LinearEasing)),
        label = "avatarT",
    ).value
    val phase = (t + offset) % 1f
    // Sbatte le palpebre per un attimo a ogni giro.
    val blink = phase in 0.92f..0.97f
    val sway = sin(phase * 2 * PI.toFloat()) * 8f
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val c = Offset(w / 2, w / 2)
        drawCircle(tone.container, radius = w / 2, center = c)
        val faceR = w * 0.2f
        val head = Offset(c.x, c.y + w * 0.12f)
        // Capelli/barba dietro al viso.
        if (beard) {
            listOf(-0.75f to 0.45f, 0f to 0.75f, 0.75f to 0.45f, 0f to 1.1f).forEach { (dx, dy) ->
                drawCircle(hair, radius = faceR * 0.62f, center = Offset(head.x + dx * faceR, head.y + dy * faceR))
            }
        } else {
            drawCircle(hair, radius = faceR * 1.15f, center = Offset(head.x, head.y + faceR * 0.25f))
        }
        drawCircle(skin, radius = faceR, center = head)
        // Occhi (chiusi quando sbatte) e guance.
        val eyeY = head.y - faceR * 0.05f
        listOf(-1f, 1f).forEach { side ->
            val eye = Offset(head.x + side * faceR * 0.4f, eyeY)
            if (blink) drawLine(Ink, Offset(eye.x - faceR * 0.14f, eye.y), Offset(eye.x + faceR * 0.14f, eye.y), strokeWidth = faceR * 0.1f, cap = StrokeCap.Round)
            else drawCircle(Ink, radius = faceR * 0.11f, center = eye)
            drawCircle(Blush.copy(alpha = 0.6f), radius = faceR * 0.14f, center = Offset(head.x + side * faceR * 0.62f, eyeY + faceR * 0.35f))
            if (glasses) drawCircle(Ink, radius = faceR * 0.26f, center = eye, style = Stroke(width = faceR * 0.07f))
        }
        drawArc(
            Ink, 20f, 140f, false,
            topLeft = Offset(head.x - faceR * 0.25f, head.y + faceR * 0.15f),
            size = Size(faceR * 0.5f, faceR * 0.35f),
            style = Stroke(width = faceR * 0.09f, cap = StrokeCap.Round),
        )
        // Cappello a punta: la punta ondeggia.
        val brimY = head.y - faceR * 0.65f
        rotate(sway, Offset(head.x, brimY)) {
            val hat = Path().apply {
                moveTo(head.x - faceR * 1.1f, brimY)
                quadraticTo(head.x - faceR * 0.1f, brimY - faceR * 1.1f, head.x + faceR * 0.55f, brimY - faceR * 2.1f)
                quadraticTo(head.x + faceR * 0.35f, brimY - faceR * 0.9f, head.x + faceR * 1.1f, brimY)
                close()
            }
            drawPath(hat, tone.solid)
            drawFourPointStar(
                Offset(head.x + faceR * 0.05f, brimY - faceR * 0.7f),
                faceR * (0.28f + 0.06f * sin(phase * 4 * PI.toFloat())),
                colors.bolt,
            )
        }
        drawRoundRect(
            tone.solid.copy(alpha = 0.85f),
            topLeft = Offset(head.x - faceR * 1.35f, brimY - faceR * 0.12f),
            size = Size(faceR * 2.7f, faceR * 0.28f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(faceR * 0.14f),
        )
    }
}
