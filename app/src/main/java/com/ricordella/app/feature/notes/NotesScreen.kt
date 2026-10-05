package com.ricordella.app.feature.notes

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.navigationBarsPadding
import com.ricordella.app.core.ui.pressScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChecklistRtl
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.navigation.AppNavigator
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.EmptyState
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Le note: testo libero, liste e liste della spesa con prezzi e totale. */
@Composable
fun NotesScreen(navigator: AppNavigator, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as RicordellaApplication).container }
    val scope = rememberCoroutineScope()
    val notes = LocalAppSettings.current.notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    var addMenu by remember { mutableStateOf(false) }
    DetailScaffold(
        title = tr("Note"),
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { addMenu = true },
                containerColor = MaterialTheme.ricordellaColors.bolt,
                contentColor = MaterialTheme.ricordellaColors.onBolt,
            ) { Icon(Icons.Rounded.Add, contentDescription = tr("Nuova nota")) }
        },
    ) { padding ->
        if (addMenu) NewNoteChoice(onDismiss = { addMenu = false }, onChoose = { list, prices -> addMenu = false; navigator.newNote(list = list, prices = prices) })
        if (notes.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.StickyNote2,
                title = tr("Nessuna nota"),
                message = tr("Scrivi una nota o prepara una lista della spesa con i prezzi: il totale lo calcolo io."),
                actionLabel = tr("Nuova nota"),
                onAction = { navigator.newNote() },
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize().contentWidth(),
                contentPadding = PaddingValues(
                    start = RicordellaDimensions.screenPadding,
                    end = RicordellaDimensions.screenPadding,
                    top = padding.calculateTopPadding() + RicordellaDimensions.spaceS,
                    bottom = 112.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
                verticalItemSpacing = RicordellaDimensions.spaceS,
            ) {
                items(notes, key = { it.id }) { note -> NoteCard(note) { navigator.openNote(note.id) } }
            }
        }
    }
}

/** Colore e icona di una nota secondo il tipo: testo (pera), lista (azzurro), spesa (menta). */
internal val Note.tone: com.ricordella.app.core.ui.theme.Tone
    @Composable get() = MaterialTheme.ricordellaColors.let { if (prices) it.mint else if (isList) it.cyan else it.pear }

internal val Note.icon get() = if (prices) Icons.Rounded.ShoppingCart else if (isList) Icons.Rounded.ChecklistRtl else Icons.Rounded.StickyNote2

/** Biglietto della nota: pallino colorato con l'icona del tipo, titolo, anteprima e avanzamento. */
@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    val tone = note.tone
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = tone.container),
    ) {
        Column(Modifier.padding(RicordellaDimensions.spaceM), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(Modifier.size(32.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(note.icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(18.dp))
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                if (note.pinned) Icon(Icons.Rounded.PushPin, contentDescription = tr("Fissata"), tint = tone.content, modifier = Modifier.size(18.dp))
            }
            Text(
                note.title.ifBlank { note.summary.ifBlank { tr("Senza titolo") } },
                style = MaterialTheme.typography.titleMedium,
                color = tone.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.title.isNotBlank() && note.summary.isNotBlank()) {
                Text(note.summary, style = MaterialTheme.typography.bodyMedium, color = tone.content.copy(alpha = 0.8f), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            if (note.isList) {
                val done = note.lines.count { it.done }
                val total = note.lines.count { it.text.isNotBlank() }
                Text(
                    listOfNotNull(trf("%1\$s di %2\$s", done, total), if (note.prices && note.totalCents > 0) DateTexts.money(note.totalCents) else null).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = tone.content,
                )
            }
        }
    }
}

/** "Nuova nota": foglio dal basso con un tassello colorato per tipo, come il foglio "Aggiungi". */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NewNoteChoice(onDismiss: () -> Unit, onChoose: (list: Boolean, prices: Boolean) -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(tr("Che nota vuoi scrivere?"), style = MaterialTheme.typography.headlineSmall)
            listOf(
                Triple(Triple(false, false, Icons.Rounded.StickyNote2), tr("Nota") to tr("Testo libero"), colors.pear),
                Triple(Triple(true, false, Icons.Rounded.ChecklistRtl), tr("Lista") to tr("Voci da spuntare"), colors.cyan),
                Triple(Triple(true, true, Icons.Rounded.ShoppingCart), tr("Lista della spesa") to tr("Con prezzi e totale"), colors.mint),
            ).forEach { (kind, text, tone) ->
                val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale(interaction, pressedScale = 0.97f)
                        .clip(MaterialTheme.shapes.large)
                        .background(tone.container)
                        .clickable(interactionSource = interaction, indication = null) { onChoose(kind.first, kind.second) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.size(52.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(kind.third, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(28.dp))
                    }
                    Column {
                        Text(text.first, style = MaterialTheme.typography.titleLarge, color = tone.content)
                        Text(text.second, style = MaterialTheme.typography.bodyMedium, color = tone.content)
                    }
                }
            }
        }
    }
}

/** Condivide la nota come testo: elenco con caselle e, se ci sono i prezzi, il totale. */
fun shareText(note: Note): String = buildString {
    if (note.title.isNotBlank()) appendLine(note.title)
    if (!note.isList) append(note.text)
    else {
        note.lines.filter { it.text.isNotBlank() }.forEach { line ->
            append(if (line.done) "☑ " else "☐ ")
            append(if (line.qty > 1) "${line.qty}× ${line.text}" else line.text)
            if (line.priceCents != null) append(" — ").append(DateTexts.money(line.totalCents))
            appendLine()
        }
        if (note.prices && note.totalCents > 0) append(trf("Totale: %1\$s", DateTexts.money(note.totalCents)))
    }
}.trim()

fun shareIntent(note: Note): Intent =
    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText(note)).putExtra(Intent.EXTRA_SUBJECT, note.title), null)
