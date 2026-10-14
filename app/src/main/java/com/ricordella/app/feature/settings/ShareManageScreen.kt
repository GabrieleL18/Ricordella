package com.ricordella.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.emoji
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.data.backup.BackupDatabaseContent
import com.ricordella.app.domain.model.ReminderStatus
import kotlinx.coroutines.launch

/** Una riga della gestione: chi è, a quale categoria appartiene e se la categoria è accesa. */
private class ShareRow(val id: String, val title: String, val subtitle: String, val enabled: Boolean)

/**
 * Cosa c'è nel file condiviso, elemento per elemento: ogni persona, cosa, promemoria e carta si può
 * lasciare fuori anche se la sua categoria è accesa. Toglierne uno non lo cancella da nessuna parte.
 */
@Composable
fun ShareManageScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as RicordellaApplication).container
    val settings = LocalAppSettings.current
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf<BackupDatabaseContent?>(null) }
    LaunchedEffect(Unit) { content = container.backupDao.readAll() }
    val excluded = settings.shareExcluded
    val on = settings.shareScope
    fun toggle(id: String) = scope.launch {
        container.settingsRepository.update { it.copy(shareExcluded = if (id in it.shareExcluded) it.shareExcluded - id else it.shareExcluded + id) }
    }

    val data = content
    val sections = if (data == null) emptyList() else listOf(
        Triple(tr("Persone"), on.people, data.people.sortedBy { it.name.lowercase() }.map { ShareRow(it.id, it.name, tr("Le sue cose e i suoi promemoria restano fuori se non sono di nessun altro"), on.people) }),
        Triple(tr("Cose e spese"), on.items, data.items.sortedBy { it.name.lowercase() }.map { ShareRow(it.id, it.name, "", on.items) }),
        Triple(
            tr("Promemoria ed eventi"), on.reminders || on.alarms,
            data.reminders.filter { it.status != ReminderStatus.CANCELLED }.sortedBy { it.title.lowercase() }
                .map { ShareRow(it.id, it.emoji() + " " + it.title, "", if (it.type == com.ricordella.app.domain.model.ReminderType.ALARM) on.alarms else on.reminders) },
        ),
        Triple(tr("Carte"), on.cards, settings.cards.sortedBy { it.name.lowercase() }.map { ShareRow(it.id, it.name, it.category, on.cards) }),
    )

    DetailScaffold(title = tr("Cosa è condiviso"), onBack = onBack) { padding ->
        val colors = MaterialTheme.ricordellaColors
        LazyColumn(
            Modifier.fillMaxSize().contentWidth(),
            contentPadding = PaddingValues(
                start = RicordellaDimensions.screenPadding, end = RicordellaDimensions.screenPadding,
                top = padding.calculateTopPadding() + RicordellaDimensions.spaceS, bottom = 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().background(colors.cyan.container, MaterialTheme.shapes.large).padding(RicordellaDimensions.spaceL),
                    verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
                ) {
                    Text(tr("Se condividi una cosa e l'altra persona no"), style = MaterialTheme.typography.titleSmall, color = colors.cyan.content)
                    Text(
                        tr("Ognuno decide per sé cosa mandare e cosa ricevere. Se tu condividi una cosa e lei no, la cosa resta nel file ma sul suo telefono non arriva e non la può toccare; tu non vedi nulla di suo. Se togli dalla condivisione una cosa già mandata, nel file resta com'è: l'altra persona la tiene, ma non si aggiorna più."),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.cyan.content,
                    )
                }
            }
            if (settings.sharedFileUri == null) item {
                Text(tr("Il file condiviso non è ancora attivo: le scelte valgono dalla prima sincronizzazione."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            sections.forEach { (title, active, rows) ->
                item(key = "h/$title") {
                    val shared = rows.count { it.enabled && it.id !in excluded }
                    Text(
                        title + "  ·  " + trf("%1\$s di %2\$s", shared, rows.size) + if (!active) "  ·  " + tr("spento") else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = RicordellaDimensions.spaceM),
                    )
                }
                items(rows, key = { it.id }) { row ->
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium).padding(horizontal = RicordellaDimensions.spaceM, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            if (row.subtitle.isNotEmpty()) Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Switch(checked = row.enabled && row.id !in excluded, enabled = row.enabled, onCheckedChange = { toggle(row.id) })
                    }
                }
            }
        }
    }
}

private fun com.ricordella.app.domain.model.Reminder.emoji(): String = type.emoji

