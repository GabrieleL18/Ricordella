package com.ricordella.app.feature.calendar

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.ricordella.app.core.ui.RicordellaMotion
import com.ricordella.app.core.ui.theme.ricordellaColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import com.ricordella.app.core.ui.MonthYearGrid
import java.time.temporal.ChronoUnit
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ricordella.app.core.ui.tone
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.navigation.AppNavigator
import com.ricordella.app.core.ui.EmptyState
import com.ricordella.app.core.ui.ReminderCard
import com.ricordella.app.core.ui.ReminderDateMode
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.TopLevelScaffold
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.domain.model.ReminderOccurrence
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.ReminderType
import java.time.LocalDate

private val CalendarMode.label: String
    get() = when (this) {
        CalendarMode.MONTH -> tr("Mese")
        CalendarMode.DAY -> tr("Giorno")
        CalendarMode.AGENDA -> tr("Agenda")
    }

@Composable
fun CalendarScreen(navigator: AppNavigator, onAddOn: (LocalDate) -> Unit) {
    val viewModel = appViewModel { c, _ ->
        CalendarViewModel(c.reminderRepository, c.settingsRepository, c.recurrenceCalculator, c.completeReminder, c.time)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = state.now.toLocalDate()

    // Il "+" crea promemoria ed eventi nel giorno selezionato, non oggi.
    TopLevelScaffold(title = tr("Calendario"), navigator = navigator, onAdd = { onAddOn(state.selectedDate) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().contentWidth(),
            contentPadding = PaddingValues(
                start = RicordellaDimensions.screenPadding,
                end = RicordellaDimensions.screenPadding,
                top = padding.calculateTopPadding(),
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
        ) {
            item(key = "mode") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CalendarMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { viewModel.onModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, CalendarMode.entries.size),
                        ) { Text(mode.label) }
                    }
                }
            }
            when (state.mode) {
                CalendarMode.MONTH -> {
                    item(key = "month") { MonthView(state, viewModel, today) }
                    item(key = "day-header") { SectionHeader(DateTexts.dayHeader(state.selectedDate, today)) }
                    dayItems(state.selectedOccurrences, state, navigator, viewModel, state.selectedDate)
                }
                CalendarMode.DAY -> {
                    item(key = "day-nav") {
                        PeriodHeader(
                            title = DateTexts.dayHeader(state.selectedDate, today),
                            onPrevious = { viewModel.onShiftDay(-1) },
                            onNext = { viewModel.onShiftDay(1) },
                            onToday = { viewModel.onToday(today) },
                        )
                    }
                    item(key = "timeline-${state.selectedDate}") {
                        DayTimeline(
                            date = state.selectedDate,
                            occurrences = state.selectedOccurrences,
                            now = state.now,
                            onOpen = { navigator.openReminder(it.reminder.id) },
                            onAdd = { navigator.newReminder(date = state.selectedDate) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                CalendarMode.AGENDA -> {
                    val days = state.occurrences.toSortedMap()
                    if (!state.isLoading && days.isEmpty()) {
                        item(key = "agenda-empty") {
                            EmptyState(
                                icon = Icons.Rounded.EventAvailable,
                                title = tr("Agenda libera"),
                                message = tr("Nessun promemoria nei prossimi 90 giorni."),
                            )
                        }
                    }
                    days.forEach { (date, occurrences) ->
                        item(key = "agenda-$date") { SectionHeader(DateTexts.dayHeader(date, today)) }
                        items(occurrences, key = { "agenda-$date-${it.reminder.id}" }) { occurrence ->
                            OccurrenceCard(occurrence, state, navigator, viewModel, Modifier.animateItem())
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.dayItems(
    occurrences: List<ReminderOccurrence>,
    state: CalendarUiState,
    navigator: AppNavigator,
    viewModel: CalendarViewModel,
    date: LocalDate,
    showEmpty: Boolean = true,
) {
    if (occurrences.isEmpty() && showEmpty && !state.isLoading) {
        item(key = "day-empty-$date") {
            EmptyState(
                icon = Icons.Rounded.EventAvailable,
                title = tr("Nessun promemoria"),
                message = tr("Non c'è nulla da ricordare in questa giornata."),
                actionLabel = tr("Aggiungi"),
                onAction = { navigator.newReminder(date = date) },
            )
        }
    }
    items(occurrences, key = { "day-$date-${it.reminder.id}" }) { occurrence ->
        OccurrenceCard(occurrence, state, navigator, viewModel, Modifier.animateItem())
    }
}

@Composable
private fun OccurrenceCard(
    occurrence: ReminderOccurrence,
    state: CalendarUiState,
    navigator: AppNavigator,
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier,
) {
    ReminderCard(
        entry = occurrence.entry,
        now = state.now,
        onClick = { navigator.openReminder(occurrence.reminder.id) },
        onToggleComplete = if (occurrence.isProjected || occurrence.reminder.status != ReminderStatus.ACTIVE) null
        else ({ viewModel.onComplete(occurrence.reminder.id) }),
        dateMode = ReminderDateMode.TIME_ONLY,
        occurrenceDate = occurrence.date,
        modifier = modifier,
    )
}

@Composable
private fun PeriodHeader(title: String, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit, onTitleClick: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = tr("Precedente")) }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clip(CircleShape)
                .then(if (onTitleClick != null) Modifier.clickable(role = Role.Button, onClickLabel = tr("Scegli mese e anno"), onClick = onTitleClick) else Modifier)
                .padding(vertical = 8.dp),
        )
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = tr("Successivo")) }
        TextButton(onClick = onToday) { Text(tr("Oggi")) }
    }
}

@Composable
private fun MonthView(state: CalendarUiState, viewModel: CalendarViewModel, today: LocalDate) {
    var pickingMonth by remember { mutableStateOf(false) }
    if (pickingMonth) {
        AlertDialog(
            onDismissRequest = { pickingMonth = false },
            confirmButton = { TextButton(onClick = { pickingMonth = false }) { Text(tr("Annulla")) } },
            text = {
                MonthYearGrid(state.month) {
                    viewModel.onShiftMonth(ChronoUnit.MONTHS.between(state.month, it))
                    pickingMonth = false
                }
            },
        )
    }
    Column {
        PeriodHeader(
            title = DateTexts.monthTitle(state.month),
            onPrevious = { viewModel.onShiftMonth(-1) },
            onNext = { viewModel.onShiftMonth(1) },
            onToday = { viewModel.onToday(today) },
            onTitleClick = { pickingMonth = true },
        )
        Row(Modifier.fillMaxWidth()) {
            state.gridDays.take(7).forEach { day ->
                Text(
                    DateTexts.weekdayShort(day.dayOfWeek),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Il mese scorre nella direzione in cui si va; si può anche trascinare a destra/sinistra.
        val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
        AnimatedContent(
            targetState = state.month,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(RicordellaMotion.LONG, easing = RicordellaMotion.EaseOut)) { if (forward) it / 3 else -it / 3 } +
                    fadeIn(tween(RicordellaMotion.SHORT))) togetherWith
                    (slideOutHorizontally(tween(RicordellaMotion.SHORT, easing = RicordellaMotion.EaseIn)) { if (forward) -it / 3 else it / 3 } +
                        fadeOut(tween(RicordellaMotion.MICRO)))
            },
            modifier = Modifier.pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total > swipeThreshold -> viewModel.onShiftMonth(-1)
                            total < -swipeThreshold -> viewModel.onShiftMonth(1)
                        }
                    },
                ) { _, delta -> total += delta }
            },
            label = "month",
        ) { month ->
            val lanes = remember(state.occurrences) { spanLanes(state.occurrences) }
            val laneCount = (lanes.values.maxOrNull()?.plus(1) ?: 0).coerceAtMost(MAX_LANES)
            Column {
                monthGrid(month, state.firstDayOfWeek).chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            DayCell(
                                date = day,
                                occurrences = state.occurrences[day].orEmpty(),
                                lanes = lanes,
                                laneCount = laneCount,
                                firstDayOfWeek = state.firstDayOfWeek,
                                inMonth = day.month == month.month,
                                isToday = day == today,
                                isSelected = day == state.selectedDate,
                                onClick = { viewModel.onSelectDate(day) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        Legend()
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    occurrences: List<ReminderOccurrence>,
    lanes: Map<String, Int>,
    laneCount: Int,
    firstDayOfWeek: java.time.DayOfWeek,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val extra = MaterialTheme.ricordellaColors
    val background by animateColorAsState(
        if (isSelected) extra.bolt else Color.Transparent,
        tween(RicordellaMotion.SHORT, easing = RicordellaMotion.EaseOut),
        label = "daySelected",
    )
    val scale by animateFloatAsState(
        if (isSelected) 1f else 0.92f,
        tween(RicordellaMotion.SHORT, easing = RicordellaMotion.EaseOut),
        label = "dayScale",
    )
    val single = occurrences.filterNot { it.isMultiDay }
    val hasDeadline = single.any { it.reminder.type.isDeadlineLike }
    val hasEvent = single.any { it.reminder.type == ReminderType.EVENT || it.reminder.type == ReminderType.BIRTHDAY }
    val hasTask = single.any { !it.reminder.type.isDeadlineLike && it.reminder.type != ReminderType.EVENT && it.reminder.type != ReminderType.BIRTHDAY }
    val description = buildString {
        append(DateTexts.fullDate(date))
        if (occurrences.isNotEmpty()) append(trf(", %1\$s promemoria", occurrences.size))
        if (hasDeadline) append(tr(", con scadenze"))
    }
    Column(modifier) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(2.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .then(if (isToday && !isSelected) Modifier.border(2.dp, colors.primary, RoundedCornerShape(16.dp)) else Modifier)
            .semantics {
                contentDescription = description
                selected = isSelected
            }
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            color = when {
                isSelected -> extra.onBolt
                inMonth -> colors.onSurface
                else -> colors.outline
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(8.dp).padding(top = 2.dp)) {
            if (hasTask) Marker(MarkerShape.DOT)
            if (hasEvent) Marker(MarkerShape.RING)
            if (hasDeadline) Marker(MarkerShape.SQUARE)
        }
    }
    SpanBars(date, occurrences.filter { it.isMultiDay }, lanes, laneCount, firstDayOfWeek)
    }
}

private const val MAX_LANES = 3

private fun spanKey(occurrence: ReminderOccurrence) = occurrence.reminder.id + "@" + occurrence.start

/**
 * Assegna a ogni evento di più giorni una "corsia" fissa, così la sua barra resta alla
 * stessa altezza per tutti i giorni e si vede la continuità.
 */
private fun spanLanes(occurrences: Map<LocalDate, List<ReminderOccurrence>>): Map<String, Int> {
    val spans = occurrences.values.flatten().filter { it.isMultiDay }.distinctBy(::spanKey)
        .sortedWith(compareBy<ReminderOccurrence> { it.start }.thenByDescending { it.end })
    val laneEnds = mutableListOf<LocalDate>()
    return spans.associate { span ->
        val lane = laneEnds.indexOfFirst { it.isBefore(span.start) }.takeIf { it >= 0 } ?: laneEnds.size.also { laneEnds += span.end }
        laneEnds[lane] = span.end
        spanKey(span) to lane
    }
}

/** Barre degli eventi lunghi: arrotondate a inizio/fine evento e a inizio/fine settimana, col titolo all'inizio. */
@Composable
private fun SpanBars(
    date: LocalDate,
    spans: List<ReminderOccurrence>,
    lanes: Map<String, Int>,
    laneCount: Int,
    firstDayOfWeek: java.time.DayOfWeek,
) {
    if (laneCount == 0) return
    val byLane = spans.associateBy { lanes[spanKey(it)] ?: MAX_LANES }
    val rowStart = date.dayOfWeek == firstDayOfWeek
    val rowEnd = date.dayOfWeek == firstDayOfWeek.minus(1)
    Column(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(laneCount) { lane ->
            val span = byLane[lane]
            if (span == null) {
                Box(Modifier.height(12.dp))
            } else {
                val first = date == span.start
                val last = date == span.end
                val tone = span.reminder.type.tone
                val shape = RoundedCornerShape(
                    topStart = if (first || rowStart) 6.dp else 0.dp,
                    bottomStart = if (first || rowStart) 6.dp else 0.dp,
                    topEnd = if (last || rowEnd) 6.dp else 0.dp,
                    bottomEnd = if (last || rowEnd) 6.dp else 0.dp,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = if (first) 3.dp else 0.dp, end = if (last) 3.dp else 0.dp)
                        .height(12.dp)
                        .background(tone.solid, shape),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (first || rowStart) {
                        Text(
                            span.reminder.title,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 9.sp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.padding(start = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

private enum class MarkerShape { DOT, RING, SQUARE }

@Composable
private fun Marker(shape: MarkerShape) {
    val colors = MaterialTheme.ricordellaColors
    val modifier = Modifier.size(6.dp)
    when (shape) {
        MarkerShape.DOT -> Box(modifier.background(colors.cyan.solid, CircleShape))
        MarkerShape.RING -> Box(modifier.border(1.5.dp, colors.lavender.solid, CircleShape))
        MarkerShape.SQUARE -> Box(modifier.background(colors.coral.solid, RoundedCornerShape(1.dp)))
    }
}

@Composable
private fun Legend() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = RicordellaDimensions.spaceS),
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceL, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendEntry(MarkerShape.DOT, tr("Attività"))
        LegendEntry(MarkerShape.RING, tr("Eventi"))
        LegendEntry(MarkerShape.SQUARE, tr("Scadenze"))
    }
}

@Composable
private fun LegendEntry(shape: MarkerShape, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Marker(shape)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

