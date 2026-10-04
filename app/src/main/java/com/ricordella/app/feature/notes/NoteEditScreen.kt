package com.ricordella.app.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.navigation.NoteRoute
import com.ricordella.app.core.ui.ConfirmDialog
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import androidx.compose.foundation.background
import com.ricordella.app.domain.model.Note
import com.ricordella.app.domain.model.NoteLine
import com.ricordella.app.domain.model.toList
import com.ricordella.app.domain.model.toText
import com.ricordella.app.feature.items.formatCents
import com.ricordella.app.feature.items.parseCents
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Modifica di una nota. Si salva da sola (un attimo dopo l'ultima modifica e all'uscita):
 * una nota nuova rimasta vuota non viene creata.
 */
@Composable
fun NoteEditScreen(route: NoteRoute, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as RicordellaApplication).container }
    val saved = LocalAppSettings.current.notes
    var note by remember(route.id) {
        mutableStateOf(saved.firstOrNull { it.id == route.id } ?: Note(id = route.id, isList = route.list, prices = route.prices).let { if (it.isList) it.copy(lines = listOf(NoteLine())) else it })
    }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var focusLine by remember { mutableStateOf<String?>(null) }
    val latest by rememberUpdatedState(note)
    // Dopo "Elimina" niente più salvataggi (quello all'uscita la ricreerebbe).
    val gone = remember { booleanArrayOf(false) }

    fun persist(value: Note) = container.applicationScope.launch {
        if (gone[0]) return@launch
        container.settingsRepository.update { app ->
            val others = app.notes.filterNot { it.id == value.id }
            if (value.isEmpty) app.copy(notes = others) else app.copy(notes = others + value.copy(updatedAt = System.currentTimeMillis()))
        }
    }
    // Salvataggio dopo una breve pausa, e sicuro all'uscita.
    LaunchedEffect(note) {
        delay(400)
        persist(note)
    }
    DisposableEffect(Unit) { onDispose { persist(latest) } }

    fun edit(transform: (Note) -> Note) { note = transform(note) }
    fun editLine(id: String, transform: (NoteLine) -> NoteLine) = edit { n -> n.copy(lines = n.lines.map { if (it.id == id) transform(it) else it }) }
    fun addLineAfter(id: String?) {
        val fresh = NoteLine()
        edit { n ->
            val at = n.lines.indexOfFirst { it.id == id }.let { if (it < 0) n.lines.size else it + 1 }
            n.copy(lines = n.lines.toMutableList().apply { add(at, fresh) })
        }
        focusLine = fresh.id
    }

    DetailScaffold(
        title = if (note.isList) (if (note.prices) tr("Lista della spesa") else tr("Lista")) else tr("Nota"),
        onBack = onBack,
        actions = {
            IconButton(onClick = { edit { it.copy(pinned = !it.pinned) } }) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = tr("Fissa in alto"),
                    tint = if (note.pinned) MaterialTheme.ricordellaColors.coral.solid else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = tr("Altro")) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (note.isList) {
                    DropdownMenuItem(
                        text = { Text(if (note.prices) tr("Togli i prezzi") else tr("Aggiungi i prezzi")) },
                        onClick = { menu = false; edit { it.copy(prices = !it.prices) } },
                    )
                    DropdownMenuItem(text = { Text(tr("Trasforma in testo")) }, onClick = { menu = false; edit { it.toText() } })
                    if (note.lines.any { it.done }) {
                        DropdownMenuItem(
                            text = { Text(tr("Togli le voci spuntate")) },
                            onClick = { menu = false; edit { n -> n.copy(lines = n.lines.filterNot { it.done }.ifEmpty { listOf(NoteLine()) }) } },
                        )
                    }
                } else {
                    DropdownMenuItem(text = { Text(tr("Trasforma in lista")) }, onClick = { menu = false; edit { it.toList().let { n -> if (n.lines.isEmpty()) n.copy(lines = listOf(NoteLine())) else n } } })
                }
                DropdownMenuItem(
                    text = { Text(tr("Condividi")) },
                    onClick = { menu = false; context.startActivity(shareIntent(note)) },
                )
                DropdownMenuItem(text = { Text(tr("Elimina")) }, onClick = { menu = false; confirmDelete = true })
            }
        },
        bottomBar = { if (note.isList && note.prices) TotalsBar(note) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .contentWidth()
                .padding(horizontal = RicordellaDimensions.screenPadding)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
        ) {
            val tone = note.tone
            val fieldColors = noteFieldColors(tone)
            // Un foglio colorato come il tipo di nota: titolo grande, poi testo o voci, tutto senza bordi.
            Column(
                Modifier.fillMaxWidth().background(tone.container, MaterialTheme.shapes.extraLarge).padding(RicordellaDimensions.spaceS),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextField(
                    value = note.title,
                    onValueChange = { v -> edit { it.copy(title = v) } },
                    placeholder = { Text(tr("Titolo")) },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!note.isList) {
                    TextField(
                        value = note.text,
                        onValueChange = { v -> edit { it.copy(text = v) } },
                        placeholder = { Text(tr("Scrivi qui")) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        minLines = 10,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    note.lines.forEach { line ->
                        androidx.compose.runtime.key(line.id) {
                            LineRow(
                                line = line,
                                prices = note.prices,
                                tone = tone,
                                focused = focusLine == line.id,
                                onFocused = { focusLine = null },
                                onChange = { transform -> editLine(line.id, transform) },
                                onNext = { addLineAfter(line.id) },
                                onRemove = { edit { n -> n.copy(lines = n.lines.filterNot { it.id == line.id }.ifEmpty { listOf(NoteLine()) }) } },
                            )
                        }
                    }
                    TextButton(onClick = { addLineAfter(note.lines.lastOrNull()?.id) }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = tone.content)
                        Text(tr("Aggiungi voce"), color = tone.content, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = tr("Eliminare la nota?"),
            message = tr("La nota viene eliminata. Se hai un backup la ritrovi lì."),
            confirmLabel = tr("Elimina"),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                gone[0] = true
                container.applicationScope.launch { container.settingsRepository.update { app -> app.copy(notes = app.notes.filterNot { it.id == route.id }) } }
                onBack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun LineRow(
    line: NoteLine,
    prices: Boolean,
    tone: com.ricordella.app.core.ui.theme.Tone,
    focused: Boolean,
    onFocused: () -> Unit,
    onChange: ((NoteLine) -> NoteLine) -> Unit,
    onNext: () -> Unit,
    onRemove: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(focused) { if (focused) { runCatching { requester.requestFocus() }; onFocused() } }
    var price by remember { mutableStateOf(line.priceCents?.let(::formatCents).orEmpty()) }
    var qty by remember { mutableStateOf(if (line.qty > 1) line.qty.toString() else "") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Checkbox(
            checked = line.done,
            onCheckedChange = { v -> onChange { it.copy(done = v) } },
            colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = tone.solid, uncheckedColor = tone.content),
        )
        TextField(
            value = line.text,
            onValueChange = { v -> onChange { it.copy(text = v) } },
            placeholder = { Text(tr("Voce")) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (line.done) TextDecoration.LineThrough else null),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            singleLine = true,
            colors = noteFieldColors(tone),
            modifier = Modifier.weight(1f).focusRequester(requester),
        )
        if (prices) {
            TextField(
                value = qty,
                onValueChange = { v -> qty = v.filter(Char::isDigit).take(3); onChange { it.copy(qty = qty.toIntOrNull()?.coerceAtLeast(1) ?: 1) } },
                placeholder = { Text("×1") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = noteFieldColors(tone),
                modifier = Modifier.width(60.dp),
            )
            TextField(
                value = price,
                onValueChange = { v -> price = v.filter { it.isDigit() || it == ',' || it == '.' }.take(9); onChange { it.copy(priceCents = parseCents(price)) } },
                placeholder = { Text("€") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = noteFieldColors(tone),
                modifier = Modifier.width(86.dp),
            )
        }
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = tr("Elimina voce"), tint = tone.content) }
    }
}

/** Campi senza bordo né sfondo, con il testo del colore del tipo di nota. */
@Composable
private fun noteFieldColors(tone: com.ricordella.app.core.ui.theme.Tone) = TextFieldDefaults.colors(
    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    focusedTextColor = tone.content,
    unfocusedTextColor = tone.content,
    focusedPlaceholderColor = tone.content.copy(alpha = 0.5f),
    unfocusedPlaceholderColor = tone.content.copy(alpha = 0.5f),
    cursorColor = tone.solid,
)

/** Il calcolo: totale della lista, quanto è già nel carrello e quanto manca. */
@Composable
private fun TotalsBar(note: Note) {
    val colors = MaterialTheme.ricordellaColors
    Surface(color = colors.mint.container, tonalElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = RicordellaDimensions.screenPadding, vertical = RicordellaDimensions.spaceM),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tr("Totale"), style = MaterialTheme.typography.titleMedium, color = colors.mint.content)
                Text(DateTexts.money(note.totalCents), style = MaterialTheme.typography.titleMedium, color = colors.mint.content)
            }
            if (note.lines.any { it.done }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(trf("Nel carrello (%1\$s)", note.lines.count { it.done }), style = MaterialTheme.typography.bodyMedium, color = colors.mint.content)
                    Text(DateTexts.money(note.inCartCents), style = MaterialTheme.typography.bodyMedium, color = colors.mint.content)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("Ancora da prendere"), style = MaterialTheme.typography.bodyMedium, color = colors.mint.content)
                    Text(DateTexts.money(note.totalCents - note.inCartCents), style = MaterialTheme.typography.bodyMedium, color = colors.mint.content)
                }
            }
        }
    }
}
