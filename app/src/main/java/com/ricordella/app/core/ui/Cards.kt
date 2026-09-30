package com.ricordella.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Come mostrare la data in una [ReminderCard]. */
enum class ReminderDateMode { ABSOLUTE, RELATIVE, TIME_ONLY }

/** Tempo lasciato alla festa della spunta prima di aggiornare i dati (e far sparire la card). */
private const val CELEBRATION_MS = 420L

/**
 * Card di un promemoria. Si completa toccando il cerchio a destra oppure trascinando la card
 * verso destra: la spunta si riempie con un piccolo scoppio di stelle e una vibrazione.
 */
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
    val isDone = reminder.status == ReminderStatus.COMPLETED
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var celebrating by remember(reminder.id, reminder.dueDate, reminder.status) { mutableStateOf(false) }
    var burst by remember { mutableIntStateOf(0) }

    val toggle: (() -> Unit)? = onToggleComplete?.let { callback ->
        {
            if (!isDone && !celebrating) {
                celebrating = true
                burst++
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch {
                    delay(CELEBRATION_MS)
                    callback()
                }
            } else if (isDone) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                callback()
            }
        }
    }

    val body: @Composable () -> Unit = {
        ReminderCardBody(
            entry = entry,
            now = now,
            onClick = onClick,
            toggle = toggle,
            checked = isDone || celebrating,
            burst = burst,
            dateMode = dateMode,
            occurrenceDate = occurrenceDate,
            highlighted = highlighted,
        )
    }

    if (toggle != null && !isDone) {
        val swipeState = rememberSwipeToDismissBoxState()
        SwipeToDismissBox(
            state = swipeState,
            modifier = modifier,
            enableDismissFromEndToStart = false,
            onDismiss = { value ->
                if (value == SwipeToDismissBoxValue.StartToEnd) toggle()
                // La card torna al suo posto: la festa della spunta fa il resto.
                scope.launch { swipeState.reset() }
            },
            backgroundContent = {
                val tone = MaterialTheme.ricordellaColors.mint
                val progress = swipeState.progress.coerceIn(0f, 1f)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.large)
                        .background(tone.container)
                        .padding(horizontal = RicordellaDimensions.spaceXl),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = tone.content,
                        modifier = Modifier.graphicsLayer {
                            val scale = 0.6f + 0.8f * progress
                            scaleX = scale
                            scaleY = scale
                        },
                    )
                    Text("Completa", style = MaterialTheme.typography.titleMedium, color = tone.content)
                }
            },
        ) { body() }
    } else {
        Box(modifier) { body() }
    }
}

@Composable
private fun ReminderCardBody(
    entry: ReminderWithLinks,
    now: LocalDateTime,
    onClick: () -> Unit,
    toggle: (() -> Unit)?,
    checked: Boolean,
    burst: Int,
    dateMode: ReminderDateMode,
    occurrenceDate: LocalDate,
    highlighted: Boolean,
) {
    val reminder = entry.reminder
    val settings = LocalAppSettings.current
    val colors = MaterialTheme.ricordellaColors
    val today = now.toLocalDate()
    val timeStatus = ReminderTimeline.timeStatus(reminder, now)
    val overdue = timeStatus == ReminderTimeStatus.OVERDUE && !checked
    val interaction = remember { MutableInteractionSource() }
    val tone = if (checked) colors.mint else reminder.type.tone

    val dateText = when (dateMode) {
        ReminderDateMode.ABSOLUTE -> DateTexts.dateWithTime(occurrenceDate, reminder.dueTime, settings.dateFormat, today)
        ReminderDateMode.TIME_ONLY -> reminder.dueTime?.let(DateTexts::time) ?: "Tutto il giorno"
        ReminderDateMode.RELATIVE ->
            if (reminder.type.isDeadlineLike) RelativeDateDescriber.describeDeadline(occurrenceDate, today)
            else DateTexts.relativeWithTime(occurrenceDate, reminder.dueTime, today)
    }
    val links = (entry.items.map { it.name } + entry.people.map { it.displayName }).joinToString(" · ")
    val titleAlpha by animateFloatAsState(if (checked) 0.55f else 1f, tween(RicordellaMotion.SHORT), label = "titleAlpha")
    val badgeContainer by animateColorAsState(tone.container, tween(RicordellaMotion.SHORT), label = "badgeContainer")
    val badgeContent by animateColorAsState(tone.content, tween(RicordellaMotion.SHORT), label = "badgeContent")

    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) colors.pear.container else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = RicordellaDimensions.spaceM, top = RicordellaDimensions.spaceM, bottom = RicordellaDimensions.spaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            IconBadge(reminder.type.icon, containerColor = badgeContainer, contentColor = badgeContent)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                    modifier = Modifier.graphicsLayer { alpha = titleAlpha },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        dateText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (overdue) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (overdue) FontWeight.Bold else FontWeight.Medium,
                    )
                    if (entry.recurrenceRule != null) {
                        Icon(Icons.Rounded.Repeat, contentDescription = "Ricorrente", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (links.isNotEmpty()) {
                    Text(links, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ReminderBadges(entry, overdue)
            }
            if (toggle != null) {
                CompleteToggle(checked = checked, burst = burst, onClick = toggle)
            } else {
                Box(Modifier.size(RicordellaDimensions.spaceS))
            }
        }
    }
}

/** Cerchio di completamento: si riempie di verde con una piccola molla e uno scoppio di stelle. */
@Composable
private fun CompleteToggle(checked: Boolean, burst: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    val scale = remember { Animatable(1f) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(burst) {
        if (burst > 0 && !reduced) {
            scale.snapTo(0.55f)
            scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        }
    }
    val fill by animateColorAsState(if (checked) colors.mint.solid else Color.Transparent, tween(RicordellaMotion.MICRO), label = "checkFill")
    val ring by animateColorAsState(if (checked) colors.mint.solid else MaterialTheme.colorScheme.outline, tween(RicordellaMotion.MICRO), label = "checkRing")

    Box(
        modifier = Modifier
            .size(RicordellaDimensions.minTouchTarget)
            .semantics {
                contentDescription = "Completato"
                stateDescription = if (checked) "Fatto" else "Da fare"
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        StarBurst(
            trigger = burst,
            colors = listOf(colors.bolt, colors.coral.solid, colors.mint.solid),
            modifier = Modifier.requiredSize(84.dp),
        )
        Box(
            Modifier
                .size(28.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .background(fill, CircleShape)
                .border(2.5.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ReminderBadges(entry: ReminderWithLinks, overdue: Boolean) {
    val odometer = entry.odometerStatus?.takeIf { it != OdometerStatus.FAR }
    if (!overdue && odometer == null && entry.reminder.priority.icon == null) return
    val colors = MaterialTheme.ricordellaColors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        if (overdue) {
            StatusBadge("Scaduto", colors.coral.container, colors.coral.content)
        }
        if (odometer != null) {
            StatusBadge("Km: ${odometer.label}", colors.pear.container, colors.pear.content)
        }
        PriorityIndicator(entry.reminder.priority)
    }
}

@Composable
fun PersonAvatar(person: Person, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val initials = person.displayName.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    val tone = toneFor(person.id)
    val placeholder: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size)
                .background(tone.container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials.ifEmpty { "?" },
                style = if (size > 56.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                color = tone.content,
            )
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
    val interaction = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().pressScale(interaction),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun ItemCard(entry: ItemWithCategory, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    val item = entry.item
    val tone = entry.group.tone
    val interaction = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().pressScale(interaction),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(RicordellaDimensions.spaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            val iconBadge: @Composable () -> Unit = {
                IconBadge(entry.category?.kind.icon, containerColor = tone.container, contentColor = tone.content)
            }
            val photo = item.photoUri
            if (photo != null) {
                UriImage(
                    photo,
                    contentDescription = null,
                    modifier = Modifier.size(RicordellaDimensions.iconBadge).clip(RoundedCornerShape(14.dp)),
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

/** Chip cliccabile per le relazioni (persona, cosa) nelle schede di dettaglio. */
@Composable
fun LinkChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tone = toneFor(text)
    val interaction = remember { MutableInteractionSource() }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = tone.content,
        modifier = modifier
            .pressScale(interaction)
            .clip(CircleShape)
            .background(tone.container)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
