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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
            Box {
                FloatingActionButton(
                    onClick = { addMenu = true },
                    containerColor = MaterialTheme.ricordellaColors.bolt,
                    contentColor = MaterialTheme.ricordellaColors.onBolt,
                ) { Icon(Icons.Rounded.Add, contentDescription = tr("Nuova nota")) }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("Nota")) }, leadingIcon = { Icon(Icons.Rounded.StickyNote2, null) },
                        onClick = { addMenu = false; navigator.newNote() },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Lista")) }, leadingIcon = { Icon(Icons.Rounded.ChecklistRtl, null) },
                        onClick = { addMenu = false; navigator.newNote(list = true) },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Lista della spesa")) }, leadingIcon = { Icon(Icons.Rounded.ShoppingCart, null) },
                        onClick = { addMenu = false; navigator.newNote(list = true, prices = true) },
                    )
                }
            }
        },
    ) { padding ->
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
            LazyColumn(
                Modifier.fillMaxSize().contentWidth(),
                contentPadding = PaddingValues(
                    start = RicordellaDimensions.screenPadding,
                    end = RicordellaDimensions.screenPadding,
                    top = padding.calculateTopPadding() + RicordellaDimensions.spaceS,
                    bottom = 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
            ) {
                items(notes, key = { it.id }) { note -> NoteCard(note) { navigator.openNote(note.id) } }
            }
        }
    }
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    val tone = if (note.prices) colors.mint else if (note.isList) colors.cyan else colors.pear
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = tone.container),
    ) {
        Column(Modifier.padding(RicordellaDimensions.spaceM), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    note.title.ifBlank { note.summary.ifBlank { tr("Senza titolo") } },
                    style = MaterialTheme.typography.titleMedium,
                    color = tone.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (note.pinned) Icon(Icons.Rounded.PushPin, contentDescription = tr("Fissata"), tint = tone.content, modifier = Modifier.padding(start = 6.dp))
            }
            if (note.title.isNotBlank() && note.summary.isNotBlank()) {
                Text(note.summary, style = MaterialTheme.typography.bodyMedium, color = tone.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
