package com.ricordella.app.feature.news

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.HappyWizard
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.WizardScene
import com.ricordella.app.core.ui.rememberReducedMotion
import com.ricordella.app.core.ui.theme.RicordellaColors
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors

/** Una novità: icona e colore come nel resto dell'app, titolo e riassunto nella card; toccandola si apre il testo completo [body]. */
class NewsItem(val icon: ImageVector, val tone: (RicordellaColors) -> Tone, val title: String, val summary: String, val body: String = summary)

/** Le novità di una versione (versionCode dell'app). */
class NewsRelease(val versionCode: Int, val versionName: String, val items: List<NewsItem>, val date: java.time.LocalDate? = null)

/**
 * Tutte le novità, dalla più recente. Per una nuova versione si aggiunge un [NewsRelease] in cima
 * con il versionCode di build.gradle: al primo avvio dopo l'aggiornamento compaiono da sole.
 */
val AllNews: List<NewsRelease>
    get() = listOf(
        NewsRelease(
            versionCode = 5,
            versionName = "0.3.0",
            date = java.time.LocalDate.of(2026, 10, 2),
            items = listOf(
                NewsItem(
                    Icons.Rounded.Payments, { it.mint }, tr("Rate e pagamenti"),
                    tr("Condominio, mutuo e finanziamenti a rate, con scadenze."),
                    tr("Un pagamento può essere a rate: indichi quante, l'importo e ogni quanti mesi. Se l'importo cambia, modifichi ogni rata (anche le successive). Ogni rata pagata resta registrata con data e importo; finite le rate scegli se eliminare l'evento l'anno dopo. I pagamenti ripetuti possono essere annuali, mensili, settimanali o in un giorno preciso del mese."),
                ),
                NewsItem(
                    Icons.Rounded.Alarm, { it.coral }, tr("Sveglie a giorni fissi"),
                    tr("Scegli i giorni della settimana, ricevi un avviso silenzioso prima per saltarla e sospendila fino a una data."),
                    tr("Scegli i giorni della settimana in cui suona (es. lunedì–venerdì per lavoro). Qualche minuto prima ricevi una notifica silenziosa per saltarla solo questa volta, e puoi sospenderla fino a una data. Ogni sveglia è assegnata a una persona e, se condividi il file, suona solo sul telefono della sua persona. Nel calendario puoi nascondere le sveglie normali."),
                ),
                NewsItem(Icons.Rounded.Widgets, { it.lavender }, tr("Widget Sveglia"), tr("La prossima sveglia sulla schermata Home, con il + per crearne una."),
                    tr("Aggiungi il widget Sveglia alla schermata Home: vedi l'ora e il titolo della prossima sveglia, un tocco la apre e il + ne crea una nuova.")),
                NewsItem(
                    Icons.Rounded.WaterDrop, { it.coral }, tr("Ciclo"),
                    tr("Previsione, cronologia di 2 anni e goccia nel calendario, per ogni persona. Dati solo sul telefono."),
                    tr("Per ogni persona indichi quanto dura la mestruazione e ogni quanto torna il ciclo. Nel calendario compare una goccia (chiara se è una previsione), tieni la cronologia di 2 anni e, solo per il ciclo che ti appartiene, ricevi un avviso se è in ritardo. I dati restano solo sul telefono."),
                ),
                NewsItem(
                    Icons.Rounded.DeleteSweep, { it.cyan }, tr("Cestino"),
                    tr("Recuperi ciò che elimini per 7 giorni."),
                    tr("Promemoria, cose, persone e interventi eliminati finiscono nel Cestino (Impostazioni › Dati e backup): li recuperi entro 7 giorni, poi vengono eliminati definitivamente."),
                ),
                NewsItem(
                    Icons.Rounded.Shield, { it.mint }, tr("Backup più sicuri"),
                    tr("Ultime 3 copie con la data, anche su Drive."),
                    tr("Ogni backup lascia sul telefono le ultime 3 copie con la data. Puoi scegliere una cartella su Drive dove salvare i backup con la data (tengo le ultime 3). Un tutorial spiega cosa succede, e nella configurazione iniziale ti consiglio di salvare online per non perdere i dati se cambi telefono."),
                ),
                NewsItem(
                    Icons.Rounded.CloudSync, { it.lavender }, tr("Condivisione su misura"),
                    tr("Scegli cosa condividere e come usare Drive."),
                    tr("In Impostazioni › Condivisione scegli cosa mettere nel file (promemoria, persone, cose e spese, sveglie) e leggi cosa non si condivide mai. Una guida in 3 passi spiega che il file va messo su Drive e condiviso con l'altra persona."),
                ),
                NewsItem(
                    Icons.Rounded.CalendarMonth, { it.pear }, tr("Importa da Samsung e altri"),
                    tr("Oltre a Google, importi i calendari Samsung e quelli locali del telefono."),
                    tr("Importi da più account insieme (Google, Samsung Calendar, calendari del telefono) e scegli prima cosa fare se un evento c'è già: tenere il tuo, sostituirlo (il tuo va nel Cestino) o tenerli entrambi."),
                ),
                NewsItem(Icons.Rounded.WaterDrop, { it.cyan }, tr("Acqua della settimana"), tr("Nelle Pozioni il recap degli ultimi 7 giorni, con media e obiettivi raggiunti.")),
                NewsItem(
                    Icons.Rounded.TouchApp, { it.mint }, tr("Più comodo"),
                    tr("I numeri della Home portano alla sezione, «Scrivi al volo» si cancella e si modifica, Elimina è ben visibile e le notifiche si aprono senza errori."),
                    tr("I numeri della Home portano alla sezione. «Scrivi al volo» si cancella e, toccando l'anteprima, si modifica nel modulo completo. Modifica ed Elimina sono pulsanti ben visibili. Sul tablet la barra di sistema non copre più l'app e le notifiche si aprono senza errori."),
                ),
            ),
        ),
        NewsRelease(
            versionCode = 1,
            versionName = "0.1.0",
            items = listOf(
                NewsItem(Icons.Rounded.Mic, { it.cyan }, tr("Dillo a voce"), tr("In \"Scrivi al volo\" tocca il microfono e parla: la sfera ti ascolta e scrive per te.")),
                NewsItem(Icons.Rounded.CloudSync, { it.lavender }, tr("Condivisione automatica"), tr("Il file condiviso si sincronizza da solo e, se avete modificato entrambi, unisce le modifiche senza perdere niente.")),
                NewsItem(Icons.Rounded.Euro, { it.mint }, tr("Spese più chiare"), tr("Riepiloghi per mese, per categoria e per cosa, con il confronto con l'anno prima.")),
                NewsItem(Icons.Rounded.CalendarMonth, { it.pear }, tr("Calendario più leggibile"), tr("Il mese occupa tutto lo schermo: tocca un giorno per vedere i suoi impegni.")),
                NewsItem(Icons.Rounded.Language, { it.cyan }, tr("Cinque lingue"), tr("Italiano, English, Deutsch, Français ed Español: la scegli nelle Impostazioni.")),
                NewsItem(Icons.Rounded.CameraAlt, { it.coral }, tr("Allegati con la fotocamera"), tr("Allegando un documento puoi anche scattare subito una foto.")),
                NewsItem(Icons.Rounded.Shield, { it.mint }, tr("Backup al sicuro"), tr("Ogni backup ha una copia di riserva: se un file si rovina, c'è l'altro.")),
                NewsItem(Icons.Rounded.Widgets, { it.lavender }, tr("Widget più grande"), tr("\"La tua giornata\" mostra più giorni se gli dai spazio.")),
                NewsItem(Icons.Rounded.School, { it.pear }, tr("Tutorial nuovi"), tr("Anche \"Scrivi al volo\" ha il suo tutorial. Se non li vuoi, nascondili nelle Impostazioni.")),
            ),
        ),
    )

/** Le novità più recenti di [seenVersion] (vuoto se non c'è nulla di nuovo). */
fun newsSince(seenVersion: Int): List<NewsRelease> = AllNews.filter { it.versionCode > seenVersion }

/**
 * Finestra delle novità: il mago che fa magie, le card colorate che entrano una dopo l'altra
 * e un pulsante giallo in fondo, sempre visibile.
 */
@Composable
fun NewsDialog(releases: List<NewsRelease>, onDismiss: () -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<NewsItem?>(null) }
    open?.let { NewsDetail(it, it.tone(colors), onDismiss = { open = null }) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.widthIn(max = 520.dp)) {
            Column(Modifier.padding(RicordellaDimensions.spaceXl)) {
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        HappyWizard(size = 96.dp, scene = WizardScene.CONJURING)
                        Text(tr("Novità"), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            tr("Ecco cosa c'è di nuovo in Remindella."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    var index = 0
                    releases.forEach { release ->
                        Text(
                            trf("Versione %1\$s", release.versionName) + (release.date?.let { " · " + com.ricordella.app.core.date.DateTexts.date(it, com.ricordella.app.core.ui.LocalAppSettings.current.dateFormat) } ?: ""),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = RicordellaDimensions.spaceS),
                        )
                        release.items.forEach { item -> NewsCard(item, item.tone(colors), delayIndex = index++, onOpen = { open = item }) }
                    }
                }
                PushButton(tr("Fantastico!"), onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(top = RicordellaDimensions.spaceM))
            }
        }
    }
}

@Composable
private fun NewsCard(item: NewsItem, tone: Tone, delayIndex: Int, onOpen: () -> Unit) {
    val reduced = rememberReducedMotion()
    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80L * delayIndex.coerceAtMost(8))
        appear.animateTo(1f, tween(320))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 24.dp.toPx() }
            .clip(MaterialTheme.shapes.large)
            .background(tone.container)
            .clickable(onClick = onOpen)
            .padding(RicordellaDimensions.spaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Box(Modifier.size(36.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = tone.content)
            Text(item.summary, style = MaterialTheme.typography.bodySmall, color = tone.content, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = tr("Leggi tutto"), tint = tone.content)
    }
}

/** La novità completa: icona grande, titolo e testo per intero. */
@Composable
private fun NewsDetail(item: NewsItem, tone: Tone, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.widthIn(max = 520.dp)) {
            Column(Modifier.padding(RicordellaDimensions.spaceXl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM)) {
                Box(Modifier.size(64.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(34.dp))
                }
                Text(item.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Text(item.body, style = MaterialTheme.typography.bodyLarge)
                }
                PushButton(tr("Chiudi"), onClick = onDismiss)
            }
        }
    }
}
