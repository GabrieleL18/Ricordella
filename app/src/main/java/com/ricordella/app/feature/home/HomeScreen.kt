package com.ricordella.app.feature.home

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.WbSunny
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

@Composable
fun HomeScreen(navigator: AppNavigator, onAdd: () -> Unit) {
    val viewModel = appViewModel { c, _ -> HomeViewModel(c.reminderRepository, c.completeReminder, c.time, c.settingsRepository, c.housekeeping) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cleanupIds by viewModel.cleanupIds.collectAsStateWithLifecycle()
    val backupEvent by viewModel.backupEvent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooseFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::onNewBackupFile)
    }
    LaunchedEffect(backupEvent) {
        when (backupEvent) {
            BackupEvent.ChooseFile -> chooseFile.launch("remindella-backup.zip")
            BackupEvent.Updated -> Toast.makeText(context, "Backup aggiornato", Toast.LENGTH_SHORT).show()
            BackupEvent.Failed -> Toast.makeText(context, "Backup non riuscito, riprova", Toast.LENGTH_SHORT).show()
            null -> return@LaunchedEffect
        }
        viewModel.onBackupEventHandled()
    }
    val mascot = rememberMascotState()
    val tracker = rememberRevealTracker()
    val colors = MaterialTheme.ricordellaColors
    val complete: (ReminderWithLinks) -> Unit = { entry ->
        viewModel.onToggleComplete(entry.reminder.id)
        mascot.celebrate()
    }

    TopLevelScaffold(title = "Remindella", navigator = navigator, onAdd = onAdd, brandTitle = true) { padding ->
        LazyColumn(
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
            item(key = "permission") { NotificationPermissionCard() }
            item(key = "backup") {
                BackupDueCard(visible = state.backupDue, intervalDays = state.backupIntervalDays, onExport = viewModel::onExportBackup, onLater = viewModel::onPostponeBackup)
            }

            if (!state.isLoading && state.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.EventAvailable,
                        title = "Niente all'orizzonte",
                        message = "Nei prossimi giorni non c'è nulla da ricordare. Aggiungi qualcosa e ci penso io.",
                        actionLabel = "Aggiungi",
                        onAction = onAdd,
                        modifier = Modifier.animateItem(),
                        illustration = { HappyWizard(size = 150.dp) },
                    )
                }
            }

            if (state.attention.isNotEmpty()) {
                item(key = "attention-header") {
                    SectionHeader("Da guardare", icon = Icons.Rounded.Bolt, tone = colors.coral, modifier = Modifier.animateItem())
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
                    SectionHeader("La tua giornata", icon = Icons.Rounded.WbSunny, tone = colors.pear, modifier = Modifier.animateItem())
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
                    SectionHeader("Prossimamente", icon = Icons.AutoMirrored.Rounded.EventNote, tone = colors.lavender, modifier = Modifier.animateItem()) {
                        TextButton(onClick = navigator::openReminders) { Text("Vedi tutto") }
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

    if (cleanupIds.isNotEmpty()) {
        CleanupDialog(cleanupIds.size, onConfirm = viewModel::onConfirmCleanup, onDismiss = viewModel::onDismissCleanup)
    }
}

@Composable
private fun Greeting(state: HomeUiState, mascot: MascotState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.ricordellaColors
    val today = state.now.toLocalDate()
    Column(modifier.padding(top = RicordellaDimensions.spaceS, bottom = RicordellaDimensions.spaceS)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    DateTexts.fullDate(today).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Oggi ci penso io", style = MaterialTheme.typography.headlineMedium)
                val count = state.attention.size
                val subtitle = when {
                    state.isLoading -> " "
                    count == 0 && state.today.isEmpty() -> "Tutto sotto controllo. Goditi la giornata."
                    count == 0 -> "Niente di urgente: solo le cose di oggi."
                    count == 1 -> "C'è 1 cosa che richiede attenzione."
                    else -> "Ci sono $count cose che richiedono attenzione."
                }
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CrystalBallMascot(size = 92.dp, state = mascot, overdue = state.hasOverdue)
        }
        if (!state.isLoading && !state.isEmpty) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
                modifier = Modifier.padding(top = RicordellaDimensions.spaceM),
            ) {
                CountPill(state.attention.size, "da guardare", Icons.Rounded.Bolt, colors.coral)
                CountPill(state.today.size, "oggi", Icons.Rounded.WbSunny, colors.pear)
                CountPill(state.upcoming.size, "in arrivo", Icons.AutoMirrored.Rounded.EventNote, colors.lavender, plus = state.hasMoreUpcoming)
            }
        }
    }
}

/** Contatore a pillola: il numero scorre verso l'alto quando cambia. */
@Composable
private fun CountPill(count: Int, label: String, icon: ImageVector, tone: Tone, plus: Boolean = false) {
    Surface(
        color = tone.container,
        contentColor = tone.content,
        shape = CircleShape,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$count${if (plus) " o più" else ""} $label" },
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
                    Text("È ora di aggiornare il backup", style = MaterialTheme.typography.titleMedium, color = tone.content)
                    Text(
                        when (intervalDays) {
                            1 -> "Aggiorna il backup di oggi: sovrascrivo quello precedente."
                            7 -> "È passata una settimana: aggiorno il backup sovrascrivendo il precedente."
                            30 -> "È passato un mese: aggiorno il backup sovrascrivendo il precedente."
                            365 -> "È passato un anno: aggiorno il backup sovrascrivendo il precedente."
                            else -> "Sono passati 3 mesi: aggiorno il backup sovrascrivendo il precedente."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = tone.content,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS), verticalAlignment = Alignment.CenterVertically) {
                PushButton("Aggiorna ora", onClick = onExport, modifier = Modifier.weight(1f))
                TextButton(onClick = onLater) { Text("Più tardi") }
            }
        }
    }
}

/** Una volta l'anno: proposta di eliminare i promemoria vecchi e poco utili. */
@Composable
private fun CleanupDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CleaningServices, contentDescription = null) },
        title = { Text("Pulizia di inizio anno") },
        text = {
            Text(
                "Ho trovato $count ${if (count == 1) "promemoria o evento" else "promemoria ed eventi"} degli anni precedenti " +
                    "non importanti o senza persone e cose collegate. Vuoi eliminarli per fare spazio?\n\n" +
                    "Quelli importanti e collegati a persone o cose restano.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Elimina $count", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tienili") } },
    )
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
                    Text("Le notifiche sono spente", style = MaterialTheme.typography.titleMedium, color = tone.content)
                    Text(
                        "Accendile e ti avviso io, anche con l'app chiusa.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tone.content,
                    )
                }
            }
            PushButton("Accendi le notifiche", onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
