package com.ricordella.app.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.ConfirmDialog
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.EmptyState
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.data.trash.KEEP_DAYS
import com.ricordella.app.data.trash.TrashEntry
import com.ricordella.app.data.trash.TrashKind
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Cestino: quello che hai eliminato resta qui una settimana e si può recuperare; poi sparisce per sempre. */
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as RicordellaApplication).container
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by container.trash.entries.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    var deleting by remember { mutableStateOf<TrashEntry?>(null) }
    LaunchedEffect(Unit) { container.trash.reload() }

    DetailScaffold(title = tr("Cestino"), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .contentWidth()
                .padding(horizontal = RicordellaDimensions.screenPadding)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
        ) {
            Text(
                trf("Promemoria, cose, persone e interventi eliminati restano qui %1\$s giorni: puoi recuperarli. Poi vengono cancellati definitivamente.", KEEP_DAYS),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.DeleteSweep,
                    title = tr("Il cestino è vuoto"),
                    message = tr("Quando elimini qualcosa lo trovi qui per una settimana."),
                )
            }
            val now = Instant.now()
            entries.forEach { entry ->
                val tone = entry.kind.tone()
                Column(
                    Modifier.fillMaxWidth().background(tone.container, MaterialTheme.shapes.large).padding(RicordellaDimensions.spaceL),
                    verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM)) {
                        Box(Modifier.size(40.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(entry.kind.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (entry.count > 1) trf("%1\$s e altri %2\$s", entry.title, entry.count - 1) else entry.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = tone.content,
                            )
                            val at = entry.deletedAt.atZone(ZoneId.systemDefault()).toLocalDate()
                            Text(
                                trf("%1\$s · eliminato il %2\$s · ancora %3\$s giorni", entry.kind.label(), DateTexts.date(at, settings.dateFormat), entry.daysLeft(now)),
                                style = MaterialTheme.typography.bodySmall,
                                color = tone.content,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS), verticalAlignment = Alignment.CenterVertically) {
                        PushButton(
                            tr("Recupera"),
                            icon = Icons.Rounded.Restore,
                            onClick = {
                                scope.launch {
                                    val ok = runCatching { container.trash.restore(entry) }.isSuccess
                                    if (ok) container.reminderScheduler.refresh()
                                    Toast.makeText(context, if (ok) tr("Recuperato ✓") else tr("Non è stato possibile recuperarlo"), Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                        TextButton(onClick = { deleting = entry }) { Text(tr("Elimina ora"), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    deleting?.let { entry ->
        ConfirmDialog(
            title = tr("Eliminare per sempre?"),
            message = tr("Non potrai più recuperarlo."),
            confirmLabel = tr("Elimina"),
            destructive = true,
            onConfirm = { deleting = null; scope.launch { container.trash.discard(entry) } },
            onDismiss = { deleting = null },
        )
    }
}

private fun TrashKind.icon(): ImageVector = when (this) {
    TrashKind.REMINDER -> Icons.Rounded.NotificationsActive
    TrashKind.ITEM -> Icons.Rounded.Inventory2
    TrashKind.PERSON -> Icons.Rounded.Person
    TrashKind.MAINTENANCE -> Icons.Rounded.Build
}

private fun TrashKind.label(): String = when (this) {
    TrashKind.REMINDER -> tr("Promemoria")
    TrashKind.ITEM -> tr("Cosa")
    TrashKind.PERSON -> tr("Persona")
    TrashKind.MAINTENANCE -> tr("Intervento")
}

@Composable
private fun TrashKind.tone() = MaterialTheme.ricordellaColors.let {
    when (this) {
        TrashKind.REMINDER -> it.cyan
        TrashKind.ITEM -> it.pear
        TrashKind.PERSON -> it.coral
        TrashKind.MAINTENANCE -> it.mint
    }
}
