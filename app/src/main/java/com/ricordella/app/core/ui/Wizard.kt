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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.ui.theme.RicordellaColors
import com.ricordella.app.domain.model.ItemGroup
import com.ricordella.app.domain.model.ReminderType
import androidx.compose.ui.geometry.CornerRadius
import com.ricordella.app.core.ui.theme.ricordellaColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
fun HappyWizard(modifier: Modifier = Modifier, size: Dp = 140.dp, love: Float = 0f, waving: Boolean = false, magic: Int = 0, holding: WizardProp? = null, scene: WizardScene? = null) {
    val colors = MaterialTheme.ricordellaColors
    val reduced = rememberReducedMotion()
    val haptics = LocalHapticFeedback.current
    // Magia: a ogni tocco (o a ogni [magic] in più) fa un saltello e sprigiona una corona di stelle.
    var taps by remember { mutableIntStateOf(0) }
    val burst = remember { Animatable(1f) }
    LaunchedEffect(taps + magic) {
        if (taps + magic == 0 || reduced) return@LaunchedEffect
        burst.snapTo(0f)
        burst.animateTo(1f, tween(900, easing = LinearEasing))
    }
    val entrance = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }

    val loop = rememberInfiniteTransition(label = "wizard")
    val nod = if (reduced) 0f else loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(1400, easing = RicordellaMotion.EaseInOut), RepeatMode.Reverse), label = "nod").value
    val wave = if (reduced) 0f else loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(900, easing = RicordellaMotion.EaseInOut), RepeatMode.Reverse), label = "wave").value
    val twinkle = if (reduced) 0.5f else loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "twinkle").value
    val cycle = if (reduced) 0.1f else loop.animateFloat(0f, 1f, infiniteRepeatable(tween(6400, easing = LinearEasing)), label = "cycle").value

    Canvas(
        modifier
            .size(size)
            .semantics {
                contentDescription = when {
                    waving -> tr("Un mago che saluta")
                    holding != null -> tr("Un mago al lavoro")
                    scene == WizardScene.BROOM -> tr("Un mago che vola su una scopa")
                    scene == WizardScene.TV -> tr("Un mago che guarda la TV")
                    scene == WizardScene.PHONE_CALL -> tr("Un mago che parla al telefono")
                    scene == WizardScene.READING -> tr("Un mago che legge un libro")
                    scene == WizardScene.CONJURING -> tr("Un mago che crea oggetti con la magia")
                    scene == WizardScene.SEARCHING -> tr("Un mago che cerca con la lente")
                    scene == WizardScene.WAITING_BELL -> tr("Un mago che aspetta che suoni la campanella")
                    love > 0.5f -> tr("Un mago innamorato")
                    else -> tr("Un mago felice e soddisfatto")
                }
            }
            .graphicsLayer {
                scaleX = entrance.value
                scaleY = entrance.value
                translationY = (1f - entrance.value) * 30f - sin(burst.value * PI.toFloat()) * this.size.height * 0.12f
                rotationZ = sin(burst.value * 2 * PI.toFloat()) * 8f
            }
            .clickable(interactionSource = null, indication = null) {
                taps++
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
    ) {
        drawWizard(colors, nod, wave, twinkle, love, waving, holding, scene, cycle)
        if (burst.value < 1f) {
            val t = burst.value
            val c = Offset(this.size.width * 0.5f, this.size.height * 0.45f)
            drawCircle(colors.bolt.copy(alpha = 0.35f * (1f - t)), radius = this.size.minDimension * 0.6f * t, center = c)
            val tints = listOf(colors.bolt, colors.cyan.solid, colors.coral.solid, colors.lavender.solid)
            repeat(10) { i ->
                val angle = i / 10f * 2 * PI.toFloat() + t
                val r = this.size.minDimension * (0.2f + 0.55f * t)
                val star = Offset(c.x + kotlin.math.cos(angle) * r, c.y + sin(angle) * r)
                drawFourPointStar(star, this.size.minDimension * 0.07f * (1f - t), tints[i % tints.size].copy(alpha = 1f - t), rotation = t * 180f)
            }
        }
    }
}

/**
 * [love] da 0 a 1: gli occhi diventano cuori che pulsano e le guance si fanno più rosse.
 * [waving]: posa la bacchetta e alza la mano per salutare.
 * [holding]: al posto della bacchetta mostra un oggetto animato (vedi [WizardProp]).
 * [scene]: il mago fa qualcosa (vedi [WizardScene]); [cycle] (0..1, lento) scandisce la scena.
 */
internal fun DrawScope.drawWizard(
    colors: RicordellaColors,
    nod: Float,
    wave: Float,
    twinkle: Float,
    love: Float = 0f,
    waving: Boolean = false,
    holding: WizardProp? = null,
    scene: WizardScene? = null,
    cycle: Float = 0f,
) {
    val w = size.width
    val h = size.height
    val cx = w * when (scene) {
        WizardScene.TV -> 0.3f
        WizardScene.WAITING_BELL -> 0.36f
        else -> 0.46f
    }
    val flying = scene == WizardScene.BROOM
    // La campanella suona nell'ultimo quarto di ogni mezzo giro lento: il mago fa un saltello e alza il braccio.
    val ring = if (scene == WizardScene.WAITING_BELL) ((cycle * 2f % 1f) - 0.75f) / 0.25f else -1f
    val ringing = ring in 0f..1f
    val lift = when {
        flying -> sin(twinkle * 2 * PI.toFloat()) * h * 0.035f - h * 0.08f
        ringing -> -sin(ring * PI.toFloat()) * h * 0.05f
        else -> 0f
    }

    if (flying) {
        // Ombra che si stringe quando sale, e scia di vento dietro la scopa.
        drawOval(Ink.copy(alpha = 0.08f), topLeft = Offset(cx - w * 0.2f, h * 0.92f), size = Size(w * 0.4f, h * 0.05f))
        repeat(3) { i ->
            val t = (twinkle + i / 3f) % 1f
            val y = h * (0.6f + i * 0.09f) + lift
            val x = w * (0.2f - 0.22f * t)
            drawLine(colors.cyan.solid.copy(alpha = 0.6f * (1f - t)), Offset(x, y), Offset(x + w * 0.12f, y), strokeWidth = w * 0.02f, cap = StrokeCap.Round)
        }
    } else {
        drawOval(Ink.copy(alpha = 0.10f), topLeft = Offset(cx - w * 0.26f, h * 0.9f), size = Size(w * 0.52f, h * 0.07f))
    }

    if (scene == WizardScene.TV) drawTv(colors, Offset(w * 0.81f, h * 0.66f), w * 0.17f, twinkle, cycle)
    if (scene == WizardScene.WAITING_BELL) drawHandBell(colors, Offset(w * 0.8f, h * 0.9f), w, ring)

    withTransform({
        translate(top = lift)
        if (flying) rotate(-8f, Offset(cx, h * 0.75f))
    }) {
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

        // Scopa: manico sotto il mago, saggina dorata dietro.
        if (flying) {
            val bristles = Path().apply {
                moveTo(cx - w * 0.3f, h * 0.78f)
                lineTo(cx - w * 0.5f, h * 0.7f + wave * h * 0.02f)
                lineTo(cx - w * 0.5f, h * 0.92f - wave * h * 0.02f)
                lineTo(cx - w * 0.3f, h * 0.84f)
                close()
            }
            drawPath(bristles, colors.bolt)
            drawLine(Wood, Offset(cx - w * 0.3f, h * 0.76f), Offset(cx - w * 0.3f, h * 0.86f), strokeWidth = w * 0.03f)
            drawLine(Wood, Offset(cx - w * 0.3f, h * 0.81f), Offset(cx + w * 0.42f, h * 0.79f), strokeWidth = w * 0.035f, cap = StrokeCap.Round)
        }

        // Braccio: bacchetta che oscilla, oppure la posa della scena.
        val shoulder = Offset(cx + w * 0.1f, h * 0.58f)
        val armAngle = when {
            waving -> -65f + wave * 22f
            holding != null -> -50f + wave * 12f
            scene == WizardScene.BROOM -> 64f
            scene == WizardScene.TV -> 18f + wave * 3f
            scene == WizardScene.PHONE_CALL -> -82f
            scene == WizardScene.READING -> 35f
            scene == WizardScene.SEARCHING -> -12f + wave * 28f
            ringing -> -65f + wave * 22f
            scene == WizardScene.WAITING_BELL -> 72f
            else -> -35f + wave * 18f
        }
        val wand = !waving && holding == null && (scene == null || scene == WizardScene.CONJURING)
        rotate(armAngle, shoulder) {
            drawLine(colors.lavender.solid, shoulder, Offset(shoulder.x + w * 0.2f, shoulder.y), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
            drawCircle(Skin, radius = w * (if (waving) 0.055f else 0.04f), center = Offset(shoulder.x + w * 0.22f, shoulder.y))
            if (!wand) return@rotate
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
        val rad = armAngle * PI.toFloat() / 180f
        val hand = Offset(shoulder.x + cos(rad) * w * 0.22f, shoulder.y + sin(rad) * w * 0.22f)

        // Testa che annuisce, con barba e cappello.
        drawWizardHead(colors, Offset(cx, h * 0.4f + nod * h * 0.012f), w * 0.12f, Offset(cx, h * 0.5f), nod, twinkle, love)

        when (scene) {
            WizardScene.TV -> drawRemote(colors, hand, w, twinkle)
            WizardScene.PHONE_CALL -> drawPhoneCall(colors, hand, Offset(cx, h * 0.4f), w, twinkle)
            WizardScene.READING -> drawBook(colors, Offset(cx + w * 0.07f, h * 0.68f), w * 0.2f, twinkle, cycle).also {
                drawCircle(Skin, radius = w * 0.04f, center = hand)
            }
            WizardScene.CONJURING -> {
                val tip = Offset(shoulder.x + cos(rad) * w * 0.42f - sin(rad) * -w * 0.02f, shoulder.y + sin(rad) * w * 0.42f + cos(rad) * -w * 0.02f)
                drawConjured(colors, tip, w, cycle)
            }
            WizardScene.SEARCHING -> drawMagnifier(colors, hand, rad, w, twinkle)
            else -> Unit
        }

        // L'oggetto resta dritto sopra la mano, che lo alza e lo abbassa.
        if (holding != null) {
            val u = w * 0.085f
            drawProp(holding, colors, Offset(hand.x + u * 0.3f, hand.y - u * 1.1f), u, twinkle)
        }
    }
}

/** Cosa sta facendo il mago: nel dettaglio di una "Cosa" o negli stati vuoti. */
enum class WizardScene {
    BROOM, TV, PHONE_CALL, READING, CONJURING,

    /** Stato vuoto delle Cose: cerca qualcosa con la lente. */
    SEARCHING,

    /** Stato vuoto dei Promemoria: aspetta che suoni la campanella. */
    WAITING_BELL,
}

/** Scena per una "Cosa", dal gruppo: veicoli, casa, elettronica, documenti o generico. */
fun wizardSceneFor(group: ItemGroup): WizardScene = when (group) {
    ItemGroup.VEHICLES -> WizardScene.BROOM
    ItemGroup.HOME -> WizardScene.TV
    ItemGroup.ELECTRONICS -> WizardScene.PHONE_CALL
    ItemGroup.DOCUMENTS -> WizardScene.READING
    ItemGroup.GENERIC -> WizardScene.CONJURING
}

/** Televisore con le antenne: lo schermo cambia canale e sfarfalla. */
private fun DrawScope.drawTv(colors: RicordellaColors, center: Offset, r: Float, twinkle: Float, cycle: Float) {
    val round = StrokeCap.Round
    drawLine(Ink, Offset(center.x, center.y - r * 0.75f), Offset(center.x - r * 0.5f, center.y - r * 1.4f), strokeWidth = r * 0.07f, cap = round)
    drawLine(Ink, Offset(center.x, center.y - r * 0.75f), Offset(center.x + r * 0.45f, center.y - r * 1.35f), strokeWidth = r * 0.07f, cap = round)
    drawLine(Ink, Offset(center.x - r * 0.6f, center.y + r * 0.7f), Offset(center.x - r * 0.75f, center.y + r * 1.05f), strokeWidth = r * 0.1f, cap = round)
    drawLine(Ink, Offset(center.x + r * 0.6f, center.y + r * 0.7f), Offset(center.x + r * 0.75f, center.y + r * 1.05f), strokeWidth = r * 0.1f, cap = round)
    drawRoundRect(Wood, Offset(center.x - r, center.y - r * 0.8f), Size(r * 2f, r * 1.6f), CornerRadius(r * 0.25f))
    // Ogni canale ha il suo colore e il suo "programma".
    val channel = (cycle * 4).toInt() % 4
    val screen = listOf(colors.cyan.solid, colors.coral.solid, colors.pear.solid, colors.lavender.solid)[channel]
    val topLeft = Offset(center.x - r * 0.82f, center.y - r * 0.62f)
    val screenSize = Size(r * 1.3f, r * 1.24f)
    drawRoundRect(screen, topLeft, screenSize, CornerRadius(r * 0.2f))
    val mid = Offset(topLeft.x + screenSize.width / 2, topLeft.y + screenSize.height / 2)
    when (channel) {
        0 -> drawFourPointStar(mid, r * 0.35f * (0.8f + 0.2f * sin(twinkle * 2 * PI.toFloat())), Color.White, rotation = twinkle * 90f)
        1 -> drawHeart(mid, r * 0.25f * (1f + 0.1f * sin(twinkle * 4 * PI.toFloat())), Color.White)
        2 -> drawCircle(Color.White, radius = r * 0.22f, center = Offset(mid.x + sin(twinkle * 2 * PI.toFloat()) * r * 0.3f, mid.y))
        else -> repeat(3) { i -> drawLine(Color.White.copy(alpha = 0.8f), Offset(topLeft.x + r * 0.15f, topLeft.y + r * (0.3f + i * 0.3f)), Offset(topLeft.x + r * (0.5f + 0.6f * ((twinkle + i * 0.3f) % 1f)), topLeft.y + r * (0.3f + i * 0.3f)), strokeWidth = r * 0.08f, cap = round) }
    }
    // Riga che scorre sullo schermo, come nei vecchi televisori.
    val scan = topLeft.y + screenSize.height * twinkle
    drawLine(Color.White.copy(alpha = 0.25f), Offset(topLeft.x, scan), Offset(topLeft.x + screenSize.width, scan), strokeWidth = r * 0.05f)
    drawCircle(colors.bolt, radius = r * 0.1f, center = Offset(center.x + r * 0.74f, center.y - r * 0.3f))
    drawCircle(colors.bolt, radius = r * 0.1f, center = Offset(center.x + r * 0.74f, center.y + r * 0.05f))
}

/** Telecomando in mano, con le onde che partono verso la TV. */
private fun DrawScope.drawRemote(colors: RicordellaColors, hand: Offset, w: Float, twinkle: Float) {
    rotate(-20f, hand) {
        drawRoundRect(Ink, Offset(hand.x - w * 0.01f, hand.y - w * 0.035f), Size(w * 0.11f, w * 0.045f), CornerRadius(w * 0.015f))
        drawCircle(Red, radius = w * 0.009f, center = Offset(hand.x + w * 0.085f, hand.y - w * 0.013f))
    }
    drawCircle(Skin, radius = w * 0.04f, center = hand)
    repeat(2) { i ->
        val t = (twinkle + i / 2f) % 1f
        val r = w * (0.04f + 0.08f * t)
        drawArc(colors.bolt.copy(alpha = 1f - t), -60f, 70f, false, Offset(hand.x + w * 0.1f - r, hand.y - w * 0.04f - r), Size(r * 2, r * 2), style = Stroke(w * 0.012f, cap = StrokeCap.Round))
    }
}

/** Telefono all'orecchio e un fumetto con i puntini che si accendono uno dopo l'altro. */
private fun DrawScope.drawPhoneCall(colors: RicordellaColors, hand: Offset, head: Offset, w: Float, twinkle: Float) {
    rotate(12f, hand) {
        drawRoundRect(Ink, Offset(hand.x - w * 0.03f, hand.y - w * 0.09f), Size(w * 0.06f, w * 0.12f), CornerRadius(w * 0.015f))
        drawRoundRect(colors.cyan.solid, Offset(hand.x - w * 0.02f, hand.y - w * 0.078f), Size(w * 0.04f, w * 0.08f), CornerRadius(w * 0.008f))
    }
    drawCircle(Skin, radius = w * 0.04f, center = hand)
    val bubble = Offset(head.x + w * 0.2f, head.y - w * 0.33f)
    val bw = w * 0.3f
    val bh = w * 0.16f
    drawPath(Path().apply {
        moveTo(bubble.x + bw * 0.15f, bubble.y + bh * 0.9f)
        lineTo(bubble.x + bw * 0.05f, bubble.y + bh * 1.35f)
        lineTo(bubble.x + bw * 0.4f, bubble.y + bh * 0.9f)
        close()
    }, Color.White)
    drawRoundRect(Color.White, bubble, Size(bw, bh), CornerRadius(bh / 2))
    drawRoundRect(Ink.copy(alpha = 0.15f), bubble, Size(bw, bh), CornerRadius(bh / 2), style = Stroke(w * 0.008f))
    val active = (twinkle * 3).toInt()
    repeat(3) { i ->
        val c = Offset(bubble.x + bw * (0.25f + i * 0.25f), bubble.y + bh / 2 - if (i == active) bh * 0.1f else 0f)
        drawCircle((if (i == active) colors.coral.solid else Ink.copy(alpha = 0.35f)), radius = w * 0.022f, center = c)
    }
}

/** Libro aperto: ogni tanto una pagina si gira. */
private fun DrawScope.drawBook(colors: RicordellaColors, spine: Offset, pageW: Float, twinkle: Float, cycle: Float) {
    val pageH = pageW * 1.1f
    val top = spine.y - pageH / 2
    drawRoundRect(colors.coral.solid, Offset(spine.x - pageW * 1.08f, top - pageW * 0.06f), Size(pageW * 2.16f, pageH + pageW * 0.14f), CornerRadius(pageW * 0.08f))
    fun page(left: Boolean, width: Float, shade: Color) = drawPath(Path().apply {
        val dir = if (left) -1f else 1f
        moveTo(spine.x, top + pageW * 0.08f)
        quadraticTo(spine.x + dir * width * 0.5f, top - pageW * 0.04f, spine.x + dir * width, top)
        lineTo(spine.x + dir * width, top + pageH)
        quadraticTo(spine.x + dir * width * 0.5f, top + pageH - pageW * 0.12f, spine.x, top + pageH + pageW * 0.08f)
        close()
    }, shade)
    page(true, pageW, Color(0xFFFBF6E6))
    page(false, pageW, Color(0xFFF1E8CF))
    repeat(4) { i ->
        val y = top + pageH * (0.25f + i * 0.17f)
        drawLine(Ink.copy(alpha = 0.25f), Offset(spine.x - pageW * 0.8f, y), Offset(spine.x - pageW * 0.15f, y), strokeWidth = pageW * 0.05f, cap = StrokeCap.Round)
        drawLine(Ink.copy(alpha = 0.25f), Offset(spine.x + pageW * 0.15f, y), Offset(spine.x + pageW * 0.8f, y), strokeWidth = pageW * 0.05f, cap = StrokeCap.Round)
    }
    // Pagina che si gira nell'ultimo quarto del giro lento.
    val t = ((cycle * 2f % 1f) - 0.75f) / 0.25f
    if (t in 0f..1f) {
        val x = cos(t * PI.toFloat())
        page(x < 0f, pageW * kotlin.math.abs(x), Color.White)
    }
    drawFourPointStar(Offset(spine.x + pageW * 0.7f, top - pageW * 0.3f), pageW * 0.15f * (0.5f + 0.5f * sin(twinkle * 2 * PI.toFloat())), colors.bolt)
}

/** Lente d'ingrandimento che spazia avanti e indietro, con un riflesso sul vetro. */
private fun DrawScope.drawMagnifier(colors: RicordellaColors, hand: Offset, rad: Float, w: Float, twinkle: Float) {
    val r = w * 0.11f
    val lens = Offset(hand.x + cos(rad) * w * 0.24f, hand.y + sin(rad) * w * 0.24f - w * 0.04f)
    val toHand = hand - lens
    drawLine(Wood, hand, lens + toHand * (r / toHand.getDistance()), strokeWidth = w * 0.03f, cap = StrokeCap.Round)
    drawCircle(colors.cyan.solid.copy(alpha = 0.3f), radius = r, center = lens)
    drawArc(Color.White.copy(alpha = 0.8f), 200f, 60f, false, Offset(lens.x - r * 0.65f, lens.y - r * 0.65f), Size(r * 1.3f, r * 1.3f), style = Stroke(w * 0.015f, cap = StrokeCap.Round))
    drawCircle(Metal, radius = r, center = lens, style = Stroke(w * 0.025f))
    drawCircle(Skin, radius = w * 0.04f, center = hand)
    // Ogni tanto un luccichio: forse ha trovato qualcosa.
    if (twinkle > 0.7f) drawFourPointStar(Offset(lens.x + r * 0.9f, lens.y - r * 0.9f), w * 0.035f * sin((twinkle - 0.7f) / 0.3f * PI.toFloat()), colors.bolt)
}

/** Campanella a mano appoggiata a terra: quando [ring] va da 0 a 1 dondola e fa uscire le onde del suono. */
private fun DrawScope.drawHandBell(colors: RicordellaColors, base: Offset, w: Float, ring: Float) {
    val ringing = ring in 0f..1f
    val swing = if (ringing) sin(ring * 6 * PI.toFloat()) * 28f * (1f - ring) else 0f
    val top = Offset(base.x, base.y - w * 0.3f)
    drawOval(Ink.copy(alpha = 0.10f), Offset(base.x - w * 0.12f, base.y - w * 0.01f), Size(w * 0.24f, w * 0.04f))
    rotate(swing, Offset(base.x, base.y)) {
        drawRoundRect(Wood, Offset(top.x - w * 0.025f, top.y - w * 0.02f), Size(w * 0.05f, w * 0.13f), CornerRadius(w * 0.02f))
        drawCircle(Wood, radius = w * 0.035f, center = top)
        drawCircle(Ink.copy(alpha = 0.6f), radius = w * 0.022f, center = Offset(base.x + swing / 28f * w * 0.03f, base.y - w * 0.005f))
        drawPath(Path().apply {
            moveTo(base.x - w * 0.12f, base.y)
            quadraticTo(base.x - w * 0.1f, base.y - w * 0.18f, base.x, base.y - w * 0.19f)
            quadraticTo(base.x + w * 0.1f, base.y - w * 0.18f, base.x + w * 0.12f, base.y)
            close()
        }, colors.bolt)
        drawRoundRect(Color(0xFFC99A1E), Offset(base.x - w * 0.13f, base.y - w * 0.02f), Size(w * 0.26f, w * 0.035f), CornerRadius(w * 0.015f))
    }
    if (ringing) repeat(2) { i ->
        val t = ((ring * 3f) + i * 0.5f) % 1f
        val r = w * (0.14f + 0.08f * t)
        val c = Offset(base.x, base.y - w * 0.1f)
        val tint = colors.coral.solid.copy(alpha = 1f - t)
        drawArc(tint, -40f, 60f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(w * 0.015f, cap = StrokeCap.Round))
        drawArc(tint, 160f, 60f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(w * 0.015f, cap = StrokeCap.Round))
    }
}

/** Oggetti che nascono dalla punta della bacchetta, uno dopo l'altro, e salgono svanendo. */
private fun DrawScope.drawConjured(colors: RicordellaColors, tip: Offset, w: Float, cycle: Float) {
    val slot = cycle * 4f
    val t = slot % 1f
    val grow = (t * 4f).coerceAtMost(1f)
    val alpha = 1f - ((t - 0.7f) / 0.3f).coerceIn(0f, 1f)
    val c = Offset(tip.x - w * 0.12f * t, tip.y - w * 0.06f - w * 0.22f * t)
    val s = w * 0.07f * grow
    drawCircle(colors.bolt.copy(alpha = 0.3f * alpha), radius = s * 1.6f, center = c)
    when (slot.toInt() % 4) {
        // Pacco regalo.
        0 -> {
            drawRect(colors.coral.solid.copy(alpha = alpha), Offset(c.x - s, c.y - s * 0.8f), Size(s * 2, s * 1.8f))
            drawRect(colors.bolt.copy(alpha = alpha), Offset(c.x - s * 0.2f, c.y - s * 0.8f), Size(s * 0.4f, s * 1.8f))
            drawCircle(colors.bolt.copy(alpha = alpha), radius = s * 0.35f, center = Offset(c.x, c.y - s * 0.9f))
        }
        // Palla colorata.
        1 -> {
            drawCircle(colors.cyan.solid.copy(alpha = alpha), radius = s, center = c)
            drawCircle(Color.White.copy(alpha = 0.6f * alpha), radius = s * 0.3f, center = Offset(c.x - s * 0.35f, c.y - s * 0.35f))
        }
        // Cuore.
        2 -> drawHeart(c, s * 0.9f, colors.coral.solid.copy(alpha = alpha))
        // Pozione.
        else -> {
            drawRect(Wood.copy(alpha = alpha), Offset(c.x - s * 0.2f, c.y - s * 1.2f), Size(s * 0.4f, s * 0.4f))
            drawCircle(colors.pear.solid.copy(alpha = alpha), radius = s * 0.85f, center = Offset(c.x, c.y + s * 0.1f))
        }
    }
    drawFourPointStar(Offset(c.x + s * 1.2f, c.y - s), s * 0.4f, Color.White.copy(alpha = alpha))
}

/** Oggetto che il mago tiene in mano al posto della bacchetta. */
enum class WizardProp { CHECKLIST, CALENDAR, UMBRELLA, FIRST_AID, BALLOON, HOURGLASS, CAKE, SHIELD, WRENCH, COINS, ARROWS, ALARM_CLOCK, BELL }

val ReminderType.wizardProp: WizardProp
    get() = when (this) {
        ReminderType.TASK -> WizardProp.CHECKLIST
        ReminderType.EVENT -> WizardProp.CALENDAR
        ReminderType.VACATION -> WizardProp.UMBRELLA
        ReminderType.MEDICAL_VISIT -> WizardProp.FIRST_AID
        ReminderType.HOLIDAY -> WizardProp.BALLOON
        ReminderType.DEADLINE -> WizardProp.HOURGLASS
        ReminderType.BIRTHDAY -> WizardProp.CAKE
        ReminderType.WARRANTY -> WizardProp.SHIELD
        ReminderType.MAINTENANCE -> WizardProp.WRENCH
        ReminderType.PAYMENT -> WizardProp.COINS
        ReminderType.RENEWAL -> WizardProp.ARROWS
        ReminderType.ALARM -> WizardProp.ALARM_CLOCK
        ReminderType.OTHER -> WizardProp.BELL
    }

private val Metal = Color(0xFFB0B7C3)
private val Red = Color(0xFFE53935)

/** Disegna [prop] centrato in [at], largo circa 2 × [u]; [twinkle] (0..1) lo anima. */
private fun DrawScope.drawProp(prop: WizardProp, colors: RicordellaColors, at: Offset, u: Float, twinkle: Float) {
    val pulse = sin(twinkle * 2 * PI.toFloat())
    val round = StrokeCap.Round
    fun p(x: Float, y: Float) = Offset(at.x + x * u, at.y + y * u)
    fun box(color: Color, x: Float, y: Float, bw: Float, bh: Float) =
        drawRoundRect(color, p(x, y), Size(bw * u, bh * u), CornerRadius(u * 0.18f))
    fun tri(color: Color, a: Offset, b: Offset, c: Offset) =
        drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }, color)
    fun check(color: Color, c: Offset, s: Float) {
        drawLine(color, Offset(c.x - s, c.y), Offset(c.x - s * 0.3f, c.y + s * 0.7f), strokeWidth = s * 0.45f, cap = round)
        drawLine(color, Offset(c.x - s * 0.3f, c.y + s * 0.7f), Offset(c.x + s, c.y - s * 0.7f), strokeWidth = s * 0.45f, cap = round)
    }
    when (prop) {
        // Lista che si spunta una riga alla volta.
        WizardProp.CHECKLIST -> {
            box(Color.White, -0.85f, -1.1f, 1.7f, 2.2f)
            repeat(3) { i ->
                val y = -0.55f + i * 0.6f
                drawLine(Ink.copy(alpha = 0.35f), p(-0.05f, y), p(0.6f, y), strokeWidth = u * 0.14f, cap = round)
                if (twinkle * 4f > i + 1) check(colors.pear.solid, p(-0.45f, y - 0.05f), u * 0.22f)
                else drawCircle(Ink.copy(alpha = 0.25f), radius = u * 0.14f, center = p(-0.45f, y), style = Stroke(u * 0.08f))
            }
        }
        // Calendario con il giorno che lampeggia.
        WizardProp.CALENDAR -> {
            box(Color.White, -0.95f, -0.9f, 1.9f, 1.9f)
            box(colors.coral.solid, -0.95f, -0.9f, 1.9f, 0.55f)
            repeat(2) { i -> drawLine(Ink, p(-0.45f + i * 0.9f, -1.1f), p(-0.45f + i * 0.9f, -0.7f), strokeWidth = u * 0.14f, cap = round) }
            repeat(6) { i -> drawCircle(Ink.copy(alpha = 0.25f), radius = u * 0.1f, center = p(-0.5f + (i % 3) * 0.5f, 0.05f + (i / 3) * 0.5f)) }
            drawCircle(colors.cyan.solid, radius = u * (0.2f + 0.06f * pulse), center = p(0.5f, 0.55f))
        }
        // Ombrellone che ondeggia sotto il sole.
        WizardProp.UMBRELLA -> {
            drawCircle(colors.bolt, radius = u * (0.35f + 0.05f * pulse), center = p(1.1f, -1.5f))
            rotate(pulse * 8f, p(0f, 1.2f)) {
                drawLine(Wood, p(0f, -0.3f), p(0f, 1.2f), strokeWidth = u * 0.16f, cap = round)
                drawArc(colors.coral.solid, 180f, 180f, true, p(-1.2f, -1.5f), Size(2.4f * u, 2.4f * u))
                drawArc(Color.White, 240f, 60f, true, p(-1.2f, -1.5f), Size(2.4f * u, 2.4f * u))
            }
        }
        // Valigetta del pronto soccorso con la croce che batte come un cuore.
        WizardProp.FIRST_AID -> {
            drawLine(Ink, p(-0.35f, -0.85f), p(0.35f, -0.85f), strokeWidth = u * 0.16f, cap = round)
            box(Color.White, -1f, -0.7f, 2f, 1.5f)
            val s = 1f + 0.15f * maxOf(0f, pulse)
            box(Red, -0.15f * s, 0.05f - 0.5f * s, 0.3f * s, 1f * s)
            box(Red, -0.5f * s, 0.05f - 0.15f * s, 1f * s, 0.3f * s)
        }
        // Palloncino che fluttua tra i coriandoli.
        WizardProp.BALLOON -> {
            val top = p(0f, -1.3f + 0.2f * pulse)
            drawLine(Ink.copy(alpha = 0.5f), Offset(top.x, top.y + u * 0.75f), p(0f, 0.8f), strokeWidth = u * 0.06f)
            drawOval(colors.coral.solid, Offset(top.x - u * 0.6f, top.y - u * 0.75f), Size(1.2f * u, 1.5f * u))
            drawCircle(Color.White.copy(alpha = 0.6f), radius = u * 0.15f, center = Offset(top.x - u * 0.25f, top.y - u * 0.35f))
            listOf(colors.bolt, colors.cyan.solid, colors.lavender.solid).forEachIndexed { i, c ->
                val t = (twinkle + i / 3f) % 1f
                drawFourPointStar(p(-1.1f + i * 1.1f, -1.8f + t * 2.4f), u * 0.18f, c.copy(alpha = 1f - t), rotation = t * 180f)
            }
        }
        // Clessidra: la sabbia scende.
        WizardProp.HOURGLASS -> {
            val glass = Color.White.copy(alpha = 0.85f)
            tri(glass, p(-0.7f, -0.9f), p(0.7f, -0.9f), at)
            tri(glass, p(-0.7f, 0.9f), p(0.7f, 0.9f), at)
            val top = 1f - twinkle
            if (top > 0f) tri(colors.bolt, p(-0.55f * top, -0.8f * top), p(0.55f * top, -0.8f * top), at)
            tri(colors.bolt, p(-0.6f, 0.85f), p(0.6f, 0.85f), p(0f, 0.85f - 0.6f * twinkle))
            drawLine(colors.bolt, at, p(0f, 0.85f), strokeWidth = u * 0.06f)
            box(Wood, -0.9f, -1.1f, 1.8f, 0.25f)
            box(Wood, -0.9f, 0.85f, 1.8f, 0.25f)
        }
        // Torta con la candelina che tremola.
        WizardProp.CAKE -> {
            box(colors.coral.solid, -1f, -0.1f, 2f, 1.1f)
            box(Color.White, -1f, -0.35f, 2f, 0.4f)
            repeat(3) { i -> drawCircle(Color.White.copy(alpha = 0.6f), radius = u * 0.12f, center = p(-0.6f + i * 0.6f, 0.45f)) }
            box(colors.cyan.solid, -0.1f, -1.05f, 0.2f, 0.7f)
            val flicker = 1f + 0.2f * sin(twinkle * 6 * PI.toFloat())
            drawOval(colors.bolt, p(-0.15f * flicker, -1.55f), Size(0.3f * u * flicker, 0.5f * u))
        }
        // Scudo con la spunta e un luccichio.
        WizardProp.SHIELD -> {
            val shield = Path().apply {
                moveTo(p(-0.9f, -1f).x, p(0f, -1f).y)
                lineTo(p(0.9f, -1f).x, p(0f, -1f).y)
                lineTo(p(0.9f, 0f).x, at.y)
                quadraticTo(p(0.8f, 0f).x, p(0f, 0.9f).y, at.x, p(0f, 1.2f).y)
                quadraticTo(p(-0.8f, 0f).x, p(0f, 0.9f).y, p(-0.9f, 0f).x, at.y)
                close()
            }
            drawPath(shield, colors.cyan.solid)
            check(Color.White, p(0f, -0.05f), u * 0.4f)
            if (pulse > 0f) drawFourPointStar(p(0.9f, -1.1f), u * 0.3f * pulse, colors.bolt)
        }
        // Chiave inglese che stringe avanti e indietro.
        WizardProp.WRENCH -> rotate(pulse * 30f, at) {
            drawLine(Metal, p(0f, 1.1f), p(0f, -0.5f), strokeWidth = u * 0.35f, cap = round)
            drawArc(Metal, 120f, 300f, false, p(-0.5f, -1.4f), Size(u, u), style = Stroke(u * 0.3f))
        }
        // Pila di monete con una che gira in aria.
        WizardProp.COINS -> {
            repeat(3) { i ->
                drawOval(Color(0xFFC99A1E), p(-0.8f, 0.55f - i * 0.35f), Size(1.6f * u, 0.55f * u))
                drawOval(colors.bolt, p(-0.8f, 0.45f - i * 0.35f), Size(1.6f * u, 0.55f * u))
            }
            val flip = abs(cos(twinkle * 2 * PI.toFloat())).coerceAtLeast(0.08f)
            val y = -1.3f - 0.4f * sin(twinkle * PI.toFloat())
            drawOval(colors.bolt, p(-0.5f * flip, y - 0.5f), Size(flip * u, u))
        }
        // Frecce che girano in tondo.
        WizardProp.ARROWS -> rotate(twinkle * 360f, at) {
            val r = 0.8f * u
            listOf(20f, 200f).forEach { start ->
                drawArc(colors.pear.solid, start, 130f, false, Offset(at.x - r, at.y - r), Size(2 * r, 2 * r), style = Stroke(u * 0.3f, cap = round))
                val a = (start + 130f) * PI.toFloat() / 180f
                val tip = Offset(at.x + cos(a) * r, at.y + sin(a) * r)
                val dir = a + PI.toFloat() / 2
                tri(
                    colors.pear.solid,
                    Offset(tip.x + cos(dir) * u * 0.4f, tip.y + sin(dir) * u * 0.4f),
                    Offset(tip.x + cos(a) * u * 0.3f, tip.y + sin(a) * u * 0.3f),
                    Offset(tip.x - cos(a) * u * 0.3f, tip.y - sin(a) * u * 0.3f),
                )
            }
        }
        // Sveglia che trilla.
        WizardProp.ALARM_CLOCK -> rotate(sin(twinkle * 12 * PI.toFloat()) * 10f, at) {
            drawCircle(colors.bolt, radius = u * 0.35f, center = p(-0.65f, -0.8f))
            drawCircle(colors.bolt, radius = u * 0.35f, center = p(0.65f, -0.8f))
            drawCircle(colors.coral.solid, radius = u * 0.95f, center = at)
            drawCircle(Color.White, radius = u * 0.72f, center = at)
            drawLine(Ink, at, p(0f, -0.5f), strokeWidth = u * 0.12f, cap = round)
            drawLine(Ink, at, p(0.35f, 0.1f), strokeWidth = u * 0.12f, cap = round)
        }
        // Campanella che dondola.
        WizardProp.BELL -> rotate(pulse * 22f, p(0f, -1.2f)) {
            drawCircle(colors.bolt, radius = u * 0.2f, center = p(0.3f * pulse, 0.65f))
            drawPath(Path().apply {
                moveTo(p(-0.9f, 0f).x, p(0f, 0.5f).y)
                quadraticTo(p(-0.8f, 0f).x, p(0f, -1.1f).y, at.x, p(0f, -1.1f).y)
                quadraticTo(p(0.8f, 0f).x, p(0f, -1.1f).y, p(0.9f, 0f).x, p(0f, 0.5f).y)
                close()
            }, colors.bolt)
        }
    }
}


/** Testa del mago (viso, barba, occhi, sorriso, cappello) centrata in [head], che ruota attorno a [pivot] quando annuisce. */
internal fun DrawScope.drawWizardHead(colors: RicordellaColors, head: Offset, faceR: Float, pivot: Offset, nod: Float, twinkle: Float, love: Float = 0f) {
    rotate(nod * 4f, pivot) {
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
            if (love > 0f) {
                // Innamorato: occhi a cuore che battono.
                val beat = 1f + 0.12f * sin(twinkle * 4 * PI.toFloat())
                drawHeart(eye, faceR * 0.34f * love * beat, colors.coral.solid)
            } else {
                drawArc(
                    Ink,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(eye.x - faceR * 0.2f, eye.y - faceR * 0.12f),
                    size = Size(faceR * 0.4f, faceR * 0.32f),
                    style = Stroke(width = faceR * 0.12f, cap = StrokeCap.Round),
                )
            }
            drawCircle(Blush.copy(alpha = 0.7f + 0.3f * love), radius = faceR * (0.16f + 0.06f * love), center = Offset(head.x + side * faceR * 0.62f, eyeY + faceR * 0.32f))
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
fun WizardAvatar(seed: String, size: Dp, modifier: Modifier = Modifier, toneIndex: Int? = null) {
    val colors = MaterialTheme.ricordellaColors
    val tone = toneIndex?.let { listOf(colors.cyan, colors.lavender, colors.coral, colors.pear, colors.mint)[it % 5] } ?: toneFor(seed)
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

/** Cuore pieno centrato in [center], largo circa 2 × [size]. */
fun DrawScope.drawHeart(center: Offset, size: Float, color: Color) {
    if (size <= 0f) return
    val path = Path().apply {
        moveTo(center.x, center.y + size * 0.9f)
        cubicTo(center.x - size * 1.6f, center.y - size * 0.1f, center.x - size * 0.8f, center.y - size * 1.3f, center.x, center.y - size * 0.45f)
        cubicTo(center.x + size * 0.8f, center.y - size * 1.3f, center.x + size * 1.6f, center.y - size * 0.1f, center.x, center.y + size * 0.9f)
        close()
    }
    drawPath(path, color)
}
