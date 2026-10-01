package com.ricordella.app.feature.items

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.Lang
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.ConfirmDialog
import com.ricordella.app.core.ui.DateField
import com.ricordella.app.core.ui.HistoryItem
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.ExpenseKind
import com.ricordella.app.domain.model.ExpenseProfile
import com.ricordella.app.domain.model.ExpenseStats
import com.ricordella.app.domain.model.MaintenanceRecord
import com.ricordella.app.domain.usecase.NextMaintenance
import java.time.LocalDate

/*
 * Registro di manutenzione e spese di una cosa: riepilogo dell'anno, pulsanti per aggiungere
 * le voci adatte al tipo di cosa e storico a linea del tempo. Colori fissi per tipo di voce:
 * intervento giallo, rifornimento azzurro, altra spesa lilla, i soldi in verde menta.
 */

@Composable
internal fun ExpenseKind.tone(): Tone = MaterialTheme.ricordellaColors.let {
    when (this) {
        ExpenseKind.SERVICE -> it.pear
        ExpenseKind.FUEL -> it.cyan
        ExpenseKind.OTHER -> it.lavender
    }
}

internal val ExpenseKind.icon: ImageVector
    get() = when (this) {
        ExpenseKind.SERVICE -> Icons.Rounded.Build
        ExpenseKind.FUEL -> Icons.Rounded.LocalGasStation
        ExpenseKind.OTHER -> Icons.Rounded.Payments
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExpensesSection(
    profile: ExpenseProfile,
    records: List<MaintenanceRecord>,
    year: Int,
    isVehicle: Boolean,
    onAdd: (ExpenseKind) -> Unit,
    onDelete: (MaintenanceRecord) -> Unit,
    onEdit: (MaintenanceRecord) -> Unit = {},
) {
    val colors = MaterialTheme.ricordellaColors
    SectionHeader(profile.sectionTitle, icon = Icons.Rounded.ReceiptLong, tone = colors.mint)
    // Un pulsante per ogni voce che ha senso per questo tipo di cosa (il rifornimento solo per i veicoli).
    FlowRow(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS), verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
        profile.kinds.forEach { kind -> AddPill(profile.label(kind), kind.icon, kind.tone(), onClick = { onAdd(kind) }) }
    }
    if (records.isEmpty()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                .padding(RicordellaDimensions.spaceL),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            Box(Modifier.size(40.dp).background(colors.mint.container, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Euro, contentDescription = null, tint = colors.mint.content)
            }
            Text(profile.emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        return
    }
    SummaryCard(profile, records, year, isVehicle)

    // Con tanti rifornimenti la lista crescerebbe all'infinito: si mostrano gli ultimi, il resto a richiesta.
    var showAll by rememberSaveable { mutableStateOf(false) }
    val sorted = remember(records) { records.sortedByDescending { it.date } }
    val shown = if (showAll) sorted else sorted.take(RECENT)
    Column(Modifier.animateContentSize().padding(top = RicordellaDimensions.spaceS)) {
        shown.forEachIndexed { index, record ->
            RecordRow(profile, record, isLast = index == shown.lastIndex, onDelete = { onDelete(record) }, onEdit = { onEdit(record) })
        }
    }
    if (sorted.size > RECENT) {
        TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showAll) tr("Mostra solo le ultime") else trf("Mostra tutte (%1\$s)", sorted.size))
        }
    }
}

private const val RECENT = 5

@Composable
private fun AddPill(label: String, icon: ImageVector, tone: Tone, onClick: () -> Unit) {
    Row(
        Modifier
            .background(tone.container, CircleShape)
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(28.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(18.dp))
        }
        Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = tone.content)
    }
}

/** Totale dell'anno come il riquadro della lista Cose, più una tessera per tipo di voce e i dati dei veicoli. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(profile: ExpenseProfile, records: List<MaintenanceRecord>, year: Int, isVehicle: Boolean) {
    val stats = remember(records, year) { ExpenseStats.of(records, year) }
    val mint = MaterialTheme.ricordellaColors.mint
    Column(
        Modifier
            .fillMaxWidth()
            .background(mint.container, MaterialTheme.shapes.large)
            .padding(RicordellaDimensions.spaceL),
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM)) {
            Box(Modifier.size(40.dp).background(mint.solid, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Euro, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest)
            }
            Column(Modifier.weight(1f)) {
                Text(trf("Spese %1\$s", year), style = MaterialTheme.typography.labelLarge, color = mint.content)
                Text(DateTexts.money(stats.totalCents), style = MaterialTheme.typography.headlineSmall, color = mint.content)
            }
            if (stats.previousYearCents > 0) {
                Text(
                    trf("Anno scorso: %1\$s", DateTexts.money(stats.previousYearCents)),
                    style = MaterialTheme.typography.labelMedium,
                    color = mint.content,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
            }
        }
        val tiles = buildList<@Composable () -> Unit> {
            profile.kinds.forEach { kind ->
                stats.byKind[kind]?.let { cents -> add { StatTile(profile.label(kind), DateTexts.money(cents), kind.icon, kind.tone()) } }
            }
            if (isVehicle) {
                stats.kmPerLiter?.let { add { StatTile(tr("Consumo medio"), trf("%1\$s km/l", "%.1f".format(Lang.locale, it)), Icons.Rounded.LocalGasStation, mint) } }
                stats.centsPerKm?.let { add { StatTile(tr("Costo al km"), DateTexts.money(it.toLong().coerceAtLeast(1)), Icons.Rounded.Euro, mint) } }
            }
        }
        if (tiles.isNotEmpty()) {
            // Le tessere vanno a capo da sole: due per riga sui telefoni stretti, tutte in fila su tablet.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS), verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                tiles.forEach { it() }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, icon: ImageVector, tone: Tone) {
    Column(
        Modifier
            .widthIn(min = 132.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(20.dp).background(tone.container, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(12.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RecordRow(profile: ExpenseProfile, record: MaintenanceRecord, isLast: Boolean, onDelete: () -> Unit, onEdit: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    HistoryItem(
        modifier = Modifier.clickable(onClickLabel = tr("Modifica"), onClick = onEdit),
        date = record.date,
        title = record.title,
        tone = record.kind.tone(),
        isLast = isLast,
        subtitle = listOfNotNull(
            profile.label(record.kind).takeIf { !record.title.equals(it, ignoreCase = true) },
            record.odometerKm?.let(DateTexts::kilometers),
            record.liters?.let { trf("%1\$s l", "%.1f".format(Lang.locale, it)) },
            record.description,
        ).joinToString(" · ").ifEmpty { null },
        badge = record.costCents?.let { DateTexts.money(it) to MaterialTheme.ricordellaColors.mint },
        trailing = {
            IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, contentDescription = trf("Elimina intervento %1\$s", record.title)) }
        },
    )
    if (confirm) {
        ConfirmDialog(
            title = tr("Eliminare la voce?"),
            message = trf("\"%1\$s\" verrà rimosso dallo storico.", record.title),
            confirmLabel = tr("Elimina"),
            destructive = true,
            onConfirm = onDelete,
            onDismiss = { confirm = false },
        )
    }
}

/**
 * Nuova voce del registro, con la grafica dell'app: testata del colore del tipo di voce, scelta
 * del tipo a tessere, suggerimenti adatti alla cosa. Su schermi larghi i campi corti stanno affiancati.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExpenseDialog(
    profile: ExpenseProfile,
    initialKind: ExpenseKind,
    isVehicle: Boolean,
    currentKm: Int?,
    onDismiss: () -> Unit,
    onConfirm: (ExpenseKind, String, LocalDate, Int?, Long?, Double?, String?, NextMaintenance?) -> Unit,
    /** Voce da modificare (null = nuova voce). */
    initial: MaintenanceRecord? = null,
) {
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: initialKind) }
    var title by rememberSaveable { mutableStateOf(initial?.title ?: if (initialKind == ExpenseKind.FUEL) tr("Rifornimento") else "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var km by rememberSaveable { mutableStateOf((initial?.odometerKm ?: currentKm.takeIf { initial == null })?.toString().orEmpty()) }
    var liters by rememberSaveable { mutableStateOf(initial?.liters?.let { "%.2f".format(Lang.locale, it).trimEnd('0').trimEnd(',', '.') }.orEmpty()) }
    var cost by rememberSaveable { mutableStateOf(initial?.costCents?.let { "%.2f".format(Lang.locale, it / 100.0) }.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var scheduleNext by rememberSaveable { mutableStateOf(false) }
    var nextMonths by rememberSaveable { mutableStateOf(profile.nextMonths.toString()) }
    var nextKm by rememberSaveable { mutableStateOf(profile.nextKm?.toString().orEmpty()) }
    val tone = kind.tone()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.padding(16.dp).widthIn(max = 560.dp).imePadding(),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Testata colorata come il tipo di voce scelto.
                Row(
                    Modifier.fillMaxWidth().background(tone.container).padding(RicordellaDimensions.spaceL),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
                ) {
                    Box(Modifier.size(44.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(kind.icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest)
                    }
                    Column {
                        Text(if (initial == null) tr("Nuova voce") else tr("Modifica voce"), style = MaterialTheme.typography.titleLarge, color = tone.content)
                        Text(profile.label(kind), style = MaterialTheme.typography.bodyMedium, color = tone.content)
                    }
                }
                Column(Modifier.padding(RicordellaDimensions.spaceL), verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                    if (profile.kinds.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                        profile.kinds.forEach { option ->
                            KindTile(
                                label = profile.label(option),
                                icon = option.icon,
                                tone = option.tone(),
                                selected = option == kind,
                                onClick = {
                                    if (title.isBlank() || title == tr("Rifornimento")) title = if (option == ExpenseKind.FUEL) tr("Rifornimento") else ""
                                    kind = option
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(if (kind == ExpenseKind.SERVICE) trf("%1\$s *", profile.serviceLabel) else tr("Descrizione breve *")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val suggestions = profile.suggestions(kind)
                    if (suggestions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { suggestion ->
                            FilterChip(selected = title == suggestion, onClick = { title = suggestion }, label = { Text(suggestion) })
                        }
                    }
                    // Campi corti affiancati se c'è spazio (tablet, telefoni in orizzontale), uno sotto l'altro altrimenti.
                    BoxWithConstraints {
                        val wide = maxWidth >= 440.dp
                        val dateField = @Composable { m: Modifier -> DateField(tr("Data"), date, { it?.let { picked -> date = picked } }, modifier = m) }
                        val kmField = @Composable { m: Modifier ->
                            NumberField(km, { km = it.filter(Char::isDigit).take(7) }, tr("Km"), KeyboardType.Number, m)
                        }
                        val costField = @Composable { m: Modifier ->
                            NumberField(cost, { cost = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(10) }, tr("Costo €"), KeyboardType.Decimal, m)
                        }
                        val litersField = @Composable { m: Modifier ->
                            NumberField(liters, { liters = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) }, tr("Litri"), KeyboardType.Decimal, m)
                        }
                        val fields = buildList {
                            add(dateField)
                            if (isVehicle) add(kmField)
                            add(costField)
                            if (kind == ExpenseKind.FUEL) add(litersField)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                            fields.chunked(if (wide) 2 else 1).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                                    row.forEach { it(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    if (kind == ExpenseKind.FUEL) {
                        Text(tr("Con litri e km calcolo il consumo medio."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(tr("Note")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (kind == ExpenseKind.SERVICE && initial == null) {
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium).padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(tr("Ricordami il prossimo"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(checked = scheduleNext, onCheckedChange = { scheduleNext = it })
                        }
                        if (scheduleNext) Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                            NumberField(nextMonths, { nextMonths = it.filter(Char::isDigit).take(3) }, tr("Tra mesi"), KeyboardType.Number, Modifier.weight(1f))
                            if (isVehicle) NumberField(nextKm, { nextKm = it.filter(Char::isDigit).take(6) }, tr("o tra km"), KeyboardType.Number, Modifier.weight(1f))
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = RicordellaDimensions.spaceS),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
                    ) {
                        TextButton(onClick = onDismiss) { Text(tr("Annulla")) }
                        PushButton(
                            tr("Salva"),
                            enabled = title.isNotBlank(),
                            onClick = {
                                val next = if (scheduleNext && kind == ExpenseKind.SERVICE) NextMaintenance(nextMonths.toIntOrNull(), nextKm.toIntOrNull()) else null
                                val litersValue = liters.replace(',', '.').toDoubleOrNull()?.takeIf { kind == ExpenseKind.FUEL && it > 0 }
                                onConfirm(kind, title.trim(), date, km.toIntOrNull(), parseCents(cost), litersValue, description.trim().ifEmpty { null }, next)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KindTile(label: String, icon: ImageVector, tone: Tone, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(if (selected) tone.container else MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .border(BorderStroke(2.dp, if (selected) tone.solid else MaterialTheme.colorScheme.surfaceContainerLow), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) tone.content else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) tone.content else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, type: KeyboardType, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = type),
        singleLine = true,
        modifier = modifier,
    )
}
