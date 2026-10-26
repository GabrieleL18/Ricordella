package com.ricordella.app.feature.home

import com.ricordella.app.domain.model.AppSection
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.CreditCard
import com.ricordella.app.core.ui.pressScale
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarHostState
import com.ricordella.app.core.ui.offerReopen
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.material.icons.rounded.Whatshot
import com.ricordella.app.core.ui.rememberNotificationChecks
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

@Composable
fun HomeScreen(navigator: AppNavigator, onAdd: () -> Unit) {
    val viewModel = appViewModel { c, _ -> HomeViewModel(c.reminderRepository, c.completeReminder, c.time, c.settingsRepository, c.housekeeping, c.saveReminder, c.developerTools.resolutionsDay, c.reopenReminder) }
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
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val complete: (ReminderWithLinks) -> Unit = { entry ->
        viewModel.onToggleComplete(entry.reminder.id)
        mascot.celebrate()
        scope.launch { snackbar.offerReopen(entry) { viewModel.onReopen(entry.reminder.id) } }
    }

    val listState = rememberLazyListState()
    val showExtras = LocalAppSettings.current.showExtrasOnHome
    // Un solo avviso alla volta, il più importante: notifiche, poi backup, poi buoni propositi.
    val granted = rememberNotificationsGranted()
    var healthHidden by rememberSaveable { mutableStateOf(false) }
    val broken = rememberNotificationChecks().filter { it.critical && !it.ok }.takeIf { !healthHidden }.orEmpty()
    var staleHidden by rememberSaveable { mutableStateOf(false) }
    val stale = state.backupStaleDays > 0 && !staleHidden
    val notice = when {
        !granted.value -> Notice.PERMISSION
        broken.isNotEmpty() -> Notice.HEALTH
        state.backupDue || stale -> Notice.BACKUP
        resolutions != null -> Notice.RESOLUTIONS
        else -> null
    }

    TopLevelScaffold(title = tr("Remindella"), navigator = navigator, onAdd = onAdd, snackbarHostState = snackbar, brandTitle = true) { padding ->
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
            item(key = "greeting") { Greeting(state, mascot, Modifier.reveal(tracker, "greeting", 0)) }
            // Le notifiche che non arrivano vanno sistemate subito: in cima.
            if (notice == Notice.PERMISSION || notice == Notice.HEALTH) item(key = "notice-top") {
                Box(Modifier.animateItem()) {
                    if (notice == Notice.PERMISSION) NotificationPermissionCard { granted.value = it }
                    else com.ricordella.app.core.ui.NotificationHealthCard(broken, onLater = { healthHidden = true })
                }
            }
            if (showExtras) item(key = "extras") { HomeExtras(navigator) }

            if (!state.isLoading && state.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.EventAvailable,
                        title = tr("Niente all'orizzonte"),
                        message = tr("Nei prossimi giorni non c'è nulla da ricordare. Prova a scrivere qui sotto «dentista domani alle 9»: ci penso io."),
                        actionLabel = tr("Aggiungi"),
                        onAction = onAdd,
                        modifier = Modifier.animateItem(),
                        illustration = { HappyWizard(size = 150.dp) },
                    )
                }
            }

            if (state.overdue.isNotEmpty()) {
                item(key = "overdue-header") {
                    SectionHeader(tr("Scaduti"), icon = Icons.Rounded.Whatshot, tone = colors.coral, modifier = Modifier.animateItem())
                }
            }
            itemsIndexed(state.overdue, key = { _, it -> "overdue-" + it.reminder.id }) { index, entry ->
                ReminderCard(
                    entry = entry,
                    now = state.now,
                    onClick = { navigator.openReminder(entry.reminder.id) },
                    onToggleComplete = { complete(entry) },
                    dateMode = ReminderDateMode.RELATIVE,
                    highlighted = true,
                    modifier = Modifier.animateItem().reveal(tracker, "overdue-" + entry.reminder.id, index + 1),
                )
            }

            if (!state.isLoading && !state.isEmpty) {
                item(key = "today-header") {
                    SectionHeader(tr("Oggi"), icon = Icons.Rounded.WbSunny, tone = colors.pear, modifier = Modifier.animateItem())
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
            // Prima si guarda cosa c'è da fare, poi si aggiunge: per questo sta sotto la giornata.
            item(key = "quick-entry") {
                QuickEntryBar(state.now, onAdd = viewModel::onQuickAdd, onEdit = { navigator.newReminder(quickText = it) }, modifier = Modifier.animateItem().padding(vertical = RicordellaDimensions.spaceXs))
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

            // Backup e buoni propositi in fondo: non coprono il contenuto.
            if (notice == Notice.BACKUP) item(key = "backup") {
                BackupDueCard(
                    intervalDays = state.backupIntervalDays,
                    staleDays = if (stale && !state.backupDue) state.backupStaleDays else 0,
                    onExport = { showBackupChoices = true },
                    onLater = { if (state.backupDue) viewModel.onPostponeBackup() else staleHidden = true },
                    modifier = Modifier.animateItem(),
                )
            }
            if (notice == Notice.RESOLUTIONS) resolutions?.let { (prompt, year) ->
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

/** Data, saluto e una frase concreta su oggi. Le sezioni sotto dicono il resto. */
@Composable
private fun Greeting(state: HomeUiState, mascot: MascotState, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = RicordellaDimensions.spaceS, bottom = RicordellaDimensions.spaceS), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                DateTexts.weekdayAndDay(state.now.toLocalDate()).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(greeting(state.now.hour), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 2.dp))
            Text(if (state.isLoading) " " else summary(state.today.size, state.overdue.size), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Con qualcosa di scaduto torna la sfera col fumo rosso; altrimenti il mago.
        if (state.overdue.isNotEmpty()) CrystalBallMascot(size = 92.dp, state = mascot, overdue = true)
        else HappyWizard(size = 92.dp, waving = true, magic = mascot.sparks)
    }
}

/** "Oggi hai 3 cose. 1 è scaduta." */
internal fun summary(today: Int, overdue: Int): String {
    val first = when {
        today == 0 && overdue == 0 -> tr("Tutto sotto controllo. Goditi la giornata.")
        today == 0 -> tr("Oggi niente in programma.")
        today == 1 -> tr("Oggi hai 1 cosa.")
        else -> trf("Oggi hai %1\$s cose.", today)
    }
    val second = when (overdue) {
        0 -> null
        1 -> tr("1 è scaduta.")
        else -> trf("%1\$s sono scadute.", overdue)
    }
    return listOfNotNull(first, second).joinToString(" ")
}

private fun greeting(hour: Int): String = when (hour) {
    in 5..12 -> tr("Buongiorno")
    in 13..17 -> tr("Buon pomeriggio")
    else -> tr("Buonasera")
}

/** Avviso mostrato in Home: uno alla volta, nell'ordine di importanza. */
private enum class Notice { PERMISSION, HEALTH, BACKUP, RESOLUTIONS }

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
private fun BackupDueCard(intervalDays: Int, staleDays: Int, onExport: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val tone = if (staleDays > 0) MaterialTheme.ricordellaColors.coral else MaterialTheme.ricordellaColors.mint
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = RicordellaDimensions.spaceS)
            .background(tone.container, MaterialTheme.shapes.large)
            .padding(RicordellaDimensions.spaceL),
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM)) {
            Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = tone.content)
            Column(Modifier.weight(1f)) {
                Text(
                    if (staleDays > 0) tr("Il backup è vecchio") else tr("È ora di aggiornare il backup"),
                    style = MaterialTheme.typography.titleMedium,
                    color = tone.content,
                )
                Text(
                    if (staleDays > 0) trf("L'ultimo backup risale a %1\$s giorni fa: se perdi il telefono perdi i dati dopo quella data.", staleDays) else when (intervalDays) {
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

/** Permesso di notifica (Android 13+): prima di Android 13 c'è sempre. Si ricontrolla tornando nell'app. */
@Composable
private fun rememberNotificationsGranted(): MutableState<Boolean> {
    val context = LocalContext.current
    fun isGranted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val granted = remember { mutableStateOf(isGranted()) }
    LifecycleResumeEffect(Unit) {
        granted.value = isGranted()
        onPauseOrDispose { }
    }
    return granted
}

/** Invito a concedere il permesso di notifica, finché non è concesso. */
@Composable
private fun NotificationPermissionCard(onResult: (Boolean) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    val tone = MaterialTheme.ricordellaColors.pear
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

/** Collegamenti alle sezioni extra: si possono nascondere da Impostazioni. */
@Composable
private fun HomeExtras(navigator: AppNavigator) {
    val colors = MaterialTheme.ricordellaColors
    val settings = LocalAppSettings.current
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS)) {
        if (settings.shows(AppSection.ALARMS)) ExtraTile(Icons.Rounded.Alarm, tr("Sveglie"), colors.coral, navigator::openAlarms, Modifier.weight(1f))
        if (settings.shows(AppSection.TIMERS)) ExtraTile(Icons.Rounded.Timer, tr("Timer"), colors.mint, navigator::openTimers, Modifier.weight(1f))
        if (settings.shows(AppSection.NOTES)) ExtraTile(Icons.Rounded.StickyNote2, tr("Note"), colors.pear, navigator::openNotes, Modifier.weight(1f))
        if (settings.shows(AppSection.CARDS)) ExtraTile(Icons.Rounded.CreditCard, tr("Carte"), colors.lavender, navigator::openCards, Modifier.weight(1f))
        if (settings.shows(AppSection.POTIONS)) ExtraTile(Icons.Rounded.WaterDrop, tr("Pozioni"), colors.cyan, navigator::openPotions, Modifier.weight(1f), hint = tr("acqua"))
    }
}

@Composable
/** [hint]: una parola sotto il nome quando il nome da solo non dice cosa c'è dentro (Pozioni = acqua). */
private fun ExtraTile(icon: ImageVector, label: String, tone: Tone, onClick: () -> Unit, modifier: Modifier, hint: String? = null) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier
            .fillMaxHeight()
            .pressScale(interaction, pressedScale = 0.92f)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(tone.container)
            .clickable(interactionSource = interaction, indication = null, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(vertical = RicordellaDimensions.spaceL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(48.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(26.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = tone.content, maxLines = 1)
        if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = tone.content, maxLines = 1)
    }
}
