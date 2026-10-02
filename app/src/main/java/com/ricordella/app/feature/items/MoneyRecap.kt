package com.ricordella.app.feature.items

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.navigation.AppNavigator
import com.ricordella.app.core.ui.EmptyState
import com.ricordella.app.core.ui.FilterChipRow
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.icon
import com.ricordella.app.core.ui.label
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.date.RecurrenceCalculator
import com.ricordella.app.domain.model.MoneyKind
import com.ricordella.app.domain.model.MoneyLedger
import com.ricordella.app.domain.model.PersonRecap
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.model.displayName
import com.ricordella.app.domain.repository.PersonRepository
import com.ricordella.app.domain.repository.ReminderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class MoneyUiState(val year: Int, val recap: List<PersonRecap>, val names: Map<String, String>)

class MoneyViewModel(
    reminders: ReminderRepository,
    people: PersonRepository,
    private val calculator: RecurrenceCalculator,
) : ViewModel() {
    private val year = MutableStateFlow(LocalDate.now().year)

    val uiState: StateFlow<MoneyUiState?> = combine(
        reminders.observePayments(),
        combine(people.observePeople(archived = false), people.observePeople(archived = true)) { a, b -> (a + b).associate { it.id to it.displayName } },
        year,
    ) { payments, names, y ->
        val entries = MoneyLedger.entries(payments, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31), calculator)
        MoneyUiState(y, MoneyLedger.recap(entries), names)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setYear(value: Int) {
        year.value = value
    }
}

/**
 * Spese e entrate dell'anno divise per persona: il totale di tutta la cerchia, quanto ha pagato ciascuno
 * e per ogni pagamento la sua parte. Il PDF contiene quello che si sta guardando.
 */
@Composable
fun MoneyRecap(year: Int, navigator: AppNavigator) {
    val viewModel = appViewModel { c, _ -> MoneyViewModel(c.reminderRepository, c.personRepository, c.recurrenceCalculator) }
    androidx.compose.runtime.LaunchedEffect(year) { viewModel.setYear(year) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    fun name(id: String) = if (id == MoneyLedger.NOBODY) tr("Senza persona") else current.names[id] ?: tr("Persona eliminata")

    if (current.recap.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Euro,
            title = tr("Nessuna spesa in quest'anno"),
            message = tr("Aggiungi una spesa, un abbonamento, delle rate o un'entrata: scegli le persone e come dividere il costo."),
            actionLabel = tr("Nuova spesa"),
            onAction = { navigator.newReminder(type = ReminderType.PAYMENT) },
        )
        return
    }
    val shown = current.recap.filter { picked == null || it.personId == picked }.ifEmpty { current.recap }
    val scopeLabel = if (shown.size == 1 && picked != null) name(shown.first().personId) else tr("Tutta la cerchia")

    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)!!.use { MoneyPdf.write(it, year, scopeLabel, shown, ::name) }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) tr("PDF salvato") else tr("Non è stato possibile creare il PDF"), Toast.LENGTH_LONG).show()
        }
    }

    FilterChipRow(
        options = listOf<String?>(null) + current.recap.map { it.personId },
        selected = picked,
        label = { id -> if (id == null) tr("Tutta la cerchia") else name(id) },
        onSelected = { picked = it },
    )
    SummaryCard(scopeLabel, shown.sumOf { it.spentCents }, shown.sumOf { it.receivedCents })
    shown.forEach { person -> PersonCard(person, name(person.personId), navigator) }
    PushButton(
        tr("Crea PDF"),
        icon = Icons.Rounded.PictureAsPdf,
        onClick = { pdf.launch("Remindella-spese-$year.pdf") },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        trf("Il PDF contiene quello che vedi: %1\$s.", scopeLabel),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SummaryCard(scope: String, spent: Long, received: Long) {
    val mint = MaterialTheme.ricordellaColors.mint
    Column(
        Modifier.fillMaxWidth().background(mint.container, MaterialTheme.shapes.large).padding(RicordellaDimensions.spaceL),
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM)) {
            Box(Modifier.size(44.dp).background(mint.solid, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Euro, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest)
            }
            Column(Modifier.weight(1f)) {
                Text(scope, style = MaterialTheme.typography.labelLarge, color = mint.content)
                Text(DateTexts.money(spent), style = MaterialTheme.typography.headlineMedium, color = mint.content)
            }
        }
        if (received > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                Fact(tr("Entrate"), "+ " + DateTexts.money(received), Modifier.weight(1f))
                Fact(tr("Saldo"), DateTexts.money(received - spent), Modifier.weight(1f))
            }
        }
    }
}

/** Una persona: quanto ha speso nell'anno e, per ogni pagamento, la sua parte. */
@Composable
private fun PersonCard(person: PersonRecap, name: String, navigator: AppNavigator) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large).padding(RicordellaDimensions.spaceL),
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(DateTexts.money(person.spentCents), style = MaterialTheme.typography.titleMedium)
        }
        person.lines.forEach { line ->
            Row(
                Modifier.fillMaxWidth().clickable { if (line.reminderId.isNotEmpty()) navigator.openReminder(line.reminderId) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
            ) {
                Icon(line.kind.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(line.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        line.kind.label + if (line.count > 1) " · " + trf("%1\$s volte", line.count) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text((if (line.kind == MoneyKind.INCOME) "+ " else "") + DateTexts.money(line.cents), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
