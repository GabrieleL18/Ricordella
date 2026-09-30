package com.ricordella.app.feature.search

import com.ricordella.app.core.i18n.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.navigation.AppNavigator
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.EmptyState
import com.ricordella.app.core.ui.ItemCard
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.PersonCard
import com.ricordella.app.core.ui.ReminderCard
import com.ricordella.app.core.ui.SearchField
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.model.SearchResults
import com.ricordella.app.domain.usecase.GlobalSearchUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime

data class SearchUiState(
    val query: String = "",
    val results: SearchResults = SearchResults(),
    val searched: Boolean = false,
    val now: LocalDateTime = LocalDateTime.now(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(search: GlobalSearchUseCase, time: TimeSource) : ViewModel() {

    private val query = MutableStateFlow("")

    private val results = query.debounce(250).distinctUntilChanged().mapLatest { text -> text to search(text) }

    val uiState: StateFlow<SearchUiState> = combine(query, results) { text, (searchedText, found) ->
        SearchUiState(query = text, results = found, searched = searchedText.isNotBlank(), now = LocalDateTime.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState(now = time.localNow()))

    fun onQueryChange(value: String) {
        query.value = value
    }
}

@Composable
fun SearchScreen(navigator: AppNavigator) {
    val viewModel = appViewModel { c, _ -> SearchViewModel(c.globalSearch, c.time) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val settings = LocalAppSettings.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    DetailScaffold(title = tr("Cerca"), onBack = navigator::back) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).contentWidth(),
            contentPadding = PaddingValues(RicordellaDimensions.screenPadding),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
        ) {
            item(key = "field") {
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = tr("Persone, cose, promemoria, note…"),
                    modifier = Modifier.focusRequester(focusRequester),
                )
            }
            val results = state.results
            if (state.searched && results.isEmpty) {
                item(key = "empty") {
                    EmptyState(Icons.Rounded.SearchOff, tr("Nessun risultato"), tr("Prova con un'altra parola."))
                }
            }
            if (results.people.isNotEmpty()) {
                item(key = "people") { SectionHeader(tr("Persone")) }
                items(results.people, key = { "p-" + it.id }) { person ->
                    PersonCard(person, onClick = { navigator.openPerson(person.id) })
                }
            }
            if (results.items.isNotEmpty()) {
                item(key = "items") { SectionHeader(tr("Cose")) }
                items(results.items, key = { "i-" + it.item.id }) { entry ->
                    ItemCard(entry, onClick = { navigator.openItem(entry.item.id) })
                }
            }
            if (results.reminders.isNotEmpty()) {
                item(key = "reminders") { SectionHeader(tr("Promemoria ed eventi")) }
                items(results.reminders, key = { "r-" + it.reminder.id }) { entry ->
                    ReminderCard(entry, now = state.now, onClick = { navigator.openReminder(entry.reminder.id) })
                }
            }
            if (results.maintenance.isNotEmpty()) {
                item(key = "maintenance") { SectionHeader(tr("Manutenzioni")) }
                items(results.maintenance, key = { "m-" + it.id }) { record ->
                    ListItem(
                        headlineContent = { Text(record.title) },
                        supportingContent = {
                            Text(listOfNotNull(DateTexts.date(record.date, settings.dateFormat), record.description).joinToString(" · "))
                        },
                        leadingContent = { Icon(Icons.Rounded.Build, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable { navigator.openItem(record.itemId) },
                    )
                }
            }
        }
    }
}

