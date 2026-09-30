package com.ricordella.app.feature.reminders

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
        ReminderListViewModel(c.reminderRepository, c.personRepository, c.itemRepository, c.completeReminder, c.reopenReminder, c.time)
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
                    FilterChipRow(
                        options = ReminderListScope.entries,
                        selected = state.filter.scope,
                        label = { it.label },
                        onSelected = viewModel::onScopeChange,
                        modifier = Modifier.weight(1f),
                    )
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
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    if (showFilters) {
        FiltersSheet(state, viewModel, onDismiss = { showFilters = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersSheet(state: ReminderListUiState, viewModel: ReminderListViewModel, onDismiss: () -> Unit) {
    val filter = state.filter
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
            DropdownField(
                label = "Tipo",
                options = listOf<ReminderType?>(null) + ReminderType.entries,
                selected = filter.type,
                optionLabel = { it?.label ?: "Tutti" },
                onSelected = viewModel::onTypeFilter,
            )
            DropdownField(
                label = "Priorità",
                options = listOf<Priority?>(null) + Priority.entries,
                selected = filter.priority,
                optionLabel = { it?.label ?: "Tutte" },
                onSelected = viewModel::onPriorityFilter,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Fatto") }
        }
    }
}
