package com.ricordella.app.feature.items

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.navigation.AppNavigator
import com.ricordella.app.core.ui.EmptyState
import com.ricordella.app.core.ui.IconChipRow
import com.ricordella.app.core.ui.IconChoiceChip
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.SearchField
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.TopLevelScaffold
import com.ricordella.app.core.ui.UriImage
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.icon
import com.ricordella.app.core.ui.label
import com.ricordella.app.core.ui.pressScale
import com.ricordella.app.core.ui.rememberRevealTracker
import com.ricordella.app.core.ui.reveal
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.core.ui.tone
import com.ricordella.app.domain.model.ItemGroup
import com.ricordella.app.domain.model.ItemWithCategory

@Composable
fun ItemListScreen(navigator: AppNavigator, onAdd: () -> Unit) {
    val viewModel = appViewModel { c, _ -> ItemListViewModel(c.itemRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tracker = rememberRevealTracker()
    val colors = MaterialTheme.ricordellaColors

    TopLevelScaffold(title = "Cose", navigator = navigator, onAdd = onAdd) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            modifier = Modifier.fillMaxSize().contentWidth(),
            contentPadding = PaddingValues(
                start = RicordellaDimensions.screenPadding,
                end = RicordellaDimensions.screenPadding,
                top = padding.calculateTopPadding(),
                bottom = 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
            horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                SearchField(state.query, viewModel::onQueryChange, placeholder = "Cerca per nome, marca, modello, targa")
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "groups") {
                IconChipRow {
                    IconChoiceChip(
                        label = "Tutte",
                        icon = Icons.Rounded.Apps,
                        tone = colors.cyan,
                        selected = state.groupFilter == null,
                        onClick = { viewModel.onGroupFilter(null) },
                        count = state.counts.values.sum(),
                    )
                    ItemGroup.entries.forEach { group ->
                        IconChoiceChip(
                            label = group.label,
                            icon = group.icon,
                            tone = group.tone,
                            selected = state.groupFilter == group,
                            onClick = { viewModel.onGroupFilter(if (state.groupFilter == group) null else group) },
                            count = state.counts[group] ?: 0,
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "archived") {
                IconChipRow {
                    IconChoiceChip("Attive", Icons.Rounded.Inventory2, colors.mint, !state.showArchived, onClick = { viewModel.onShowArchived(false) })
                    IconChoiceChip("Archiviate", Icons.Rounded.Archive, colors.lavender, state.showArchived, onClick = { viewModel.onShowArchived(true) })
                }
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
                itemsIndexed(entries, key = { _, it -> it.item.id }) { index, entry ->
                    ItemTile(
                        entry,
                        onClick = { navigator.openItem(entry.item.id) },
                        modifier = Modifier.animateItem().reveal(tracker, entry.item.id, index),
                    )
                }
            }
        }
    }
}

/** Tessera di una cosa: icona (o foto) grande, nome, categoria e i dati chiave con le loro icone. */
@Composable
private fun ItemTile(entry: ItemWithCategory, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val item = entry.item
    val tone = entry.group.tone
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    val dateFormat = LocalAppSettings.current.dateFormat
    Column(
        modifier
            .pressScale(interaction, pressedScale = 0.95f)
            .clip(MaterialTheme.shapes.large)
            .background(tone.container)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(RicordellaDimensions.spaceM)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val badge: @Composable () -> Unit = {
            Box(Modifier.size(52.dp).background(tone.solid, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Icon(entry.category?.kind.icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(28.dp))
            }
        }
        val photo = item.photoUri
        if (photo != null) {
            UriImage(photo, contentDescription = null, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)), maxSizePx = 192, fallback = badge)
        } else {
            badge()
        }
        Text(item.name, style = MaterialTheme.typography.titleMedium, color = tone.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val subtitle = listOfNotNull(entry.category?.name, item.brand, item.model).joinToString(" · ")
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tone.content.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        item.licensePlate?.let { Fact(Icons.Rounded.Pin, it) }
        item.odometerKm?.let { Fact(Icons.Rounded.Speed, DateTexts.kilometers(it)) }
        item.warrantyEndDate?.let { Fact(Icons.Rounded.VerifiedUser, "Garanzia al " + DateTexts.date(it, dateFormat)) }
    }
}

@Composable
private fun Fact(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(20.dp).background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
