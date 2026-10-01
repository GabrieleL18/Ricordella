package com.ricordella.app.feature.items

import com.ricordella.app.core.ui.label

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricordella.app.domain.model.ItemGroup
import com.ricordella.app.domain.model.ItemWithCategory
import com.ricordella.app.domain.repository.ItemRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import com.ricordella.app.domain.model.ExpenseStats
import com.ricordella.app.domain.repository.MaintenanceRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class ItemListUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val showArchived: Boolean = false,
    val groupFilter: ItemGroup? = null,
    val groups: Map<ItemGroup, List<ItemWithCategory>> = emptyMap(),
    /** Quante cose ci sono per categoria, indipendentemente dal filtro scelto. */
    val counts: Map<ItemGroup, Int> = emptyMap(),
) {
    val isEmpty: Boolean get() = groups.values.all { it.isEmpty() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ItemListViewModel(items: ItemRepository, maintenance: MaintenanceRepository) : ViewModel() {

    /** Spese di tutte le cose: anno corrente e anno scorso, per il riquadro in cima. */
    val expenses: StateFlow<ExpenseStats?> = LocalDate.now().year.let { year ->
        maintenance.observeSince(LocalDate.of(year - 1, 1, 1))
            .map { records -> ExpenseStats.of(records, year).takeIf { it.totalCents > 0 || it.previousYearCents > 0 } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }


    private val query = MutableStateFlow("")
    private val showArchived = MutableStateFlow(false)
    private val groupFilter = MutableStateFlow<ItemGroup?>(null)

    val uiState: StateFlow<ItemListUiState> = combine(
        showArchived.flatMapLatest { items.observeItems(archived = it) },
        query,
        showArchived,
        groupFilter,
    ) { list, text, archived, group ->
        val needle = text.trim()
        val filtered = list.filter { entry ->
            (group == null || entry.group == group) &&
                (needle.isEmpty() || listOfNotNull(entry.item.name, entry.item.brand, entry.item.model, entry.item.licensePlate, entry.category?.label)
                    .any { it.contains(needle, ignoreCase = true) })
        }
        ItemListUiState(
            isLoading = false,
            query = text,
            showArchived = archived,
            groupFilter = group,
            groups = ItemGroup.entries.associateWith { g -> filtered.filter { it.group == g } }.filterValues { it.isNotEmpty() },
            counts = list.groupingBy { it.group }.eachCount(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemListUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onShowArchived(value: Boolean) {
        showArchived.value = value
    }

    fun onGroupFilter(group: ItemGroup?) {
        groupFilter.value = group
    }
}
