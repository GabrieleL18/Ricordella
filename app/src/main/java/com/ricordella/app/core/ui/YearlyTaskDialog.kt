package com.ricordella.app.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors

/** Operazioni che l'app propone una volta l'anno. */
enum class YearlyTask { HOLIDAYS, CLEANUP }

/** Titolo e spiegazione di un'operazione annuale (usati anche nel tutorial delle Impostazioni). */
fun YearlyTask.title(): String = when (this) {
    YearlyTask.HOLIDAYS -> tr("Anno nuovo, feste nuove")
    YearlyTask.CLEANUP -> tr("Pulizia di inizio anno")
}

fun YearlyTask.explanation(count: Int?, year: Int): String = when (this) {
    YearlyTask.HOLIDAYS ->
        (if (count != null) trf("Ci sono %1\$s feste importate rimaste nell'anno passato. ", count) else "") +
            trf("Le sposto al %1\$s: Natale e le feste fisse restano nello stesso giorno, Pasqua, Pasquetta e le altre feste mobili vengono ricalcolate.", year)
    YearlyTask.CLEANUP ->
        (if (count != null) trf("Ho trovato %1\$s promemoria ed eventi più vecchi di un anno non importanti o senza persone e cose collegate. ", count) else "") +
            tr("Posso eliminarli per fare ordine. Quelli importanti e collegati a persone o cose restano.")
}

@Composable
fun YearlyTask.Illustration() = when (this) {
    YearlyTask.HOLIDAYS -> HolidayRollIllustration()
    YearlyTask.CLEANUP -> CleanupIllustration()
}

/** Pagina di tutorial di un'operazione annuale, per le Impostazioni e la configurazione iniziale. */
fun YearlyTask.tutorialPage(year: Int): TutorialPage = TutorialPage(title(), explanation(null, year)) { Illustration() }

/**
 * Proposta di un'operazione annuale: animazione di cosa succede, spiegazione, opzione
 * "da ora fallo in automatico" e, prima di procedere, la conferma "Sei sicuro?".
 */
@Composable
fun YearlyTaskDialog(
    task: YearlyTask,
    count: Int,
    year: Int,
    onConfirm: (makeAutomatic: Boolean) -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
) {
    var confirming by rememberSaveable(task) { mutableStateOf(false) }
    var automatic by rememberSaveable(task) { mutableStateOf(false) }
    val colors = MaterialTheme.ricordellaColors
    Dialog(onDismissRequest = onLater) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.widthIn(max = 460.dp)) {
            AnimatedContent(confirming, label = "yearlyStep") { sure ->
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(RicordellaDimensions.spaceXl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
                ) {
                    if (!sure) {
                        Box(Modifier.size(170.dp).graphicsLayer { scaleX = 0.7f; scaleY = 0.7f }, contentAlignment = Alignment.Center) {
                            Box(Modifier.requiredSize(240.dp), contentAlignment = Alignment.Center) { task.Illustration() }
                        }
                        Text(task.title(), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Text(task.explanation(count, year), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Row(
                            Modifier.fillMaxWidth().toggleable(automatic, role = Role.Checkbox) { automatic = it },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = automatic, onCheckedChange = null)
                            Text(tr("Dagli anni prossimi fallo in automatico"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                        PushButton(tr("Procedi"), onClick = { confirming = true }, icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = onNever) { Text(tr("Non chiedermelo più")) }
                            TextButton(onClick = onLater) { Text(tr("Più tardi")) }
                        }
                    } else {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = colors.coral.solid, modifier = Modifier.size(48.dp))
                        Text(tr("Sei sicuro?"), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            when (task) {
                                YearlyTask.HOLIDAYS -> trf("Sposto %1\$s feste al %2\$s. Le date dell'anno passato non si potranno ripristinare.", count, year)
                                YearlyTask.CLEANUP -> trf("Elimino definitivamente %1\$s promemoria. L'operazione non si può annullare: se vuoi conservarli, esporta prima un backup.", count)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        PushButton(tr("Sì, procedi"), onClick = { onConfirm(automatic) }, icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { confirming = false }) { Text(tr("Indietro")) }
                    }
                }
            }
        }
    }
}
