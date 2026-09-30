package com.ricordella.app.core.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors

/**
 * Pulsante "push" dell'azione principale: una pastiglia gialla con uno spessore visibile
 * che si abbassa fisicamente quando viene premuta. La pressione stessa è il feedback.
 * Stati: normale · premuto · disabilitato · in caricamento (focus e ripple da Material).
 */
@Composable
fun PushButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    face: Color = MaterialTheme.ricordellaColors.bolt,
    content: Color = MaterialTheme.ricordellaColors.onBolt,
    edge: Color = MaterialTheme.ricordellaColors.boltEdge,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val edgeHeight = RicordellaDimensions.pushEdge
    val visibleEdge by animateDpAsState(
        targetValue = if (pressed && enabled) 1.dp else edgeHeight,
        animationSpec = tween(if (pressed) 70 else 140, easing = RicordellaMotion.Snappy),
        label = "pushEdge",
    )
    val active = enabled && !loading

    // propagateMinConstraints: con fillMaxWidth la faccia occupa tutta la larghezza, come lo spessore.
    Box(modifier.alpha(if (enabled) 1f else 0.5f), propagateMinConstraints = true) {
        // Lo spessore del pulsante: resta fermo, la faccia ci scende sopra.
        Box(
            Modifier
                .matchParentSize()
                .padding(top = edgeHeight)
                .background(edge, CircleShape),
        )
        Row(
            modifier = Modifier
                .padding(bottom = edgeHeight)
                .offset { IntOffset(0, (edgeHeight - visibleEdge).roundToPx()) }
                .background(face, CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = active,
                    role = Role.Button,
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                }
                .defaultMinSize(minHeight = 52.dp)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                loading -> CircularProgressIndicator(color = content, strokeWidth = 2.5.dp, modifier = Modifier.size(18.dp))
                icon != null -> Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium, color = content, maxLines = 1)
        }
    }
}
