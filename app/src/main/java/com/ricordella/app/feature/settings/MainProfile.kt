package com.ricordella.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.WizardAvatar
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.Person
import kotlinx.coroutines.launch

/**
 * Profilo principale: la persona che sei tu su questo telefono. Si sceglie all'inizio (tra le persone o scrivendo il nome),
 * non si può eliminare e dà il nome ai backup nella cartella condivisa. Va messo una volta sola nella shell dell'app.
 */
@Composable
fun MainProfileHost() {
    val app = LocalAppSettings.current
    if (app.sharedMeId != null) return
    val container = (LocalContext.current.applicationContext as RicordellaApplication).container
    val scope = rememberCoroutineScope()
    val people by container.personRepository.observePeople(archived = false).collectAsStateWithLifecycle(initialValue = emptyList())
    var name by rememberSaveable { mutableStateOf("") }
    fun choose(person: Person) = scope.launch {
        container.settingsRepository.update { it.copy(sharedMeId = person.id, sharedMeName = person.name) }
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(tr("Chi sei tu?")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Scegli il tuo profilo principale: non si può eliminare e dà il nome ai tuoi backup."))
                people.forEach { person ->
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium).clickable { choose(person) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        WizardAvatar(seed = person.name, size = 36.dp)
                        Text(person.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.ricordellaColors.cyan.content)
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (people.isEmpty()) tr("Il tuo nome") else tr("Oppure scrivi il tuo nome")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    scope.launch {
                        val now = container.time.now()
                        val person = Person(name = name.trim(), createdAt = now, updatedAt = now)
                        container.personRepository.save(person)
                        choose(person)
                    }
                },
            ) { Text(tr("Continua")) }
        },
    )
}
