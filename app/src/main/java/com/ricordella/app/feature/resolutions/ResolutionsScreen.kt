package com.ricordella.app.feature.resolutions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.HappyWizard
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.model.Resolution
import kotlinx.coroutines.launch

/**
 * Buoni propositi di un anno: si scrivono a gennaio, si spuntano quando sono rispettati
 * e a fine anno il recap mostra com'è andata. Le frecce passano agli altri anni.
 */
@Composable
fun ResolutionsScreen(initialYear: Int, recap: Boolean, onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as RicordellaApplication).container
    val scope = rememberCoroutineScope()
    var year by rememberSaveable { mutableIntStateOf(initialYear) }
    var draft by rememberSaveable { mutableStateOf("") }
    val list = LocalAppSettings.current.resolutions.filter { it.year == year }
    val kept = list.count { it.kept }
    fun update(transform: (AppSettings) -> AppSettings) = scope.launch { container.settingsRepository.update(transform) }
    fun edit(transform: (List<Resolution>) -> List<Resolution>) = update { it.copy(resolutions = transform(it.resolutions)) }
    fun add() {
        val text = draft.trim()
        if (text.isEmpty()) return
        edit { it + Resolution(year = year, text = text) }
        draft = ""
    }

    // Aperto dal recap della Home: l'invito non torna più per quest'anno.
    LaunchedEffect(recap) {
        if (recap) update { it.copy(resolutionsRecapYear = maxOf(it.resolutionsRecapYear ?: 0, initialYear)) }
    }

    DetailScaffold(title = tr("Buoni propositi"), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .contentWidth()
                .padding(horizontal = RicordellaDimensions.screenPadding)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = tr("Anno precedente")) }
                Text("$year", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { year++ }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = tr("Anno successivo")) }
            }

            val tone = MaterialTheme.ricordellaColors.pear
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(tone.container, MaterialTheme.shapes.large)
                    .padding(RicordellaDimensions.spaceL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
            ) {
                HappyWizard(size = 96.dp, love = if (list.isNotEmpty() && kept == list.size) 1f else 0f)
                if (list.isEmpty()) {
                    Text(
                        tr("Scrivi cosa vuoi fare quest'anno: a fine anno ti mostro quali propositi hai rispettato."),
                        style = MaterialTheme.typography.bodyLarge,
                        color = tone.content,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(trf("Rispettati %1\$s su %2\$s", kept, list.size), style = MaterialTheme.typography.titleLarge, color = tone.content)
                    LinearProgressIndicator(progress = { kept.toFloat() / list.size }, modifier = Modifier.fillMaxWidth())
                    Text(
                        when {
                            kept == list.size -> tr("Tutti rispettati: che anno magico!")
                            kept * 2 >= list.size -> tr("Più della metà: ottimo lavoro!")
                            else -> tr("Spunta i propositi man mano che li rispetti.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = tone.content,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            list.forEach { resolution ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = resolution.kept,
                        onCheckedChange = { value -> edit { all -> all.map { if (it.id == resolution.id) it.copy(kept = value) else it } } },
                    )
                    Text(resolution.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { edit { all -> all.filterNot { it.id == resolution.id } } }) {
                        Icon(Icons.Rounded.Close, contentDescription = tr("Elimina"))
                    }
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(MAX_LENGTH) },
                label = { Text(tr("Nuovo proposito")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                trailingIcon = { IconButton(onClick = ::add, enabled = draft.isNotBlank()) { Icon(Icons.Rounded.Add, contentDescription = tr("Aggiungi")) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val MAX_LENGTH = 120
