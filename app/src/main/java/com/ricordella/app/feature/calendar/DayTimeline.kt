package com.ricordella.app.feature.calendar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.ui.RicordellaMotion
import com.ricordella.app.core.ui.icon
import com.ricordella.app.core.ui.label
import com.ricordella.app.core.ui.pressScale
import com.ricordella.app.core.ui.rememberReducedMotion
import com.ricordella.app.core.ui.rememberRevealTracker
import com.ricordella.app.core.ui.reveal
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.core.ui.tone
import com.ricordella.app.domain.model.ReminderOccurrence
import com.ricordella.app.domain.model.ReminderStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private const val DEFAULT_FIRST_HOUR = 7
private const val DEFAULT_LAST_HOUR = 22

/**
 * Timeline oraria di una giornata: una riga per ora con linea verticale, i promemoria
 * all'ora giusta con icona del tipo e descrizione, e (oggi) l'indicatore dell'ora corrente
 * che pulsa. Le ore vuote si toccano per aggiungere un promemoria in quel giorno.
 */
@Composable
fun DayTimeline(
    date: LocalDate,
    occurrences: List<ReminderOccurrence>,
    now: LocalDateTime,
    onOpen: (ReminderOccurrence) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tracker = rememberRevealTracker()
    val (allDay, timed) = occurrences.partition { it.reminder.dueTime == null }
    val byHour = timed.groupBy { it.reminder.dueTime!!.hour }
    val isToday = date == now.toLocalDate()
    val first = minOf(DEFAULT_FIRST_HOUR, byHour.keys.minOrNull() ?: DEFAULT_FIRST_HOUR, if (isToday) now.hour else DEFAULT_FIRST_HOUR)
    val last = maxOf(DEFAULT_LAST_HOUR, byHour.keys.maxOrNull() ?: DEFAULT_LAST_HOUR)
    var index = 0

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (allDay.isNotEmpty()) {
            Text(
                "TUTTO IL GIORNO",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            allDay.forEach { occurrence ->
                TimelineCard(occurrence, onOpen, Modifier.padding(bottom = 6.dp).reveal(tracker, "all-" + occurrence.reminder.id, index++))
            }
        }
        for (hour in first..last) {
            val entries = byHour[hour].orEmpty().sortedBy { it.reminder.dueTime }
            HourRow(
                hour = hour,
                nowMinute = if (isToday && now.hour == hour) now.minute else null,
                empty = entries.isEmpty(),
                onAdd = onAdd,
            ) {
                entries.forEach { occurrence ->
                    TimelineCard(occurrence, onOpen, Modifier.reveal(tracker, "h-" + occurrence.reminder.id, index++))
                }
            }
        }
    }
}

@Composable
private fun HourRow(hour: Int, nowMinute: Int?, empty: Boolean, onAdd: () -> Unit, content: @Composable () -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(
            DateTexts.time(LocalTime.of(hour, 0)),
            style = MaterialTheme.typography.labelMedium,
            color = if (nowMinute != null) colors.coral.content else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp).padding(top = 2.dp),
        )
        // Linea del tempo con il pallino dell'ora.
        Box(Modifier.width(20.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            Box(Modifier.padding(top = 4.dp).size(10.dp).background(if (empty) MaterialTheme.colorScheme.outlineVariant else colors.boltEdge, CircleShape))
        }
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = if (empty) 36.dp else 0.dp)
                .padding(start = 6.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (nowMinute != null) NowIndicator(nowMinute)
            if (empty) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(role = Role.Button, onClickLabel = "Aggiungi alle ${hour}", onClick = onAdd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(16.dp))
                }
            } else {
                content()
            }
        }
    }
}

/** "Adesso": linea corallo con un pallino che pulsa. */
@Composable
private fun NowIndicator(minute: Int) {
    val colors = MaterialTheme.ricordellaColors
    val pulse = if (rememberReducedMotion()) 1f else rememberInfiniteTransition(label = "now").animateFloat(
        0.6f,
        1f,
        infiniteRepeatable(tween(900, easing = RicordellaMotion.EaseInOut), RepeatMode.Reverse),
        label = "pulse",
    ).value
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }.background(colors.coral.solid, CircleShape))
        Box(Modifier.weight(1f).height(2.dp).background(colors.coral.solid))
        Text("adesso :%02d".format(minute), style = MaterialTheme.typography.labelSmall, color = colors.coral.content)
    }
}

/** Scheda di un promemoria nella timeline: icona del tipo, titolo, orario e descrizione. */
@Composable
private fun TimelineCard(occurrence: ReminderOccurrence, onOpen: (ReminderOccurrence) -> Unit, modifier: Modifier = Modifier) {
    val reminder = occurrence.reminder
    val tone = reminder.type.tone
    val done = reminder.status == ReminderStatus.COMPLETED
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(MaterialTheme.shapes.medium)
            .background(tone.container)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onOpen(occurrence) }
            .padding(RicordellaDimensions.spaceM),
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Box(Modifier.size(40.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                if (done) Icons.Rounded.CheckCircle else reminder.type.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                reminder.title,
                style = MaterialTheme.typography.titleMedium,
                color = tone.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            Text(
                listOfNotNull(reminder.dueTime?.let(DateTexts::time), reminder.type.label).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = tone.content.copy(alpha = 0.8f),
            )
            (reminder.description ?: reminder.notes)?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = tone.content, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
