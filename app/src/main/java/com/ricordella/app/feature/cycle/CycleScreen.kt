package com.ricordella.app.feature.cycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Check
import com.ricordella.app.core.ui.HappyWizard
import com.ricordella.app.core.ui.TutorialDialog
import com.ricordella.app.core.ui.TutorialPage
import com.ricordella.app.core.ui.WizardScene
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.ConfirmDialog
import com.ricordella.app.core.ui.DateField
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.DropdownField
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.date.RelativeDateDescriber
import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.model.CycleCalendar
import com.ricordella.app.domain.model.CycleEntry
import com.ricordella.app.domain.model.CycleProfile
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.displayName
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Ciclo mestruale: per ogni persona durata, frequenza, previsione, cronologia di due anni e avviso di ritardo.
 * Nel calendario i giorni (veri e previsti) hanno una goccia. Dati solo su questo telefono.
 */
@Composable
fun CycleScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as RicordellaApplication).container
    val scope = rememberCoroutineScope()
    val app = LocalAppSettings.current
    val people by container.personRepository.observePeople(archived = false).collectAsStateWithLifecycle(initialValue = emptyList())
    val today = container.time.today()
    var editing by remember { mutableStateOf<CycleProfile?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CycleProfile?>(null) }
    fun update(transform: (AppSettings) -> AppSettings) = scope.launch { container.settingsRepository.update(transform) }

    val colors = MaterialTheme.ricordellaColors
    val profiles = app.cycleProfiles.mapNotNull { profile -> people.firstOrNull { it.id == profile.personId }?.let { profile to it } }

    DetailScaffold(
        title = tr("Ciclo"),
        onBack = onBack,
        actions = {
            IconButton(onClick = { help = true }) { Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = tr("Come funziona")) }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .contentWidth()
                .padding(horizontal = RicordellaDimensions.screenPadding)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
        ) {
            Text(
                tr("Previsioni indicative in base ai giorni che indichi: nel calendario compare una goccia, senza notifiche per i giorni previsti. Tocca ? per sapere come funziona."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            profiles.forEach { (profile, person) ->
                ProfileCard(
                    profile = profile,
                    person = person,
                    log = app.cycleLog,
                    today = today,
                    onStart = { update { it.copy(cycleLog = CycleCalendar.withStart(it.cycleLog, profile, today)) } },
                    onEnd = { update { it.copy(cycleLog = CycleCalendar.withEnd(it.cycleLog, profile.personId, today)) } },
                    onEdit = { editing = profile },
                    onDelete = { deleting = profile },
                    onRemoveEntry = { entry -> update { it.copy(cycleLog = it.cycleLog - entry) } },
                )
            }
            // Si può tenere un solo ciclo.
            if (people.isEmpty()) {
                Text(tr("Il ciclo va sempre assegnato a una persona: aggiungila prima nella sezione Persone."), style = MaterialTheme.typography.bodyLarge)
            } else if (app.cycleProfiles.isEmpty()) {
                PushButton(tr("Aggiungi un ciclo"), onClick = { adding = true }, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth().padding(top = RicordellaDimensions.spaceM))
            }
        }
    }

    if (adding || editing != null) {
        val current = editing
        val choices = if (current != null) people.filter { it.id == current.personId } else people.filter { p -> app.cycleProfiles.none { it.personId == p.id } }
        SetupDialog(
            existing = current,
            people = choices,
            // L'avviso è acceso di default solo per il ciclo di chi usa l'app (o per il primo che si crea).
            notifyByDefault = { person -> person.id == app.sharedMeId || app.cycleProfiles.isEmpty() },
            onDismiss = { adding = false; editing = null },
            onSave = { profile, lastStart ->
                adding = false
                editing = null
                update { s ->
                    val others = s.cycleProfiles.filter { it.personId != profile.personId }
                    val log = if (lastStart != null) CycleCalendar.withStart(s.cycleLog, profile, lastStart) else s.cycleLog
                    s.copy(cycleProfiles = others + profile, cycleLog = log)
                }
            },
        )
    }
    deleting?.let { profile ->
        ConfirmDialog(
            title = tr("Eliminare il ciclo?"),
            message = tr("Si cancellano le impostazioni e la cronologia di questa persona. Non si può annullare."),
            confirmLabel = tr("Elimina"),
            destructive = true,
            onConfirm = {
                deleting = null
                update { s -> s.copy(cycleProfiles = s.cycleProfiles - profile, cycleLog = s.cycleLog.filter { it.personId != profile.personId }) }
            },
            onDismiss = { deleting = null },
        )
    }
    if (help) TutorialDialog(CyclePages, onDismiss = { help = false })
}

/** Come funziona il ciclo: pagine animate come gli altri tutorial dell'app. */
private val CyclePages: List<TutorialPage>
    get() = listOf(
        TutorialPage(
            tr("Chi, quanto, ogni quanto"),
            tr("Scegli la persona (il ciclo è sempre di qualcuno), quanti giorni dura la mestruazione, ogni quanti giorni torna il ciclo e quando è iniziata l'ultima."),
        ) { HappyWizard(size = 180.dp, scene = WizardScene.READING) },
        TutorialPage(
            tr("Nel calendario"),
            tr("Nel calendario i giorni hanno una goccia di sangue: piena se è già successo, chiara se è solo una previsione. Per i giorni previsti non arrivano notifiche."),
        ) { HappyWizard(size = 180.dp, scene = WizardScene.CONJURING) },
        TutorialPage(
            tr("Inizio e fine"),
            tr("Quando inizia tocca «È iniziato oggi»: la previsione riparte da quel giorno. Quando finisce tocca «È finito oggi»; se non lo fai vale la durata che hai indicato."),
        ) { HappyWizard(size = 180.dp, scene = WizardScene.WAITING_BELL) },
        TutorialPage(
            tr("La cronologia"),
            tr("Per ogni persona tengo la cronologia degli ultimi 2 anni, con la lunghezza di ogni ciclo."),
        ) { HappyWizard(size = 180.dp, scene = WizardScene.SEARCHING) },
        TutorialPage(
            tr("Se è in ritardo"),
            tr("Se il ciclo è in ritardo ti avviso alle 9 del giorno dopo la data prevista e poi dopo una settimana. L'avviso è acceso di default solo per il ciclo di chi usa l'app (la persona «Io sono» nelle Impostazioni › Condivisione) o per il primo che crei; per gli altri lo accendi tu."),
        ) { HappyWizard(size = 180.dp, scene = WizardScene.PHONE_CALL) },
        TutorialPage(
            tr("Solo sul tuo telefono"),
            tr("I dati restano su questo telefono e non vanno nel file condiviso. Sono previsioni indicative: non sostituiscono un parere medico e non sono un metodo contraccettivo."),
        ) { HappyWizard(size = 180.dp, scene = WizardScene.TV) },
    )

@Composable
private fun ProfileCard(
    profile: CycleProfile,
    person: Person,
    log: List<CycleEntry>,
    today: LocalDate,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRemoveEntry: (CycleEntry) -> Unit,
) {
    val colors = MaterialTheme.ricordellaColors
    val settings = LocalAppSettings.current
    val tone = colors.coral
    val next = CycleCalendar.nextStart(profile, log)
    val late = CycleCalendar.lateDays(profile, log, today)
    val day = CycleCalendar.currentDay(profile, log, today)
    Column(
        Modifier.fillMaxWidth().background(tone.container, MaterialTheme.shapes.large).padding(RicordellaDimensions.spaceL),
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
            Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = tone.solid)
            Text(person.displayName, style = MaterialTheme.typography.titleLarge, color = tone.content, modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text(tr("Modifica")) }
        }
        Text(
            trf("Mestruazione di %1\$s giorni · ciclo di %2\$s giorni", profile.periodDays, profile.cycleDays),
            style = MaterialTheme.typography.bodyMedium,
            color = tone.content,
        )
        Text(
            when {
                day != null -> trf("Oggi è il giorno %1\$s di %2\$s", day, profile.periodDays)
                next == null -> tr("Segna quando inizia per avere la previsione.")
                late > 0 -> trf("In ritardo di %1\$s giorni (previsto il %2\$s)", late, DateTexts.date(next, settings.dateFormat, today))
                else -> trf("Prossimo ciclo previsto: %1\$s (%2\$s)", DateTexts.date(next, settings.dateFormat, today), RelativeDateDescriber.describe(next, today))
            },
            style = MaterialTheme.typography.titleMedium,
            color = tone.content,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS), verticalAlignment = Alignment.CenterVertically) {
            PushButton(tr("È iniziato oggi"), onClick = onStart, enabled = day == null, modifier = Modifier.weight(1f))
            if (CycleCalendar.isOpen(profile, log, today)) TextButton(onClick = onEnd) { Text(tr("È finito oggi")) }
        }
        val history = CycleCalendar.history(profile, log, today)
        if (history.isNotEmpty()) {
            SectionHeader(tr("Cronologia (ultimi 2 anni)"))
            Text(
                tr("Una riga per ogni mestruazione, dalla più recente: quando è iniziata e finita, quanti giorni è durata e a quanti giorni dall'inizio della precedente è arrivata."),
                style = MaterialTheme.typography.bodySmall,
                color = tone.content,
            )
            history.forEach { row ->
                val last = CycleCalendar.lastDay(row.entry, profile)
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.medium).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(
                            trf("%1\$s → %2\$s", DateTexts.date(row.entry.start, settings.dateFormat, today), DateTexts.date(last, settings.dateFormat, today)),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            trf("Durata: %1\$s giorni", row.days) + (if (row.entry.end == null && !last.isBefore(today)) " · " + tr("in corso") else ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            row.cycleLength?.let { trf("Arrivata %1\$s giorni dopo l'inizio della precedente", it) } ?: tr("Primo ciclo registrato"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onRemoveEntry(row.entry) }) {
                        Icon(Icons.Rounded.Close, contentDescription = tr("Elimina"))
                    }
                }
            }
        }
        TextButton(onClick = onDelete) { Text(tr("Elimina il ciclo"), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun SetupDialog(
    existing: CycleProfile?,
    people: List<Person>,
    notifyByDefault: (Person) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (CycleProfile, LocalDate?) -> Unit,
) {
    var person by remember { mutableStateOf(people.firstOrNull()) }
    var periodDays by remember { mutableStateOf(existing?.periodDays ?: 5) }
    var cycleDays by remember { mutableStateOf(existing?.cycleDays ?: 28) }
    var lastStart by remember { mutableStateOf<LocalDate?>(null) }
    var notify by remember { mutableStateOf(existing?.notifyLate ?: people.firstOrNull()?.let(notifyByDefault) ?: false) }
    val tone = MaterialTheme.ricordellaColors.coral
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().background(tone.container).padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(48.dp).background(tone.solid, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(26.dp))
                    }
                    Column {
                        Text(if (existing == null) tr("Nuovo ciclo") else tr("Modifica il ciclo"), style = MaterialTheme.typography.titleLarge, color = tone.content)
                        Text(tr("Solo su questo telefono"), style = MaterialTheme.typography.bodySmall, color = tone.content.copy(alpha = 0.8f))
                    }
                }
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM)) {
                    DropdownField(
                        label = tr("Di chi è *"),
                        options = people,
                        selected = person ?: people.firstOrNull() ?: return@Column,
                        optionLabel = { it.displayName },
                        onSelected = { chosen -> person = chosen; if (existing == null) notify = notifyByDefault(chosen) },
                    )
                    DropdownField(
                        label = tr("Quanti giorni dura la mestruazione"),
                        options = (1..10).toList(),
                        selected = periodDays,
                        optionLabel = { trf("%1\$s giorni", it) },
                        onSelected = { periodDays = it },
                    )
                    DropdownField(
                        label = tr("Ogni quanti giorni torna il ciclo"),
                        options = (20..45).toList(),
                        selected = cycleDays,
                        optionLabel = { trf("%1\$s giorni", it) },
                        onSelected = { cycleDays = it },
                    )
                    if (existing == null) {
                        DateField(
                            label = tr("Quando è iniziata l'ultima *"),
                            value = lastStart,
                            onValueChange = { lastStart = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Avvisami se è in ritardo"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = notify, onCheckedChange = { notify = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text(tr("Annulla")) }
                        PushButton(
                            text = tr("Salva"),
                            icon = androidx.compose.material.icons.Icons.Rounded.Check,
                            enabled = person != null && (existing != null || lastStart != null),
                            onClick = { person?.let { onSave(CycleProfile(it.id, periodDays, cycleDays, notify), lastStart) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
