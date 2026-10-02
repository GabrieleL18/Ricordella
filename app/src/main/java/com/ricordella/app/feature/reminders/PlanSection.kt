package com.ricordella.app.feature.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.InstallmentPlan
import com.ricordella.app.feature.items.formatCents
import com.ricordella.app.feature.items.parseCents

/**
 * Le rate di un pagamento (condominio, mutuo...): quante pagate, il totale, le prossime e lo storico.
 * Toccando una rata si cambia l'importo (anche per le successive) o la si segna pagata / da pagare.
 */
@Composable
fun PlanSection(reminderId: String, plan: InstallmentPlan, viewModel: ReminderDetailViewModel) {
    val tone = MaterialTheme.ricordellaColors.mint
    val settings = LocalAppSettings.current
    var showAll by rememberSaveable(reminderId) { mutableStateOf(false) }
    var editing by rememberSaveable(reminderId) { mutableStateOf<Int?>(null) }
    SectionHeader(tr("Rate"))
    Column(
        Modifier.fillMaxWidth().background(tone.container, MaterialTheme.shapes.large).padding(RicordellaDimensions.spaceL),
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
    ) {
        Text(trf("Pagate %1\$s di %2\$s", plan.paidCount, plan.count), style = MaterialTheme.typography.titleMedium, color = tone.content)
        Text(trf("%1\$s pagati su %2\$s", DateTexts.money(plan.paidCents), DateTexts.money(plan.totalCents)), style = MaterialTheme.typography.bodyMedium, color = tone.content)
        LinearProgressIndicator(
            progress = { plan.paidCount / plan.count.coerceAtLeast(1).toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = tone.solid,
            trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        )
        val shown = if (showAll) (0 until plan.count).toList() else (0 until plan.count).filter { !plan.isPaid(it) }.take(5)
        shown.forEach { i ->
            Row(
                Modifier.fillMaxWidth().clickable { editing = i }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
            ) {
                Icon(
                    if (plan.isPaid(i)) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (plan.isPaid(i)) tr("Pagata") else tr("Da pagare"),
                    tint = tone.solid,
                )
                Column(Modifier.weight(1f)) {
                    Text(trf("Rata %1\$s · %2\$s", i + 1, DateTexts.date(plan.dueDate(i), settings.dateFormat)), style = MaterialTheme.typography.bodyMedium, color = tone.content)
                    plan.paid.firstOrNull { it.index == i }?.let {
                        Text(trf("Pagata il %1\$s", DateTexts.date(it.on, settings.dateFormat)), style = MaterialTheme.typography.bodySmall, color = tone.content)
                    }
                }
                Text(DateTexts.money(plan.cents(i)), style = MaterialTheme.typography.titleSmall, color = tone.content)
            }
        }
        if (plan.count > 5 || plan.paidCount > 0) {
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) tr("Mostra solo le prossime") else trf("Mostra tutte le %1\$s rate", plan.count)) }
        }
        if (plan.finished) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Rate finite!"), style = MaterialTheme.typography.titleSmall, color = tone.content)
                    Text(tr("Elimina l'evento un anno dopo l'ultima rata"), style = MaterialTheme.typography.bodySmall, color = tone.content)
                }
                Switch(checked = plan.deleteAfter, onCheckedChange = viewModel::onDeleteAfter)
            }
        }
    }
    editing?.takeIf { it in 0 until plan.count }?.let { i ->
        var amount by remember(i) { mutableStateOf(formatCents(plan.cents(i))) }
        var following by remember(i) { mutableStateOf(plan.variable && !plan.isPaid(i)) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(trf("Rata %1\$s di %2\$s", i + 1, plan.count)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
                    Text(DateTexts.date(plan.dueDate(i), settings.dateFormat), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { value -> amount = value.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12) },
                        label = { Text(tr("Importo (€)")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    if (!plan.isPaid(i) && i < plan.count - 1) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Anche per le rate successive"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = following, onCheckedChange = { following = it })
                        }
                    }
                    TextButton(onClick = {
                        if (plan.isPaid(i)) viewModel.onUnpayInstallment(i) else viewModel.onPayInstallment(i)
                        editing = null
                    }) { Text(if (plan.isPaid(i)) tr("Segna da pagare") else tr("Segna pagata")) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = (parseCents(amount) ?: 0L) > 0L,
                    onClick = { parseCents(amount)?.let { viewModel.onInstallmentAmount(i, it, following) }; editing = null },
                ) { Text(tr("Salva")) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(tr("Annulla")) } },
        )
    }
}
