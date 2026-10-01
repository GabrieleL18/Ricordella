package com.ricordella.app.feature.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.HappyWizard
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.MagicTitle
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.WizardScene
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors
import java.time.LocalDate

/** Versione dei termini: aumentandola, l'app li richiede da accettare a tutti al prossimo avvio. */
const val TERMS_VERSION = 2

private const val SUPPORT_EMAIL = "lannilab.support@gmail.com"

private class TermsSection(val icon: ImageVector, val title: String, val body: String, val tone: Tone)

@Composable
private fun sections(): List<TermsSection> {
    val c = MaterialTheme.ricordellaColors
    return listOf(
        TermsSection(
            Icons.Rounded.TaskAlt, tr("Termini d'uso"),
            tr("Remindella è un'app gratuita per organizzare promemoria, scadenze, eventi, cose e persone. Usandola accetti questi termini. ") +
                tr("Puoi usarla per scopi personali e familiari; non usarla per attività illecite né per conservare dati di altre persone senza averne il diritto."),
            c.lavender,
        ),
        TermsSection(
            Icons.Rounded.NotificationsActive, tr("Uno strumento di supporto"),
            tr("Remindella ti aiuta a ricordare, ma non può garantire che una notifica, una sveglia o un promemoria arrivi sempre e in tempo. ") +
                tr("Android può ritardarli o bloccarli: risparmio energetico, Non disturbare, permessi revocati, telefono spento o scarico, ora del telefono sbagliata, aggiornamenti. ") +
                tr("Per le scadenze importanti (pagamenti, documenti, visite, farmaci) non affidarti soltanto all'app."),
            c.coral,
        ),
        TermsSection(
            Icons.Rounded.Shield, tr("Le informazioni le controlli tu"),
            tr("Sei responsabile di ciò che inserisci: date, orari, importi e ricorrenze, anche quando arrivano dal calendario o sono letti da scontrini e carte d'imbarco. ") +
                tr("Il riconoscimento automatico può sbagliare e le date calcolate dall'app (ricorrenze, feste, chilometri) vanno verificate con le fonti ufficiali."),
            c.pear,
        ),
        TermsSection(
            Icons.Rounded.PhoneAndroid, tr("I dati vivono solo sul telefono"),
            tr("Non ci sono account né copie sul cloud, e i dati sono esclusi dal backup automatico di Android. ") +
                tr("Se disinstalli l'app, ne cancelli i dati, ripristini, perdi o rompi il telefono, i dati vanno persi per sempre, a meno che tu non abbia esportato un backup."),
            c.cyan,
        ),
        TermsSection(
            Icons.Rounded.SwapVert, tr("Esportare e importare i dati"),
            tr("Impostazioni › Dati e backup › Esporta backup crea un file .zip con promemoria, cose, persone, foto e impostazioni: puoi sovrascriverlo, salvarne una nuova versione o condividerlo. Il backup automatico lo aggiorna da solo all'orario scelto.\n\n") +
                tr("Per ripristinare: Impostazioni › Dati e backup › Importa backup e scegli il file. I dati presenti vengono sostituiti. ") +
                tr("Tieni il file in un posto sicuro (Drive, un computer): non è cifrato e chi lo ha può leggerne il contenuto. ") +
                tr("Di ogni backup l'app tiene anche una copia di riserva sul telefono, per quando il file principale si rovina; anche la copia sparisce se disinstalli l'app."),
            c.mint,
        ),
        TermsSection(
            Icons.Rounded.CloudSync, tr("Condivisione con un'altra persona"),
            tr("Con la Condivisione due telefoni leggono e scrivono lo stesso file (es. su Google Drive). Chi ha il file può leggere e modificare promemoria, persone, cose e spese che contiene: condividilo solo con chi vuoi. ") +
                tr("In automatico il file si aggiorna da solo, anche in background; in manuale solo quando lo chiedi. Se entrambi modificate gli stessi dati, l'app unisce le modifiche e, se lo stesso campo è cambiato da tutti e due, tiene la più recente. ") +
                tr("Quando spunti o modifichi una cosa di un'altra persona, nel file resta scritto chi l'ha fatto. La sincronizzazione dipende dall'app che gestisce il file: verifica che sia aggiornato prima di decisioni importanti."),
            c.cyan,
        ),
        TermsSection(
            Icons.Rounded.Lock, tr("Informativa privacy"),
            tr("Remindella non chiede account, non ha accesso a Internet, non mostra pubblicità, non raccoglie statistiche e non invia i tuoi dati allo sviluppatore. ") +
                tr("Solo quando lo chiedi tu: usa il microfono per «Scrivi al volo» a voce (l'audio lo trascrive il riconoscimento vocale del telefono, che può usare Internet secondo le sue regole; Remindella non registra né salva l'audio); usa la fotocamera per scattare foto da allegare o scansionare; legge il calendario del telefono per importare gli eventi; usa i servizi Google Play del telefono per leggere testi e codici dalle foto (l'elaborazione avviene sul telefono, ma i servizi Google possono inviare a Google dati tecnici secondo le loro condizioni); con la Condivisione scrive un file nel posto che scegli (es. Google Drive), gestito da quel servizio secondo le sue regole.\n\n") +
                tr("Puoi cancellare tutto da Impostazioni › Dati e backup › Elimina tutti i dati o disinstallando l'app. Per domande: ") + SUPPORT_EMAIL,
            c.lavender,
        ),
        TermsSection(
            Icons.Rounded.Gavel, tr("Limitazione di responsabilità"),
            tr("L'app è fornita gratuitamente «così com'è», senza garanzia che funzioni sempre e senza errori. ") +
                tr("Nei limiti consentiti dalla legge, lo sviluppatore non risponde dei danni diretti o indiretti dovuti all'uso o al mancato funzionamento dell'app, come scadenze mancate, sanzioni, interessi di mora, appuntamenti persi o dati perduti. ") +
                tr("Restano salvi i casi di dolo o colpa grave e i diritti che la legge riconosce ai consumatori e che non possono essere esclusi."),
            c.coral,
        ),
        TermsSection(
            Icons.Rounded.Update, tr("Modifiche"),
            tr("Questi termini possono cambiare: in quel caso l'app te li riproporrà da accettare. ") + trf("Versione %1\$s, ottobre 2026.", TERMS_VERSION),
            c.mint,
        ),
    )
}

/**
 * Termini d'uso, limitazione di responsabilità e privacy. Al primo avvio (o quando cambiano)
 * vanno accettati per usare l'app; dalle Impostazioni si rileggono con [onAccept] null.
 */
@Composable
fun TermsScreen(onAccept: (() -> Unit)?, onDecline: () -> Unit) {
    var agreed by rememberSaveable { mutableStateOf(false) }
    var confirmDecline by rememberSaveable { mutableStateOf(false) }
    val settings = LocalAppSettings.current
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .contentWidth()
                    .padding(horizontal = RicordellaDimensions.screenPadding, vertical = RicordellaDimensions.spaceL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
            ) {
                HappyWizard(size = 120.dp, scene = WizardScene.READING)
                MagicTitle()
                Text(
                    if (onAccept != null) tr("Prima di iniziare, leggi come funziona e cosa può e non può fare l'app.")
                    else tr("Termini d'uso e privacy"),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                sections().forEach { SectionCard(it) }
            }
            HorizontalDivider()
            Column(
                Modifier.contentWidth().padding(horizontal = RicordellaDimensions.screenPadding, vertical = RicordellaDimensions.spaceS),
                verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
            ) {
                if (onAccept != null) {
                    Row(
                        Modifier.fillMaxWidth().clickable { agreed = !agreed },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                        Text(tr("Ho letto e accetto i termini d'uso, la limitazione di responsabilità e l'informativa privacy."), style = MaterialTheme.typography.bodyMedium)
                    }
                    PushButton(tr("Accetto e inizio"), onClick = onAccept, enabled = agreed, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { confirmDecline = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(tr("Non accetto")) }
                } else {
                    settings.termsAcceptedEpochDay?.let {
                        Text(
                            trf("Accettati il %1\$s", DateTexts.date(LocalDate.ofEpochDay(it), settings.dateFormat)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    PushButton(tr("Chiudi"), onClick = onDecline, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (confirmDecline) {
        AlertDialog(
            onDismissRequest = { confirmDecline = false },
            title = { Text(tr("Senza accettare non si parte")) },
            text = { Text(tr("Per usare Remindella devi accettare i termini. Puoi rileggerli con calma o chiudere l'app.")) },
            confirmButton = { TextButton(onClick = { confirmDecline = false }) { Text(tr("Rileggo")) } },
            dismissButton = { TextButton(onClick = onDecline) { Text(tr("Chiudi l'app")) } },
        )
    }
}

@Composable
private fun SectionCard(section: TermsSection) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(section.tone.container, MaterialTheme.shapes.large)
            .padding(RicordellaDimensions.spaceL),
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            Box(Modifier.size(32.dp).background(section.tone.solid, CircleShape), contentAlignment = Alignment.Center) {
                Icon(section.icon, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(18.dp))
            }
            Text(section.title, style = MaterialTheme.typography.titleMedium, color = section.tone.content)
        }
        Text(section.body, style = MaterialTheme.typography.bodyMedium, color = section.tone.content)
    }
}
