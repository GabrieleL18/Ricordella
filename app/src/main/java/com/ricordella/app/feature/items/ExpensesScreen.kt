package com.ricordella.app.feature.items

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.Lang
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.navigation.AppNavigator
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.HistoryItem
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.ExpenseKind
import com.ricordella.app.domain.model.ExpenseStats
import com.ricordella.app.domain.model.ItemWithCategory
import com.ricordella.app.domain.model.MaintenanceRecord
import com.ricordella.app.domain.repository.ItemRepository
import com.ricordella.app.domain.repository.MaintenanceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle

/** Riepilogo delle spese di un anno, di tutte le cose. */
data class ExpensesUiState(
    val year: Int,
    val stats: ExpenseStats,
    val records: List<MaintenanceRecord>,
    val items: Map<String, ItemWithCategory>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModel(items: ItemRepository, maintenance: MaintenanceRepository, today: LocalDate) : ViewModel() {
    private val year = MutableStateFlow(today.year)

    val uiState: StateFlow<ExpensesUiState?> = combine(
        year.flatMapLatest { y -> maintenance.observeSince(LocalDate.of(y - 1, 1, 1)) },
        year,
        combine(items.observeItems(archived = false), items.observeItems(archived = true)) { a, b -> (a + b).associateBy { it.item.id } },
    ) { records, y, byId ->
        ExpensesUiState(
            year = y,
            stats = ExpenseStats.of(records, y, today),
            records = records.filter { it.date.year == y && (it.costCents ?: 0) > 0 }.sortedByDescending { it.date },
            items = byId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onShiftYear(delta: Int) {
        year.value += delta
    }
}

private val ExpenseKind.summaryLabel: String
    get() = when (this) {
        ExpenseKind.SERVICE -> tr("Manutenzione")
        ExpenseKind.FUEL -> tr("Rifornimenti")
        ExpenseKind.OTHER -> tr("Altre spese")
    }

/**
 * Le spese di tutte le cose in un anno: totale con il confronto con l'anno prima, i mesi
 * a barre, per tipo, per cosa e l'elenco completo. Si sfoglia da un anno all'altro.
 */
@Composable
fun ExpensesScreen(navigator: AppNavigator) {
    val viewModel = appViewModel { c, _ -> ExpensesViewModel(c.itemRepository, c.maintenanceRepository, c.time.today()) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DetailScaffold(title = tr("Spese"), onBack = navigator::back) { padding ->
        val current = state ?: return@DetailScaffold
        val stats = current.stats
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .contentWidth()
                .padding(horizontal = RicordellaDimensions.screenPadding)
                .padding(bottom = RicordellaDimensions.spaceXl),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.onShiftYear(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = tr("Anno precedente")) }
                Text(current.year.toString(), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.onShiftYear(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = tr("Anno successivo")) }
            }
            TotalCard(stats)
            if (stats.totalCents > 0) {
                MonthsCard(stats)
                BreakdownCard(
                    title = tr("Per tipo"),
                    rows = ExpenseKind.entries.mapNotNull { kind ->
                        stats.byKind[kind]?.let { BreakdownRow(kind.summaryLabel, it, kind.icon, kind.tone()) }
                    },
                    total = stats.totalCents,
                )
                val mint = MaterialTheme.ricordellaColors.mint
                BreakdownCard(
                    title = tr("Per cosa"),
                    rows = stats.byItem.entries.sortedByDescending { it.value }.map { (id, cents) ->
                        BreakdownRow(current.items[id]?.item?.name ?: tr("Cosa eliminata"), cents, Icons.Rounded.Inventory2, mint, onClick = { navigator.openItem(id) })
                    },
                    total = stats.totalCents,
                )
                SectionHeader(trf("Tutte le spese del %1\$s", current.year), icon = Icons.Rounded.Euro, tone = mint)
                Column {
                    current.records.forEachIndexed { index, record ->
                        HistoryItem(
                            date = record.date,
                            title = record.title,
                            tone = record.kind.tone(),
                            isLast = index == current.records.lastIndex,
                            subtitle = current.items[record.itemId]?.item?.name,
                            badge = DateTexts.money(record.costCents ?: 0) to mint,
                            modifier = Modifier.clickable { navigator.openItem(record.itemId) },
                        )
                    }
                }
            }
        }
    }
}

/** Totale dell'anno, freccia su/giù rispetto all'anno prima, media al mese e spesa più alta. */
@Composable
private fun TotalCard(stats: ExpenseStats) {
    val colors = MaterialTheme.ricordellaColors
    val mint = colors.mint
    Column(
        Modifier.fillMaxWidth().background(mint.container, MaterialTheme.shapes.large).padding(RicordellaDimensions.spaceL),
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM)) {
            Box(Modifier.size(44.dp).background(mint.solid, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Euro, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest)
            }
            Column(Modifier.weight(1f)) {
                Text(trf("Spese %1\$s", stats.year), style = MaterialTheme.typography.labelLarge, color = mint.content)
                Text(DateTexts.money(stats.totalCents), style = MaterialTheme.typography.headlineMedium, color = mint.content)
            }
            stats.changePercent?.let { change ->
                // Spendere di più è "corallo", di meno "menta", come nel resto dell'app.
                val up = change > 0
                val tone = if (up) colors.coral else colors.mint
                Row(
                    Modifier.background(if (up) tone.solid else MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val ink = if (up) MaterialTheme.colorScheme.surfaceContainerLowest else mint.content
                    Icon(if (up) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
                    Text((if (up) "+" else "") + "$change%", style = MaterialTheme.typography.labelLarge, color = ink)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            Fact(tr("Anno prima"), DateTexts.money(stats.previousYearCents), Modifier.weight(1f))
            Fact(tr("Media al mese"), DateTexts.money(stats.averagePerMonthCents), Modifier.weight(1f))
        }
        stats.biggest?.let { biggest ->
            Fact(tr("La spesa più alta"), "${biggest.title} · ${DateTexts.money(biggest.costCents ?: 0)}", Modifier.fillMaxWidth())
        }
        if (stats.totalCents == 0L) {
            Text(tr("Nessuna spesa registrata in quest'anno."), style = MaterialTheme.typography.bodyMedium, color = mint.content)
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** I dodici mesi a barre; toccando una barra se ne legge l'importo. */
@Composable
private fun MonthsCard(stats: ExpenseStats) {
    val colors = MaterialTheme.ricordellaColors
    val max = stats.monthly.max().coerceAtLeast(1)
    var picked by rememberSaveable(stats.year) { mutableIntStateOf(stats.monthly.indexOf(stats.monthly.max())) }
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large).padding(RicordellaDimensions.spaceL),
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
    ) {
        Text(tr("Mese per mese"), style = MaterialTheme.typography.titleMedium)
        val month = Month.of(picked + 1).getDisplayName(TextStyle.FULL_STANDALONE, Lang.locale).replaceFirstChar { it.uppercase() }
        Text("$month: ${DateTexts.money(stats.monthly[picked])}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            stats.monthly.forEachIndexed { index, cents ->
                val fraction by animateFloatAsState((cents.toFloat() / max).coerceAtLeast(if (cents > 0) 0.04f else 0f), tween(500), label = "bar")
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { picked = index },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .fillMaxHeight(fraction.coerceAtLeast(0.001f))
                            .background(if (index == picked) colors.bolt else colors.mint.solid, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                    )
                    Text(
                        Month.of(index + 1).getDisplayName(TextStyle.NARROW_STANDALONE, Lang.locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (index == picked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private class BreakdownRow(val label: String, val cents: Long, val icon: ImageVector, val tone: Tone, val onClick: (() -> Unit)? = null)

/** Ripartizione in righe con barra di avanzamento e percentuale sul totale. */
@Composable
private fun BreakdownCard(title: String, rows: List<BreakdownRow>, total: Long) {
    if (rows.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large).padding(RicordellaDimensions.spaceL),
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        rows.forEach { row ->
            val share = row.cents.toFloat() / total.coerceAtLeast(1)
            Row(
                Modifier.fillMaxWidth().then(if (row.onClick != null) Modifier.clickable(onClick = row.onClick) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
            ) {
                Box(Modifier.size(32.dp).background(row.tone.container, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(row.icon, contentDescription = null, tint = row.tone.content, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text(row.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(DateTexts.money(row.cents), style = MaterialTheme.typography.titleSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).height(8.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)) {
                            Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).fillMaxHeight().background(row.tone.solid, CircleShape))
                        }
                        Text("${Math.round(share * 100)}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
