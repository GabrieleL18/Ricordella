package com.ricordella.app.feature.timers

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderDraft
import com.ricordella.app.domain.model.ReminderFilter
import com.ricordella.app.domain.model.ReminderListScope
import com.ricordella.app.domain.model.ReminderSortOrder
import com.ricordella.app.domain.model.ReminderType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration

/** I timer sono sveglie che suonano una volta sola, riconoscibili dal nome: così suonano a tutto schermo come le sveglie. */
const val TIMER_PREFIX = "⏱ "

fun Reminder.isTimer(): Boolean = type == ReminderType.ALARM && title.startsWith(TIMER_PREFIX)

/** Timer: scegli i minuti e parte il conto alla rovescia; allo scadere suona come una sveglia. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as RicordellaApplication).container }
    val scope = rememberCoroutineScope()
    val timers by remember {
        container.reminderRepository
            .observeFiltered(ReminderFilter(scope = ReminderListScope.ALL, type = ReminderType.ALARM, sortOrder = ReminderSortOrder.DATE_ASC, limit = 200), container.time.today())
            .map { list -> list.filter { it.reminder.isTimer() } }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { nowMs = System.currentTimeMillis(); delay(1000) } }
    var custom by remember { mutableStateOf("") }
    // Senza passare dal Cestino: un timer non serve ritrovarlo.
    suspend fun remove(id: String) {
        container.reminderRepository.delete(id)
        container.reminderScheduler.dismissNotification(id)
        container.reminderScheduler.refresh()
    }
    // I timer già suonati si tolgono da soli.
    LaunchedEffect(timers) { timers.filter { !it.reminder.notificationsEnabled }.forEach { remove(it.reminder.id) } }

    fun start(minutes: Int) {
        if (minutes <= 0) return
        scope.launch {
            val who = container.settingsRepository.current().sharedMeId
                ?: container.personRepository.observePeople(archived = false).first().firstOrNull()?.id
            if (who == null) {
                Toast.makeText(context, tr("Aggiungi prima una persona nella sezione Persone."), Toast.LENGTH_LONG).show()
                return@launch
            }
            val now = container.time.now()
            // Al minuto più vicino, come le sveglie.
            val ring = container.time.localNow().plusMinutes(minutes.toLong()).plusSeconds(30).withSecond(0).withNano(0)
            val timer = Reminder(
                title = TIMER_PREFIX + tr("Timer"), type = ReminderType.ALARM, dueDate = ring.toLocalDate(), dueTime = ring.toLocalTime(),
                notificationsEnabled = true, notifyOffsetMinutes = 0, createdAt = now, updatedAt = now,
            )
            container.saveReminder(ReminderDraft(timer, null, setOf(who), emptySet()))
            custom = ""
        }
    }

    DetailScaffold(title = tr("Timer"), onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).contentWidth()
                .padding(horizontal = RicordellaDimensions.screenPadding).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            val tone = MaterialTheme.ricordellaColors.cyan
            Text(tr("Scegli quanto dura: allo scadere suona come una sveglia."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10, 15, 30, 60).forEach { m ->
                    AssistChip(onClick = { start(m) }, label = { Text(trf("%1\$s min", m)) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { v -> custom = v.filter(Char::isDigit).take(4) },
                    label = { Text(tr("Minuti")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                PushButton(text = tr("Avvia"), icon = Icons.Rounded.PlayArrow, onClick = { start(custom.toIntOrNull() ?: 0) })
            }
            timers.filter { it.reminder.notificationsEnabled }.forEach { entry ->
                val ring = entry.reminder.dueTime?.let { entry.reminder.dueDate.atTime(it) }?.atZone(container.time.zone)?.toInstant()?.toEpochMilli() ?: return@forEach
                val left = Duration.ofMillis((ring - nowMs).coerceAtLeast(0))
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = tone.container), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(RicordellaDimensions.spaceM), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            "%d:%02d:%02d".format(left.toHours(), left.toMinutesPart(), left.toSecondsPart()),
                            style = MaterialTheme.typography.displaySmall, color = tone.content, modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { scope.launch { remove(entry.reminder.id) } }) {
                            Icon(Icons.Rounded.Close, contentDescription = tr("Annulla"), tint = tone.content)
                        }
                    }
                }
            }
        }
    }
}
