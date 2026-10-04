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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.ricordella.app.core.ui.pressScale
import androidx.compose.material3.Icon
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
        val tone = MaterialTheme.ricordellaColors.mint
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).contentWidth()
                .padding(horizontal = RicordellaDimensions.screenPadding).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            val running = timers.filter { it.reminder.notificationsEnabled }
            running.forEach { entry ->
                val ring = entry.reminder.dueTime?.let { entry.reminder.dueDate.atTime(it) }?.atZone(container.time.zone)?.toInstant()?.toEpochMilli() ?: return@forEach
                val total = (ring - entry.reminder.createdAt.toEpochMilli()).coerceAtLeast(1)
                val leftMs = (ring - nowMs).coerceAtLeast(0)
                val left = Duration.ofMillis(leftMs)
                Column(
                    Modifier.fillMaxWidth().background(tone.container, MaterialTheme.shapes.extraLarge).padding(RicordellaDimensions.spaceL),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { (leftMs.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.size(200.dp),
                            strokeWidth = 12.dp,
                            color = tone.solid,
                            trackColor = tone.solid.copy(alpha = 0.2f),
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        )
                        Text(
                            if (left.toHours() > 0) "%d:%02d:%02d".format(left.toHours(), left.toMinutesPart(), left.toSecondsPart()) else "%02d:%02d".format(left.toMinutesPart(), left.toSecondsPart()),
                            style = MaterialTheme.typography.displayMedium, color = tone.content,
                        )
                    }
                    Text(
                        trf("Suona alle %1\$s", com.ricordella.app.core.date.DateTexts.time(entry.reminder.dueTime!!)),
                        style = MaterialTheme.typography.bodyMedium, color = tone.content,
                    )
                    TextButton(onClick = { scope.launch { remove(entry.reminder.id) } }) {
                        Icon(Icons.Rounded.Close, contentDescription = null, tint = tone.content)
                        Text(tr("Annulla"), color = tone.content, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            Text(
                if (running.isEmpty()) tr("Scegli quanto dura: allo scadere suona come una sveglia.") else tr("Un altro timer"),
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(1, 5, 10, 15, 30, 60).forEach { m ->
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        Modifier
                            .width(100.dp)
                            .pressScale(interaction, pressedScale = 0.94f)
                            .clip(MaterialTheme.shapes.large)
                            .background(tone.container)
                            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { start(m) }
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("$m", style = MaterialTheme.typography.headlineMedium, color = tone.content)
                        Text(tr("min"), style = MaterialTheme.typography.labelMedium, color = tone.content)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        }
    }
}
