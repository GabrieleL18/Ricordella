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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.StickyNote2
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            versionCode = 9,
            versionName = "0.5.2",
            date = java.time.LocalDate.of(2026, 10, 5),
            items = listOf(
                NewsItem(Icons.Rounded.Alarm, { it.coral }, tr("Sveglia classica più magica"), tr("Stelline di polvere magica sopra il bagliore arancione e una grafica in linea con il resto dell'app, anche in orizzontale.")),
                NewsItem(Icons.Rounded.Mic, { it.lavender }, tr("Disegno in orizzontale"), tr("Con il telefono girato il foglio occupa tutto lo spazio e gli strumenti stanno di fianco.")),
            ),
        ),
        NewsRelease(
            versionCode = 8,
            versionName = "0.5.1",
            date = java.time.LocalDate.of(2026, 10, 5),
            items = listOf(
                NewsItem(Icons.Rounded.Alarm, { it.coral }, tr("Sveglia classica"), tr("Una sfumatura arancione che respira, solo l'ora al centro. È la sveglia di default: la cambi nelle Impostazioni o per ogni sveglia.")),
                NewsItem(Icons.Rounded.Mic, { it.lavender }, tr("Note con audio e disegni"), tr("Registra la voce, carica immagini o disegna con penna e colori. Ordina le note e trovale dalla ricerca.")),
            ),
        ),
        NewsRelease(
            versionCode = 7,
            versionName = "0.5.0",
            date = java.time.LocalDate.of(2026, 10, 5),
            items = listOf(
                NewsItem(Icons.Rounded.Timer, { it.mint }, tr("Timer con anello"), tr("Scegli quanto dura e guarda l'anello svuotarsi, preciso al secondo. Nuovo widget Timer sulla Home.")),
                NewsItem(Icons.Rounded.Alarm, { it.coral }, tr("Widget Sveglia più pulito"), tr("Al posto di «Prossima sveglia» ora c'è l'icona della sveglia.")),
                NewsItem(Icons.Rounded.Person, { it.cyan }, tr("Profilo e backup"), tr("Scegli il tuo profilo principale e dai un nome ai backup.")),
                NewsItem(Icons.Rounded.StickyNote2, { it.pear }, tr("Note a tema"), tr("Scegli il tipo di nota, personalizza il tema e vedile a tasselli.")),
                NewsItem(Icons.Rounded.School, { it.lavender }, tr("Home più ricca"), tr("Tasselli extra, un tutorial per ogni sezione e «Indietro» che torna al foglio del giorno.")),
            ),
        ),
        NewsRelease(
            versionCode = 6,
            versionName = "0.4.0",
            date = java.time.LocalDate.of(2026, 10, 3),
            items = listOf(
                NewsItem(
                    Icons.Rounded.Payments, { it.mint }, tr("Spese e conti"),
                    tr("Spese, abbonamenti, rate ed entrate, divise tra le persone, con il riepilogo dell'anno e il PDF."),
                    tr("La spesa è un evento speciale: scegli se è una spesa, un abbonamento, un pagamento a rate o un'entrata, l'importo, di chi è e come dividerla. In Spese e conti vedi il totale di tutta la cerchia o di una persona durante l'anno e crei un PDF."),
                ),
                NewsItem(
                    Icons.Rounded.Alarm, { it.coral }, tr("Sveglie e ciclo nel calendario"),
                    tr("Icone e legenda nel calendario; le sveglie si mostrano solo se lo scegli nelle Impostazioni."),
                    tr("Le sveglie hanno la loro icona e di default non compaiono nel calendario: in Impostazioni › Sveglie scegli quando mostrarle. C'è la legenda anche per il ciclo, che ora è uno solo e ha una cronologia più chiara."),
                ),
                NewsItem(
                    Icons.Rounded.Alarm, { it.coral }, tr("Sveglie a parte"),
                    tr("Una sezione solo per le sveglie: ora e giorni, accese o spente quando vuoi, e un avviso un'ora prima."),
                    tr("Le sveglie non hanno più una data: scegli l'ora e i giorni e le accendi o spegni con un interruttore. Un'ora prima arriva un avviso silenzioso con il pulsante per spegnerle. Puoi importare la prossima sveglia del telefono. Le trovi dal menu in alto, accanto a Note e Pozioni."),
                ),
                NewsItem(
                    Icons.Rounded.StickyNote2, { it.pear }, tr("Note e lista della spesa"),
                    tr("Note di testo, liste e liste della spesa con prezzi e totale."),
                    tr("Scrivi note, crea liste con le caselle o liste della spesa con prezzo e quantità: il totale e quanto è già nel carrello li calcolo io. Ci sono anche i widget Note e Sveglie, con la stessa grafica dell'app."),
                ),
                NewsItem(
                    Icons.Rounded.TouchApp, { it.cyan }, tr("Più semplice"),
                    tr("Ricerca con il microfono, form più chiaro, un solo Modifica/Elimina."),
                    tr("La ricerca globale ha il microfono. Nel form scegli per prima cosa il tipo e le persone obbligatorie si vedono subito. Modifica, Elimina e Archivia compaiono una volta sola; descrizione e categoria non ci sono più."),
                ),
            ),
        ),
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
 * Finestra delle novità in stile "news" da gioco: striscione colorato con titolo grosso, una riga chunky per versione
 * (la più recente col bollino NUOVO!) e card con spessore, icona nel badge inclinato e titolo in grassetto.
 * Con una sola versione (dopo un aggiornamento) è già aperta.
 */
@Composable
fun NewsDialog(releases: List<NewsRelease>, onDismiss: () -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    var expanded by remember { mutableStateOf(releases.singleOrNull()?.versionCode) }
    val newest = releases.maxOfOrNull { it.versionCode }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.widthIn(max = 520.dp)) {
            Column {
                Box(Modifier.fillMaxWidth().background(colors.bolt).padding(vertical = RicordellaDimensions.spaceL), contentAlignment = Alignment.Center) {
                    Text(
                        tr("Novità").uppercase() + "!",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp),
                        color = colors.onBolt,
                    )
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(RicordellaDimensions.spaceL),
                    verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
                ) {
                    releases.forEach { release ->
                        val open = expanded == release.versionCode
                        ChunkyBox(colors.bolt, MaterialTheme.colorScheme.surface, Modifier.clickable { expanded = if (open) null else release.versionCode }) {
                            Row(Modifier.padding(RicordellaDimensions.spaceM), verticalAlignment = Alignment.CenterVertically) {
                                Text(release.heading, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.weight(1f))
                                if (release.versionCode == newest) {
                                    Text(
                                        tr("Nuovo!").uppercase(),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                        color = colors.onBolt,
                                        modifier = Modifier.padding(end = RicordellaDimensions.spaceS).clip(CircleShape).background(colors.bolt).padding(horizontal = 10.dp, vertical = 3.dp),
                                    )
                                }
                                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = if (open) tr("Nascondi") else tr("Mostra il dettaglio"))
                            }
                        }
                        if (open) release.items.forEach { item ->
                            val tone = item.tone(colors)
                            ChunkyBox(tone.solid, tone.container) {
                                Row(Modifier.padding(RicordellaDimensions.spaceM), horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(52.dp).graphicsLayer { rotationZ = -6f }.clip(RoundedCornerShape(16.dp)).background(tone.solid), contentAlignment = Alignment.Center) {
                                        Icon(item.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                                    }
                                    Column {
                                        Text(item.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = tone.content)
                                        Text(item.body, style = MaterialTheme.typography.bodyMedium, color = tone.content)
                                    }
                                }
                            }
                        }
                    }
                }
                PushButton(tr("Fantastico!"), onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(RicordellaDimensions.spaceL))
            }
        }
    }
}

/** Card "a rilievo": bordo spesso del colore [edge] e uno spessore più marcato sotto, come i pulsanti. */
@Composable
private fun ChunkyBox(edge: Color, face: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.fillMaxWidth().clip(shape).background(edge).padding(start = 2.dp, top = 2.dp, end = 2.dp, bottom = 5.dp)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(face)) { content() }
    }
}

private val NewsRelease.heading: String
    @Composable get() = trf("Versione %1\$s", versionName) +
        (date?.let { " · " + com.ricordella.app.core.date.DateTexts.date(it, com.ricordella.app.core.ui.LocalAppSettings.current.dateFormat) } ?: "")
