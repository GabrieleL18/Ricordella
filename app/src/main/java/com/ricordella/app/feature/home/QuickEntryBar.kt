package com.ricordella.app.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.Lang
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.HappyWizard
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.UiSound
import com.ricordella.app.core.ui.WizardScene
import com.ricordella.app.core.ui.describe
import com.ricordella.app.core.ui.icon
import com.ricordella.app.core.ui.label
import com.ricordella.app.core.ui.rememberUiSounds
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.core.ui.tone
import com.ricordella.app.domain.model.Priority
import com.ricordella.app.domain.text.QuickEntry
import com.ricordella.app.domain.text.QuickEntryParser
import java.time.LocalDateTime

/**
 * "Scrivi al volo": una frase come «dentista domani alle 9» diventa un promemoria.
 * Mentre si scrive compaiono le parole suggerite (per completare o per dire quello che manca)
 * e l'anteprima di cosa ho capito; il "?" spiega tutto quello che so capire, con esempi da provare.
 */
@Composable
fun QuickEntryBar(now: LocalDateTime, onAdd: (QuickEntry) -> Unit, modifier: Modifier = Modifier) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val text = field.text
    val entry = remember(text, now.toLocalDate(), now.hour) { QuickEntryParser.parse(text, now) }
    val suggestions = remember(text, now.toLocalDate()) {
        if (text.isBlank()) emptyList() else QuickEntryParser.suggestions(text, now, Lang.english)
    }
    val focus = LocalFocusManager.current
    val sounds = rememberUiSounds()
    fun setText(value: String) {
        field = TextFieldValue(value, TextRange(value.length))
    }
    val submit = {
        entry?.let {
            onAdd(it)
            sounds(UiSound.DING)
            setText("")
            focus.clearFocus()
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
        OutlinedTextField(
            value = field,
            onValueChange = { field = if (it.text.length > 200) it.copy(text = it.text.take(200)) else it },
            placeholder = { Text(tr("Scrivi al volo: «dentista domani alle 9»"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                IconButton(onClick = { showHelp = true }) {
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = tr("Come funziona"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
        // Parole suggerite: un tocco le inserisce e si continua a scrivere.
        AnimatedVisibility(visible = focused && suggestions.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                suggestions.forEach { word ->
                    SuggestionPill(word, onClick = { setText(QuickEntryParser.applySuggestion(text, word)) })
                }
            }
        }
        AnimatedVisibility(
            visible = entry != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            val shown = entry ?: return@AnimatedVisibility
            Preview(shown, now, onAdd = { submit() })
        }
    }
    if (showHelp) {
        QuickEntryHelp(
            onTry = { example -> setText(example); showHelp = false },
            onDismiss = { showHelp = false },
        )
    }
}

@Composable
private fun SuggestionPill(word: String, onClick: () -> Unit) {
    val tone = MaterialTheme.ricordellaColors.pear
    Row(
        Modifier
            .background(tone.container, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = tone.content, modifier = Modifier.size(14.dp))
        Text(word, style = MaterialTheme.typography.labelLarge, color = tone.content, maxLines = 1)
    }
}

/** Anteprima di cosa ho capito: tipo, titolo, quando (fino a quando), ripetizione e importanza. */
@Composable
private fun Preview(shown: QuickEntry, now: LocalDateTime, onAdd: () -> Unit) {
    val tone = shown.type.tone
    val settings = LocalAppSettings.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(tone.container, MaterialTheme.shapes.large)
            .padding(RicordellaDimensions.spaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Box(Modifier.size(40.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
            Icon(shown.type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest)
        }
        Column(Modifier.weight(1f)) {
            Text(shown.title, style = MaterialTheme.typography.titleMedium, color = tone.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    shown.type.label,
                    DateTexts.relativeWithTime(shown.date, shown.time, now.toLocalDate()),
                    shown.endDate?.let { trf("fino al %1\$s", DateTexts.date(it, settings.dateFormat)) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = tone.content,
            )
            shown.recurrence?.let { rule ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Repeat, contentDescription = null, tint = tone.content, modifier = Modifier.size(14.dp))
                    Text(rule.describe(), style = MaterialTheme.typography.bodySmall, color = tone.content)
                }
            }
            if (shown.priority != Priority.NORMAL) {
                val badge = if (shown.priority == Priority.URGENT) MaterialTheme.ricordellaColors.coral else MaterialTheme.ricordellaColors.pear
                Row(
                    Modifier.padding(top = 4.dp).background(badge.solid, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PriorityHigh, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(12.dp))
                    Text(shown.priority.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.surfaceContainerLowest)
                }
            }
        }
        PushButton(tr("Aggiungi"), onClick = onAdd)
    }
}

/** Un gruppo della guida: cosa so capire, con le parole da usare. */
private class HelpGroup(val icon: ImageVector, val title: String, val words: String)

/**
 * Guida di "Scrivi al volo" con la grafica dell'app: cosa capisco (gruppi colorati con le
 * parole da usare) ed esempi completi che, toccati, finiscono nella barra per provarli.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickEntryHelp(onTry: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    val english = Lang.english
    val groups = listOf(
        HelpGroup(Icons.Rounded.Event, tr("Quando"), tr("oggi, domani, dopodomani · lunedì, sabato prossimo · 24/12, 31 gennaio · tra 3 giorni, prossima settimana, nel weekend, fine mese, mese prossimo")) to colors.cyan,
        HelpGroup(Icons.Rounded.Schedule, tr("A che ora"), tr("alle 9, alle 18:30, alle 9 e mezza, alle 6 meno un quarto · stamattina, stasera, stanotte · a pranzo, a cena · tra mezz'ora, tra 2 ore")) to colors.pear,
        HelpGroup(Icons.Rounded.Repeat, tr("Si ripete"), tr("ogni giorno, ogni sera · ogni lunedì e giovedì, giorni feriali, ogni weekend · ogni 2 settimane, ogni mese il 5, ogni anno")) to colors.lavender,
        HelpGroup(Icons.Rounded.DateRange, tr("Più giorni"), tr("dal 10 al 15 agosto · per 3 giorni")) to colors.mint,
        HelpGroup(Icons.Rounded.PriorityHigh, tr("Importanza"), tr("urgente o !! · importante o !")) to colors.coral,
        HelpGroup(Icons.Rounded.NotificationsActive, tr("Tipo"), tr("sveglia alle 7 → sveglia · compleanno di… → ogni anno · dentista, visita → visita medica · pagare, bolletta → pagamento")) to colors.cyan,
    )
    val examples = if (english) listOf(
        "dentist tomorrow at 9:30", "gym every monday and thursday at 6pm", "holiday from 10 to 15 august",
        "pay rent every month on the 5th urgent", "call Luca in 2 hours", "wake me at 7 alarm",
    ) else listOf(
        "dentista domani alle 9 e mezza", "palestra ogni lunedì e giovedì alle 18", "vacanza dal 10 al 15 agosto",
        "pagare affitto ogni mese il 5 urgente", "chiamare Luca tra 2 ore", "sveglia alle 7",
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.widthIn(max = 520.dp)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(RicordellaDimensions.spaceXl),
                verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                    HappyWizard(size = 96.dp, scene = WizardScene.CONJURING)
                    Text(tr("Scrivi al volo"), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        tr("Scrivi come parli: capisco quando, a che ora, se si ripete, quanto dura e quanto è importante. Quello che resta diventa il titolo. Mentre scrivi ti suggerisco le parole giuste."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                groups.forEach { (group, tone) -> HelpCard(group, tone) }
                Text(tr("Prova un esempio"), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS), verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                    examples.forEach { example -> SuggestionPill(example, onClick = { onTry(example) }) }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(tr("Ho capito")) }
            }
        }
    }
}

@Composable
private fun HelpCard(group: HelpGroup, tone: Tone) {
    Row(
        Modifier.fillMaxWidth().background(tone.container, MaterialTheme.shapes.large).padding(RicordellaDimensions.spaceM),
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Box(Modifier.size(32.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
            Icon(group.icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(group.title, style = MaterialTheme.typography.titleSmall, color = tone.content)
            Text(group.words, style = MaterialTheme.typography.bodySmall, color = tone.content)
        }
    }
}
