package com.ricordella.app.feature.potions

import com.ricordella.app.core.i18n.Lang
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.FilterChipRow
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.RicordellaMotion
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.StarBurst
import com.ricordella.app.core.ui.TimePickerDialogFor
import com.ricordella.app.core.ui.UiSound
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.drawFourPointStar
import com.ricordella.app.core.ui.drawWizardHead
import com.ricordella.app.core.ui.rememberReducedMotion
import com.ricordella.app.core.ui.rememberUiSounds
import com.ricordella.app.core.ui.theme.RicordellaColors
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.PotionSettings
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.sin

private val EveryOptions = listOf(30, 45, 60, 90, 120, 180)

/**
 * Pozioni: l'acqua bevuta oggi, contata in pozioni. Il mago si riempie man mano; oltre l'obiettivo
 * gli si gonfia la pancia. Qui si scelgono anche obiettivo, dimensione della pozione e promemoria.
 */
@Composable
fun PotionsScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as RicordellaApplication).container
    val scope = rememberCoroutineScope()
    val app = LocalAppSettings.current
    val potions = app.potions
    val today = container.time.today().toEpochDay()
    val drank = potions.drankOn(today)
    val play = rememberUiSounds()
    var splash by remember { mutableIntStateOf(0) }
    fun update(transform: (PotionSettings) -> PotionSettings) = scope.launch {
        container.settingsRepository.update { it.copy(potions = transform(it.potions)) }
    }
    fun drink(ml: Int) = scope.launch { container.potionReminders.drink(ml) }

    DetailScaffold(title = tr("Pozioni"), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .contentWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            val colors = MaterialTheme.ricordellaColors
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.cyan.container, MaterialTheme.shapes.extraLarge)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PotionWizard(fill = drank.toFloat() / potions.goalMl.coerceAtLeast(1), splash = splash, size = 220.dp)
                Text(
                    trf("%1\$s di %2\$s", liters(drank), liters(potions.goalMl)),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.cyan.content,
                )
                val count = drank / potions.potionMl.coerceAtLeast(1)
                val total = (potions.goalMl + potions.potionMl - 1) / potions.potionMl.coerceAtLeast(1)
                Text(
                    when {
                        drank > potions.goalMl -> tr("Pancia piena! Hai superato l'obiettivo di oggi")
                        drank == potions.goalMl -> tr("Obiettivo raggiunto: il mago è pieno di magia ✨")
                        else -> trf("%1\$s pozioni su %2\$s · ne mancano %3\$s", count, total, (total - count).coerceAtLeast(1))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.cyan.content,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    Modifier.padding(top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = { drink(-potions.potionMl) },
                        enabled = drank > 0,
                    ) { Icon(Icons.Rounded.Remove, contentDescription = tr("Togli una pozione")) }
                    PushButton(
                        text = trf("Bevi %1\$s ml", potions.potionMl),
                        icon = Icons.Rounded.WaterDrop,
                        onClick = {
                            play(UiSound.POTION)
                            splash++
                            drink(potions.potionMl)
                        },
                    )
                }
            }

            SectionHeader(tr("Promemoria"), icon = Icons.Rounded.NotificationsActive, tone = MaterialTheme.ricordellaColors.cyan)
            ListItem(
                headlineContent = { Text(tr("Ricordamelo")) },
                supportingContent = {
                    Text(
                        // In demo le notifiche sono spente apposta: niente avviso negli screenshot.
                        if (app.notificationsEnabled || container.isDemo) tr("Una notifica che suona come una pozione. Arrivato all'obiettivo smette da sola.")
                        else tr("Le notifiche di Remindella sono spente nelle Impostazioni."),
                    )
                },
                trailingContent = { Switch(checked = potions.remindersOn, onCheckedChange = { on -> update { it.copy(remindersOn = on) } }) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            if (potions.remindersOn) {
                Text(tr("Ogni"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp))
                FilterChipRow(
                    options = EveryOptions,
                    selected = potions.everyMinutes,
                    label = ::everyLabel,
                    onSelected = { minutes -> update { it.copy(everyMinutes = minutes) } },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                TimeRow(tr("Dalle"), potions.from) { time -> update { it.copy(from = time, until = maxOf(it.until, time)) } }
                TimeRow(tr("Fino alle"), potions.until) { time -> update { it.copy(until = time, from = minOf(it.from, time)) } }
            }

            SectionHeader(tr("Obiettivo"), icon = Icons.Rounded.EmojiEvents, tone = MaterialTheme.ricordellaColors.mint)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MlField(tr("Al giorno"), potions.goalMl, 100..10_000, Modifier.weight(1f), hint = liters(potions.goalMl)) { ml ->
                    update { it.copy(goalMl = ml) }
                }
                MlField(tr("Una pozione"), potions.potionMl, 20..2_000, Modifier.weight(1f), hint = tr("un bicchiere ≈ 250 ml")) { ml ->
                    update { it.copy(potionMl = ml) }
                }
            }
        }
    }
}

@Composable
private fun TimeRow(label: String, value: LocalTime, onChange: (LocalTime) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
        trailingContent = { Text(DateTexts.time(value), style = MaterialTheme.typography.titleMedium) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { picking = true },
    )
    if (picking) TimePickerDialogFor(initial = value, onDismiss = { picking = false }, onConfirm = { onChange(it); picking = false })
}

/** Campo in millilitri: salva solo valori dentro [range], così si può scrivere con calma. */
@Composable
private fun MlField(label: String, value: Int, range: IntRange, modifier: Modifier, hint: String, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in range
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter(Char::isDigit).take(5)
            text.toIntOrNull()?.takeIf { it in range }?.let(onChange)
        },
        label = { Text(label) },
        suffix = { Text("ml") },
        supportingText = { Text(if (valid) hint else trf("Da %1\$s a %2\$s ml", range.first, range.last)) },
        isError = !valid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

private fun everyLabel(minutes: Int): String = when {
    minutes < 60 -> trf("%1\$s min", minutes)
    minutes % 60 == 0 -> trf("%1\$s h", minutes / 60)
    else -> trf("%1\$s h %2\$s", minutes / 60, minutes % 60)
}

/** 1250 → "1,25 L", 2000 → "2 L" (separatore della lingua dell'app). */
internal fun liters(ml: Int): String = DecimalFormat("0.##", DecimalFormatSymbols(Lang.locale)).format(ml / 1000.0) + " L"

private val Skin = Color(0xFFFFD7B5)
private val Wood = Color(0xFF8A5A3B)

/**
 * Il mago-pozione: la tunica è la boccetta e si riempie d'acqua fino a [fill] (1 = obiettivo).
 * Oltre l'obiettivo la pancia si gonfia (al massimo a 1,5 volte). Ogni cambio di [splash] fa sprizzare stelline.
 */
@Composable
fun PotionWizard(fill: Float, splash: Int, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val colors = MaterialTheme.ricordellaColors
    val reduced = rememberReducedMotion()
    val level by animateFloatAsState(fill.coerceIn(0f, 1.5f), spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessVeryLow), label = "potionLevel")
    val loop = rememberInfiniteTransition(label = "potionWizard")
    val wave = if (reduced) 0f else loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "wave").value
    val nod = if (reduced) 0f else loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(1600, easing = RicordellaMotion.EaseInOut), RepeatMode.Reverse), label = "nod").value
    val twinkle = if (reduced) 0.5f else loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "twinkle").value
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(size)
                .semantics { contentDescription = trf("Il mago è pieno al %1\$s%%", (fill * 100).toInt()) },
        ) {
            drawPotionWizard(colors, level, wave, nod, twinkle)
        }
        StarBurst(trigger = splash, colors = listOf(colors.cyan.solid, colors.bolt, Color.White), modifier = Modifier.requiredSize(size * 1.2f))
    }
}

private fun DrawScope.drawPotionWizard(colors: RicordellaColors, fill: Float, wave: Float, nod: Float, twinkle: Float) {
    val w = size.width
    val h = size.height
    val cx = w * 0.46f
    val belly = ((fill - 1f) / 0.5f).coerceIn(0f, 1f)
    val top = h * 0.5f
    val bottom = h * 0.93f
    val topHalf = w * 0.12f
    val bottomHalf = w * 0.26f + belly * w * 0.03f
    val bulge = belly * w * 0.17f

    drawOval(Color.Black.copy(alpha = 0.1f), topLeft = Offset(cx - bottomHalf - bulge * 0.3f, h * 0.9f), size = Size((bottomHalf + bulge * 0.3f) * 2, h * 0.07f))

    // Tunica-boccetta: i fianchi sono curve che, oltre l'obiettivo, si allargano come una pancia piena.
    val robe = Path().apply {
        moveTo(cx - topHalf, top)
        lineTo(cx + topHalf, top)
        cubicTo(cx + topHalf + (bottomHalf - topHalf) * 0.33f + bulge, top + (bottom - top) * 0.33f, cx + topHalf + (bottomHalf - topHalf) * 0.66f + bulge, top + (bottom - top) * 0.7f, cx + bottomHalf, bottom)
        lineTo(cx - bottomHalf, bottom)
        cubicTo(cx - topHalf - (bottomHalf - topHalf) * 0.66f - bulge, top + (bottom - top) * 0.7f, cx - topHalf - (bottomHalf - topHalf) * 0.33f - bulge, top + (bottom - top) * 0.33f, cx - topHalf, top)
        close()
    }
    drawPath(robe, colors.lavender.solid)
    clipPath(robe) {
        // Acqua con l'onda che scorre e bollicine che salgono.
        val full = fill.coerceIn(0f, 1f)
        val level = bottom - (bottom - top + h * 0.02f) * full
        val amp = if (full in 0.02f..0.98f) h * 0.012f else h * 0.004f
        val left = cx - bottomHalf - bulge
        val right = cx + bottomHalf + bulge
        val water = Path().apply {
            moveTo(left, bottom + h)
            lineTo(left, level)
            var x = left
            while (x <= right) {
                lineTo(x, level + sin((x / w * 3f + wave) * 2f * PI.toFloat()) * amp)
                x += w * 0.02f
            }
            lineTo(right, bottom + h)
            close()
        }
        if (full > 0f) {
            drawPath(water, colors.cyan.solid)
            drawLine(Color.White.copy(alpha = 0.35f), Offset(left, level + amp * 2.5f), Offset(right, level + amp * 2.5f), strokeWidth = h * 0.008f)
            repeat(6) { i ->
                val t = (wave * 1.5f + i / 6f) % 1f
                val bx = cx + (i - 2.5f) * w * 0.06f + sin((t + i) * 6f) * w * 0.01f
                val by = bottom - (bottom - level) * t
                drawCircle(Color.White.copy(alpha = 0.5f * (1f - t)), radius = w * (0.008f + 0.004f * (i % 2)), center = Offset(bx, by))
            }
        }
        drawRect(colors.bolt, topLeft = Offset(left, bottom - h * 0.05f), size = Size(right - left, h * 0.05f))
    }

    // Braccio sinistro: con la pancia piena si appoggia sulla pancia.
    val leftShoulder = Offset(cx - topHalf * 0.8f, h * 0.58f)
    val leftHand = Offset(cx - topHalf - w * 0.07f - bulge * 0.55f + belly * w * 0.1f, h * 0.72f)
    drawLine(colors.lavender.solid, leftShoulder, leftHand, strokeWidth = w * 0.07f, cap = StrokeCap.Round)
    drawCircle(Skin, radius = w * 0.04f, center = leftHand)

    // Braccio destro: alza la boccetta per un brindisi.
    val shoulder = Offset(cx + topHalf * 0.8f, h * 0.58f)
    rotate(-50f + nod * 6f, shoulder) {
        val hand = Offset(shoulder.x + w * 0.22f, shoulder.y)
        drawLine(colors.lavender.solid, shoulder, Offset(hand.x - w * 0.02f, hand.y), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
        rotate(50f - nod * 6f, hand) { drawFlask(colors, Offset(hand.x, hand.y - w * 0.075f), w * 0.06f, wave) }
        drawCircle(Skin, radius = w * 0.04f, center = hand)
    }

    drawWizardHead(colors, Offset(cx, h * 0.4f + nod * h * 0.01f), w * 0.12f, Offset(cx, h * 0.5f), nod, twinkle)
}

/** Boccetta tonda con tappo di sughero e pozione azzurra che brilla. */
private fun DrawScope.drawFlask(colors: RicordellaColors, center: Offset, r: Float, wave: Float) {
    drawRect(Color.White.copy(alpha = 0.55f), topLeft = Offset(center.x - r * 0.3f, center.y - r * 1.55f), size = Size(r * 0.6f, r * 0.8f))
    drawRect(Wood, topLeft = Offset(center.x - r * 0.38f, center.y - r * 1.85f), size = Size(r * 0.76f, r * 0.4f))
    drawCircle(Color.White.copy(alpha = 0.55f), radius = r, center = center)
    clipPath(Path().apply { addOval(Rect(center, r * 0.86f)) }) {
        drawRect(colors.cyan.solid, topLeft = Offset(center.x - r, center.y - r * 0.15f + sin(wave * 2 * PI.toFloat()) * r * 0.06f), size = Size(r * 2, r * 2))
    }
    drawCircle(Color.White, radius = r, center = center, style = Stroke(width = r * 0.14f))
    drawFourPointStar(Offset(center.x + r * 0.9f, center.y - r * 1.3f), r * (0.35f + 0.15f * sin(wave * 4 * PI.toFloat())), colors.bolt)
}
