package com.ricordella.app.feature.calendar

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        CalendarMode.MONTH -> "Mese"
        CalendarMode.DAY -> "Giorno"
        CalendarMode.AGENDA -> "Agenda"
    }

@Composable
fun CalendarScreen(navigator: AppNavigator, onAdd: () -> Unit) {
    val viewModel = appViewModel { c, _ ->
        CalendarViewModel(c.reminderRepository, c.settingsRepository, c.recurrenceCalculator, c.completeReminder, c.time)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = state.now.toLocalDate()

    TopLevelScaffold(title = "Calendario", navigator = navigator, onAdd = onAdd) { padding ->
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
                    val (allDay, timed) = state.selectedOccurrences.partition { it.reminder.dueTime == null }
                    if (allDay.isNotEmpty()) {
                        item(key = "all-day") { SectionHeader("Tutto il giorno") }
                        dayItems(allDay, state, navigator, viewModel, state.selectedDate, showEmpty = false)
                    }
                    if (timed.isNotEmpty()) {
                        item(key = "timed") { SectionHeader("Con orario") }
                        dayItems(timed, state, navigator, viewModel, state.selectedDate, showEmpty = false)
                    }
                    if (state.selectedOccurrences.isEmpty()) dayItems(emptyList(), state, navigator, viewModel, state.selectedDate)
                }
                CalendarMode.AGENDA -> {
                    val days = state.occurrences.toSortedMap()
                    if (!state.isLoading && days.isEmpty()) {
                        item(key = "agenda-empty") {
                            EmptyState(
                                icon = Icons.Rounded.EventAvailable,
                                title = "Agenda libera",
                                message = "Nessun promemoria nei prossimi 90 giorni.",
                            )
                        }
                    }
                    days.forEach { (date, occurrences) ->
                        item(key = "agenda-$date") { SectionHeader(DateTexts.dayHeader(date, today)) }
                        items(occurrences, key = { "agenda-$date-${it.reminder.id}" }) { occurrence ->
                            OccurrenceCard(occurrence, state, navigator, viewModel)
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
                title = "Nessun promemoria",
                message = "Non c'è nulla da ricordare in questa giornata.",
                actionLabel = "Aggiungi",
                onAction = { navigator.newReminder(date = date) },
            )
        }
    }
    items(occurrences, key = { "day-$date-${it.reminder.id}" }) { occurrence ->
        OccurrenceCard(occurrence, state, navigator, viewModel)
    }
}

@Composable
private fun OccurrenceCard(occurrence: ReminderOccurrence, state: CalendarUiState, navigator: AppNavigator, viewModel: CalendarViewModel) {
    ReminderCard(
        entry = occurrence.entry,
        now = state.now,
        onClick = { navigator.openReminder(occurrence.reminder.id) },
        onToggleComplete = if (occurrence.isProjected || occurrence.reminder.status != ReminderStatus.ACTIVE) null
        else ({ viewModel.onComplete(occurrence.reminder.id) }),
        dateMode = ReminderDateMode.TIME_ONLY,
        occurrenceDate = occurrence.date,
    )
}

@Composable
private fun PeriodHeader(title: String, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Precedente") }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Successivo") }
        TextButton(onClick = onToday) { Text("Oggi") }
    }
}

@Composable
private fun MonthView(state: CalendarUiState, viewModel: CalendarViewModel, today: LocalDate) {
    Column {
        PeriodHeader(
            title = DateTexts.monthTitle(state.month),
            onPrevious = { viewModel.onShiftMonth(-1) },
            onNext = { viewModel.onShiftMonth(1) },
            onToday = { viewModel.onToday(today) },
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
        state.gridDays.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(
                        date = day,
                        occurrences = state.occurrences[day].orEmpty(),
                        inMonth = day.month == state.month.month,
                        isToday = day == today,
                        isSelected = day == state.selectedDate,
                        onClick = { viewModel.onSelectDate(day) },
                        modifier = Modifier.weight(1f),
                    )
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
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val hasDeadline = occurrences.any { it.reminder.type.isDeadlineLike }
    val hasEvent = occurrences.any { it.reminder.type == ReminderType.EVENT || it.reminder.type == ReminderType.BIRTHDAY }
    val hasTask = occurrences.any { !it.reminder.type.isDeadlineLike && it.reminder.type != ReminderType.EVENT && it.reminder.type != ReminderType.BIRTHDAY }
    val description = buildString {
        append(DateTexts.fullDate(date))
        if (occurrences.isNotEmpty()) append(", ${occurrences.size} promemoria")
        if (hasDeadline) append(", con scadenze")
    }
    Column(
        modifier = modifier
            .height(52.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) colors.primaryContainer else Color.Transparent)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, colors.primary, RoundedCornerShape(12.dp)) else Modifier)
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
            fontWeight = if (isToday) FontWeight.Bold else null,
            color = when {
                isSelected -> colors.onPrimaryContainer
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
}

private enum class MarkerShape { DOT, RING, SQUARE }

@Composable
private fun Marker(shape: MarkerShape) {
    val colors = MaterialTheme.colorScheme
    val modifier = Modifier.size(6.dp)
    when (shape) {
        MarkerShape.DOT -> Box(modifier.background(colors.primary, CircleShape))
        MarkerShape.RING -> Box(modifier.border(1.5.dp, colors.secondary, CircleShape))
        MarkerShape.SQUARE -> Box(modifier.background(colors.tertiary, RoundedCornerShape(1.dp)))
    }
}

@Composable
private fun Legend() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = RicordellaDimensions.spaceS),
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceL, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendEntry(MarkerShape.DOT, "Attività")
        LegendEntry(MarkerShape.RING, "Eventi")
        LegendEntry(MarkerShape.SQUARE, "Scadenze")
    }
}

@Composable
private fun LegendEntry(shape: MarkerShape, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Marker(shape)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

