package com.ricordella.app.core.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.date.RelativeDateDescriber
import com.ricordella.app.domain.date.ReminderTimeline
import com.ricordella.app.domain.model.ItemWithCategory
import com.ricordella.app.domain.model.OdometerStatus
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.ReminderTimeStatus
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.model.displayName
import com.ricordella.app.domain.model.odometerStatus
import java.time.LocalDate
import java.time.LocalDateTime

/** Come mostrare la data in una [ReminderCard]. */
enum class ReminderDateMode { ABSOLUTE, RELATIVE, TIME_ONLY }

@Composable
fun ReminderCard(
    entry: ReminderWithLinks,
    now: LocalDateTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleComplete: (() -> Unit)? = null,
    dateMode: ReminderDateMode = ReminderDateMode.ABSOLUTE,
    occurrenceDate: LocalDate = entry.reminder.dueDate,
    highlighted: Boolean = false,
) {
    val reminder = entry.reminder
    val settings = LocalAppSettings.current
    val today = now.toLocalDate()
    val timeStatus = ReminderTimeline.timeStatus(reminder, now)
    val isDone = reminder.status == ReminderStatus.COMPLETED

    val dateText = when (dateMode) {
        ReminderDateMode.ABSOLUTE -> DateTexts.dateWithTime(occurrenceDate, reminder.dueTime, settings.dateFormat, today)
        ReminderDateMode.TIME_ONLY -> reminder.dueTime?.let(DateTexts::time) ?: "Tutto il giorno"
        ReminderDateMode.RELATIVE ->
            if (reminder.type.isDeadlineLike) RelativeDateDescriber.describeDeadline(occurrenceDate, today)
            else DateTexts.relativeWithTime(occurrenceDate, reminder.dueTime, today)
    }
    val links = (entry.items.map { it.name } + entry.people.map { it.displayName }).joinToString(" · ")

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = RicordellaDimensions.spaceM, top = RicordellaDimensions.spaceM, bottom = RicordellaDimensions.spaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            IconBadge(reminder.type.icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        dateText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (timeStatus == ReminderTimeStatus.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (timeStatus == ReminderTimeStatus.OVERDUE) FontWeight.SemiBold else null,
                    )
                    if (entry.recurrenceRule != null) {
                        Icon(Icons.Rounded.Repeat, contentDescription = "Ricorrente", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (links.isNotEmpty()) {
                    Text(links, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ReminderBadges(entry, timeStatus)
            }
            if (onToggleComplete != null) {
                IconButton(onClick = onToggleComplete) {
                    Icon(
                        if (isDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (isDone) "Segna come da fare" else "Segna come completato",
                        tint = if (isDone) MaterialTheme.ricordellaColors.success else MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                Box(Modifier.size(RicordellaDimensions.spaceS))
            }
        }
    }
}

@Composable
private fun ReminderBadges(entry: ReminderWithLinks, timeStatus: ReminderTimeStatus) {
    val odometer = entry.odometerStatus?.takeIf { it != OdometerStatus.FAR }
    val showOverdue = timeStatus == ReminderTimeStatus.OVERDUE
    if (!showOverdue && odometer == null && entry.reminder.priority.icon == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        if (showOverdue) {
            StatusBadge("Scaduto", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        }
        if (odometer != null) {
            StatusBadge("Km: ${odometer.label}", MaterialTheme.ricordellaColors.warningContainer, MaterialTheme.ricordellaColors.onWarningContainer)
        }
        PriorityIndicator(entry.reminder.priority)
    }
}

@Composable
fun PersonAvatar(person: Person, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val initials = person.displayName.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    val placeholder: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials.ifEmpty { "?" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
    Box(modifier = modifier.size(size).clip(CircleShape)) {
        val photo = person.photoUri
        if (photo != null) {
            UriImage(photo, contentDescription = null, modifier = Modifier.size(size), maxSizePx = 256, fallback = placeholder)
        } else {
            placeholder()
        }
    }
}

@Composable
fun PersonCard(person: Person, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(RicordellaDimensions.spaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            PersonAvatar(person)
            Column(Modifier.weight(1f)) {
                Text(person.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ItemCard(entry: ItemWithCategory, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    val item = entry.item
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(RicordellaDimensions.spaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            val iconBadge: @Composable () -> Unit = { IconBadge(entry.category?.kind.icon) }
            val photo = item.photoUri
            if (photo != null) {
                UriImage(
                    photo,
                    contentDescription = null,
                    modifier = Modifier.size(RicordellaDimensions.iconBadge).clip(RoundedCornerShape(12.dp)),
                    maxSizePx = 192,
                    fallback = iconBadge,
                )
            } else {
                iconBadge()
            }
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = subtitle ?: listOfNotNull(entry.category?.name, item.brand, item.model).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Piccolo chip cliccabile per le relazioni (persona, cosa) nelle schede di dettaglio. */
@Composable
fun LinkChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
