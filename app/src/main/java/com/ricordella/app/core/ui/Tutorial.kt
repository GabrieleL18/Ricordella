package com.ricordella.app.core.ui

import com.ricordella.app.core.i18n.tr

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.ItemGroup
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** Una pagina di tutorial: testo e un'illustrazione animata in loop. */
class TutorialPage(
    val title: String,
    val body: String,
    val illustration: @Composable () -> Unit,
)

/**
 * Pagine scorrevoli con illustrazioni animate, indicatore a pallini e pulsanti
 * "Salta" / "Avanti". L'ultima pagina chiude con [doneLabel].
 */
@Composable
fun TutorialPager(
    pages: List<TutorialPage>,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    doneLabel: String = tr("Ho capito"),
) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text(if (last) " " else tr("Salta")) }
        }
        HorizontalPager(pager, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            val parallax = Modifier.graphicsLayer {
                // Parallasse leggera: il contenuto scorre un po' più lento della pagina.
                val offset = (pager.currentPage - index) + pager.currentPageOffsetFraction
                translationX = offset * size.width * 0.25f
                alpha = 1f - kotlin.math.abs(offset).coerceIn(0f, 1f) * 0.6f
            }
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = RicordellaDimensions.spaceXl).then(parallax)) {
                // Illustrazione rimpicciolita se lo spazio in altezza non basta (telefono in orizzontale).
                val landscape = maxWidth > maxHeight
                val illustrationScale = (maxHeight / (if (landscape) 280.dp else 520.dp)).coerceIn(0.5f, 1f)
                val illustration: @Composable () -> Unit = {
                    Box(
                        Modifier.size(260.dp * illustrationScale).graphicsLayer { scaleX = illustrationScale; scaleY = illustrationScale },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.requiredSize(260.dp), contentAlignment = Alignment.Center) { page.illustration() } }
                }
                val texts: @Composable (Modifier) -> Unit = { textModifier ->
                    Column(textModifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(page.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(RicordellaDimensions.spaceS))
                        Text(
                            page.body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (landscape) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { illustration() }
                        texts(Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        illustration()
                        Spacer(Modifier.height(RicordellaDimensions.spaceXl))
                        texts(Modifier)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(RicordellaDimensions.spaceXl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageDots(pages.size, pager.currentPage, Modifier.weight(1f))
            PushButton(
                text = if (last) doneLabel else tr("Avanti"),
                icon = if (last) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            )
        }
    }
}

/** Tutorial a tutto schermo sopra la schermata corrente. */
@Composable
fun TutorialDialog(pages: List<TutorialPage>, onDismiss: () -> Unit, doneLabel: String = tr("Ho capito")) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            TutorialPager(pages, onDone = onDismiss, doneLabel = doneLabel)
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.ricordellaColors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 24.dp else 8.dp, tween(RicordellaMotion.SHORT), label = "dot")
            val color by animateColorAsState(if (i == current) colors.boltEdge else MaterialTheme.colorScheme.outlineVariant, label = "dotColor")
            Box(Modifier.size(width, 8.dp).background(color, CircleShape))
        }
    }
}

/* ---------- Motore delle illustrazioni ---------- */

/** Tempo 0..1 che scorre in loop; con animazioni ridotte resta fermo su un fotogramma leggibile. */
@Composable
private fun loopTime(durationMillis: Int): Float {
    if (rememberReducedMotion()) return 0.6f
    return rememberInfiniteTransition(label = "tutorial").animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "t",
    ).value
}

/** Avanzamento 0..1 di una fase che va da [start] a [end] del loop, con easing d'uscita. */
private fun Float.phase(start: Float, end: Float): Float =
    RicordellaMotion.EaseOut.transform(((this - start) / (end - start)).coerceIn(0f, 1f))

@Composable
private fun PhoneFrame(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .size(150.dp, 240.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(4.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(26.dp))
            .padding(10.dp),
        content = content,
    )
}

@Composable
private fun Bar(width: Dp, color: Color = MaterialTheme.colorScheme.surfaceContainerHighest, height: Dp = 6.dp) {
    Box(Modifier.size(width, height).background(color, CircleShape))
}

@Composable
private fun MiniCard(tone: Tone, modifier: Modifier = Modifier, checked: Float = 0f) {
    Row(
        modifier
            .fillMaxWidth()
            .background(tone.container, RoundedCornerShape(10.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(16.dp)
                .border(2.dp, tone.solid, CircleShape)
                .background(tone.solid.copy(alpha = checked), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked > 0.5f) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Bar(54.dp, tone.solid.copy(alpha = 0.6f))
            Bar(34.dp)
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, tone: Tone, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/* ---------- Illustrazioni delle sezioni ---------- */

/** Home: le card entrano a cascata, una viene completata e scivola via; la saetta festeggia. */
@Composable
fun HomeIllustration() {
    val t = loopTime(4200)
    val c = MaterialTheme.ricordellaColors
    val tones = listOf(c.coral, c.pear, c.lavender)
    PhoneFrame {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Bar(50.dp, height = 8.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Bar(36.dp)
                }
                CrystalBallMascot(size = 40.dp, interactive = false)
            }
            tones.forEachIndexed { i, tone ->
                val appear = t.phase(0.05f + i * 0.08f, 0.3f + i * 0.08f)
                val done = if (i == 0) t.phase(0.55f, 0.65f) else 0f
                val leave = if (i == 0) t.phase(0.7f, 0.85f) else 0f
                MiniCard(
                    tone,
                    checked = done,
                    modifier = Modifier.graphicsLayer {
                        alpha = appear * (1f - leave)
                        translationY = (1f - appear) * 40f
                        translationX = leave * 220f
                    },
                )
            }
        }
    }
}

/** Calendario: il mese si disegna, i giorni con promemoria "accendono" un puntino, la selezione salta. */
@Composable
fun CalendarIllustration() {
    val t = loopTime(3600)
    val c = MaterialTheme.ricordellaColors
    val busy = setOf(3, 9, 12, 18, 24, 29)
    val selected = listOf(9, 18, 24)[(t * 3).toInt().coerceIn(0, 2)]
    PhoneFrame {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Bar(60.dp, height = 8.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            repeat(5) { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(7) { d ->
                        val day = week * 7 + d
                        val appear = t.phase(day * 0.006f, day * 0.006f + 0.12f)
                        val isSelected = day == selected
                        Box(
                            Modifier
                                .size(15.dp)
                                .graphicsLayer { scaleX = appear; scaleY = appear }
                                .background(if (isSelected) c.bolt else MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(5.dp)),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (day in busy) Box(Modifier.padding(bottom = 2.dp).size(4.dp).background(c.lavender.solid, CircleShape))
                        }
                    }
                }
            }
            MiniCard(c.lavender, Modifier.graphicsLayer { alpha = 0.4f + 0.6f * (sin(t * 6 * PI).toFloat() * 0.5f + 0.5f) })
        }
    }
}

/** Promemoria: la campanella suona, la notifica scende e la lista si riordina. */
@Composable
fun RemindersIllustration() {
    val t = loopTime(3000)
    val c = MaterialTheme.ricordellaColors
    val ring = if (t < 0.35f) sin(t * 60f).toFloat() * 18f * (1f - t / 0.35f) else 0f
    val drop = t.phase(0.3f, 0.5f)
    PhoneFrame {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = (drop - 1f) * 80f; alpha = drop }
                    .background(c.bolt, RoundedCornerShape(12.dp))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.Notifications, null, tint = c.onBolt, modifier = Modifier.size(16.dp))
                Bar(70.dp, c.onBolt.copy(alpha = 0.5f))
            }
            listOf(c.cyan, c.coral, c.mint, c.lavender).forEachIndexed { i, tone ->
                MiniCard(tone, Modifier.graphicsLayer { translationX = sin((t + i * 0.15f) * 2 * PI).toFloat() * 3f })
            }
        }
        Icon(
            Icons.Rounded.Notifications,
            null,
            tint = c.pear.solid,
            modifier = Modifier.align(Alignment.BottomEnd).size(36.dp).graphicsLayer { rotationZ = ring },
        )
    }
}

/** Cose: prima si sceglie la categoria, poi la cosa; la scelta si ingrandisce. */
@Composable
fun ItemsIllustration() {
    val t = loopTime(4000)
    val pick = t.phase(0.2f, 0.35f)
    val things = t.phase(0.45f, 0.65f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ItemGroup.entries.forEachIndexed { i, group ->
                val chosen = i == 0
                RoundIcon(
                    group.icon,
                    group.tone,
                    size = 44.dp,
                    modifier = Modifier.graphicsLayer {
                        val s = if (chosen) 1f + 0.25f * pick else 1f - 0.2f * pick
                        scaleX = s
                        scaleY = s
                        alpha = if (chosen) 1f else 1f - 0.5f * pick
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(com.ricordella.app.domain.model.ItemKind.CAR, com.ricordella.app.domain.model.ItemKind.MOTORBIKE, com.ricordella.app.domain.model.ItemKind.SCOOTER)
                .forEachIndexed { i, kind ->
                    val a = things.phase(i * 0.2f, 0.6f + i * 0.2f)
                    Surface(
                        color = ItemGroup.VEHICLES.tone.container,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 30f },
                    ) {
                        Icon(kind.icon, null, tint = ItemGroup.VEHICLES.tone.content, modifier = Modifier.padding(14.dp).size(30.dp))
                    }
                }
        }
    }
}

/** Persone: gli avatar arrivano e si collegano a un promemoria. */
@Composable
fun PeopleIllustration() {
    val t = loopTime(3600)
    val c = MaterialTheme.ricordellaColors
    val tones = listOf(c.coral, c.cyan, c.mint, c.lavender)
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        MiniCard(c.pear, Modifier.width(120.dp).graphicsLayer { scaleX = 0.8f + 0.2f * t.phase(0.5f, 0.7f); scaleY = scaleX })
        tones.forEachIndexed { i, tone ->
            val a = t.phase(i * 0.1f, 0.3f + i * 0.1f)
            val angle = (i / tones.size.toFloat() + 0.125f) * 2 * PI
            val bob = sin((t * 2 + i * 0.25f) * 2 * PI).toFloat() * 4f
            Box(
                Modifier
                    .offset(x = (kotlin.math.cos(angle) * 85).dp, y = (sin(angle) * 85).dp)
                    .graphicsLayer { scaleX = a; scaleY = a; translationY = bob }
                    .size(48.dp)
                    .background(tone.container, CircleShape)
                    .border(3.dp, tone.solid, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(listOf("M", "L", "G", "A")[i], style = MaterialTheme.typography.titleLarge, color = tone.content)
            }
        }
    }
}

/** Il "+": ruota e apre i quattro riquadri di creazione rapida. */
@Composable
fun AddIllustration() {
    val t = loopTime(3200)
    val c = MaterialTheme.ricordellaColors
    val open = t.phase(0.15f, 0.4f) * (1f - t.phase(0.85f, 1f))
    val tones = listOf(c.cyan, c.lavender, c.pear, c.coral)
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        tones.forEachIndexed { i, tone ->
            val dx = if (i % 2 == 0) -1 else 1
            val dy = if (i < 2) -1 else 1
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = dx * 55.dp.toPx() * open
                        translationY = dy * 55.dp.toPx() * open
                        alpha = open
                        scaleX = 0.5f + 0.5f * open
                        scaleY = scaleX
                    }
                    .size(76.dp)
                    .background(tone.container, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(30.dp).background(tone.solid, CircleShape)) }
        }
        Box(
            Modifier
                .size(60.dp)
                .background(c.bolt, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, null, tint = c.onBolt, modifier = Modifier.size(34.dp).graphicsLayer { rotationZ = 135f * open })
        }
    }
}

/** Widget: il calendario sulla schermata Home del telefono, con i pulsanti per aggiungere. */
@Composable
fun WidgetIllustration() {
    val t = loopTime(3000)
    val c = MaterialTheme.ricordellaColors
    val tap = t.phase(0.4f, 0.5f) * (1f - t.phase(0.5f, 0.6f))
    PhoneFrame {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Widgets, null, tint = c.cyan.solid, modifier = Modifier.size(12.dp))
                    Bar(40.dp, height = 6.dp)
                }
                repeat(4) { w ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(7) { d ->
                            val today = w == 1 && d == 3
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .background(if (today) c.bolt else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.BottomCenter,
                            ) { if ((w * 7 + d) % 5 == 2) Box(Modifier.size(3.dp).background(c.lavender.solid, CircleShape)) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(c.cyan, c.lavender, c.pear, c.coral).forEachIndexed { i, tone ->
                        Box(
                            Modifier
                                .graphicsLayer { val s = if (i == 0) 1f - 0.2f * tap else 1f; scaleX = s; scaleY = s }
                                .size(22.dp, 14.dp)
                                .background(tone.container, RoundedCornerShape(5.dp)),
                        )
                    }
                }
            }
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) { Box(Modifier.size(22.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(7.dp))) }
                }
            }
        }
    }
}

/** Pagine del tutorial delle sezioni: usate nella configurazione iniziale e dalle Impostazioni. */
val SectionTutorialPages: List<TutorialPage> get() = listOf(
    TutorialPage(
        tr("Home: cosa ricordare oggi"),
        tr("In alto trovi quello che richiede attenzione, poi le cose di oggi e quelle in arrivo. ") +
            tr("Tocca il cerchio per completare: se qualcosa è scaduto la palla si riempie di fumo rosso."),
    ) { HomeIllustration() },
    TutorialPage(
        tr("Calendario"),
        tr("Il mese a colpo d'occhio: i giorni con un puntino hanno promemoria. Tocca un giorno per vederli, scorri per cambiare mese. In \"Giorno\" trovi la timeline ora per ora."),
    ) { CalendarIllustration() },
    TutorialPage(
        tr("Promemoria"),
        tr("Tutti i promemoria, filtrabili per tipo e stato: attività, eventi, visite mediche, scadenze, compleanni. Ti avviso io con una notifica."),
    ) { RemindersIllustration() },
    TutorialPage(
        tr("Cose"),
        tr("Auto, elettrodomestici, dispositivi, documenti. Scegli prima la categoria, poi la cosa: ti propongo io revisioni, tagliandi e garanzie."),
    ) { ItemsIllustration() },
    TutorialPage(
        tr("Persone"),
        tr("Collega promemoria e cose alle persone: nella loro scheda trovi compleanni, eventi e tutto ciò che le riguarda."),
    ) { PeopleIllustration() },
    TutorialPage(
        tr("Il pulsante +"),
        tr("Da qualunque sezione, il + giallo aggiunge al volo un promemoria, un evento, una cosa o una persona."),
    ) { AddIllustration() },
    TutorialPage(
        tr("Il widget"),
        tr("Aggiungi il widget Calendario alla schermata Home del telefono: vedi questo mese e il prossimo e inserisci promemoria, eventi, cose e persone con un tocco."),
    ) { WidgetIllustration() },
)

/* ---------- Illustrazioni del backup ---------- */

/** Esporta: il file .zip parte dal telefono e vola verso l'app scelta per condividerlo. */
@Composable
fun ExportIllustration() {
    val t = loopTime(3200)
    val c = MaterialTheme.ricordellaColors
    val fly = t.phase(0.25f, 0.7f)
    val target = listOf(c.mint to Icons.Rounded.CloudDone, c.cyan to Icons.Rounded.Email, c.lavender to Icons.Rounded.Share)
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        PhoneFrame(Modifier.offset(x = (-50).dp).graphicsLayer { scaleX = 0.8f; scaleY = 0.8f }) {}
        Column(Modifier.offset(x = 85.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            target.forEachIndexed { i, (tone, icon) ->
                RoundIcon(icon, tone, modifier = Modifier.graphicsLayer { val s = if (i == 0) 1f + 0.25f * t.phase(0.7f, 0.8f) * (1f - t.phase(0.85f, 1f)) else 1f; scaleX = s; scaleY = s })
            }
        }
        Icon(
            Icons.Rounded.FolderZip,
            null,
            tint = c.boltEdge,
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer {
                    translationX = (-50 + 130 * fly).dp.toPx()
                    translationY = (-sin(fly * PI).toFloat() * 60 - 54 * fly).dp.toPx()
                    alpha = if (t > 0.8f) 1f - t.phase(0.8f, 0.9f) else t.phase(0.05f, 0.2f)
                    rotationZ = fly * 20f
                },
        )
    }
}

/** Ogni 3 mesi: tre fogli di calendario si sfogliano e suona il promemoria del backup. */
@Composable
fun BackupReminderIllustration() {
    val t = loopTime(3600)
    val c = MaterialTheme.ricordellaColors
    val month = (t * 4).toInt().coerceAtMost(3)
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        repeat(3) { i ->
            val flipped = month > i
            Box(
                Modifier
                    .offset(x = (i * 6).dp, y = (i * 6).dp)
                    .graphicsLayer {
                        rotationX = if (flipped) -90f else -90f * ((t * 4 - i).coerceIn(0f, 1f).let { if (month == i) it else 0f })
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    }
                    .size(120.dp)
                    .background(listOf(c.cyan, c.lavender, c.mint)[i].container, RoundedCornerShape(18.dp))
                    .padding(12.dp),
            ) {
                Text("${3 - i}", style = MaterialTheme.typography.displayMedium, color = listOf(c.cyan, c.lavender, c.mint)[i].content, modifier = Modifier.align(Alignment.Center))
            }
        }
        if (month == 3) {
            val ring = sin(t * 80f).toFloat() * 15f
            RoundIcon(Icons.Rounded.Notifications, c.pear, size = 72.dp, modifier = Modifier.graphicsLayer { rotationZ = ring })
        }
    }
}

/** Leggero: il backup si comprime, le foto vengono ridotte. */
@Composable
fun LightBackupIllustration() {
    val t = loopTime(2800)
    val c = MaterialTheme.ricordellaColors
    val squeeze = t.phase(0.2f, 0.6f) * (1f - t.phase(0.85f, 1f))
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .graphicsLayer { scaleX = 1f - 0.45f * squeeze; scaleY = 1f - 0.45f * squeeze }
                .size(140.dp)
                .background(c.pear.container, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.FolderZip, null, tint = c.pear.content, modifier = Modifier.size(64.dp))
        }
        listOf(-1f, 1f).forEach { side ->
            Box(
                Modifier
                    .graphicsLayer { translationX = side * (100f - 40f * squeeze).dp.toPx() }
                    .size(14.dp, 90.dp)
                    .background(c.cyan.solid, RoundedCornerShape(7.dp)),
            )
        }
    }
}

/** Importa: il file arriva nel telefono, la barra si riempie e compare la spunta. */
@Composable
fun ImportIllustration() {
    val t = loopTime(3400)
    val c = MaterialTheme.ricordellaColors
    val arrive = t.phase(0.05f, 0.35f)
    val progress = t.phase(0.35f, 0.75f)
    val done = t.phase(0.75f, 0.85f)
    PhoneFrame {
        Icon(
            Icons.Rounded.FolderZip,
            null,
            tint = c.boltEdge,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(40.dp)
                .graphicsLayer { translationY = (-120 + 150 * arrive).dp.toPx(); alpha = arrive * (1f - done) },
        )
        Column(Modifier.align(Alignment.Center).padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) {
                Box(Modifier.fillMaxWidth(progress.coerceAtLeast(0.001f)).height(10.dp).background(c.mint.solid, CircleShape))
            }
            Spacer(Modifier.height(16.dp))
            RoundIcon(Icons.Rounded.Check, c.mint, size = 48.dp, modifier = Modifier.graphicsLayer { scaleX = done; scaleY = done })
        }
    }
}

val BackupTutorialPages: List<TutorialPage> get() = listOf(
    TutorialPage(
        tr("Esporta: un solo file"),
        tr("La prima volta scegli dove salvare il .zip (es. Drive). Le volte dopo lo sovrascrivo, a meno che tu non chieda una nuova versione. Puoi anche condividerlo."),
    ) { ExportIllustration() },
    TutorialPage(
        tr("Leggero come una piuma"),
        tr("Il backup è compresso al massimo e le foto vengono ridotte: pesa il meno possibile, senza perdere i tuoi dati."),
    ) { LightBackupIllustration() },
    TutorialPage(
        tr("Te lo ricordo io"),
        tr("Ogni 3 mesi (o ogni giorno, settimana, mese, anno: lo scegli nelle Impostazioni) ti chiedo di aggiornare il backup."),
    ) { BackupReminderIllustration() },
    TutorialPage(
        tr("Importa"),
        tr("Su un nuovo telefono, \"Importa backup\" e scegli il file .zip: ti mostro cosa contiene e, se confermi, ripristino tutto."),
    ) { ImportIllustration() },
)

/**
 * Tutorial compatto da incorporare in una schermata: pagine che scorrono da sole ogni
 * pochi secondi (o col dito), illustrazione rimpicciolita e testo breve, con i pallini.
 */
@Composable
fun MiniTutorial(pages: List<TutorialPage>, modifier: Modifier = Modifier) {
    val pager = rememberPagerState { pages.size }
    androidx.compose.runtime.LaunchedEffect(pager) {
        while (true) {
            kotlinx.coroutines.delay(4500)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % pages.size)
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, modifier = Modifier.fillMaxWidth()) { index ->
            val page = pages[index]
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = RicordellaDimensions.spaceL)) {
                Box(Modifier.size(170.dp).graphicsLayer { scaleX = 0.65f; scaleY = 0.65f }, contentAlignment = Alignment.Center) {
                    Box(Modifier.requiredSize(260.dp), contentAlignment = Alignment.Center) { page.illustration() }
                }
                Text(page.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(
                    page.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    minLines = 3,
                )
            }
        }
        PageDots(pages.size, pager.currentPage, Modifier.padding(top = RicordellaDimensions.spaceS))
    }
}
