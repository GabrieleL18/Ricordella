package com.ricordella.app.core.ui

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Selettore data di Ricordella: intestazione con la data scelta (va a capo, nessun mese
 * tagliato), scorciatoie (oggi, domani, tra una settimana, tra un mese), mese che scorre
 * con le frecce o trascinando, scelta rapida di mese e anno. In orizzontale è su due colonne.
 */
@Composable
fun DatePickerDialogFor(initial: LocalDate?, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val today = remember { LocalDate.now() }
    var selected by rememberSaveable { mutableStateOf(initial ?: today) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(initial ?: today)) }
    var pickingMonth by rememberSaveable { mutableStateOf(false) }
    val select = { date: LocalDate -> selected = date; month = YearMonth.from(date); pickingMonth = false }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.padding(16.dp)) {
            val landscape = maxWidth > maxHeight
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.widthIn(max = if (landscape) 680.dp else 420.dp),
            ) {
                val header: @Composable (Modifier) -> Unit = { modifier -> Header(selected, today, select, modifier) }
                val body: @Composable () -> Unit = {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        MonthBar(month, pickingMonth, onShift = { month = month.plusMonths(it) }, onToggle = { pickingMonth = !pickingMonth })
                        AnimatedContent(pickingMonth, label = "pickerMode") { choosing ->
                            if (choosing) MonthYearGrid(month, onPicked = { month = it; pickingMonth = false })
                            else DaysGrid(month, selected, today, onSelect = { selected = it }, onShift = { month = month.plusMonths(it) })
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onDismiss) { Text(tr("Annulla")) }
                            PushButton("OK", onClick = { onConfirm(selected) }, icon = Icons.Rounded.Check, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (landscape) {
                    Row {
                        header(Modifier.width(220.dp))
                        Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { body() }
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        header(Modifier.fillMaxWidth())
                        body()
                    }
                }
            }
        }
    }
}

/** Fascia colorata con la data scelta (animata al cambio) e le scorciatoie. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Header(selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit, modifier: Modifier) {
    val tone = MaterialTheme.ricordellaColors.pear
    Column(
        modifier
            .background(tone.container)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("SCEGLI LA DATA", style = MaterialTheme.typography.labelMedium, color = tone.content)
        AnimatedContent(
            selected,
            transitionSpec = {
                val later = targetState > initialState
                (slideInVertically(tween(RicordellaMotion.SHORT)) { if (later) it / 2 else -it / 2 } + fadeIn()) togetherWith
                    (slideOutVertically(tween(RicordellaMotion.MICRO)) { if (later) -it / 2 else it / 2 } + fadeOut())
            },
            label = "headerDate",
        ) { date ->
            Column {
                Text(date.dayOfWeek.getDisplayName(TextStyle.FULL, com.ricordella.app.core.i18n.Lang.locale).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, color = tone.content)
                Text(
                    "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, com.ricordella.app.core.i18n.Lang.locale)}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = tone.content,
                )
                Text("${date.year} · ${RelativeLabel.of(date, today)}", style = MaterialTheme.typography.bodyMedium, color = tone.content)
            }
        }
        // Scorciatoie: vanno a capo se lo spazio non basta.
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(tr("Oggi") to today, tr("Domani") to today.plusDays(1), tr("+1 settimana") to today.plusWeeks(1), tr("+1 mese") to today.plusMonths(1))
                .forEach { (label, date) -> Shortcut(label, date == selected) { onSelect(date) } }
        }
    }
}

private object RelativeLabel {
    fun of(date: LocalDate, today: LocalDate): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> tr("oggi")
            days == 1L -> tr("domani")
            days == -1L -> tr("ieri")
            days > 0 -> trf("tra %1\$s giorni", days)
            else -> trf("%1\$s giorni fa", -days)
        }
    }
}

@Composable
private fun Shortcut(label: String, active: Boolean, onClick: () -> Unit) {
    val tone = MaterialTheme.ricordellaColors.pear
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) MaterialTheme.colorScheme.surfaceContainerLowest else tone.content,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) tone.solid else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun MonthBar(month: YearMonth, pickingMonth: Boolean, onShift: (Long) -> Unit, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = tr("Scegli mese e anno"), onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(DateTexts.monthTitle(month), style = MaterialTheme.typography.titleMedium)
            Icon(if (pickingMonth) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown, contentDescription = null)
        }
        if (!pickingMonth) {
            IconButton(onClick = { onShift(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = tr("Mese precedente")) }
            IconButton(onClick = { onShift(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = tr("Mese successivo")) }
        }
    }
}

@Composable
private fun DaysGrid(month: YearMonth, selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit, onShift: (Long) -> Unit) {
    val firstDay = LocalAppSettings.current.firstDayOfWeek
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    Column {
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { i ->
                Text(
                    DateTexts.weekdayShort(firstDay.plus(i.toLong())),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedContent(
            month,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(RicordellaMotion.LONG, easing = RicordellaMotion.EaseOut)) { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally(tween(RicordellaMotion.SHORT, easing = RicordellaMotion.EaseIn)) { if (forward) -it / 3 else it / 3 } + fadeOut())
            },
            modifier = Modifier.pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > threshold) onShift(-1) else if (total < -threshold) onShift(1) },
                ) { _, delta -> total += delta }
            },
            label = "pickerMonth",
        ) { shown ->
            val start = shown.atDay(1).with(TemporalAdjusters.previousOrSame(firstDay))
            Column {
                repeat(6) { week ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { d ->
                            val date = start.plusDays((week * 7 + d).toLong())
                            DayButton(
                                date = date,
                                inMonth = YearMonth.from(date) == shown,
                                isSelected = date == selected,
                                isToday = date == today,
                                onClick = { onSelect(date) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Giorno: quello scelto si riempie di giallo con una piccola molla, oggi ha l'anello. */
@Composable
private fun DayButton(date: LocalDate, inMonth: Boolean, isSelected: Boolean, isToday: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.ricordellaColors
    val fill by animateColorAsState(if (isSelected) colors.bolt else Color.Transparent, tween(RicordellaMotion.MICRO), label = "dayFill")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(isSelected) {
        if (isSelected) {
            pop.snapTo(0.7f)
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        }
    }
    Box(modifier.aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .clip(CircleShape)
                .background(fill)
                .then(if (isToday && !isSelected) Modifier.border(2.dp, colors.boltEdge, CircleShape) else Modifier)
                .semantics {
                    contentDescription = DateTexts.fullDate(date)
                    this.selected = isSelected
                }
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected || isToday) FontWeight.ExtraBold else FontWeight.Medium,
                color = when {
                    isSelected -> colors.onBolt
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.outline
                },
            )
        }
    }
}

/** Scelta rapida: anno con le frecce e i dodici mesi in griglia. */
@Composable
internal fun MonthYearGrid(current: YearMonth, onPicked: (YearMonth) -> Unit) {
    var year by remember { mutableStateOf(current.year) }
    val colors = MaterialTheme.ricordellaColors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = tr("Anno precedente")) }
            AnimatedContent(year, label = "year") { Text("$it", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.width(88.dp), textAlign = TextAlign.Center) }
            IconButton(onClick = { year++ }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = tr("Anno successivo")) }
        }
        Month.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { month ->
                    val active = year == current.year && month == current.month
                    Text(
                        month.getDisplayName(TextStyle.FULL_STANDALONE, com.ricordella.app.core.i18n.Lang.locale).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        color = if (active) colors.onBolt else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) colors.bolt else MaterialTheme.colorScheme.surfaceContainer)
                            .clickable(role = Role.Button) { onPicked(YearMonth.of(year, month)) }
                            .padding(vertical = 14.dp),
                    )
                }
            }
        }
        Box(Modifier.height(RicordellaDimensions.spaceS))
    }
}
