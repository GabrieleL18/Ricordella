package com.ricordella.app.feature.calendar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.ricordella.app.core.ui.UiSound
import com.ricordella.app.core.ui.rememberUiSounds
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
import com.ricordella.app.domain.model.extraDays
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime


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
    /** Se presente, le schede hanno il cerchio per completare. */
    onToggle: ((ReminderOccurrence) -> Unit)? = null,
) {
    val tracker = rememberRevealTracker()
    // Gli eventi di più giorni stanno in cima, come "tutto il giorno".
    val (allDay, timed) = occurrences.partition { it.reminder.dueTime == null || it.isMultiDay }
    val byHour = timed.groupBy { it.reminder.dueTime!!.hour }
    val isToday = date == now.toLocalDate()
    // Solo le ore con impegni: niente righe vuote. Oggi la linea "adesso" si inserisce al suo posto.
    val hours = byHour.keys.sorted()
    var nowPlaced = !isToday || now.hour in byHour
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
                TimelineCard(occurrence, onOpen, onToggle, Modifier.padding(bottom = 6.dp).reveal(tracker, "all-" + occurrence.reminder.id, index++))
            }
        }
        hours.forEach { hour ->
            if (!nowPlaced && hour > now.hour) {
                NowRow(now.minute)
                nowPlaced = true
            }
            val entries = byHour.getValue(hour).sortedBy { it.reminder.dueTime }
            HourRow(
                label = DateTexts.time(entries.first().reminder.dueTime!!),
                nowMinute = if (isToday && now.hour == hour) now.minute else null,
            ) {
                entries.forEach { occurrence ->
                    TimelineCard(occurrence, onOpen, onToggle, Modifier.reveal(tracker, "h-" + occurrence.reminder.id, index++))
                }
            }
        }
        if (!nowPlaced) NowRow(now.minute)
        AddRow(if (occurrences.isEmpty()) "Nessun impegno: aggiungine uno" else "Aggiungi", onAdd)
    }
}

/** Riga "adesso" quando in quest'ora non c'è nulla. */
@Composable
private fun NowRow(minute: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(68.dp))
        Box(Modifier.weight(1f)) { NowIndicator(minute) }
    }
}

@Composable
private fun AddRow(text: String, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onAdd)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(60.dp), contentAlignment = Alignment.CenterEnd) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun HourRow(label: String, nowMinute: Int?, content: @Composable () -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (nowMinute != null) colors.coral.content else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(48.dp).padding(top = 12.dp),
        )
        // Linea del tempo con il pallino dell'ora.
        Box(Modifier.width(20.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            Box(Modifier.padding(top = 14.dp).size(10.dp).background(colors.boltEdge, CircleShape))
        }
        Column(
            Modifier.weight(1f).padding(start = 6.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (nowMinute != null) NowIndicator(nowMinute)
            content()
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
private fun TimelineCard(
    occurrence: ReminderOccurrence,
    onOpen: (ReminderOccurrence) -> Unit,
    onToggle: ((ReminderOccurrence) -> Unit)?,
    modifier: Modifier = Modifier,
) {
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
                listOfNotNull(
                    if (occurrence.isMultiDay) "Giorno ${occurrence.dayNumber} di ${reminder.extraDays + 1}" else reminder.dueTime?.let(DateTexts::time),
                    reminder.type.label,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = tone.content.copy(alpha = 0.8f),
            )
            (reminder.description ?: reminder.notes)?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = tone.content, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        val canComplete = onToggle != null && !done && reminder.type.isCompletable && !occurrence.isProjected
        if (canComplete) {
            val sounds = rememberUiSounds()
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(2.5.dp, tone.solid, CircleShape)
                    .clickable(role = Role.Checkbox, onClickLabel = "Completa") { sounds(UiSound.DING); onToggle!!(occurrence) },
            )
        }
    }
}
