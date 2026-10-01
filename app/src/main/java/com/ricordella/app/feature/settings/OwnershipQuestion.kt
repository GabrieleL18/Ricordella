package com.ricordella.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.WizardAvatar
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.domain.usecase.GuardedAction

/**
 * Finestra "È di Giulia": compare quando, con il file condiviso, si spunta, riapre o modifica una cosa
 * di un'altra persona. Va messa una volta sola nella shell dell'app.
 */
@Composable
fun OwnershipQuestionHost() {
    val ownership = (LocalContext.current.applicationContext as RicordellaApplication).container.sharedOwnership
    LifecycleResumeEffect(ownership) {
        ownership.hosts++
        onPauseOrDispose { ownership.hosts-- }
    }
    val question by ownership.question.collectAsStateWithLifecycle()
    val current = question ?: return
    val answer = { yes: Boolean -> current.answer.complete(yes); Unit }
    Dialog(onDismissRequest = { answer(false) }) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.widthIn(max = 460.dp)) {
            Column(
                Modifier.padding(RicordellaDimensions.spaceXl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
            ) {
                WizardAvatar(seed = current.owner, size = 88.dp)
                Text(trf("È di %1\$s", current.owner), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    when (current.action) {
                        GuardedAction.COMPLETE -> trf("Vuoi davvero segnare come fatto «%1\$s»?", current.title)
                        GuardedAction.REOPEN -> trf("Vuoi davvero riaprire «%1\$s»?", current.title)
                        GuardedAction.EDIT -> trf("Vuoi davvero modificare «%1\$s»?", current.title)
                    } + " " + trf("Lo sincronizzo subito e %1\$s saprà che l'hai fatto tu.", current.owner),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { answer(false) }) { Text(tr("Annulla")) }
                    PushButton(tr("Sì, continua"), onClick = { answer(true) })
                }
            }
        }
    }
}
