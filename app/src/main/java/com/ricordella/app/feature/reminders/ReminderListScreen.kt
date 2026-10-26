package com.ricordella.app.feature.reminders

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.History
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.domain.date.ReminderTimeline
import com.ricordella.app.domain.model.Reminder
import java.time.LocalDateTime
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.ricordella.app.core.ui.HappyWizard
import com.ricordella.app.core.ui.WizardScene
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.domain.model.ReminderFilter
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.launch
import com.ricordella.app.core.ui.offerReopen
import com.ricordella.app.domain.model.ReminderStatus
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
        ReminderListScope.ALL -> tr("Tutti")
        ReminderListScope.TODAY -> tr("Oggi")
        ReminderListScope.UPCOMING -> tr("In arrivo")
        ReminderListScope.OVERDUE -> tr("Scaduti")
        ReminderListScope.COMPLETED -> tr("Completati")
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
        ReminderSortOrder.DATE_ASC -> tr("Data (prima i più vicini)")
        ReminderSortOrder.DATE_DESC -> tr("Data (prima i più lontani)")
        ReminderSortOrder.PRIORITY -> tr("Priorità")
        ReminderSortOrder.TITLE -> tr("Titolo (A-Z)")
    }

@Composable
fun ReminderListScreen(navigator: AppNavigator, onAdd: () -> Unit) {
    val viewModel = appViewModel { c, _ ->
        ReminderListViewModel(c.reminderRepository, c.personRepository, c.itemRepository, c.completeReminder, c.reopenReminder, c.recurrenceCalculator, c.time)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    TopLevelScaffold(title = tr("Promemoria"), navigator = navigator, onAdd = onAdd, snackbarHostState = snackbar) { padding ->
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
                // I filtri stanno nella barra di ricerca: una riga in meno prima dei promemoria.
                SearchField(value = state.query, onValueChange = viewModel::onQueryChange, placeholder = tr("Cerca promemoria, cose, persone"), extraTrailing = {
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(badge = { if (state.filter.activeCount > 0) Badge { Text("${state.filter.activeCount}") } }) {
                            Icon(Icons.Rounded.Tune, contentDescription = tr("Filtri e ordine"))
                        }
                    }
                })
            }
            item(key = "scope") {
                IconChipRow {
                    ReminderListScope.entries.forEach { scope ->
                        IconChoiceChip(scope.label, scope.icon, scope.tone(), state.filter.scope == scope, onClick = { viewModel.onScopeChange(scope) })
                    }
                }
            }
            if (state.filter.activeCount > 0 || state.filter.sortOrder != ReminderSortOrder.DATE_ASC) {
                item(key = "active-filters") { ActiveFilters(state, viewModel) }
            }
            if (!state.isLoading && state.reminders.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        modifier = Modifier.animateItem(),
                        icon = Icons.Rounded.NotificationsNone,
                        title = tr("Nessun promemoria"),
                        message = if (state.query.isNotBlank() || state.filter.hasSecondaryFilters) tr("Nessun risultato con questi filtri.")
                        else tr("Non c'è nulla da ricordare qui."),
                        actionLabel = tr("Aggiungi"),
                        onAction = onAdd,
                        illustration = { HappyWizard(size = 140.dp, scene = WizardScene.WAITING_BELL) },
                    )
                }
            }
            // In ordine di data la lista si divide per tempo (Scaduti, Oggi, Domani...); con altri ordini o un periodo resta unica.
            val grouped = state.filter.sortOrder == ReminderSortOrder.DATE_ASC && state.filter.period.kind == PeriodKind.ALL &&
                state.filter.scope != ReminderListScope.COMPLETED
            val groups = if (grouped) state.reminders.groupBy { listGroup(it.reminder, state.now) }.toSortedMap() else sortedMapOf(ListGroup.NONE to state.reminders)
            groups.forEach { (group, entries) ->
                if (group != ListGroup.NONE) item(key = "group-${group.name}") {
                    SectionHeader(trf("%1\$s · %2\$s", group.label, entries.size), Modifier.animateItem(), icon = group.icon, tone = group.tone())
                }
                items(entries, key = { it.reminder.id }) { entry ->
                    ReminderCard(
                        entry = entry,
                        now = state.now,
                        onClick = { navigator.openReminder(entry.reminder.id) },
                        onToggleComplete = {
                            viewModel.onToggleComplete(entry)
                            if (entry.reminder.status == ReminderStatus.ACTIVE) scope.launch { snackbar.offerReopen(entry) { viewModel.onReopen(entry.reminder.id) } }
                        },
                        occurrenceDate = state.occurrenceDates[entry.reminder.id] ?: entry.reminder.dueDate,
                        modifier = Modifier.animateItem(),
                    )
                }
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
            IconChoiceChip(tr("Sempre"), Icons.Rounded.AllInclusive, colors.cyan, period.kind == PeriodKind.ALL, onClick = { viewModel.onPeriodKind(PeriodKind.ALL) })
            IconChoiceChip(tr("Tutto il mese"), Icons.Rounded.CalendarViewMonth, colors.lavender, period.kind == PeriodKind.MONTH, onClick = { viewModel.onPeriodKind(PeriodKind.MONTH) })
            IconChoiceChip(tr("Tutto l'anno"), Icons.Rounded.CalendarToday, colors.mint, period.kind == PeriodKind.YEAR, onClick = { viewModel.onPeriodKind(PeriodKind.YEAR) })
        }
        AnimatedVisibility(period.kind != PeriodKind.ALL) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.onShiftPeriod(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = tr("Periodo precedente")) }
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
                        if (shown.kind == PeriodKind.YEAR) trf("Anno %1\$s", shown.anchor.year) else DateTexts.monthTitle(YearMonth.from(shown.anchor)),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                IconButton(onClick = { viewModel.onShiftPeriod(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = tr("Periodo successivo")) }
                TextButton(onClick = { viewModel.onPeriodKind(period.kind) }) { Text(tr("Oggi")) }
            }
        }
    }
}

/** Numero di filtri attivi oltre allo stato (periodo, tipo, priorità, persona, cosa, categoria). */
private val ReminderFilter.activeCount: Int
    get() = listOfNotNull(personId, itemId, category, type, priority).size + if (period.kind != PeriodKind.ALL) 1 else 0

/** Gruppi della lista in ordine di data: si mostrano in quest'ordine. */
internal enum class ListGroup { OVERDUE, TODAY, TOMORROW, WEEK, LATER, PAST, DONE, NONE }

/** In che gruppo cade un promemoria adesso. Le feste passate (che non scadono) vanno in "Già passati". */
internal fun listGroup(reminder: Reminder, now: LocalDateTime): ListGroup {
    val today = now.toLocalDate()
    val days = ReminderTimeline.daysUntil(reminder.dueDate, today)
    return when {
        reminder.status != ReminderStatus.ACTIVE -> ListGroup.DONE
        ReminderTimeline.isOverdue(reminder, now) -> ListGroup.OVERDUE
        ReminderTimeline.isOngoing(reminder, today) -> ListGroup.TODAY
        days < 0 -> ListGroup.PAST
        days == 1L -> ListGroup.TOMORROW
        days <= ReminderTimeline.NEXT_DAYS_LIMIT -> ListGroup.WEEK
        else -> ListGroup.LATER
    }
}

private val ListGroup.label: String
    get() = when (this) {
        ListGroup.OVERDUE -> tr("Scaduti")
        ListGroup.TODAY -> tr("Oggi")
        ListGroup.TOMORROW -> tr("Domani")
        ListGroup.WEEK -> tr("Prossimi 7 giorni")
        ListGroup.LATER -> tr("Più avanti")
        ListGroup.PAST -> tr("Già passati")
        ListGroup.DONE, ListGroup.NONE -> tr("Completati")
    }

private val ListGroup.icon: ImageVector
    get() = when (this) {
        ListGroup.OVERDUE -> Icons.Rounded.Whatshot
        ListGroup.TODAY -> Icons.Rounded.WbSunny
        ListGroup.TOMORROW, ListGroup.WEEK -> Icons.Rounded.Upcoming
        ListGroup.LATER -> Icons.Rounded.CalendarMonth
        ListGroup.PAST -> Icons.Rounded.History
        ListGroup.DONE, ListGroup.NONE -> Icons.Rounded.TaskAlt
    }

@Composable
private fun ListGroup.tone(): Tone = with(MaterialTheme.ricordellaColors) {
    when (this@tone) {
        ListGroup.OVERDUE -> coral
        ListGroup.TODAY -> pear
        ListGroup.TOMORROW, ListGroup.WEEK -> lavender
        ListGroup.LATER, ListGroup.PAST -> cyan
        ListGroup.DONE, ListGroup.NONE -> mint
    }
}

/**
 * Riga sotto lo stato, solo se c'è qualche filtro: ogni filtro attivo come pillola con la X per toglierlo.
 * Così si vede sempre cosa si sta guardando. I filtri si aprono dall'icona nella ricerca.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilters(state: ReminderListUiState, viewModel: ReminderListViewModel) {
    val filter = state.filter
    val colors = MaterialTheme.ricordellaColors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        if (filter.period.kind != PeriodKind.ALL) {
            val label = if (filter.period.kind == PeriodKind.YEAR) trf("Anno %1\$s", filter.period.anchor.year) else DateTexts.monthTitle(YearMonth.from(filter.period.anchor))
            RemovableFilter(label, Icons.Rounded.CalendarMonth, colors.lavender) { viewModel.onPeriodKind(PeriodKind.ALL) }
        }
        filter.type?.let { RemovableFilter(it.label, it.icon, it.tone) { viewModel.onTypeFilter(null) } }
        filter.priority?.let { RemovableFilter(it.label, it.icon ?: Icons.Rounded.Flag, colors.coral) { viewModel.onPriorityFilter(null) } }
        filter.personId?.let { id ->
            RemovableFilter(state.people.firstOrNull { it.id == id }?.displayName ?: tr("Persona"), Icons.Rounded.Person, colors.coral) { viewModel.onPersonFilter(null) }
        }
        filter.itemId?.let { id ->
            RemovableFilter(state.items.firstOrNull { it.item.id == id }?.item?.name ?: tr("Cosa"), Icons.Rounded.Inventory2, colors.pear) { viewModel.onItemFilter(null) }
        }
        filter.category?.let { RemovableFilter(it, Icons.AutoMirrored.Rounded.Label, colors.cyan) { viewModel.onCategoryFilter(null) } }
        if (filter.sortOrder != ReminderSortOrder.DATE_ASC) {
            Text(trf("Ordine: %1\$s", filter.sortOrder.label.lowercase()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (filter.activeCount > 1) {
            TextButton(onClick = { viewModel.onClearSecondaryFilters(); viewModel.onPeriodKind(PeriodKind.ALL) }) { Text(tr("Azzera tutto")) }
        }
    }
}

@Composable
private fun RemovableFilter(label: String, icon: ImageVector, tone: Tone, onRemove: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(tone.container)
            .clickable(role = Role.Button, onClickLabel = trf("Togli filtro %1\$s", label), onClick = onRemove)
            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = tone.content, maxLines = 1)
        Icon(Icons.Rounded.Close, contentDescription = null, tint = tone.content, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SheetSection(title: String, icon: ImageVector, tone: Tone, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(28.dp).background(tone.container, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(16.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FiltersSheet(state: ReminderListUiState, viewModel: ReminderListViewModel, onDismiss: () -> Unit) {
    val filter = state.filter
    val colors = MaterialTheme.ricordellaColors
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = RicordellaDimensions.screenPadding, vertical = RicordellaDimensions.spaceS),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceL),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Filtri e ordine"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { viewModel.onClearSecondaryFilters(); viewModel.onPeriodKind(PeriodKind.ALL) },
                    enabled = filter.activeCount > 0,
                ) { Text(tr("Azzera")) }
            }
            SheetSection(tr("Periodo"), Icons.Rounded.CalendarMonth, colors.lavender) { PeriodFilter(filter.period, viewModel) }
            SheetSection(tr("Tipo"), Icons.Rounded.Category, colors.cyan) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderType.entries.forEach { type ->
                        IconChoiceChip(type.label, type.icon, type.tone, filter.type == type, onClick = { viewModel.onTypeFilter(if (filter.type == type) null else type) })
                    }
                }
            }
            SheetSection(tr("Priorità"), Icons.Rounded.Flag, colors.coral) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
            SheetSection(tr("Collegati a"), Icons.Rounded.Link, colors.pear) {
                DropdownField(
                    label = tr("Persona"),
                    options = listOf(null) + state.people.map { it.id },
                    selected = filter.personId,
                    optionLabel = { id -> state.people.firstOrNull { it.id == id }?.displayName ?: tr("Tutte") },
                    onSelected = viewModel::onPersonFilter,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(
                    label = tr("Cosa"),
                    options = listOf(null) + state.items.map { it.item.id },
                    selected = filter.itemId,
                    optionLabel = { id -> state.items.firstOrNull { it.item.id == id }?.item?.name ?: tr("Tutte") },
                    onSelected = viewModel::onItemFilter,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(
                    label = tr("Categoria"),
                    options = listOf<String?>(null) + state.categories,
                    selected = filter.category,
                    optionLabel = { it ?: tr("Tutte") },
                    onSelected = viewModel::onCategoryFilter,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SheetSection(tr("Ordina per"), Icons.AutoMirrored.Rounded.Sort, colors.mint) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderSortOrder.entries.forEach { order ->
                        IconChoiceChip(order.label, order.icon, colors.mint, filter.sortOrder == order, onClick = { viewModel.onSortChange(order) })
                    }
                }
            }
            PushButton(
                text = if (state.reminders.size == 1) tr("Mostra 1 promemoria") else trf("Mostra %1\$s promemoria", state.reminders.size),
                onClick = onDismiss,
                icon = Icons.Rounded.Check,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val ReminderSortOrder.icon: ImageVector
    get() = when (this) {
        ReminderSortOrder.DATE_ASC -> Icons.Rounded.ArrowUpward
        ReminderSortOrder.DATE_DESC -> Icons.Rounded.ArrowDownward
        ReminderSortOrder.PRIORITY -> Icons.Rounded.Flag
        ReminderSortOrder.TITLE -> Icons.Rounded.SortByAlpha
    }
