package com.ricordella.app.feature.reminders

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CalendarViewMonth
import androidx.compose.ui.text.style.TextAlign
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.domain.model.PeriodKind
import com.ricordella.app.domain.model.ReminderPeriod
import java.time.YearMonth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector
import com.ricordella.app.core.ui.IconChipRow
import com.ricordella.app.core.ui.IconChoiceChip
import com.ricordella.app.core.ui.icon
import com.ricordella.app.core.ui.tone
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.navigation.AppNavigator
import com.ricordella.app.core.ui.DropdownField
import com.ricordella.app.core.ui.EmptyState
import com.ricordella.app.core.ui.FilterChipRow
import com.ricordella.app.core.ui.ReminderCard
import com.ricordella.app.core.ui.SearchField
import com.ricordella.app.core.ui.TopLevelScaffold
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.label
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.domain.model.Priority
import com.ricordella.app.domain.model.ReminderListScope
import com.ricordella.app.domain.model.ReminderSortOrder
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.model.displayName

private val ReminderListScope.label: String
    get() = when (this) {
        ReminderListScope.ALL -> "Tutti"
        ReminderListScope.TODAY -> "Oggi"
        ReminderListScope.UPCOMING -> "In arrivo"
        ReminderListScope.OVERDUE -> "Scaduti"
        ReminderListScope.COMPLETED -> "Completati"
    }

private val ReminderListScope.icon: ImageVector
    get() = when (this) {
        ReminderListScope.ALL -> Icons.AutoMirrored.Rounded.List
        ReminderListScope.TODAY -> Icons.Rounded.WbSunny
        ReminderListScope.UPCOMING -> Icons.Rounded.Upcoming
        ReminderListScope.OVERDUE -> Icons.Rounded.Whatshot
        ReminderListScope.COMPLETED -> Icons.Rounded.TaskAlt
    }

@Composable
private fun ReminderListScope.tone(): Tone = with(MaterialTheme.ricordellaColors) {
    when (this@tone) {
        ReminderListScope.ALL -> cyan
        ReminderListScope.TODAY -> pear
        ReminderListScope.UPCOMING -> lavender
        ReminderListScope.OVERDUE -> coral
        ReminderListScope.COMPLETED -> mint
    }
}

private val ReminderSortOrder.label: String
    get() = when (this) {
        ReminderSortOrder.DATE_ASC -> "Data (prima i più vicini)"
        ReminderSortOrder.DATE_DESC -> "Data (prima i più lontani)"
        ReminderSortOrder.PRIORITY -> "Priorità"
        ReminderSortOrder.TITLE -> "Titolo (A-Z)"
    }

@Composable
fun ReminderListScreen(navigator: AppNavigator, onAdd: () -> Unit) {
    val viewModel = appViewModel { c, _ ->
        ReminderListViewModel(c.reminderRepository, c.personRepository, c.itemRepository, c.completeReminder, c.reopenReminder, c.recurrenceCalculator, c.time)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    TopLevelScaffold(title = "Promemoria", navigator = navigator, onAdd = onAdd) { padding ->
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
            item(key = "search") {
                SearchField(value = state.query, onValueChange = viewModel::onQueryChange, placeholder = "Cerca promemoria, cose, persone")
            }
            item(key = "filters") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChipRow(Modifier.weight(1f)) {
                        ReminderListScope.entries.forEach { scope ->
                            IconChoiceChip(scope.label, scope.icon, scope.tone(), state.filter.scope == scope, onClick = { viewModel.onScopeChange(scope) })
                        }
                    }
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(badge = { if (state.filter.hasSecondaryFilters) Badge() }) {
                            Icon(Icons.Rounded.FilterList, contentDescription = "Filtri")
                        }
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Ordina")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            ReminderSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    leadingIcon = { RadioButton(selected = state.filter.sortOrder == order, onClick = null) },
                                    onClick = { sortMenu = false; viewModel.onSortChange(order) },
                                )
                            }
                        }
                    }
                }
            }
            item(key = "period") { PeriodFilter(state.filter.period, viewModel) }
            item(key = "types") {
                // Filtro rapido per tipo: un tocco lo attiva, un secondo tocco lo toglie.
                IconChipRow {
                    ReminderType.entries.forEach { type ->
                        IconChoiceChip(
                            type.label,
                            type.icon,
                            type.tone,
                            state.filter.type == type,
                            onClick = { viewModel.onTypeFilter(if (state.filter.type == type) null else type) },
                        )
                    }
                }
            }
            if (!state.isLoading && state.reminders.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        modifier = Modifier.animateItem(),
                        icon = Icons.Rounded.NotificationsNone,
                        title = "Nessun promemoria",
                        message = if (state.query.isNotBlank() || state.filter.hasSecondaryFilters) "Nessun risultato con questi filtri."
                        else "Non c'è nulla da ricordare qui.",
                        actionLabel = "Aggiungi",
                        onAction = onAdd,
                    )
                }
            }
            items(state.reminders, key = { it.reminder.id }) { entry ->
                ReminderCard(
                    entry = entry,
                    now = state.now,
                    onClick = { navigator.openReminder(entry.reminder.id) },
                    onToggleComplete = { viewModel.onToggleComplete(entry) },
                    occurrenceDate = state.occurrenceDates[entry.reminder.id] ?: entry.reminder.dueDate,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    if (showFilters) {
        FiltersSheet(state, viewModel, onDismiss = { showFilters = false })
    }
}

/** Periodo: sempre, un mese intero o un anno intero, sfogliabile con le frecce. */
@Composable
private fun PeriodFilter(period: ReminderPeriod, viewModel: ReminderListViewModel) {
    val colors = MaterialTheme.ricordellaColors
    Column(verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceXs)) {
        IconChipRow {
            IconChoiceChip("Sempre", Icons.Rounded.AllInclusive, colors.cyan, period.kind == PeriodKind.ALL, onClick = { viewModel.onPeriodKind(PeriodKind.ALL) })
            IconChoiceChip("Tutto il mese", Icons.Rounded.CalendarViewMonth, colors.lavender, period.kind == PeriodKind.MONTH, onClick = { viewModel.onPeriodKind(PeriodKind.MONTH) })
            IconChoiceChip("Tutto l'anno", Icons.Rounded.CalendarToday, colors.mint, period.kind == PeriodKind.YEAR, onClick = { viewModel.onPeriodKind(PeriodKind.YEAR) })
        }
        AnimatedVisibility(period.kind != PeriodKind.ALL) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.onShiftPeriod(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Periodo precedente") }
                AnimatedContent(
                    period,
                    transitionSpec = {
                        val forward = targetState.anchor > initialState.anchor
                        (slideInHorizontally { if (forward) it / 2 else -it / 2 } + fadeIn()) togetherWith
                            (slideOutHorizontally { if (forward) -it / 2 else it / 2 } + fadeOut())
                    },
                    modifier = Modifier.weight(1f),
                    label = "period",
                ) { shown ->
                    Text(
                        if (shown.kind == PeriodKind.YEAR) "Anno ${shown.anchor.year}" else DateTexts.monthTitle(YearMonth.from(shown.anchor)),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                IconButton(onClick = { viewModel.onShiftPeriod(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Periodo successivo") }
                TextButton(onClick = { viewModel.onPeriodKind(period.kind) }) { Text("Oggi") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FiltersSheet(state: ReminderListUiState, viewModel: ReminderListViewModel, onDismiss: () -> Unit) {
    val filter = state.filter
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = RicordellaDimensions.screenPadding, vertical = RicordellaDimensions.spaceS),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filtri", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::onClearSecondaryFilters, enabled = filter.hasSecondaryFilters) { Text("Azzera") }
            }
            DropdownField(
                label = "Persona",
                options = listOf(null) + state.people.map { it.id },
                selected = filter.personId,
                optionLabel = { id -> state.people.firstOrNull { it.id == id }?.displayName ?: "Tutte" },
                onSelected = viewModel::onPersonFilter,
            )
            DropdownField(
                label = "Cosa",
                options = listOf(null) + state.items.map { it.item.id },
                selected = filter.itemId,
                optionLabel = { id -> state.items.firstOrNull { it.item.id == id }?.item?.name ?: "Tutte" },
                onSelected = viewModel::onItemFilter,
            )
            DropdownField(
                label = "Categoria",
                options = listOf<String?>(null) + state.categories,
                selected = filter.category,
                optionLabel = { it ?: "Tutte" },
                onSelected = viewModel::onCategoryFilter,
            )
            Text("Tipo", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderType.entries.forEach { type ->
                    IconChoiceChip(type.label, type.icon, type.tone, filter.type == type, onClick = { viewModel.onTypeFilter(if (filter.type == type) null else type) })
                }
            }
            Text("Priorità", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val colors = MaterialTheme.ricordellaColors
                Priority.entries.forEach { priority ->
                    IconChoiceChip(
                        priority.label,
                        priority.icon ?: Icons.Rounded.Remove,
                        if (priority == Priority.URGENT) colors.coral else if (priority == Priority.IMPORTANT) colors.pear else colors.cyan,
                        filter.priority == priority,
                        onClick = { viewModel.onPriorityFilter(if (filter.priority == priority) null else priority) },
                    )
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Fatto") }
        }
    }
}
