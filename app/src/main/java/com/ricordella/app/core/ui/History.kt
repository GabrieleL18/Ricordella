package com.ricordella.app.core.ui

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.ReminderCompletion
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit

/**
 * Una voce dello storico: a sinistra il "foglietto" del calendario con giorno e mese, poi la linea
 * del tempo con il pallino, a destra la scheda con titolo, dettagli ed eventuale azione.
 * [isLast] chiude la linea sull'ultima voce.
 */
@Composable
fun HistoryItem(
    date: LocalDate,
    title: String,
    tone: Tone,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badge: Pair<String, Tone>? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        CalendarLeaf(date, tone)
        Box(Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .padding(top = 20.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(if (isLast) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.outlineVariant),
            )
            Box(Modifier.padding(top = 16.dp).size(10.dp).background(tone.solid, CircleShape))
        }
        Row(
            Modifier
                .weight(1f)
                .padding(bottom = 10.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = if (trailing == null) 14.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                badge?.let { (text, badgeTone) ->
                    Text(
                        text,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeTone.content,
                        modifier = Modifier.padding(top = 2.dp).background(badgeTone.container, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/** Foglietto del calendario: mese in alto su fascia colorata, giorno grande sotto. */
@Composable
private fun CalendarLeaf(date: LocalDate, tone: Tone) {
    val month = date.month.getDisplayName(TextStyle.SHORT, com.ricordella.app.core.i18n.Lang.locale).uppercase().take(3)
    Column(
        Modifier
            .width(48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(12.dp))
            .clearAndSetSemantics { contentDescription = DateTexts.fullDate(date) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            month,
            style = MaterialTheme.typography.labelSmall,
            color = tone.content,
            modifier = Modifier.fillMaxWidth().background(tone.container, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).padding(vertical = 2.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${date.year}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
    }
}

/** Storico dei completamenti di un promemoria, dal più recente, con "in ritardo"/"in anticipo". */
@Composable
fun CompletionHistory(completions: List<ReminderCompletion>) {
    val colors = MaterialTheme.ricordellaColors
    val format = LocalAppSettings.current.dateFormat
    val sorted = completions.sortedByDescending { it.completedAt }
    SectionHeader(trf("Storico · %1\$s", sorted.size), icon = Icons.Rounded.History, tone = colors.mint)
    sorted.forEachIndexed { index, completion ->
        val done = LocalDateTime.ofInstant(completion.completedAt, ZoneId.systemDefault())
        val late = ChronoUnit.DAYS.between(completion.occurrenceDate, done.toLocalDate())
        HistoryItem(
            date = completion.occurrenceDate,
            title = tr("Completato"),
            subtitle = trf("il %1\$s", DateTexts.dateWithTime(done.toLocalDate(), done.toLocalTime(), format)),
            tone = colors.mint,
            isLast = index == sorted.lastIndex,
            badge = when {
                late > 0 -> (if (late == 1L) tr("1 giorno in ritardo") else trf("%1\$s giorni in ritardo", late)) to colors.coral
                late < 0 -> (if (late == -1L) tr("1 giorno in anticipo") else trf("%1\$s giorni in anticipo", -late)) to colors.cyan
                else -> tr("puntuale") to colors.mint
            },
        )
    }
}
