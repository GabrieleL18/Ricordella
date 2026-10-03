package com.ricordella.app.feature.home

import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.AutoAwesome
import com.ricordella.app.domain.model.ResolutionsPrompt
import androidx.compose.runtime.saveable.rememberSaveable
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.YearlyTask
import com.ricordella.app.core.ui.YearlyTaskDialog
import com.ricordella.app.core.ui.shareBackup
import com.ricordella.app.feature.settings.BackupDialog
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.navigation.AppNavigator
import com.ricordella.app.core.ui.CrystalBallMascot
import com.ricordella.app.core.ui.EmptyState
import com.ricordella.app.core.ui.HappyWizard
import com.ricordella.app.core.ui.MascotState
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.ReminderCard
import com.ricordella.app.core.ui.ReminderDateMode
import com.ricordella.app.core.ui.RevealTracker
import com.ricordella.app.core.ui.RicordellaMotion
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.TopLevelScaffold
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.rememberMascotState
import com.ricordella.app.core.ui.rememberRevealTracker
import com.ricordella.app.core.ui.reveal
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.model.ReminderOccurrence
import com.ricordella.app.feature.calendar.DayTimeline
import com.ricordella.app.feature.calendar.LiveTime
import androidx.compose.ui.text.font.FontWeight

@Composable
fun HomeScreen(navigator: AppNavigator, onAdd: () -> Unit) {
    val viewModel = appViewModel { c, _ -> HomeViewModel(c.reminderRepository, c.completeReminder, c.time, c.settingsRepository, c.housekeeping, c.saveReminder, c.developerTools.resolutionsDay) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val yearly by viewModel.yearly.collectAsStateWithLifecycle()
    val resolutions by viewModel.resolutions.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val backupEvent by viewModel.backupEvent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showBackupChoices by rememberSaveable { mutableStateOf(false) }
    val chooseFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::onNewBackupFile)
    }
    LaunchedEffect(backupEvent) {
        when (val event = backupEvent) {
            BackupEvent.ChooseFile -> chooseFile.launch("remindella-backup.zip")
            BackupEvent.Updated -> Toast.makeText(context, tr("Backup aggiornato"), Toast.LENGTH_SHORT).show()
            BackupEvent.Failed -> Toast.makeText(context, tr("Backup non riuscito, riprova"), Toast.LENGTH_SHORT).show()
            is BackupEvent.Share -> shareBackup(context, event.uri)
            null -> return@LaunchedEffect
        }
        viewModel.onBackupEventHandled()
    }
    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.onMessageShown() }
    }
    val mascot = rememberMascotState()
    val tracker = rememberRevealTracker()
    val colors = MaterialTheme.ricordellaColors
    val complete: (ReminderWithLinks) -> Unit = { entry ->
        viewModel.onToggleComplete(entry.reminder.id)
        mascot.celebrate()
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Posizione delle intestazioni nella lista: serve ai pulsanti del recap per saltare alla sezione.
    val showExtras = LocalAppSettings.current.showExtrasOnHome
    val attentionAt = 4 + (if (showExtras) 1 else 0) + (if (resolutions != null) 1 else 0) + (if (!state.isLoading && state.isEmpty) 1 else 0)
    val todayAt = attentionAt + if (state.attention.isNotEmpty()) 1 + state.attention.size else 0
    val upcomingAt = todayAt + 2
    val goTo: (Int) -> Unit = { index -> scope.launch { listState.animateScrollToItem(index) } }

    TopLevelScaffold(title = tr("Remindella"), navigator = navigator, onAdd = onAdd, brandTitle = true) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().contentWidth(),
            contentPadding = PaddingValues(
                start = RicordellaDimensions.screenPadding,
                end = RicordellaDimensions.screenPadding,
                top = padding.calculateTopPadding(),
                bottom = 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
        ) {
            item(key = "greeting") { Greeting(state, mascot, Modifier.reveal(tracker, "greeting", 0), onAttention = { goTo(attentionAt) }, onToday = { goTo(todayAt) }, onUpcoming = { goTo(upcomingAt) }) }
            item(key = "quick-entry") {
                QuickEntryBar(state.now, onAdd = viewModel::onQuickAdd, onEdit = { navigator.newReminder(quickText = it) }, modifier = Modifier.padding(vertical = RicordellaDimensions.spaceXs))
            }
            if (showExtras) item(key = "extras") { HomeExtras(navigator) }
            item(key = "permission") { NotificationPermissionCard() }
            item(key = "backup") {
                BackupDueCard(visible = state.backupDue, intervalDays = state.backupIntervalDays, onExport = { showBackupChoices = true }, onLater = viewModel::onPostponeBackup)
            }
            resolutions?.let { (prompt, year) ->
                item(key = "resolutions") {
                    ResolutionsCard(
                        prompt = prompt,
                        year = year,
                        count = LocalAppSettings.current.resolutions.count { it.year == year },
                        onOpen = { navigator.openResolutions(year, recap = prompt == ResolutionsPrompt.RECAP) },
                        onLater = { viewModel.onResolutionsLater(prompt) },
                        onSkip = { viewModel.onResolutionsSkip(year) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (!state.isLoading && state.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.EventAvailable,
                        title = tr("Niente all'orizzonte"),
                        message = tr("Nei prossimi giorni non c'è nulla da ricordare. Aggiungi qualcosa e ci penso io."),
                        actionLabel = tr("Aggiungi"),
                        onAction = onAdd,
                        modifier = Modifier.animateItem(),
                        illustration = { HappyWizard(size = 150.dp) },
                    )
                }
            }

            if (state.attention.isNotEmpty()) {
                item(key = "attention-header") {
                    SectionHeader(tr("Da guardare"), icon = Icons.Rounded.Bolt, tone = colors.coral, modifier = Modifier.animateItem())
                }
            }
            itemsIndexed(state.attention, key = { _, it -> "attention-" + it.reminder.id }) { index, entry ->
                ReminderCard(
                    entry = entry,
                    now = state.now,
                    onClick = { navigator.openReminder(entry.reminder.id) },
                    onToggleComplete = { complete(entry) },
                    dateMode = ReminderDateMode.RELATIVE,
                    highlighted = true,
                    modifier = Modifier.animateItem().reveal(tracker, "attention-" + entry.reminder.id, index + 1),
                )
            }

            if (!state.isLoading) {
                item(key = "today-header") {
                    SectionHeader(tr("La tua giornata"), icon = Icons.Rounded.WbSunny, tone = colors.pear, modifier = Modifier.animateItem())
                }
                item(key = "today-timeline") {
                    val today = state.now.toLocalDate()
                    DayTimeline(
                        date = today,
                        occurrences = state.today.map { ReminderOccurrence(it, today, it.reminder.dueDate) },
                        now = state.now,
                        onOpen = { navigator.openReminder(it.reminder.id) },
                        onAdd = { navigator.newReminder(date = today) },
                        onToggle = { complete(it.entry) },
                        modifier = Modifier.animateItem().reveal(tracker, "today-timeline", 2),
                    )
                }
            }

            if (state.upcoming.isNotEmpty()) {
                item(key = "upcoming-header") {
                    SectionHeader(tr("Prossimamente"), icon = Icons.AutoMirrored.Rounded.EventNote, tone = colors.lavender, modifier = Modifier.animateItem()) {
                        TextButton(onClick = navigator::openReminders) { Text(tr("Vedi tutto")) }
                    }
                }
                itemsIndexed(state.upcoming, key = { _, it -> "upcoming-" + it.reminder.id }) { index, entry ->
                    ReminderCard(
                        entry = entry,
                        now = state.now,
                        onClick = { navigator.openReminder(entry.reminder.id) },
                        onToggleComplete = { complete(entry) },
                        dateMode = ReminderDateMode.RELATIVE,
                        modifier = Modifier.animateItem().reveal(tracker, "upcoming-" + entry.reminder.id, index + 3),
                    )
                }
            }
        }
    }

    // Operazioni annuali: prima le feste, poi la pulizia. Una finestra alla volta.
    val year = state.now.year
    when {
        yearly.holidays > 0 -> YearlyTaskDialog(
            YearlyTask.HOLIDAYS, yearly.holidays, year,
            onConfirm = { viewModel.onConfirmYearly(YearlyTask.HOLIDAYS, it) },
            onLater = { viewModel.onLaterYearly(YearlyTask.HOLIDAYS) },
            onNever = { viewModel.onNeverYearly(YearlyTask.HOLIDAYS) },
        )
        yearly.cleanupIds.isNotEmpty() -> YearlyTaskDialog(
            YearlyTask.CLEANUP, yearly.cleanupIds.size, year,
            onConfirm = { viewModel.onConfirmYearly(YearlyTask.CLEANUP, it) },
            onLater = { viewModel.onLaterYearly(YearlyTask.CLEANUP) },
            onNever = { viewModel.onNeverYearly(YearlyTask.CLEANUP) },
        )
    }

    // L'invito al backup chiede sempre se salvare (sovrascrivere o nuova versione) o condividere.
    if (showBackupChoices) {
        BackupDialog(
            export = true,
            hasTarget = LocalAppSettings.current.backupTargetUri != null,
            onDismiss = { showBackupChoices = false },
            onOverwrite = viewModel::onExportBackup,
            onNewVersion = {
                chooseFile.launch("remindella-backup-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".zip")
            },
            onShare = viewModel::onShareBackup,
            onChooseFile = {},
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Greeting(state: HomeUiState, mascot: MascotState, modifier: Modifier = Modifier, onAttention: () -> Unit, onToday: () -> Unit, onUpcoming: () -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    Column(modifier.padding(top = RicordellaDimensions.spaceS, bottom = RicordellaDimensions.spaceS)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                DayPill(state.now)
                Text(greeting(state.now.hour), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 6.dp))
                val count = state.attention.size
                val subtitle = when {
                    state.isLoading -> " "
                    count == 0 && state.today.isEmpty() -> tr("Tutto sotto controllo. Goditi la giornata.")
                    count == 0 -> tr("Niente di urgente: solo le cose di oggi.")
                    count == 1 -> tr("C'è 1 cosa che richiede attenzione.")
                    else -> trf("Ci sono %1\$s cose che richiedono attenzione.", count)
                }
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Con qualcosa di scaduto torna la sfera col fumo rosso; altrimenti il mago.
            if (state.hasOverdue) CrystalBallMascot(size = 92.dp, state = mascot, overdue = true)
            else HappyWizard(size = 92.dp, waving = true, magic = mascot.sparks)
        }
        if (!state.isLoading && !state.isEmpty) {
            // A capo se, in una lingua con parole lunghe, le pillole non entrano in una riga.
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
                verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
                modifier = Modifier.padding(top = RicordellaDimensions.spaceM),
            ) {
                CountPill(state.attention.size, tr("da guardare"), Icons.Rounded.Bolt, colors.coral, onClick = onAttention)
                CountPill(state.today.size, tr("oggi"), Icons.Rounded.WbSunny, colors.pear, onClick = onToday)
                CountPill(state.upcoming.size, tr("in arrivo"), Icons.AutoMirrored.Rounded.EventNote, colors.lavender, plus = state.hasMoreUpcoming, onClick = onUpcoming)
            }
        }
    }
}

private fun greeting(hour: Int): String = when (hour) {
    in 5..12 -> tr("Buongiorno")
    in 13..17 -> tr("Buon pomeriggio")
    else -> tr("Buonasera")
}

/** Giorno e ora in una pillola: l'icona segue il momento della giornata, i due punti lampeggiano. */
@Composable
private fun DayPill(now: java.time.LocalDateTime) {
    val tone = when (now.hour) {
        in 5..17 -> MaterialTheme.ricordellaColors.pear
        else -> MaterialTheme.ricordellaColors.lavender
    }
    val icon = when (now.hour) {
        in 5..8 -> Icons.Rounded.WbTwilight
        in 9..17 -> Icons.Rounded.WbSunny
        in 18..20 -> Icons.Rounded.WbTwilight
        else -> Icons.Rounded.Bedtime
    }
    Surface(color = tone.container, contentColor = tone.content, shape = CircleShape) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(DateTexts.weekdayAndDay(now.toLocalDate()).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge)
            Text("·", style = MaterialTheme.typography.labelLarge)
            LiveTime(now, MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), tone.content)
        }
    }
}

/** Contatore a pillola: il numero scorre verso l'alto quando cambia. */
@Composable
private fun CountPill(count: Int, label: String, icon: ImageVector, tone: Tone, plus: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = tone.container,
        contentColor = tone.content,
        shape = CircleShape,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = if (plus) trf("%1\$s o più %2\$s", count, label) else "$count $label" },
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(tween(RicordellaMotion.SHORT, easing = RicordellaMotion.EaseOut)) { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically(tween(RicordellaMotion.SHORT, easing = RicordellaMotion.EaseIn)) { if (up) -it else it } + fadeOut())
                },
                label = "count-$label",
            ) { value ->
                Text(if (plus) "$value+" else "$value", style = MaterialTheme.typography.titleMedium)
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Buoni propositi: a gennaio l'invito a scriverli, a fine anno il recap di quelli rispettati. */
@Composable
private fun ResolutionsCard(
    prompt: ResolutionsPrompt,
    year: Int,
    count: Int,
    onOpen: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = MaterialTheme.ricordellaColors.pear
    val write = prompt == ResolutionsPrompt.WRITE
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = RicordellaDimensions.spaceS)
            .background(tone.container, MaterialTheme.shapes.large)
            .padding(RicordellaDimensions.spaceL),
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM)) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = tone.content)
            Column(Modifier.weight(1f)) {
                Text(
                    if (write) trf("Buoni propositi per il %1\$s", year) else trf("Com'è andato il %1\$s?", year),
                    style = MaterialTheme.typography.titleMedium,
                    color = tone.content,
                )
                Text(
                    if (write) tr("Inizia l'anno con qualche proposito: a fine anno vediamo insieme quali hai rispettato.")
                    else trf("Avevi %1\$s buoni propositi: segna quelli che hai rispettato e guarda il recap.", count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tone.content,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS), verticalAlignment = Alignment.CenterVertically) {
            PushButton(if (write) tr("Scrivili") else tr("Vedi il recap"), onClick = onOpen, modifier = Modifier.weight(1f))
            TextButton(onClick = if (write) onSkip else onLater) { Text(if (write) tr("Non quest'anno") else tr("Più tardi")) }
        }
    }
}

/** Scaduto l'intervallo scelto: invito ad aggiornare il backup, che sovrascrive il precedente. */
@Composable
private fun BackupDueCard(visible: Boolean, intervalDays: Int, onExport: () -> Unit, onLater: () -> Unit) {
    val tone = MaterialTheme.ricordellaColors.mint
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(tween(RicordellaMotion.LONG, easing = RicordellaMotion.EaseOut)),
        exit = fadeOut(tween(RicordellaMotion.SHORT)) + shrinkVertically(tween(RicordellaMotion.LONG, easing = RicordellaMotion.EaseInOut)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = RicordellaDimensions.spaceS)
                .background(tone.container, MaterialTheme.shapes.large)
                .padding(RicordellaDimensions.spaceL),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM)) {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = tone.content)
                Column(Modifier.weight(1f)) {
                    Text(tr("È ora di aggiornare il backup"), style = MaterialTheme.typography.titleMedium, color = tone.content)
                    Text(
                        when (intervalDays) {
                            1 -> tr("Aggiorna il backup di oggi: sovrascrivo quello precedente.")
                            7 -> tr("È passata una settimana: aggiorno il backup sovrascrivendo il precedente.")
                            30 -> tr("È passato un mese: aggiorno il backup sovrascrivendo il precedente.")
                            365 -> tr("È passato un anno: aggiorno il backup sovrascrivendo il precedente.")
                            else -> tr("Sono passati 3 mesi: aggiorno il backup sovrascrivendo il precedente.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = tone.content,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS), verticalAlignment = Alignment.CenterVertically) {
                PushButton(tr("Aggiorna ora"), onClick = onExport, modifier = Modifier.weight(1f))
                TextButton(onClick = onLater) { Text(tr("Più tardi")) }
            }
        }
    }
}

/** Invito a concedere il permesso di notifica (Android 13+), finché non è concesso. */
@Composable
private fun NotificationPermissionCard() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    fun isGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(isGranted()) }
    LifecycleResumeEffect(Unit) {
        granted = isGranted()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val tone = MaterialTheme.ricordellaColors.pear

    AnimatedVisibility(
        visible = !granted,
        enter = fadeIn(),
        exit = fadeOut(tween(RicordellaMotion.SHORT)) + shrinkVertically(tween(RicordellaMotion.LONG, easing = RicordellaMotion.EaseInOut)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tone.container, MaterialTheme.shapes.large)
                .padding(RicordellaDimensions.spaceL),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM)) {
                Icon(Icons.Rounded.NotificationsOff, contentDescription = null, tint = tone.content)
                Column(Modifier.weight(1f)) {
                    Text(tr("Le notifiche sono spente"), style = MaterialTheme.typography.titleMedium, color = tone.content)
                    Text(
                        tr("Accendile e ti avviso io, anche con l'app chiusa."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tone.content,
                    )
                }
            }
            PushButton(tr("Accendi le notifiche"), onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Collegamenti alle sezioni extra: si possono nascondere da Impostazioni. */
@Composable
private fun HomeExtras(navigator: AppNavigator) {
    val colors = MaterialTheme.ricordellaColors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
        ExtraTile(Icons.Rounded.Alarm, tr("Sveglie"), colors.coral, navigator::openAlarms, Modifier.weight(1f))
        ExtraTile(Icons.Rounded.StickyNote2, tr("Note"), colors.pear, navigator::openNotes, Modifier.weight(1f))
        ExtraTile(Icons.Rounded.WaterDrop, tr("Pozioni"), colors.cyan, navigator::openPotions, Modifier.weight(1f))
    }
}

@Composable
private fun ExtraTile(icon: ImageVector, label: String, tone: Tone, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(tone.container)
            .clickable(onClick = onClick)
            .padding(vertical = RicordellaDimensions.spaceM),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(36.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = tone.content)
    }
}
