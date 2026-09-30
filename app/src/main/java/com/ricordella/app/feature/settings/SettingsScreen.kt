package com.ricordella.app.feature.settings

import androidx.core.net.toUri
import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import com.ricordella.app.domain.usecase.Housekeeping
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ricordella.app.core.ui.BackupTutorialPages
import com.ricordella.app.core.ui.SectionTutorialPages
import com.ricordella.app.core.ui.TutorialDialog
import com.ricordella.app.core.ui.shareBackup
import com.ricordella.app.feature.onboarding.CalendarImportStep
import com.ricordella.app.feature.onboarding.CalendarImportViewModel
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.BuildConfig
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.notifications.ReminderNotifier
import com.ricordella.app.core.ui.ConfirmDialog
import com.ricordella.app.core.ui.DetailScaffold
import com.ricordella.app.core.ui.DropdownField
import com.ricordella.app.core.ui.NotifyOffsetPresets
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.TimePickerDialogFor
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.notifyOffsetLabel
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.domain.model.DateFormatStyle
import com.ricordella.app.domain.model.ThemeMode
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { c, _ ->
        SettingsViewModel(c.settingsRepository, c.backupRepository, c.restoreBackup, c.deleteAllData, c.reminderScheduler, c.housekeeping)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(0) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var pickAllDayTime by rememberSaveable { mutableStateOf(false) }
    var showSectionsTutorial by rememberSaveable { mutableStateOf(false) }
    var showCalendarImport by rememberSaveable { mutableStateOf(false) }
    // Azione di backup in attesa che l'utente chiuda il tutorial mostrato la prima volta.
    var backupTutorialFor by rememberSaveable { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::readBackup)
    }
    val startImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
    var showExportChoices by rememberSaveable { mutableStateOf(false) }
    val newFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::exportNewFile)
    }
    val startNewFile = { newFileLauncher.launch(backupFileName(newVersion = settings.backupTargetUri != null)) }
    LaunchedEffect(state.askNewFile) {
        if (state.askNewFile) {
            viewModel.onAskedNewFile()
            newFileLauncher.launch(backupFileName(newVersion = false))
        }
    }
    fun backupAction(action: String) {
        if (!settings.backupTutorialSeen) backupTutorialFor = action
        else if (action == BACKUP_EXPORT) showExportChoices = true else if (action == BACKUP_IMPORT) startImport()
    }

    LaunchedEffect(state.shareUri) {
        state.shareUri?.let { shareBackup(context, it); viewModel.onShared() }
    }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.onMessageShown() }
    }

    DetailScaffold(title = "Impostazioni", onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .contentWidth()
                .padding(horizontal = RicordellaDimensions.screenPadding),
        ) {
            if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())

            SectionHeader("Aspetto")
            Text("Tema", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
            Segmented(
                options = ThemeMode.entries,
                selected = settings.themeMode,
                label = { when (it) { ThemeMode.SYSTEM -> "Sistema"; ThemeMode.LIGHT -> "Chiaro"; ThemeMode.DARK -> "Scuro" } },
                onSelected = { mode -> viewModel.update { it.copy(themeMode = mode) } },
            )

            SectionHeader("Calendario")
            Text("Primo giorno della settimana", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
            Segmented(
                options = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY),
                selected = settings.firstDayOfWeek,
                label = DateTexts::weekdayFull,
                onSelected = { day -> viewModel.update { it.copy(firstDayOfWeek = day) } },
            )
            Text("Formato data", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
            val sample = LocalDate.of(2026, 3, 12)
            Segmented(
                options = DateFormatStyle.entries,
                selected = settings.dateFormat,
                label = { DateTexts.date(sample, it) },
                onSelected = { style -> viewModel.update { it.copy(dateFormat = style) } },
            )

            SectionHeader("Suoni")
            SettingRow(
                icon = Icons.Rounded.MusicNote,
                title = "Suoni delle scelte",
                subtitle = "Un piccolo suono quando scegli categorie e cose.",
                trailing = {
                    Switch(checked = settings.soundsEnabled, onCheckedChange = { value -> viewModel.update { it.copy(soundsEnabled = value) } })
                },
            )

            SectionHeader("Notifiche")
            SettingRow(
                icon = Icons.Rounded.Notifications,
                title = "Notifiche abilitate",
                subtitle = "Ricordella ti avvisa anche ad app chiusa.",
                trailing = {
                    Switch(checked = settings.notificationsEnabled, onCheckedChange = { value -> viewModel.update { it.copy(notificationsEnabled = value) } })
                },
            )
            NotificationPermissionRow(onChanged = viewModel::onNotificationSettingsChanged)
            ExactAlarmRow(onChanged = viewModel::onNotificationSettingsChanged)
            SettingRow(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                title = "Suono e vibrazione",
                subtitle = "Gestiti dalle impostazioni di sistema del canale \"Promemoria\".",
                onClick = { openChannelSettings(context) },
            )
            SettingRow(
                icon = Icons.Rounded.Schedule,
                title = "Orario promemoria senza ora",
                subtitle = "Notifica alle ${DateTexts.time(settings.allDayNotificationTime)}",
                onClick = { pickAllDayTime = true },
            )
            DropdownField(
                label = "Anticipo predefinito",
                options = NotifyOffsetPresets,
                selected = settings.defaultNotifyOffsetMinutes,
                optionLabel = ::notifyOffsetLabel,
                onSelected = { minutes -> viewModel.update { it.copy(defaultNotifyOffsetMinutes = minutes) } },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            SectionHeader("Dati")
            SettingRow(
                icon = Icons.Rounded.Upload,
                title = "Esporta backup",
                subtitle = "Un file .zip compresso al massimo: ogni volta sovrascrive il precedente, a meno che tu non chieda una nuova versione.",
                onClick = { backupAction(BACKUP_EXPORT) },
            )
            DropdownField(
                label = "Ricordami di aggiornare il backup",
                options = Housekeeping.BACKUP_INTERVALS,
                selected = settings.backupIntervalDays,
                optionLabel = Housekeeping::intervalLabel,
                onSelected = { days -> viewModel.update { it.copy(backupIntervalDays = days) } },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            SettingRow(
                icon = Icons.Rounded.Download,
                title = "Importa backup",
                subtitle = "Ripristina un backup sostituendo i dati attuali.",
                onClick = { backupAction(BACKUP_IMPORT) },
            )
            SettingRow(
                icon = Icons.AutoMirrored.Rounded.HelpOutline,
                title = "Come funziona il backup",
                subtitle = "Tutorial animato di esporta e importa.",
                onClick = { backupTutorialFor = BACKUP_TUTORIAL_ONLY },
            )
            SettingRow(
                icon = Icons.Rounded.CalendarMonth,
                title = "Importa da Google Calendar",
                subtitle = "Copia gli eventi di un account Google presente sul telefono.",
                onClick = { showCalendarImport = true },
            )
            SettingRow(
                icon = Icons.Rounded.DeleteForever,
                title = "Elimina tutti i dati",
                subtitle = "Cancella persone, cose, promemoria e storico da questo dispositivo.",
                onClick = { confirmDelete = 1 },
            )

            SectionHeader("Informazioni")
            SettingRow(
                icon = Icons.Rounded.School,
                title = "Rivedi il tutorial",
                subtitle = "Come funzionano le sezioni dell'app.",
                onClick = { showSectionsTutorial = true },
            )
            SettingRow(icon = Icons.Rounded.Info, title = "Versione", subtitle = BuildConfig.VERSION_NAME)
            SettingRow(
                icon = Icons.Rounded.PrivacyTip,
                title = "Privacy",
                subtitle = "I tuoi dati restano sul dispositivo.",
                onClick = { showPrivacy = true },
            )
        }
    }

    state.restoreSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Ripristinare il backup?") },
            text = {
                Text(
                    "Backup del ${summary.createdAt.take(10)}\n\n" +
                        "• ${summary.people} persone\n• ${summary.items} cose\n• ${summary.reminders} promemoria\n" +
                        "• ${summary.maintenance} manutenzioni\n• ${summary.files} file allegati\n\n" +
                        "Attenzione: tutti i dati attuali verranno sostituiti da quelli del backup.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) {
                    Text("Ripristina sostituendo i dati", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("Annulla") } },
        )
    }

    when (confirmDelete) {
        1 -> ConfirmDialog(
            title = "Eliminare tutti i dati?",
            message = "Verranno eliminati definitivamente persone, cose, promemoria, manutenzioni e storico. " +
                "Se vuoi conservarli, esporta prima un backup.",
            confirmLabel = "Continua",
            destructive = true,
            onConfirm = { confirmDelete = 2 },
            onDismiss = { if (confirmDelete == 1) confirmDelete = 0 },
        )
        2 -> ConfirmDialog(
            title = "Sei sicuro?",
            message = "Questa operazione non si può annullare.",
            confirmLabel = "Elimina tutto",
            destructive = true,
            onConfirm = viewModel::deleteEverything,
            onDismiss = { confirmDelete = 0 },
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("Privacy") },
            text = {
                Text(
                    "Ricordella funziona completamente offline e non richiede alcun account. " +
                        "Tutti i dati restano sul tuo dispositivo: l'app non ha accesso a Internet, " +
                        "non usa servizi cloud né statistiche di utilizzo.\n\n" +
                        "I dati sono esclusi dal backup automatico di Android. " +
                        "Per trasferirli usa \"Esporta backup\": il file resta sotto il tuo controllo.",
                )
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("OK") } },
        )
    }

    backupTutorialFor?.let { action ->
        TutorialDialog(BackupTutorialPages, onDismiss = {
            backupTutorialFor = null
            if (!settings.backupTutorialSeen) viewModel.update { it.copy(backupTutorialSeen = true) }
            when (action) {
                BACKUP_EXPORT -> showExportChoices = true
                BACKUP_IMPORT -> startImport()
            }
        }, doneLabel = when (action) { BACKUP_EXPORT -> "Esporta"; BACKUP_IMPORT -> "Scegli il file"; else -> "Ho capito" })
    }

    if (showExportChoices) {
        AlertDialog(
            onDismissRequest = { showExportChoices = false },
            title = { Text("Esporta backup") },
            text = {
                Column {
                    SettingRow(
                        icon = Icons.Rounded.Save,
                        title = if (settings.backupTargetUri != null) "Aggiorna il backup" else "Salva il backup",
                        subtitle = if (settings.backupTargetUri != null) "Sovrascrive il file precedente." else "Scegli dove salvarlo (es. Drive): le prossime volte verrà sovrascritto.",
                        onClick = { showExportChoices = false; viewModel.exportOverwrite() },
                    )
                    if (settings.backupTargetUri != null) {
                        SettingRow(
                            icon = Icons.Rounded.AddCircleOutline,
                            title = "Nuova versione",
                            subtitle = "Crea un nuovo file e tiene quello vecchio.",
                            onClick = { showExportChoices = false; startNewFile() },
                        )
                    }
                    SettingRow(
                        icon = Icons.Rounded.Share,
                        title = "Condividi",
                        subtitle = "Invialo con un'app (email, chat...).",
                        onClick = { showExportChoices = false; viewModel.export() },
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showExportChoices = false }) { Text("Annulla") } },
        )
    }

    if (showSectionsTutorial) TutorialDialog(SectionTutorialPages, onDismiss = { showSectionsTutorial = false })

    if (showCalendarImport) {
        val importViewModel = appViewModel { c, _ -> CalendarImportViewModel(c.calendarImporter, c.settingsRepository, c.housekeeping) }
        Dialog(
            onDismissRequest = { showCalendarImport = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                CalendarImportStep(importViewModel, onDone = { showCalendarImport = false }, doneLabel = "Chiudi")
            }
        }
    }

    if (pickAllDayTime) {
        TimePickerDialogFor(
            initial = settings.allDayNotificationTime,
            onDismiss = { pickAllDayTime = false },
            onConfirm = { time ->
                pickAllDayTime = false
                viewModel.update { it.copy(allDayNotificationTime = time) }
            },
        )
    }
}

/** Nome del file: fisso per il backup "principale", con data e ora per le nuove versioni. */
private fun backupFileName(newVersion: Boolean): String =
    if (newVersion) "ricordella-backup-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))}.zip"
    else "ricordella-backup.zip"

private const val BACKUP_EXPORT = "export"
private const val BACKUP_IMPORT = "import"
private const val BACKUP_TUTORIAL_ONLY = "tutorial"

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label(option), maxLines = 1) }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = trailing,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun NotificationPermissionRow(onChanged: () -> Unit) {
    val context = LocalContext.current
    fun granted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var isGranted by remember { mutableStateOf(granted()) }
    LifecycleResumeEffect(Unit) {
        isGranted = granted()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted = it
        if (!it) openAppNotificationSettings(context)
        onChanged()
    }
    SettingRow(
        icon = Icons.Rounded.Notifications,
        title = "Permesso di notifica",
        subtitle = if (isGranted) "Concesso" else "Non concesso: tocca per attivarlo",
        onClick = if (isGranted) null else ({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }),
    )
}

@Composable
private fun ExactAlarmRow(onChanged: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val context = LocalContext.current
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    var allowed by remember { mutableStateOf(alarmManager.canScheduleExactAlarms()) }
    LifecycleResumeEffect(Unit) {
        val now = alarmManager.canScheduleExactAlarms()
        if (now != allowed) onChanged()
        allowed = now
        onPauseOrDispose { }
    }
    SettingRow(
        icon = Icons.Rounded.AlarmOn,
        title = "Promemoria puntuali",
        subtitle = if (allowed) "Le notifiche con orario arrivano al minuto esatto."
        else "Non consentito: le notifiche possono arrivare con qualche minuto di ritardo. Tocca per consentire.",
        onClick = {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}

private fun openChannelSettings(context: Context) {
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderNotifier.CHANNEL_REMINDERS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

