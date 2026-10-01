package com.ricordella.app.feature.items

import com.ricordella.app.core.ui.label

import com.ricordella.app.core.i18n.tr

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ricordella.app.core.navigation.ItemEditRoute
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.Category
import com.ricordella.app.domain.model.Item
import com.ricordella.app.domain.model.ItemGroup
import com.ricordella.app.core.ui.ScannedReceipt
import com.ricordella.app.domain.model.ItemReminderTemplates
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.PersonItemRole
import com.ricordella.app.domain.model.ReminderSuggestion
import com.ricordella.app.domain.repository.ItemRepository
import com.ricordella.app.domain.repository.PersonRepository
import com.ricordella.app.domain.usecase.SaveItemUseCase
import com.ricordella.app.domain.usecase.SelectedSuggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SuggestionChoice(val suggestion: ReminderSuggestion, val selected: Boolean, val firstDueDate: LocalDate)

data class ItemForm(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val category: Category? = null,
    val brand: String = "",
    val model: String = "",
    val serialNumber: String = "",
    val licensePlate: String = "",
    val productionYear: String = "",
    val odometerKm: String = "",
    val purchaseDate: LocalDate? = null,
    val purchasePrice: String = "",
    val hasWarranty: Boolean = false,
    val warrantyStart: LocalDate? = null,
    val warrantyEnd: LocalDate? = null,
    val warrantySeller: String = "",
    val warrantyDocumentUri: String? = null,
    val notes: String = "",
    val photoUri: String? = null,
    val people: Map<String, PersonItemRole> = emptyMap(),
    val suggestions: List<SuggestionChoice> = emptyList(),
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null,
) {
    val group: ItemGroup get() = category?.itemGroup ?: ItemGroup.GENERIC
    val isVehicle: Boolean get() = group == ItemGroup.VEHICLES
    val nameError: Boolean get() = showErrors && name.isBlank()
    val warrantyError: Boolean get() = showErrors && hasWarranty && warrantyEnd == null
}

class ItemEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val items: ItemRepository,
    people: PersonRepository,
    private val saveItem: SaveItemUseCase,
    private val time: TimeSource,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ItemEditRoute>()
    private var existing: Item? = null

    private val _form = MutableStateFlow(ItemForm())
    val form: StateFlow<ItemForm> = _form.asStateFlow()

    val categories: StateFlow<List<Category>> =
        items.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val people: StateFlow<List<Person>> =
        people.observePeople(archived = false).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val entry = route.id?.let { items.getItem(it) }
        if (entry == null) {
            _form.value = ItemForm(
                isLoading = false,
                people = route.personId?.let { mapOf(it to PersonItemRole.OWNER) }.orEmpty(),
            )
            return
        }
        val item = entry.item
        existing = item
        _form.value = ItemForm(
            isLoading = false,
            isNew = false,
            name = item.name,
            category = entry.category,
            brand = item.brand.orEmpty(),
            model = item.model.orEmpty(),
            serialNumber = item.serialNumber.orEmpty(),
            licensePlate = item.licensePlate.orEmpty(),
            productionYear = item.productionYear?.toString().orEmpty(),
            odometerKm = item.odometerKm?.toString().orEmpty(),
            purchaseDate = item.purchaseDate,
            purchasePrice = item.purchasePriceCents?.let(::formatCents).orEmpty(),
            hasWarranty = item.warrantyEndDate != null,
            warrantyStart = item.warrantyStartDate,
            warrantyEnd = item.warrantyEndDate,
            warrantySeller = item.warrantySeller.orEmpty(),
            warrantyDocumentUri = item.warrantyDocumentUri,
            notes = item.notes.orEmpty(),
            photoUri = item.photoUri,
            people = items.getOwners(item.id).associate { it.person.id to it.role },
        )
    }

    fun update(transform: (ItemForm) -> ItemForm) = _form.update(transform)

    /**
     * Compila il modulo dallo scontrino: data e prezzo d'acquisto, nome (se vuoto), e la garanzia
     * di 2 anni (quella legale in Italia) dal giorno d'acquisto, con lo scontrino come documento.
     */
    fun onReceiptScanned(receipt: ScannedReceipt) = _form.update { form ->
        val info = receipt.info
        val purchase = info.date ?: form.purchaseDate
        val warrantyStart = purchase ?: time.today()
        form.copy(
            name = form.name.ifBlank { info.product.orEmpty() },
            purchaseDate = purchase,
            purchasePrice = info.totalCents?.let(::formatCents) ?: form.purchasePrice,
            hasWarranty = true,
            warrantyStart = form.warrantyStart ?: warrantyStart,
            warrantyEnd = form.warrantyEnd ?: warrantyStart.plusMonths(24),
            warrantySeller = form.warrantySeller.ifBlank { info.store.orEmpty() },
            warrantyDocumentUri = receipt.imageUri ?: form.warrantyDocumentUri,
            errorMessage = tr("Ho letto lo scontrino: controlla i dati"),
        )
    }

    fun onErrorShown() = _form.update { it.copy(errorMessage = null) }

    /** Alla scelta della categoria vengono proposti i promemoria tipici (solo in creazione). */
    fun onCategorySelected(category: Category) {
        val today = time.today()
        _form.update { form ->
            val suggestions = if (form.isNew) {
                ItemReminderTemplates.suggestionsFor(category.kind).map {
                    SuggestionChoice(it, it.selectedByDefault, it.firstDueDate(today))
                }
            } else {
                form.suggestions
            }
            form.copy(
                category = category,
                name = form.name.ifBlank { if (category.isBuiltIn) category.label else "" },
                suggestions = suggestions,
            )
        }
    }

    /** Imposta la fine garanzia come inizio + mesi (inizio = data acquisto se non indicato). */
    fun onWarrantyDuration(months: Long) {
        _form.update { form ->
            val start = form.warrantyStart ?: form.purchaseDate ?: time.today()
            form.copy(warrantyStart = start, warrantyEnd = start.plusMonths(months))
        }
    }

    fun onToggleSuggestion(index: Int) = _form.update { form ->
        form.copy(suggestions = form.suggestions.mapIndexed { i, s -> if (i == index) s.copy(selected = !s.selected) else s })
    }

    fun onSuggestionDate(index: Int, date: LocalDate) = _form.update { form ->
        form.copy(suggestions = form.suggestions.mapIndexed { i, s -> if (i == index) s.copy(firstDueDate = date) else s })
    }

    fun save() {
        val form = _form.value
        if (form.name.isBlank() || (form.hasWarranty && form.warrantyEnd == null)) {
            _form.update { it.copy(showErrors = true) }
            return
        }
        _form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                saveItem(buildItem(form), form.people, form.suggestions.filter { it.selected }.map {
                    SelectedSuggestion(it.suggestion, it.firstDueDate)
                })
                _form.update { it.copy(isSaving = false, saved = true) }
            } catch (_: Exception) {
                _form.update { it.copy(isSaving = false, errorMessage = tr("Non è stato possibile salvare. Riprova.")) }
            }
        }
    }

    private fun buildItem(form: ItemForm): Item {
        val now = time.now()
        val base = existing ?: Item(name = "", createdAt = now, updatedAt = now)
        val vehicle = form.isVehicle
        return base.copy(
            name = form.name.trim(),
            categoryId = form.category?.id,
            brand = form.brand.trim().ifEmpty { null },
            model = form.model.trim().ifEmpty { null },
            serialNumber = form.serialNumber.trim().ifEmpty { null },
            licensePlate = if (vehicle) form.licensePlate.trim().uppercase().ifEmpty { null } else null,
            productionYear = form.productionYear.toIntOrNull(),
            odometerKm = if (vehicle) form.odometerKm.toIntOrNull() else null,
            purchaseDate = form.purchaseDate,
            purchasePriceCents = parseCents(form.purchasePrice),
            warrantyStartDate = if (form.hasWarranty) form.warrantyStart ?: form.purchaseDate else null,
            warrantyEndDate = if (form.hasWarranty) form.warrantyEnd else null,
            warrantySeller = if (form.hasWarranty) form.warrantySeller.trim().ifEmpty { null } else null,
            warrantyDocumentUri = if (form.hasWarranty) form.warrantyDocumentUri else null,
            notes = form.notes.trim().ifEmpty { null },
            photoUri = form.photoUri,
        )
    }
}

/** "499,90" o "499.90" → 49990 centesimi. */
fun parseCents(text: String): Long? {
    val normalized = text.trim().replace("€", "").replace(" ", "").let {
        if (it.contains(',')) it.replace(".", "").replace(',', '.') else it
    }
    return normalized.toBigDecimalOrNull()?.movePointRight(2)?.toLong()
}

fun formatCents(cents: Long): String = "%d,%02d".format(cents / 100, cents % 100)
