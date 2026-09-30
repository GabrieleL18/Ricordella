package com.ricordella.app.feature.settings

import com.ricordella.app.core.i18n.tr

import android.content.Intent
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ricordella.app.core.ui.HappyWizard
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.RicordellaMotion
import com.ricordella.app.core.ui.drawFourPointStar
import com.ricordella.app.core.ui.drawHeart
import com.ricordella.app.core.ui.rememberReducedMotion
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import kotlin.math.PI
import kotlin.math.sin

/** Link PayPal.me per le donazioni. */
private const val PAYPAL_URL = "https://paypal.me/gglanni"

/**
 * Riquadro "Grazie per il supporto": cielo magico che si muove piano, stelline che brillano, una
 * stella cadente ogni tanto, un riflesso di luce che attraversa il riquadro e il pulsante PayPal che
 * batte come un cuore. Tornando in Remindella dopo aver aperto PayPal il maghetto si innamora
 * (e resta innamorato: [loved]). L'app non può sapere se il pagamento è andato a buon fine.
 */
@Composable
internal fun SupportCard(loved: Boolean, onLoved: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.ricordellaColors
    val context = LocalContext.current
    val reduced = rememberReducedMotion()

    // Aperto PayPal: al ritorno nell'app scatta l'innamoramento.
    var wentToPayPal by rememberSaveable { mutableStateOf(false) }
    var hearts by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(wentToPayPal) {
        if (wentToPayPal) {
            wentToPayPal = false
            hearts++
            onLoved()
        }
        onPauseOrDispose { }
    }
    val love = remember { Animatable(if (loved) 1f else 0f) }
    LaunchedEffect(loved) { if (loved) love.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow)) }
    val burst = remember { Animatable(1f) }
    LaunchedEffect(hearts) {
        if (hearts > 0 && !reduced) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(2600, easing = LinearEasing))
        }
    }

    val entrance = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) }

    val loop = rememberInfiniteTransition(label = "support")
    val twinkle = if (reduced) 0.5f else loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "twinkle").value
    val drift = if (reduced) 0f else loop.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = RicordellaMotion.EaseInOut), RepeatMode.Reverse), label = "drift").value
    val shine = if (reduced) -1f else loop.animateFloat(-0.6f, 1.8f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "shine").value
    val meteor = if (reduced) -1f else loop.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "meteor").value
    // Battito del pulsante: due colpi veloci e una pausa, come un cuore.
    val beat = if (reduced) 1f else loop.animateFloat(
        1f,
        1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 1800
                1f at 0
                1.05f at 120
                1f at 260
                1.04f at 380
                1f at 560
            },
        ),
        label = "beat",
    ).value

    Box(
        modifier
            .fillMaxWidth()
            .padding(top = RicordellaDimensions.spaceXl)
            .graphicsLayer {
                alpha = entrance.value
                scaleX = 0.92f + 0.08f * entrance.value
                scaleY = 0.92f + 0.08f * entrance.value
            }
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF3B1F73), Color(0xFF6A3FB8), Color(0xFF2E7FBF)),
                    start = Offset(drift * 400f, 0f),
                    end = Offset(900f + drift * 400f, 700f),
                ),
            ),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            // Stelline sparse che si accendono e si spengono, ognuna col suo ritmo.
            val random = java.util.Random(11)
            repeat(18) {
                val star = Offset(random.nextFloat() * w, random.nextFloat() * h)
                val phase = random.nextFloat()
                val glow = (sin((twinkle + phase) * 2 * PI).toFloat() + 1f) / 2f
                drawFourPointStar(star, size.minDimension * (0.012f + 0.02f * glow) * entrance.value, Color.White.copy(alpha = 0.3f + 0.6f * glow), rotation = glow * 45f)
            }
            // Stella cadente: attraversa l'alto del riquadro nel primo terzo del giro, poi pausa.
            if (meteor in 0f..0.3f) {
                val t = meteor / 0.3f
                val head = Offset(w * (1.1f - 1.3f * t), h * (0.05f + 0.35f * t))
                val tail = Offset(head.x + w * 0.18f, head.y - h * 0.12f)
                drawLine(Brush.linearGradient(listOf(Color.White, Color.Transparent), start = head, end = tail), head, tail, strokeWidth = 4f, cap = StrokeCap.Round)
                drawFourPointStar(head, 10f, Color.White)
            }
            // Riflesso di luce in diagonale.
            if (shine > -0.6f) {
                val x = w * shine
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent),
                        start = Offset(x - w * 0.25f, 0f),
                        end = Offset(x + w * 0.05f, h),
                    ),
                )
            }
            // Cuori che salgono dal maghetto dopo la donazione (e qualcuno ogni tanto finché è innamorato).
            val wizard = Offset(w - 70.dp.toPx(), 70.dp.toPx())
            if (burst.value < 1f) {
                repeat(9) { i ->
                    val t = (burst.value * 1.4f - i * 0.05f).coerceIn(0f, 1f)
                    if (t > 0f && t < 1f) {
                        val x = wizard.x + sin((t * 3 + i) * 2f) * 40f + (i - 4) * 14f
                        drawHeart(Offset(x, wizard.y - t * h * 0.7f), 14f + (i % 3) * 5f, listOf(colors.coral.solid, Color(0xFFFF8FB1), colors.bolt)[i % 3].copy(alpha = 1f - t))
                    }
                }
            } else if (love.value > 0f && !reduced) {
                repeat(2) { i ->
                    val t = (twinkle + i * 0.5f) % 1f
                    drawHeart(Offset(wizard.x + (i * 2 - 1) * 30f + sin(t * 6f) * 10f, wizard.y - 20f - t * 90f), 10f, Color(0xFFFF8FB1).copy(alpha = 1f - t))
                }
            }
        }
        Column(Modifier.padding(RicordellaDimensions.spaceL)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(loved, transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(300)) }, modifier = Modifier.weight(1f), label = "thanks") { isLoved ->
                    Column {
                        Text(
                            if (isLoved) tr("Grazie di cuore!") else tr("Grazie per il supporto"),
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.bolt,
                        )
                        Text(
                            if (isLoved) tr("Il maghetto si è innamorato di te. Ogni pozione aiuta Remindella a crescere.")
                            else tr("Remindella è gratuita e senza pubblicità. Se ti fa comodo, puoi offrirmi una pozione (o un caffè) con una piccola donazione."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                HappyWizard(size = 96.dp, love = love.value)
            }
            PushButton(
                text = tr("Dona con PayPal"),
                icon = Icons.Rounded.Favorite,
                onClick = {
                    val opened = runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, PAYPAL_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.isSuccess
                    if (opened) wentToPayPal = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = RicordellaDimensions.spaceM)
                    .graphicsLayer {
                        scaleX = beat
                        scaleY = beat
                    },
            )
        }
    }
}
