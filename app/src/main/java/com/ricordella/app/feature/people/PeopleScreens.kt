package com.ricordella.app.feature.people

import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.model.ageOn
import com.ricordella.app.domain.date.RelativeDateDescriber
import java.time.LocalDate
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ricordella.app.core.ui.RicordellaMotion
import com.ricordella.app.core.ui.pressScale
import com.ricordella.app.core.ui.rememberReducedMotion
import com.ricordella.app.core.ui.rememberRevealTracker
import com.ricordella.app.core.ui.reveal
import com.ricordella.app.core.ui.toneFor
import com.ricordella.app.core.ui.theme.ricordellaColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
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
import androidx.compose.material.icons.rounded.Check
import com.ricordella.app.core.ui.PushButton
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
    val viewModel = appViewModel { c, _ -> PersonListViewModel(c.personRepository, c.reminderRepository, c.time) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tracker = rememberRevealTracker()

    TopLevelScaffold(title = tr("Persone"), navigator = navigator, onAdd = onAdd) { padding ->
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
            val next = state.next
            if (!state.showArchived && state.query.isBlank() && next != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "next") {
                    NextWithPeople(next, state.today, onClick = { navigator.openReminder(next.reminder.id) }, modifier = Modifier.reveal(tracker, "next", 0))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                SearchField(state.query, viewModel::onQueryChange, placeholder = tr("Cerca persone"))
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "archived") {
                FilterChipRow(
                    options = listOf(false, true),
                    selected = state.showArchived,
                    label = { if (it) tr("Archiviate") else tr("Attive") },
                    onSelected = viewModel::onShowArchived,
                )
            }
            if (!state.isLoading && state.people.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.People,
                        title = if (state.showArchived) tr("Nessuna persona archiviata") else tr("Nessuna persona"),
                        message = tr("Aggiungi le persone a cui collegare promemoria e cose."),
                        actionLabel = if (state.showArchived) null else tr("Aggiungi persona"),
                        onAction = navigator::newPerson,
                    )
                }
            }
            itemsIndexed(state.people, key = { _, it -> it.id }) { index, person ->
                PersonTile(
                    person,
                    onClick = { navigator.openPerson(person.id) },
                    modifier = Modifier.animateItem().reveal(tracker, person.id, index + 1),
                )
            }
        }
    }
}

/**
 * Il prossimo appuntamento con le tue persone (es. un compleanno): cosa, quando e con chi.
 * Gli avatar arrivano uno dopo l'altro e ondeggiano piano; un tocco apre il promemoria.
 */
@Composable
private fun NextWithPeople(entry: ReminderWithLinks, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tone = MaterialTheme.ricordellaColors.coral
    val reminder = entry.reminder
    val reduced = rememberReducedMotion()
    val wave = if (reduced) 0f else rememberInfiniteTransition(label = "circle").animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "wave",
    ).value
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = RicordellaDimensions.spaceS)
            .clip(MaterialTheme.shapes.large)
            .background(tone.container)
            .clickable(onClick = onClick)
            .padding(RicordellaDimensions.spaceL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tr("Prossimo appuntamento"), style = MaterialTheme.typography.labelLarge, color = tone.content)
            Text(reminder.title, style = MaterialTheme.typography.titleLarge, color = tone.content, maxLines = 2)
            val age = reminder.ageOn(reminder.dueDate)?.let { trf(" · compie %1\$s anni", it) }.orEmpty()
            Text(
                RelativeDateDescriber.describe(reminder.dueDate, today).replaceFirstChar { it.uppercase() } + age,
                style = MaterialTheme.typography.bodyMedium,
                color = tone.content,
            )
        }
        Box {
            entry.people.take(4).forEachIndexed { index, person ->
                val appear = remember { Animatable(if (reduced) 1f else 0f) }
                LaunchedEffect(Unit) {
                    delay(120L + index * 90L)
                    appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
                }
                PersonAvatar(
                    person,
                    size = 44.dp,
                    modifier = Modifier
                        .padding(start = (index * 28).dp)
                        .graphicsLayer {
                            scaleX = appear.value
                            scaleY = appear.value
                            translationY = sin((wave + index * 0.2f) * 2 * PI).toFloat() * 3.dp.toPx()
                        }
                        .border(3.dp, tone.container, CircleShape),
                )
            }
        }
    }
}

/** Scheda persona colorata: avatar grande con anello, nome e nota; si comprime al tocco. */
@Composable
private fun PersonTile(person: Person, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tone = toneFor(person.id)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tilt by animateFloatAsState(if (pressed) -6f else 0f, tween(RicordellaMotion.SHORT, easing = RicordellaMotion.Snappy), label = "tilt")
    Column(
        modifier
            .pressScale(interaction, pressedScale = 0.95f)
            .clip(MaterialTheme.shapes.large)
            .background(tone.container)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(RicordellaDimensions.spaceL)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
    ) {
        Box(
            Modifier
                .graphicsLayer { rotationZ = tilt }
                .border(3.dp, tone.solid, CircleShape)
                .padding(4.dp),
        ) {
            PersonAvatar(person, size = 64.dp)
        }
        Text(
            person.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = tone.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            person.notes?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() } ?: " ",
            style = MaterialTheme.typography.bodySmall,
            color = tone.content.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
        title = if (form.isNew) tr("Nuova persona") else tr("Modifica persona"),
        onBack = navigator::back,
        snackbarHostState = snackbar,
        actions = { TextButton(onClick = viewModel::save, enabled = !form.isLoading) { Text(tr("Salva")) } },
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
                    id = viewModel.stableId,
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
                        Text(if (form.photoUri == null) tr("Aggiungi foto") else tr("Cambia foto"), modifier = Modifier.padding(start = 8.dp))
                    }
                    if (form.photoUri != null) {
                        TextButton(onClick = { viewModel.update { it.copy(photoUri = null) } }) { Text(tr("Rimuovi foto")) }
                    }
                }
            }
            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> viewModel.update { it.copy(name = value) } },
                label = { Text(tr("Nome *")) },
                isError = form.nameError,
                supportingText = if (form.nameError) ({ Text(tr("Inserisci un nome")) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.surname,
                onValueChange = { value -> viewModel.update { it.copy(surname = value) } },
                label = { Text(tr("Cognome")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.notes,
                onValueChange = { value -> viewModel.update { it.copy(notes = value) } },
                label = { Text(tr("Note (es. Mamma, Partner)")) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            PushButton(text = tr("Salva"), onClick = viewModel::save, icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth())
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
        title = person?.displayName ?: tr("Persona"),
        onBack = navigator::back,
        actions = {
            if (person != null) {
                IconButton(onClick = { navigator.editPerson(person.id) }) { Icon(Icons.Rounded.Edit, contentDescription = tr("Modifica")) }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = tr("Altre azioni")) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (person.isArchived) tr("Ripristina dall'archivio") else tr("Archivia")) },
                            onClick = { menuOpen = false; viewModel.onToggleArchived() },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Elimina")) },
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
            PersonHero(
                person,
                onAddReminder = { navigator.newReminder(personId = person.id) },
                onAddItem = { navigator.newItem(personId = person.id) },
            )

            ReminderSection(tr("Oggi"), state.today, state, navigator, viewModel, ReminderDateMode.RELATIVE)
            ReminderSection(tr("Prossimi eventi"), state.events, state, navigator, viewModel, ReminderDateMode.ABSOLUTE)
            ReminderSection(tr("Promemoria"), state.reminders, state, navigator, viewModel, ReminderDateMode.ABSOLUTE)
            if (state.items.isNotEmpty()) {
                SectionHeader(tr("Cose"))
                state.items.forEach { entry -> ItemCard(entry, onClick = { navigator.openItem(entry.item.id) }) }
            }
            com.ricordella.app.core.ui.EditDeleteRow(onEdit = { navigator.editPerson(person.id) }, onDelete = { confirmDelete = true })
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = tr("Eliminare la persona?"),
            message = tr("La persona verrà eliminata. I promemoria e le cose collegate restano, ma senza questo collegamento. ") +
                tr("Se vuoi solo nasconderla, usa \"Archivia\". ") +
                tr("Lo ritrovi nel Cestino (Impostazioni) per 7 giorni."),
            confirmLabel = tr("Elimina"),
            destructive = true,
            onConfirm = viewModel::onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Intestazione della scheda persona: fascia colorata, avatar che "atterra" e azioni rapide. */
@Composable
private fun PersonHero(person: Person, onAddReminder: () -> Unit, onAddItem: () -> Unit) {
    val tone = toneFor(person.id)
    val reduced = rememberReducedMotion()
    val pop = remember { Animatable(if (reduced) 1f else 0.4f) }
    LaunchedEffect(person.id) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(tone.container, MaterialTheme.shapes.extraLarge)
            .padding(RicordellaDimensions.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
    ) {
        Box(
            Modifier
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = (1f - pop.value) * -20f }
                .border(4.dp, tone.solid, CircleShape)
                .padding(5.dp),
        ) { PersonAvatar(person, size = 96.dp) }
        Text(person.displayName, style = MaterialTheme.typography.headlineSmall, color = tone.content, textAlign = TextAlign.Center)
        if (person.isArchived) Text(tr("Archiviata"), style = MaterialTheme.typography.labelLarge, color = tone.content)
        person.notes?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = tone.content, textAlign = TextAlign.Center) }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS), modifier = Modifier.padding(top = RicordellaDimensions.spaceS)) {
            PushButton(tr("Promemoria"), onClick = onAddReminder, icon = Icons.Rounded.Add)
            PushButton(tr("Cosa"), onClick = onAddItem, icon = Icons.Rounded.Add)
        }
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
