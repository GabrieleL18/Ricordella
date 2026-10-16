package com.ricordella.app.feature.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.domain.model.LoyaltyCard

/** Una carta letta in blocco, in attesa di conferma: il nome è solo una proposta, si cambia (o si lascia vuoto). */
internal data class DraftCard(val code: String, val format: Int, val name: String, val key: String = java.util.UUID.randomUUID().toString())

/** Catene e marchi italiani con tessera fedeltà: se il testo letto ne contiene uno, il nome è quello (scritto bene). */
internal val KnownChains = listOf(
    // Supermercati
    "Coop", "Conad", "Esselunga", "Carrefour", "Lidl", "Eurospin", "MD", "Despar", "Eurospar", "Interspar", "Pam", "Panorama", "Penny",
    "Aldi", "Iper", "Bennet", "Famila", "Tigros", "Unes", "Il Gigante", "Crai", "Sigma", "Todis", "Simply", "Auchan", "Metro", "Selex",
    "Dok", "Italmark", "Prix", "Superconti", "Tigotà", "Acqua e Sapone", "Naturasì", "Eataly",
    // Casa, bricolage, elettronica
    "Leroy Merlin", "Bricocenter", "Brico io", "Obi", "Ikea", "Maisons du Monde", "MediaWorld", "Unieuro", "Euronics", "Trony", "Expert",
    // Sport, abbigliamento, scarpe
    "Decathlon", "Intersport", "Foot Locker", "OVS", "Upim", "Coin", "Piazza Italia", "Tezenis", "Calzedonia", "Intimissimi", "Zara", "H&M",
    "Primark", "Geox", "Prénatal", "Toys Center",
    // Bellezza e salute
    "Sephora", "Douglas", "Kiko", "Equivalenza", "Marionnaud",
    // Libri e animali
    "Feltrinelli", "Mondadori", "Libraccio", "Arcaplanet", "Maxi Zoo", "Isola dei Tesori",
    // Carburanti, viaggi, ristorazione
    "Eni", "IP", "Q8", "Tamoil", "Esso", "Api", "Trenitalia", "Italo", "Autogrill", "McDonald's", "Burger King", "Roadhouse",
)

private fun String.plain(): String = lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

/** Il marchio noto nella riga, se c'è: le sigle corte (MD, Eni, Pam...) solo come parola intera, per non confonderle con altre parole. */
private fun knownChainIn(line: String): String? {
    val text = " " + line.plain() + " "
    return KnownChains.firstOrNull { chain ->
        val key = chain.plain()
        if (key.length <= 4) text.contains(" $key ") else text.contains(key)
    }
}

/**
 * Propone il nome dal testo stampato sulla tessera. Prima cerca un marchio noto (Coop, Decathlon...) in qualsiasi riga;
 * altrimenti prende la prima riga fatta soprattutto di lettere, di lunghezza ragionevole e che non sia un indirizzo web,
 * un'email o una parola generica. Vuoto se non ne trova una.
 */
internal fun guessCardName(lines: List<String>): String {
    lines.firstNotNullOfOrNull(::knownChainIn)?.let { return it }
    val generic = setOf("tessera", "card", "carta", "fedeltà", "fidelity", "club", "loyalty", "member", "socio", "numero", "customer", "cliente")
    return lines.map { it.trim() }.firstOrNull { line ->
        val letters = line.count { it.isLetter() }
        val lower = line.lowercase()
        line.length in 3..28 && letters >= 3 && letters >= line.length * 0.7 &&
            !lower.contains("www") && !lower.contains("@") && !lower.contains(".it") && !lower.contains(".com") && lower !in generic
    }.orEmpty()
}

/** Toglie i codici già presenti tra le carte e quelli ripetuti: la stessa tessera fotografata due volte conta una volta sola. */
internal fun newDrafts(found: List<DraftCard>, existing: List<LoyaltyCard>): List<DraftCard> {
    val known = existing.map { it.code }.toMutableSet()
    return found.filter { known.add(it.code) }
}

/** Le carte da aggiungere: chi non ha un nome riceve «Carta senza nome 1», «…2», così si ritrova e si rinomina dopo. */
internal fun draftsToCards(drafts: List<DraftCard>, category: String, now: Long, unnamed: String): List<LoyaltyCard> {
    var n = 0
    return drafts.map { d ->
        val name = d.name.trim().ifEmpty { "$unnamed ${++n}" }
        LoyaltyCard(name = name, code = d.code, format = d.format, category = category, updatedAt = now)
    }
}

/** Elenco delle carte lette, ognuna con il suo nome da correggere; una categoria sola per tutte. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BulkCardsDialog(
    drafts: List<DraftCard>,
    skipped: Int,
    suggestions: List<String>,
    onChange: (List<DraftCard>) -> Unit,
    onSave: (category: String) -> Unit,
    onMore: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    var category by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.widthIn(max = 460.dp)) {
            Column(Modifier.padding(RicordellaDimensions.spaceL), verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                Text(trf("%1\$s carte lette", drafts.size), style = MaterialTheme.typography.headlineSmall)
                Text(
                    tr("Il nome è una proposta: correggilo o lascialo vuoto, poi lo cambi quando vuoi."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (skipped > 0) Text(
                    trf("%1\$s senza codice o già presenti: saltate.", skipped),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                    itemsIndexed(drafts, key = { _, d -> d.key }) { index, draft ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            OutlinedTextField(
                                draft.name,
                                { value -> onChange(drafts.map { if (it.key == draft.key) it.copy(name = value.take(40)) else it }) },
                                label = { Text(tr("Nome")) },
                                supportingText = { Text(draft.code, maxLines = 1) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onChange(drafts.filterNot { it.key == draft.key }) }) {
                                Icon(Icons.Rounded.Close, contentDescription = trf("Togli la carta %1\$s", index + 1))
                            }
                        }
                    }
                }
                Text(tr("Categoria per tutte"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { s ->
                        FilterChip(selected = s == category, onClick = { category = if (s == category) "" else s }, label = { Text(s) })
                    }
                }
                PushButton(
                    text = trf("Aggiungi %1\$s carte", drafts.size),
                    enabled = drafts.isNotEmpty(),
                    onClick = { onSave(category) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    if (onMore != null) TextButton(onClick = onMore) { Text(tr("Scansiona un'altra")) }
                    TextButton(onClick = onCancel) { Text(tr("Annulla")) }
                }
            }
        }
    }
}
