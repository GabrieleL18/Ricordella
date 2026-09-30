package com.ricordella.app.feature.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.navigation.AppNavigator
import com.ricordella.app.core.ui.EmptyState
import com.ricordella.app.core.ui.FilterChipRow
import com.ricordella.app.core.ui.ItemCard
import com.ricordella.app.core.ui.SearchField
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.tone
import com.ricordella.app.core.ui.TopLevelScaffold
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.icon
import com.ricordella.app.core.ui.label
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.domain.model.ItemGroup

@Composable
fun ItemListScreen(navigator: AppNavigator, onAdd: () -> Unit) {
    val viewModel = appViewModel { c, _ -> ItemListViewModel(c.itemRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TopLevelScaffold(title = "Cose", navigator = navigator, onAdd = onAdd) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            modifier = Modifier.fillMaxSize().contentWidth(),
            contentPadding = PaddingValues(
                start = RicordellaDimensions.screenPadding,
                end = RicordellaDimensions.screenPadding,
                top = padding.calculateTopPadding(),
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
            horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                SearchField(state.query, viewModel::onQueryChange, placeholder = "Cerca per nome, marca, modello, targa")
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "groups") {
                FilterChipRow(
                    options = listOf<ItemGroup?>(null) + ItemGroup.entries,
                    selected = state.groupFilter,
                    label = { it?.label ?: "Tutte" },
                    onSelected = viewModel::onGroupFilter,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "archived") {
                FilterChipRow(
                    options = listOf(false, true),
                    selected = state.showArchived,
                    label = { if (it) "Archiviate" else "Attive" },
                    onSelected = viewModel::onShowArchived,
                )
            }
            if (!state.isLoading && state.isEmpty) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Inventory2,
                        title = "Nessuna cosa",
                        message = "Aggiungi auto, elettrodomestici, dispositivi e documenti per ricordarne scadenze e manutenzioni.",
                        actionLabel = if (state.showArchived) null else "Aggiungi cosa",
                        onAction = { navigator.newItem() },
                    )
                }
            }
            state.groups.forEach { (group, entries) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "header-$group") {
                    SectionHeader(
                        title = "${group.label} · ${entries.size}",
                        icon = group.icon,
                        tone = group.tone,
                        modifier = Modifier.animateItem(),
                    )
                }
                items(entries, key = { it.item.id }) { entry ->
                    ItemCard(entry, onClick = { navigator.openItem(entry.item.id) }, modifier = Modifier.animateItem())
                }
            }
        }
    }
}
