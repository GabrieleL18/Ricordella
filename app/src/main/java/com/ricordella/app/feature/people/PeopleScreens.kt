package com.ricordella.app.feature.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.navigation.AppNavigator
import com.ricordella.app.core.ui.ConfirmDialog
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.EmptyState
import com.ricordella.app.core.ui.FilterChipRow
import com.ricordella.app.core.ui.ItemCard
import com.ricordella.app.core.ui.PersonAvatar
import com.ricordella.app.core.ui.PersonCard
import com.ricordella.app.core.ui.ReminderCard
import com.ricordella.app.core.ui.ReminderDateMode
import com.ricordella.app.core.ui.SearchField
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.TopLevelScaffold
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.rememberPhotoPicker
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.displayName
import java.time.Instant

@Composable
fun PersonListScreen(navigator: AppNavigator, onAdd: () -> Unit) {
    val viewModel = appViewModel { c, _ -> PersonListViewModel(c.personRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TopLevelScaffold(title = "Persone", navigator = navigator, onAdd = onAdd) { padding ->
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
                SearchField(state.query, viewModel::onQueryChange, placeholder = "Cerca persone")
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "archived") {
                FilterChipRow(
                    options = listOf(false, true),
                    selected = state.showArchived,
                    label = { if (it) "Archiviate" else "Attive" },
                    onSelected = viewModel::onShowArchived,
                )
            }
            if (!state.isLoading && state.people.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.People,
                        title = if (state.showArchived) "Nessuna persona archiviata" else "Nessuna persona",
                        message = "Aggiungi le persone a cui collegare promemoria e cose.",
                        actionLabel = if (state.showArchived) null else "Aggiungi persona",
                        onAction = navigator::newPerson,
                    )
                }
            }
            items(state.people, key = { it.id }) { person ->
                PersonCard(person, onClick = { navigator.openPerson(person.id) }, subtitle = person.notes?.lineSequence()?.firstOrNull())
            }
        }
    }
}

@Composable
fun PersonEditScreen(navigator: AppNavigator) {
    val viewModel = appViewModel { c, handle -> PersonEditViewModel(handle, c.personRepository, c.time) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val pickPhoto = rememberPhotoPicker { uri -> viewModel.update { it.copy(photoUri = uri) } }

    LaunchedEffect(form.savedId) { if (form.savedId != null) navigator.back() }
    LaunchedEffect(form.errorMessage) {
        form.errorMessage?.let { snackbar.showSnackbar(it); viewModel.onErrorShown() }
    }

    DetailScaffold(
        title = if (form.isNew) "Nuova persona" else "Modifica persona",
        onBack = navigator::back,
        snackbarHostState = snackbar,
        actions = { TextButton(onClick = viewModel::save, enabled = !form.isLoading) { Text("Salva") } },
    ) { padding ->
        if (form.isLoading) return@DetailScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .contentWidth()
                .padding(RicordellaDimensions.screenPadding),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceL)) {
                val preview = Person(
                    name = form.name.ifBlank { "?" },
                    surname = form.surname,
                    photoUri = form.photoUri,
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                )
                PersonAvatar(preview, size = 72.dp)
                Column {
                    OutlinedButton(onClick = pickPhoto) {
                        Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                        Text(if (form.photoUri == null) "Aggiungi foto" else "Cambia foto", modifier = Modifier.padding(start = 8.dp))
                    }
                    if (form.photoUri != null) {
                        TextButton(onClick = { viewModel.update { it.copy(photoUri = null) } }) { Text("Rimuovi foto") }
                    }
                }
            }
            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> viewModel.update { it.copy(name = value) } },
                label = { Text("Nome *") },
                isError = form.nameError,
                supportingText = if (form.nameError) ({ Text("Inserisci un nome") }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.surname,
                onValueChange = { value -> viewModel.update { it.copy(surname = value) } },
                label = { Text("Cognome") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.notes,
                onValueChange = { value -> viewModel.update { it.copy(notes = value) } },
                label = { Text("Note (es. Mamma, Partner)") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text("Salva") }
        }
    }
}

@Composable
fun PersonDetailScreen(navigator: AppNavigator) {
    val viewModel = appViewModel { c, handle ->
        PersonDetailViewModel(handle, c.personRepository, c.reminderRepository, c.itemRepository, c.completeReminder, c.time)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.deleted) { if (state.deleted) navigator.back() }

    val person = state.person
    DetailScaffold(
        title = person?.displayName ?: "Persona",
        onBack = navigator::back,
        actions = {
            if (person != null) {
                IconButton(onClick = { navigator.editPerson(person.id) }) { Icon(Icons.Rounded.Edit, contentDescription = "Modifica") }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Altre azioni") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (person.isArchived) "Ripristina dall'archivio" else "Archivia") },
                            onClick = { menuOpen = false; viewModel.onToggleArchived() },
                        )
                        DropdownMenuItem(
                            text = { Text("Elimina") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (person == null) return@DetailScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .contentWidth()
                .padding(RicordellaDimensions.screenPadding),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceL)) {
                PersonAvatar(person, size = 72.dp)
                Column {
                    Text(person.displayName, style = MaterialTheme.typography.headlineSmall)
                    if (person.isArchived) Text("Archiviata", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            person.notes?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                OutlinedButton(onClick = { navigator.newReminder(personId = person.id) }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("Promemoria", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = { navigator.newItem(personId = person.id) }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("Cosa", modifier = Modifier.padding(start = 6.dp))
                }
            }

            ReminderSection("Oggi", state.today, state, navigator, viewModel, ReminderDateMode.RELATIVE)
            ReminderSection("Prossimi eventi", state.events, state, navigator, viewModel, ReminderDateMode.ABSOLUTE)
            ReminderSection("Promemoria", state.reminders, state, navigator, viewModel, ReminderDateMode.ABSOLUTE)
            if (state.items.isNotEmpty()) {
                SectionHeader("Cose")
                state.items.forEach { entry -> ItemCard(entry, onClick = { navigator.openItem(entry.item.id) }) }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Eliminare la persona?",
            message = "La persona verrà eliminata. I promemoria e le cose collegate restano, ma senza questo collegamento. " +
                "Se vuoi solo nasconderla, usa \"Archivia\".",
            confirmLabel = "Elimina",
            destructive = true,
            onConfirm = viewModel::onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ReminderSection(
    title: String,
    reminders: List<com.ricordella.app.domain.model.ReminderWithLinks>,
    state: PersonDetailUiState,
    navigator: AppNavigator,
    viewModel: PersonDetailViewModel,
    dateMode: ReminderDateMode,
) {
    if (reminders.isEmpty()) return
    SectionHeader(title)
    reminders.forEach { entry ->
        ReminderCard(
            entry = entry,
            now = state.now,
            onClick = { navigator.openReminder(entry.reminder.id) },
            onToggleComplete = { viewModel.onCompleteReminder(entry.reminder.id) },
            dateMode = dateMode,
        )
    }
}
