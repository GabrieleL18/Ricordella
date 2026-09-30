package com.ricordella.app.feature.items

import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.ricordella.app.core.ui.RicordellaMotion
import com.ricordella.app.core.ui.UiSound
import com.ricordella.app.core.ui.pressScale
import com.ricordella.app.core.ui.rememberReducedMotion
import com.ricordella.app.core.ui.rememberUiSounds
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.core.ui.tone
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import com.ricordella.app.core.ui.PushButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.ui.DateField
import com.ricordella.app.core.ui.DatePickerDialogFor
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.IconBadge
import com.ricordella.app.core.ui.MultiSelectDialog
import com.ricordella.app.core.ui.UriImage
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.icon
import com.ricordella.app.core.ui.label
import com.ricordella.app.core.ui.rememberFilePicker
import com.ricordella.app.core.ui.rememberPhotoPicker
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.domain.model.ItemGroup
import com.ricordella.app.domain.model.ItemReminderTemplates
import com.ricordella.app.domain.model.PersonItemRole
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.ReminderSuggestion
import com.ricordella.app.domain.model.displayName

private enum class ItemStep { CATEGORY, THING, DETAILS }

private val ItemGroup.examples: String
    get() = when (this) {
        ItemGroup.VEHICLES -> "Auto, moto, scooter"
        ItemGroup.HOME -> "Lavatrice, caldaia, TV"
        ItemGroup.ELECTRONICS -> "Smartphone, computer"
        ItemGroup.DOCUMENTS -> "Documenti, contratti"
        ItemGroup.GENERIC -> "Attrezzi e oggetti"
    }

/**
 * Aggiunta/modifica di una cosa, guidata in tre passi: prima la categoria
 * (veicoli, casa, elettronica, documenti, generico), poi la cosa (auto, lavatrice,
 * documento...), infine i dettagli raggruppati in schede. Ogni scelta ha animazione e suono.
 */
@Composable
fun ItemEditScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { c, handle -> ItemEditViewModel(handle, c.itemRepository, c.personRepository, c.saveItem, c.time) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val sounds = rememberUiSounds()
    var stepIndex by rememberSaveable { mutableIntStateOf(-1) }
    var group by rememberSaveable { mutableStateOf<ItemGroup?>(null) }

    LaunchedEffect(form.isLoading) {
        if (!form.isLoading && stepIndex == -1) {
            group = form.category?.itemGroup
            stepIndex = if (form.category == null) ItemStep.CATEGORY.ordinal else ItemStep.DETAILS.ordinal
        }
    }
    LaunchedEffect(form.saved) { if (form.saved) onBack() }
    LaunchedEffect(form.errorMessage) {
        form.errorMessage?.let { snackbar.showSnackbar(it); viewModel.onErrorShown() }
    }
    val step = ItemStep.entries.getOrNull(stepIndex)
    val goBack = {
        sounds(UiSound.BACK)
        stepIndex = if (step == ItemStep.DETAILS) ItemStep.CATEGORY.ordinal else stepIndex - 1
    }
    BackHandler(enabled = step == ItemStep.THING || (step == ItemStep.DETAILS && form.isNew)) { goBack() }

    DetailScaffold(
        title = if (form.isNew) "Nuova cosa" else "Modifica",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (step == ItemStep.DETAILS) TextButton(onClick = viewModel::save, enabled = !form.isSaving && !form.isLoading) { Text("Salva") }
        },
    ) { padding ->
        if (step == null) return@DetailScaffold
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            StepIndicator(step.ordinal, Modifier.contentWidth().padding(horizontal = RicordellaDimensions.screenPadding))
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally(tween(RicordellaMotion.LONG, easing = RicordellaMotion.EaseOut)) { if (forward) it / 2 else -it / 2 } + fadeIn()) togetherWith
                        (slideOutHorizontally(tween(RicordellaMotion.SHORT, easing = RicordellaMotion.EaseIn)) { if (forward) -it / 3 else it / 3 } + fadeOut())
                },
                label = "itemStep",
                modifier = Modifier.weight(1f),
            ) { current ->
                when (current) {
                    ItemStep.CATEGORY -> ChoiceGrid(
                        title = "Che tipo di cosa è?",
                        subtitle = "Scegli la categoria",
                        choices = ItemGroup.entries.map { g -> Choice(g.name, g.icon, g.label, g.examples, g.tone, g == group) },
                        onChoice = { key ->
                            sounds(UiSound.POP)
                            group = ItemGroup.valueOf(key)
                            stepIndex = ItemStep.THING.ordinal
                        },
                    )
                    ItemStep.THING -> {
                        val selectedGroup = group ?: ItemGroup.GENERIC
                        ChoiceGrid(
                            title = "Quale ${selectedGroup.label.lowercase()}?",
                            subtitle = "Scegli la cosa",
                            header = { GroupCrumb(selectedGroup, onChange = goBack) },
                            choices = categories.filter { it.itemGroup == selectedGroup }.map { category ->
                                Choice(category.id, category.kind.icon, category.name, null, selectedGroup.tone, category.id == form.category?.id)
                            },
                            onChoice = { id ->
                                sounds(UiSound.DING)
                                categories.firstOrNull { it.id == id }?.let(viewModel::onCategorySelected)
                                stepIndex = ItemStep.DETAILS.ordinal
                            },
                        )
                    }
                    ItemStep.DETAILS -> DetailsForm(form, viewModel, onChangeCategory = goBack)
                }
            }
        }
    }
}

/** Tre segmenti che si riempiono man mano che si avanza. */
@Composable
private fun StepIndicator(current: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.ricordellaColors
    Row(modifier.fillMaxWidth().padding(vertical = RicordellaDimensions.spaceS), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Categoria", "Cosa", "Dettagli").forEachIndexed { index, label ->
            val fill by animateFloatAsState(if (index <= current) 1f else 0f, tween(RicordellaMotion.LONG, easing = RicordellaMotion.EaseOut), label = "step")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Box(Modifier.fillMaxWidth(fill.coerceAtLeast(0.001f)).height(6.dp).background(colors.boltEdge, CircleShape))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class Choice(val key: String, val icon: ImageVector, val title: String, val subtitle: String?, val tone: Tone, val selected: Boolean)

@Composable
private fun ChoiceGrid(
    title: String,
    subtitle: String,
    choices: List<Choice>,
    onChoice: (String) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize().contentWidth(),
        contentPadding = PaddingValues(RicordellaDimensions.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceXs)) {
                header?.invoke()
                Text(subtitle.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
        }
        itemsIndexed(choices, key = { _, it -> it.key }) { index, choice ->
            ChoiceTile(choice, index, onClick = { onChoice(choice.key) })
        }
    }
}

/** Riquadro di scelta: entra a cascata con una molla, si comprime al tocco; se già scelto è bordato. */
@Composable
private fun ChoiceTile(choice: Choice, index: Int, onClick: () -> Unit) {
    val reduced = rememberReducedMotion()
    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
    }
    val interaction = remember { MutableInteractionSource() }
    val iconSpin by animateFloatAsState(if (choice.selected) 360f else 0f, tween(RicordellaMotion.LONG, easing = RicordellaMotion.EaseOut), label = "spin")
    Column(
        Modifier
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                val s = 0.8f + 0.2f * appear.value
                scaleX = s
                scaleY = s
            }
            .pressScale(interaction, pressedScale = 0.93f)
            .clip(MaterialTheme.shapes.large)
            .background(choice.tone.container)
            .border(if (choice.selected) 3.dp else 0.dp, if (choice.selected) choice.tone.solid else Color.Transparent, MaterialTheme.shapes.large)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(RicordellaDimensions.spaceL)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(52.dp).background(choice.tone.solid, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                choice.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.size(28.dp).graphicsLayer { rotationY = iconSpin },
            )
        }
        Text(choice.title, style = MaterialTheme.typography.titleMedium, color = choice.tone.content, maxLines = 2)
        choice.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = choice.tone.content) }
    }
}

/** Categoria (e cosa) scelta, con la possibilità di tornare indietro a cambiarla. */
@Composable
private fun GroupCrumb(group: ItemGroup, onChange: () -> Unit, kindName: String? = null) {
    val tone = group.tone
    Surface(color = tone.container, contentColor = tone.content, shape = CircleShape, onClick = onChange) {
        Row(
            Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(28.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
                Icon(group.icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(16.dp))
            }
            Text(listOfNotNull(group.label, kindName).joinToString("  ›  "), style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Rounded.Edit, contentDescription = "Cambia", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun DetailsForm(form: ItemForm, viewModel: ItemEditViewModel, onChangeCategory: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .contentWidth()
            .padding(horizontal = RicordellaDimensions.screenPadding, vertical = RicordellaDimensions.spaceS),
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        GroupCrumb(form.group, onChange = onChangeCategory, kindName = form.category?.name)
        FormSection("Com'è fatta", Icons.Rounded.Info, form.group.tone) {
            PhotoRow(form, viewModel)
            MainFields(form, viewModel)
        }
        if (ItemReminderTemplates.supportsWarranty(form.group)) {
            FormSection("Garanzia", Icons.Rounded.VerifiedUser, MaterialTheme.ricordellaColors.mint) { WarrantyFields(form, viewModel) }
        }
        FormSection("Persone", Icons.Rounded.People, MaterialTheme.ricordellaColors.coral) { PeopleFields(form, viewModel) }
        if (form.isNew && form.suggestions.isNotEmpty()) {
            FormSection("Cosa vuoi ricordare?", Icons.Rounded.NotificationsActive, MaterialTheme.ricordellaColors.pear) { SuggestionFields(form, viewModel) }
        }
        FormSection("Note", Icons.AutoMirrored.Rounded.Notes, MaterialTheme.ricordellaColors.lavender) {
            OutlinedTextField(
                value = form.notes,
                onValueChange = { value -> viewModel.update { it.copy(notes = value) } },
                label = { Text("Note") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PushButton(
            text = "Salva",
            onClick = viewModel::save,
            icon = Icons.Rounded.Check,
            loading = form.isSaving,
            modifier = Modifier.fillMaxWidth().padding(vertical = RicordellaDimensions.spaceL),
        )
    }
}

/** Scheda di un gruppo di campi, con icona colorata nell'intestazione. */
@Composable
private fun FormSection(title: String, icon: ImageVector, tone: Tone, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), MaterialTheme.shapes.large)
            .padding(RicordellaDimensions.spaceL)
            .animateContentSize(tween(RicordellaMotion.SHORT, easing = RicordellaMotion.EaseInOut)),
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            Box(Modifier.size(30.dp).background(tone.container, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(18.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

@Composable
private fun PhotoRow(form: ItemForm, viewModel: ItemEditViewModel) {
    val pickPhoto = rememberPhotoPicker { uri -> viewModel.update { it.copy(photoUri = uri) } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceL)) {
        val placeholder: @Composable () -> Unit = { IconBadge(form.category?.kind.icon, modifier = Modifier.size(72.dp)) }
        val photo = form.photoUri
        if (photo != null) {
            UriImage(photo, contentDescription = "Foto", modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)), fallback = placeholder)
        } else {
            placeholder()
        }
        Column {
            OutlinedButton(onClick = pickPhoto) {
                Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                Text(if (photo == null) "Aggiungi foto" else "Cambia foto", modifier = Modifier.padding(start = 8.dp))
            }
            if (photo != null) TextButton(onClick = { viewModel.update { it.copy(photoUri = null) } }) { Text("Rimuovi foto") }
        }
    }
}

@Composable
private fun MainFields(form: ItemForm, viewModel: ItemEditViewModel) {
    val update = viewModel::update
    OutlinedTextField(
        value = form.name,
        onValueChange = { value -> update { it.copy(name = value) } },
        label = { Text("Nome *") },
        isError = form.nameError,
        supportingText = if (form.nameError) ({ Text("Inserisci un nome") }) else ({ Text("Es. Fiat Panda, Lavatrice Samsung") }),
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
    if (form.group != ItemGroup.DOCUMENTS) {
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            TextInput("Marca", form.brand, Modifier.weight(1f)) { value -> update { it.copy(brand = value) } }
            TextInput("Modello", form.model, Modifier.weight(1f)) { value -> update { it.copy(model = value) } }
        }
    }
    if (form.isVehicle) {
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            TextInput("Targa", form.licensePlate, Modifier.weight(1f), capitalization = KeyboardCapitalization.Characters) { value ->
                update { it.copy(licensePlate = value) }
            }
            NumberInput("Anno", form.productionYear, Modifier.weight(0.7f), maxLength = 4) { value -> update { it.copy(productionYear = value) } }
        }
        NumberInput("Chilometri attuali", form.odometerKm, Modifier.fillMaxWidth(), maxLength = 7) { value -> update { it.copy(odometerKm = value) } }
    }
    if (form.group != ItemGroup.DOCUMENTS) {
        TextInput("Numero di serie", form.serialNumber, Modifier.fillMaxWidth(), capitalization = KeyboardCapitalization.Characters) { value ->
            update { it.copy(serialNumber = value) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            DateField(
                label = "Data acquisto",
                value = form.purchaseDate,
                onValueChange = { value -> update { it.copy(purchaseDate = value) } },
                clearable = true,
                modifier = Modifier.weight(1.6f),
            )
            OutlinedTextField(
                value = form.purchasePrice,
                onValueChange = { value -> update { it.copy(purchasePrice = value.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(10)) } },
                label = { Text("Prezzo €") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WarrantyFields(form: ItemForm, viewModel: ItemEditViewModel) {
    val update = viewModel::update
    val pickDocument = rememberFilePicker { file -> update { it.copy(warrantyDocumentUri = file.uri) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Ha una garanzia", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = form.hasWarranty, onCheckedChange = { value ->
            update { it.copy(hasWarranty = value) }
            if (value && form.warrantyEnd == null) viewModel.onWarrantyDuration(24)
        })
    }
    if (!form.hasWarranty) return
    DateField(
        label = "Inizio garanzia",
        value = form.warrantyStart,
        onValueChange = { value -> update { it.copy(warrantyStart = value) } },
        modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(12L, 24L, 36L).forEach { months ->
            FilterChip(
                selected = form.warrantyStart != null && form.warrantyEnd == form.warrantyStart.plusMonths(months),
                onClick = { viewModel.onWarrantyDuration(months) },
                label = { Text("${months / 12} ${if (months == 12L) "anno" else "anni"}") },
            )
        }
    }
    DateField(
        label = "Scadenza garanzia *",
        value = form.warrantyEnd,
        onValueChange = { value -> update { it.copy(warrantyEnd = value) } },
        isError = form.warrantyError,
        modifier = Modifier.fillMaxWidth(),
    )
    TextInput("Venditore", form.warrantySeller, Modifier.fillMaxWidth()) { value -> update { it.copy(warrantySeller = value) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = pickDocument) {
            Icon(Icons.Rounded.AttachFile, contentDescription = null)
            Text(if (form.warrantyDocumentUri == null) "Allega documento" else "Cambia documento", modifier = Modifier.padding(start = 8.dp))
        }
        if (form.warrantyDocumentUri != null) {
            TextButton(onClick = { update { it.copy(warrantyDocumentUri = null) } }) { Text("Rimuovi") }
        }
    }
    Text(
        "Remindella ti avviserà 30 giorni prima della scadenza.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeopleFields(form: ItemForm, viewModel: ItemEditViewModel) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        people.filter { it.id in form.people }.forEach { person ->
            var menu by remember { mutableStateOf(false) }
            val role = form.people.getValue(person.id)
            Box {
                InputChip(selected = true, onClick = { menu = true }, label = { Text("${person.displayName} · ${role.label}") })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    PersonItemRole.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { menu = false; viewModel.update { it.copy(people = it.people + (person.id to option)) } },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rimuovi") },
                        onClick = { menu = false; viewModel.update { it.copy(people = it.people - person.id) } },
                    )
                }
            }
        }
        FilterChip(
            selected = false,
            onClick = { showPicker = true },
            label = { Text("Persona") },
            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        )
    }
    if (showPicker) {
        MultiSelectDialog(
            title = "Persone associate",
            options = people,
            initiallySelected = form.people.keys,
            idOf = { it.id },
            labelOf = { it.displayName },
            emptyMessage = "Non hai ancora aggiunto persone.",
            onDismiss = { showPicker = false },
            onConfirm = { ids ->
                viewModel.update { current ->
                    current.copy(people = ids.associateWith { current.people[it] ?: PersonItemRole.OWNER })
                }
                showPicker = false
            },
        )
    }
}

@Composable
private fun SuggestionFields(form: ItemForm, viewModel: ItemEditViewModel) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    form.suggestions.forEachIndexed { index, choice ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Checkbox) { viewModel.onToggleSuggestion(index) },
        ) {
            Checkbox(checked = choice.selected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f)) {
                Text(choice.suggestion.title, style = MaterialTheme.typography.bodyLarge)
                Text(choice.suggestion.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (choice.selected) {
                TextButton(onClick = { editingIndex = index }) { Text(DateTexts.date(choice.firstDueDate, com.ricordella.app.domain.model.DateFormatStyle.NUMERIC)) }
            }
        }
    }
    Text(
        "Potrai modificare date e dettagli in qualsiasi momento.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    editingIndex?.let { index ->
        DatePickerDialogFor(
            initial = form.suggestions[index].firstDueDate,
            onDismiss = { editingIndex = null },
            onConfirm = { viewModel.onSuggestionDate(index, it); editingIndex = null },
        )
    }
}

private fun ReminderSuggestion.describe(): String {
    val recurrence = when (frequency) {
        RecurrenceFrequency.DAILY -> if (interval == 1) "Ogni giorno" else "Ogni $interval giorni"
        RecurrenceFrequency.WEEKLY -> if (interval == 1) "Ogni settimana" else "Ogni $interval settimane"
        RecurrenceFrequency.MONTHLY -> if (interval == 1) "Ogni mese" else "Ogni $interval mesi"
        RecurrenceFrequency.YEARLY -> if (interval == 1) "Ogni anno" else "Ogni $interval anni"
        null -> null
    }
    val km = odometerIntervalKm?.let { "ogni ${DateTexts.kilometers(it)} o un anno" }
    return listOfNotNull(recurrence, km).joinToString(" · ").ifEmpty { "Una volta" }.replaceFirstChar { it.uppercase() }
}

@Composable
private fun TextInput(
    label: String,
    value: String,
    modifier: Modifier,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = capitalization),
        modifier = modifier,
    )
}

@Composable
private fun NumberInput(label: String, value: String, modifier: Modifier, maxLength: Int, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(maxLength)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
