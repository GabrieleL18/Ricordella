package com.ricordella.app.feature.alarms

import android.app.AlarmManager
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.navigation.AppNavigator
import com.ricordella.app.core.ui.ConfirmDialog
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.EmptyState
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.date.nextRing
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.ReminderFilter
import com.ricordella.app.domain.model.ReminderListScope
import com.ricordella.app.domain.model.ReminderSortOrder
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.model.displayName
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Le sveglie, a parte dai promemoria: solo ora e giorni, e un interruttore per accenderle quando si vuole.
 * Una sveglia spenta resta qui, pronta da riaccendere.
 */
@Composable
fun AlarmsScreen(navigator: AppNavigator, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as RicordellaApplication).container }
    val scope = rememberCoroutineScope()
    val alarms by remember {
        container.reminderRepository
            .observeFiltered(ReminderFilter(scope = ReminderListScope.ALL, type = ReminderType.ALARM, sortOrder = ReminderSortOrder.DATE_ASC, limit = 200), container.time.today())
            .map { list -> list.filter { it.reminder.status == ReminderStatus.ACTIVE && it.reminder.dueTime != null }.sortedBy { it.reminder.dueTime } }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val now by remember { container.time.minuteTicks() }.collectAsStateWithLifecycle(initialValue = container.time.localNow())
    var menu by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ReminderWithLinks?>(null) }

    fun importNext() {
        val next = context.getSystemService(AlarmManager::class.java).nextAlarmClock
        if (next == null) {
            Toast.makeText(context, tr("Sul telefono non c'è nessuna sveglia attiva"), Toast.LENGTH_LONG).show()
            return
        }
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(next.triggerTime), ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0)
        if (alarms.any { it.reminder.dueTime == at }) {
            Toast.makeText(context, trf("Hai già una sveglia alle %1\$s", DateTexts.time(at)), Toast.LENGTH_LONG).show()
            return
        }
        // Una sveglia già pronta con quell'ora: poi si ritocca (giorni, nome, per chi suona) dalla sua scheda.
        scope.launch {
            container.importPhoneAlarm(at, tr("Sveglia"))
            Toast.makeText(context, trf("Importata la sveglia delle %1\$s", DateTexts.time(at)), Toast.LENGTH_LONG).show()
        }
    }

    DetailScaffold(
        title = tr("Sveglie"),
        onBack = onBack,
        actions = {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = tr("Altro")) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(tr("Importa la prossima sveglia del telefono")) },
                    onClick = { menu = false; importNext() },
                )
                DropdownMenuItem(
                    text = { Text(tr("Apri le sveglie del telefono")) },
                    onClick = {
                        menu = false
                        runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { Toast.makeText(context, tr("Non trovo l'app Orologio"), Toast.LENGTH_SHORT).show() }
                    },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navigator.newAlarm() },
                containerColor = MaterialTheme.ricordellaColors.bolt,
                contentColor = MaterialTheme.ricordellaColors.onBolt,
            ) { Icon(Icons.Rounded.Add, contentDescription = tr("Nuova sveglia")) }
        },
    ) { padding ->
        if (alarms.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Alarm,
                title = tr("Nessuna sveglia"),
                message = tr("Aggiungi una sveglia: scegli l'ora e i giorni, poi accendila o spegnila quando vuoi."),
                actionLabel = tr("Nuova sveglia"),
                onAction = { navigator.newAlarm() },
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
                nextRingText(alarms, now)?.let { text ->
                    item(key = "next") { Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp)) }
                }
                items(alarms, key = { it.reminder.id }) { entry ->
                    AlarmRow(
                        entry = entry,
                        onOpen = { navigator.editAlarm(entry.reminder.id) },
                        onToggle = { on -> scope.launch { container.setAlarmEnabled(entry.reminder.id, on) } },
                        onDelete = { toDelete = entry },
                    )
                }
            }
        }
    }

    toDelete?.let { entry ->
        ConfirmDialog(
            title = tr("Eliminare la sveglia?"),
            message = trf("La sveglia delle %1\$s viene eliminata.", DateTexts.time(entry.reminder.dueTime ?: LocalTime.MIDNIGHT)),
            confirmLabel = tr("Elimina"),
            destructive = true,
            onConfirm = { scope.launch { container.deleteReminder(entry.reminder.id) }; toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun AlarmRow(entry: ReminderWithLinks, onOpen: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val reminder = entry.reminder
    val on = reminder.notificationsEnabled
    val colors = MaterialTheme.ricordellaColors
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (on) colors.coral.container else MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(Modifier.padding(RicordellaDimensions.spaceM), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            Column(Modifier.weight(1f).alpha(if (on) 1f else 0.55f)) {
                AlarmClockText(reminder.dueTime ?: LocalTime.MIDNIGHT, if (on) colors.coral.content else MaterialTheme.colorScheme.onSurface)
                Text(
                    listOf(reminder.title.takeIf { it.isNotBlank() }, repeatText(entry)).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (on) colors.coral.content else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.people.isNotEmpty()) {
                    Text(entry.people.joinToString(", ") { it.displayName }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = tr("Elimina"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked = on, onCheckedChange = onToggle)
        }
    }
}

/** L'ora grande come nella schermata della sveglia: cifre pesanti e due punti corallo. */
@Composable
fun AlarmClockText(time: LocalTime, color: androidx.compose.ui.graphics.Color, size: Int = 44) {
    val style = MaterialTheme.typography.displayLarge.copy(fontSize = size.sp, fontWeight = FontWeight.SemiBold, lineHeight = (size + 4).sp)
    Row {
        Text("%02d".format(time.hour), style = style, color = color)
        Text(":", style = style, color = MaterialTheme.ricordellaColors.coral.solid)
        Text("%02d".format(time.minute), style = style, color = color)
    }
}

/** "Una volta", "Ogni giorno", "Lun–Ven" o i giorni scelti. */
@Composable
fun repeatText(entry: ReminderWithLinks): String {
    val rule = entry.recurrenceRule ?: return tr("Una volta")
    if (rule.frequency == RecurrenceFrequency.DAILY) return tr("Ogni giorno")
    val days = rule.daysOfWeek
    return when {
        days.size == 7 -> tr("Ogni giorno")
        days == DayOfWeek.entries.take(5).toSet() -> tr("Lun–Ven")
        days.isEmpty() -> tr("Una volta")
        else -> days.sorted().joinToString(", ") { DateTexts.weekdayFull(it).take(3) }
    }
}

/** "La prossima suona tra 7 h 20 min", sulla base delle sveglie accese. */
private fun nextRingText(alarms: List<ReminderWithLinks>, now: LocalDateTime): String? {
    val next = alarms.mapNotNull { it.reminder.nextRing(now, ZoneId.systemDefault()) }.minOrNull() ?: return null
    return trf("La prossima suona %1\$s", untilText(Duration.between(now, next)))
}

fun untilText(duration: Duration): String {
    val minutes = duration.toMinutes().coerceAtLeast(0)
    val days = minutes / (24 * 60)
    val hours = minutes / 60 % 24
    val mins = minutes % 60
    return when {
        days > 0 -> trf("tra %1\$s g %2\$s h", days, hours)
        hours > 0 -> trf("tra %1\$s h %2\$s min", hours, mins)
        else -> trf("tra %1\$s min", mins.coerceAtLeast(1))
    }
}
