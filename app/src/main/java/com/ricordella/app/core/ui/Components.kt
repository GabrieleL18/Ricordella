package com.ricordella.app.core.ui

import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Delete
import com.ricordella.app.core.i18n.tr

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.Priority

/** In fondo ai dettagli: «Modifica» (giallo, come le azioni principali) e «Elimina» (rosso), entrambi pulsanti push dell'app. */
@Composable
fun EditDeleteRow(onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = RicordellaDimensions.spaceXl), horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM)) {
        PushButton(tr("Modifica"), onClick = onEdit, icon = androidx.compose.material.icons.Icons.Rounded.Edit, modifier = Modifier.weight(1f))
        PushButton(
            tr("Elimina"),
            onClick = onDelete,
            icon = androidx.compose.material.icons.Icons.Rounded.Delete,
            face = MaterialTheme.colorScheme.error,
            content = MaterialTheme.colorScheme.onError,
            edge = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: Tone? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = RicordellaDimensions.spaceXl, bottom = RicordellaDimensions.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
    ) {
        if (icon != null) {
            val colors = tone ?: MaterialTheme.ricordellaColors.cyan
            Box(
                Modifier
                    .size(28.dp)
                    .background(colors.container, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        action?.invoke()
    }
}

/** Stato vuoto: la mascotte, un messaggio utile e, se serve, l'azione per iniziare. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    /** Sostituisce la mascotte con un'illustrazione dedicata. */
    illustration: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(RicordellaDimensions.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
    ) {
        if (illustration != null) illustration() else Box(contentAlignment = Alignment.BottomEnd) {
            CrystalBallMascot(size = 104.dp)
            val tone = MaterialTheme.ricordellaColors.lavender
            Box(
                Modifier
                    .size(36.dp)
                    .background(tone.container, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(20.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            PushButton(
                text = actionLabel,
                onClick = onAction,
                icon = Icons.Rounded.Add,
                modifier = Modifier.padding(top = RicordellaDimensions.spaceM),
            )
        }
    }
}

/** Icona in un contenitore arrotondato, usata come "avatar" di promemoria e cose. */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        modifier = modifier
            .size(RicordellaDimensions.iconBadge)
            .background(containerColor, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
    }
}

/** Etichetta di stato con testo (lo stato non è mai comunicato solo dal colore). */
@Composable
fun StatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(color = containerColor, contentColor = contentColor, shape = CircleShape, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun PriorityIndicator(priority: Priority, modifier: Modifier = Modifier) {
    val icon = priority.icon ?: return
    val tone = when (priority) {
        Priority.URGENT -> MaterialTheme.ricordellaColors.coral
        else -> MaterialTheme.ricordellaColors.pear
    }
    StatusBadge(text = priority.label, containerColor = tone.container, contentColor = tone.content, icon = icon, modifier = modifier)
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    /** Pulsanti in più a destra (es. il microfono). */
    extraTrailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, maxLines = 1) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            Row {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = tr("Cancella ricerca"))
                    }
                }
                extraTrailing?.invoke()
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
fun <T> FilterChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annulla")) } },
    )
}

/** Campo a tendina per scegliere un valore da un elenco. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Riga "etichetta: valore" delle schede di dettaglio. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}

/**
 * Filtro a pillola con icona in un cerchio colorato (ed eventuale conteggio).
 * Quando è scelto si riempie del colore del tono e fa un piccolo "salto".
 */
@Composable
fun IconChoiceChip(
    label: String,
    icon: ImageVector,
    tone: Tone,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val background by animateColorAsState(if (selected) tone.solid else MaterialTheme.colorScheme.surfaceContainerLowest, tween(RicordellaMotion.SHORT), label = "chipBg")
    val content by animateColorAsState(if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.onSurface, tween(RicordellaMotion.SHORT), label = "chipFg")
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            bounce.snapTo(0.9f)
            bounce.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        }
    }
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
            .pressScale(interaction, pressedScale = 0.94f)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(28.dp).background(if (selected) MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.25f) else tone.container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) content else tone.content, modifier = Modifier.size(16.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        if (count != null) {
            Text("$count", style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.7f))
        }
    }
}

/** Riga scorrevole di [IconChoiceChip]. */
@Composable
fun IconChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
