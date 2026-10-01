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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Payments
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import com.ricordella.app.RicordellaApplication
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.Lang
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.HappyWizard
import com.ricordella.app.core.ui.QuickTypeIllustration
import com.ricordella.app.core.ui.QuickVoiceIllustration
import com.ricordella.app.core.ui.QuickWordsIllustration
import com.ricordella.app.core.ui.TutorialDialog
import com.ricordella.app.core.ui.TutorialPage
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.UiSound
import com.ricordella.app.core.ui.WizardScene
import com.ricordella.app.core.ui.describe
import com.ricordella.app.core.ui.icon
import com.ricordella.app.core.ui.label
import com.ricordella.app.core.ui.rememberUiSounds
import com.ricordella.app.core.ui.theme.RicordellaDimensions
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
 * Il microfono (vedi [VoiceButton]) chiede prima il consenso, poi ascolta e scrive la frase nella barra.
 */
@Composable
fun QuickEntryBar(now: LocalDateTime, onAdd: (QuickEntry) -> Unit, modifier: Modifier = Modifier) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val text = field.text
    val entry = remember(text, now.toLocalDate(), now.hour) { QuickEntryParser.parse(text, now) }
    val suggestions = remember(text, now.toLocalDate()) {
        if (text.isBlank()) emptyList() else QuickEntryParser.suggestions(text, now, Lang.code)
    }
    val focus = LocalFocusManager.current
    val sounds = rememberUiSounds()
    val app = LocalAppSettings.current
    val container = (LocalContext.current.applicationContext as RicordellaApplication).container
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
                Row {
                    VoiceButton(onText = ::setText)
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = tr("Come funziona"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                focused = it.isFocused
                // La prima volta che si tocca la barra parte il tutorial (se i tutorial non sono nascosti).
                if (it.isFocused && app.tutorialsEnabled && !app.quickEntryTutorialSeen) {
                    showHelp = true
                    focus.clearFocus()
                }
            },
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
        LaunchedEffect(Unit) {
            if (!app.quickEntryTutorialSeen) container.settingsRepository.update { it.copy(quickEntryTutorialSeen = true) }
        }
        QuickEntryTutorial(
            onTry = { example -> setText(example); showHelp = false },
            onDismiss = { showHelp = false },
        )
    }
}

@Composable
private fun SuggestionPill(word: String, onClick: () -> Unit, maxLines: Int = 1) {
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
        Text(word, style = MaterialTheme.typography.labelLarge, color = tone.content, maxLines = maxLines)
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

/** Esempi da provare, nella lingua dell'app. */
private fun quickExamples(): List<String> = listOf(
    tr("dentista domani alle 9 e mezza"),
    tr("palestra ogni lunedì e giovedì alle 18"),
    tr("vacanza dal 10 al 15 agosto"),
    tr("pagare affitto ogni mese il 5 urgente"),
    tr("chiamare Luca tra 2 ore"),
    tr("sveglia alle 7"),
)

/**
 * Tutorial di "Scrivi al volo", come quelli delle altre sezioni: pagine con illustrazioni animate
 * (scrivere, le parole che capisco, il microfono) e in fondo gli esempi da toccare per provare.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickEntryTutorial(onTry: (String) -> Unit, onDismiss: () -> Unit) {
    val c = MaterialTheme.ricordellaColors
    val pages = listOf(
        TutorialPage(
            tr("Scrivi come parli"),
            tr("Scrivi una frase come la diresti: capisco quando, a che ora, se si ripete e quanto è importante. Quello che resta diventa il titolo, e mentre scrivi ti suggerisco le parole giuste."),
        ) { QuickTypeIllustration() },
        TutorialPage(
            tr("Quando e a che ora"),
            tr("oggi, domani, lunedì, sabato prossimo, 24/12, 31 gennaio, tra 3 giorni, nel weekend, fine mese · alle 9, alle 18:30, alle 9 e mezza, stasera, a pranzo, tra 2 ore"),
        ) {
            QuickWordsIllustration(
                listOf(
                    Triple(Icons.Rounded.Event, tr("domani"), c.cyan),
                    Triple(Icons.Rounded.Event, tr("sabato prossimo"), c.cyan),
                    Triple(Icons.Rounded.Schedule, tr("alle 18:30"), c.pear),
                    Triple(Icons.Rounded.Schedule, tr("stasera"), c.pear),
                ),
            )
        },
        TutorialPage(
            tr("Si ripete, dura, conta"),
            tr("ogni giorno, ogni lunedì e giovedì, giorni feriali, ogni 2 settimane, ogni mese il 5, ogni anno · dal 10 al 15 agosto, per 3 giorni · urgente o !!, importante o !"),
        ) {
            QuickWordsIllustration(
                listOf(
                    Triple(Icons.Rounded.Repeat, tr("ogni lunedì"), c.lavender),
                    Triple(Icons.Rounded.DateRange, tr("dal 10 al 15 agosto"), c.mint),
                    Triple(Icons.Rounded.PriorityHigh, tr("urgente"), c.coral),
                ),
            )
        },
        TutorialPage(
            tr("Capisco anche il tipo"),
            tr("«sveglia alle 7» diventa una sveglia, «compleanno di…» si ripete ogni anno, «dentista» è una visita medica, «pagare» o «bolletta» un pagamento."),
        ) {
            QuickWordsIllustration(
                listOf(
                    Triple(Icons.Rounded.NotificationsActive, tr("sveglia alle 7"), c.cyan),
                    Triple(Icons.Rounded.Cake, tr("compleanno di Marco"), c.coral),
                    Triple(Icons.Rounded.Payments, tr("pagare la bolletta"), c.mint),
                ),
            )
        },
        TutorialPage(
            tr("Oppure dillo"),
            tr("Tocca il microfono e parla: la sfera ti ascolta e scrive per te. Il permesso del microfono te lo chiedo solo la prima volta."),
        ) { QuickVoiceIllustration() },
        TutorialPage(
            tr("Prova tu"),
            tr("Tocca un esempio: finisce nella barra e vedi subito cosa ho capito."),
            extra = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    quickExamples().forEach { example -> SuggestionPill(example, onClick = { onTry(example) }, maxLines = 2) }
                }
            },
        ) { HappyWizard(size = 180.dp, scene = WizardScene.CONJURING) },
    )
    TutorialDialog(pages, onDismiss = onDismiss)
}
