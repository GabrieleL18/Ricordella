package com.ricordella.app.feature.news

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.CalendarMonth
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

/** Una novità: icona e colore come nel resto dell'app, titolo e due righe di spiegazione. */
class NewsItem(val icon: ImageVector, val tone: (RicordellaColors) -> Tone, val title: String, val body: String)

/** Le novità di una versione (versionCode dell'app). */
class NewsRelease(val versionCode: Int, val versionName: String, val items: List<NewsItem>)

/**
 * Tutte le novità, dalla più recente. Per una nuova versione si aggiunge un [NewsRelease] in cima
 * con il versionCode di build.gradle: al primo avvio dopo l'aggiornamento compaiono da sole.
 */
val AllNews: List<NewsRelease>
    get() = listOf(
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
                            trf("Versione %1\$s", release.versionName),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = RicordellaDimensions.spaceS),
                        )
                        release.items.forEach { item -> NewsCard(item, item.tone(colors), delayIndex = index++) }
                    }
                }
                PushButton(tr("Fantastico!"), onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(top = RicordellaDimensions.spaceM))
            }
        }
    }
}

@Composable
private fun NewsCard(item: NewsItem, tone: Tone, delayIndex: Int) {
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
            .background(tone.container, MaterialTheme.shapes.large)
            .padding(RicordellaDimensions.spaceM),
        horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Box(Modifier.size(36.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = tone.content)
            Text(item.body, style = MaterialTheme.typography.bodySmall, color = tone.content)
        }
    }
}
