package com.ricordella.app.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import com.ricordella.app.core.ui.RicordellaMotion
import com.ricordella.app.core.ui.rememberReducedMotion
import kotlinx.coroutines.delay
import com.ricordella.app.feature.legal.TermsScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import com.ricordella.app.core.ui.theme.ricordellaColors
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.ui.UiSound
import com.ricordella.app.core.ui.UiSoundPlayer
import com.ricordella.app.core.i18n.AppLanguage
import com.ricordella.app.core.i18n.Lang
import com.ricordella.app.core.widget.CalendarWidgetProvider
import com.ricordella.app.core.i18n.trf

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
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
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
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lightbulb
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
import com.ricordella.app.core.ui.TimePickerDialogFor
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.notifyOffsetLabel
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.domain.model.DateFormatStyle
import com.ricordella.app.domain.model.ThemeMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenResolutions: (year: Int) -> Unit, onGoHome: () -> Unit = onBack) {
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
    var pickBackupTime by rememberSaveable { mutableStateOf(false) }
    var showSectionsTutorial by rememberSaveable { mutableStateOf(false) }
    var showNews by rememberSaveable { mutableStateOf(false) }
    var showCalendarImport by rememberSaveable { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::readBackup)
    }
    val startImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
    var showExportChoices by rememberSaveable { mutableStateOf(false) }
    var justUnlocked by remember { mutableStateOf(false) }
    var showImportChoices by rememberSaveable { mutableStateOf(false) }
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
    // Tutorial e scelte stanno nella stessa finestra.
    fun backupAction(action: String) {
        if (action == BACKUP_EXPORT) showExportChoices = true else showImportChoices = true
    }

    LaunchedEffect(state.shareUri) {
        state.shareUri?.let { shareBackup(context, it); viewModel.onShared() }
    }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.onMessageShown() }
    }

    DetailScaffold(title = tr("Impostazioni"), onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .contentWidth()
                .padding(horizontal = RicordellaDimensions.screenPadding),
        ) {
            if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())

            val year = LocalDate.now().year
            val resolutions = settings.resolutions.filter { it.year == year }
            SettingRow(
                icon = Icons.Rounded.AutoAwesome,
                title = tr("Buoni propositi"),
                subtitle = if (resolutions.isEmpty()) trf("Scrivi i propositi per il %1\$s", year)
                else trf("%1\$s: rispettati %2\$s su %3\$s", year, resolutions.count { it.kept }, resolutions.size),
                onClick = { onOpenResolutions(year) },
            )

            SettingsGroup(Icons.Rounded.Palette, tr("Aspetto"), tr("Tema, lingua, calendario e suoni")) {
                Text(tr("Tema"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                Segmented(
                    options = ThemeMode.entries,
                    selected = settings.themeMode,
                    label = { when (it) { ThemeMode.SYSTEM -> tr("Sistema"); ThemeMode.LIGHT -> tr("Chiaro"); ThemeMode.DARK -> tr("Scuro") } },
                    onSelected = { mode -> viewModel.update { it.copy(themeMode = mode) } },
                )
                Text(tr("Lingua"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                // Di default segue il sistema; cambiandola l'app si ridisegna subito nella nuova lingua.
                var language by remember { mutableStateOf(Lang.current(context)) }
                LanguagePicker(
                    selected = language,
                    onSelected = { chosen ->
                        language = chosen
                        Lang.set(context, chosen)
                        CalendarWidgetProvider.requestUpdate(context)
                        (context as? android.app.Activity)?.recreate()
                    },
                )
                Text(tr("Primo giorno della settimana"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                Segmented(
                    options = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY),
                    selected = settings.firstDayOfWeek,
                    label = DateTexts::weekdayFull,
                    onSelected = { day -> viewModel.update { it.copy(firstDayOfWeek = day) } },
                )
                Text(tr("Formato data"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                val sample = LocalDate.of(2026, 3, 12)
                Segmented(
                    options = DateFormatStyle.entries,
                    selected = settings.dateFormat,
                    label = { DateTexts.date(sample, it) },
                    onSelected = { style -> viewModel.update { it.copy(dateFormat = style) } },
                )
                SettingRow(
                    icon = Icons.Rounded.MusicNote,
                    title = tr("Suoni delle scelte"),
                    subtitle = tr("Un piccolo suono quando scegli categorie e cose. Tocca per provarlo."),
                    onClick = { UiSoundPlayer.play(UiSound.DING) },
                    trailing = {
                        Switch(checked = settings.soundsEnabled, onCheckedChange = { value -> viewModel.update { it.copy(soundsEnabled = value) } })
                    },
                )
            }

            SettingsGroup(Icons.Rounded.Notifications, tr("Notifiche"), tr("Permessi, orari, anticipo e predefiniti per tipo")) {
                SettingRow(
                    icon = Icons.Rounded.Notifications,
                    title = tr("Notifiche abilitate"),
                    subtitle = tr("Remindella ti avvisa anche ad app chiusa."),
                    trailing = {
                        Switch(checked = settings.notificationsEnabled, onCheckedChange = { value -> viewModel.update { it.copy(notificationsEnabled = value) } })
                    },
                )
                NotificationPermissionRow(onChanged = viewModel::onNotificationSettingsChanged)
                ExactAlarmRow(onChanged = viewModel::onNotificationSettingsChanged)
                SettingRow(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    title = tr("Suono e vibrazione"),
                    subtitle = tr("Gestiti dalle impostazioni di sistema del canale \"Promemoria\"."),
                    onClick = { openChannelSettings(context) },
                )
                SettingRow(
                    icon = Icons.Rounded.Schedule,
                    title = tr("Orario promemoria senza ora"),
                    subtitle = trf("Notifica alle %1\$s", DateTexts.time(settings.allDayNotificationTime)),
                    onClick = { pickAllDayTime = true },
                )
                DropdownField(
                    label = tr("Anticipo predefinito"),
                    options = NotifyOffsetPresets,
                    selected = settings.defaultNotifyOffsetMinutes,
                    optionLabel = ::notifyOffsetLabel,
                    onSelected = { minutes -> viewModel.update { it.copy(defaultNotifyOffsetMinutes = minutes) } },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                TypeDefaultsSection(settings, viewModel::update)
            }

            SettingsGroup(Icons.Rounded.Alarm, tr("Sveglia"), tr("Suono, vibrazione, volume e posticipo")) {
                AlarmSettingsSection(settings, viewModel::update)
            }

            SettingsGroup(Icons.Rounded.Storage, tr("Dati e backup"), tr("Backup, importazione e operazioni di inizio anno")) {
                SettingRow(
                    icon = Icons.Rounded.Upload,
                    title = tr("Esporta backup"),
                    subtitle = tr("Un file .zip compresso al massimo: ogni volta sovrascrive il precedente, a meno che tu non chieda una nuova versione."),
                    onClick = { backupAction(BACKUP_EXPORT) },
                )
                SettingRow(
                    icon = Icons.Rounded.Download,
                    title = tr("Importa backup"),
                    subtitle = tr("Ripristina un backup sostituendo i dati attuali."),
                    onClick = { backupAction(BACKUP_IMPORT) },
                )
                state.reserveSavedAt?.let { millis ->
                    val at = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault())
                    SettingRow(
                        icon = Icons.Rounded.Shield,
                        title = tr("Copia di riserva"),
                        subtitle = trf("Dell'ultimo backup, sul telefono: %1\$s. Usala se il file di backup si rovina.", DateTexts.relativeWithTime(at.toLocalDate(), at.toLocalTime().withSecond(0).withNano(0), LocalDate.now())),
                        onClick = viewModel::readReserve,
                    )
                }
                SettingRow(
                    icon = Icons.Rounded.Schedule,
                    title = tr("Backup automatico"),
                    subtitle = settings.autoBackupTime?.let {
                        if (settings.backupEveryHours > 0) trf("%1\$s, da sola: nel file scelto o in Download/Remindella.", Housekeeping.frequencyLabel(settings))
                        else trf("%1\$s alle %2\$s, da sola: nel file scelto o in Download/Remindella.", Housekeeping.intervalLabel(settings.backupIntervalDays), DateTexts.time(it))
                    } ?: tr("Spento: tocca per scegliere l'orario."),
                    onClick = { pickBackupTime = true },
                    trailing = {
                        Switch(
                            checked = settings.autoBackupTime != null,
                            onCheckedChange = { on -> if (on) pickBackupTime = true else viewModel.update { it.copy(autoBackupTime = null) } },
                        )
                    },
                )
                SettingRow(
                    icon = Icons.Rounded.CalendarMonth,
                    title = tr("Importa da Google Calendar"),
                    subtitle = tr("Copia gli eventi di un account Google presente sul telefono."),
                    onClick = { showCalendarImport = true },
                )
                SettingRow(
                    icon = Icons.Rounded.DeleteForever,
                    title = tr("Elimina tutti i dati"),
                    subtitle = tr("Cancella persone, cose, promemoria e storico da questo dispositivo."),
                    onClick = { confirmDelete = 1 },
                )
                Text(tr("Operazioni periodiche"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
                AutomationSettings(settings, viewModel::update, includeBackup = true)
            }

            SharingSection(settings, (LocalContext.current.applicationContext as com.ricordella.app.RicordellaApplication).container.sharedSpace)

            SupportCard(loved = settings.supportLoved, onLoved = { viewModel.update { it.copy(supportLoved = true) } })

            SettingsGroup(Icons.Rounded.Info, tr("Informazioni"), tr("Tutorial, privacy, assistenza e versione")) {
                SettingRow(
                    icon = Icons.Rounded.NewReleases,
                    title = tr("Novità"),
                    subtitle = trf("Cosa c'è di nuovo nella versione %1\$s", com.ricordella.app.BuildConfig.VERSION_NAME),
                    onClick = { showNews = true },
                )
                SettingRow(
                    icon = Icons.Rounded.School,
                    title = tr("Rivedi il tutorial"),
                    subtitle = tr("Come funzionano le sezioni dell'app."),
                    onClick = { showSectionsTutorial = true },
                )
                SettingRow(
                    icon = Icons.Rounded.Lightbulb,
                    title = tr("Mostra i tutorial"),
                    subtitle = tr("Le spiegazioni che compaiono da sole la prima volta. Spente, le trovi sempre nel \"?\"."),
                    onClick = { viewModel.update { it.copy(tutorialsEnabled = !it.tutorialsEnabled) } },
                    trailing = {
                        Switch(checked = settings.tutorialsEnabled, onCheckedChange = { value -> viewModel.update { it.copy(tutorialsEnabled = value) } })
                    },
                )
                SettingRow(
                    icon = Icons.Rounded.Lightbulb,
                    title = tr("Crediti"),
                    subtitle = tr("Da un'idea Lanni Labs"),
                )
                VersionRow(settings.developerMode, onUnlock = { justUnlocked = true; viewModel.update { it.copy(developerMode = true) } })
                SettingRow(
                    icon = Icons.Rounded.PrivacyTip,
                    title = tr("Termini d'uso e privacy"),
                    subtitle = tr("Cosa fa l'app, i suoi limiti e i tuoi dati."),
                    onClick = { showPrivacy = true },
                )
                SettingRow(
                    icon = Icons.Rounded.Email,
                    title = tr("Assistenza"),
                    subtitle = SUPPORT_EMAIL,
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:$SUPPORT_EMAIL?subject=Remindella".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                )
            }
            if (settings.developerMode) {
                val tools = (LocalContext.current.applicationContext as com.ricordella.app.RicordellaApplication).container.developerTools
                DeveloperSection(tools, viewModel::update, justUnlocked, onGoHome = onGoHome)
            }
        }
    }

    if (state.offerReserve) {
        ConfirmDialog(
            title = tr("Questo backup è rovinato"),
            message = tr("Non riesco a leggerlo. C'è però la copia di riserva che tengo sul telefono: vuoi usare quella?"),
            confirmLabel = tr("Usa la copia di riserva"),
            onConfirm = viewModel::readReserve,
            onDismiss = viewModel::onReserveOfferDismissed,
        )
    }
    state.restoreSummary?.let { summary ->
        RestoreDialog(summary, onConfirm = viewModel::confirmRestore, onCancel = viewModel::cancelRestore)
    }

    when (confirmDelete) {
        1 -> ConfirmDialog(
            title = tr("Eliminare tutti i dati?"),
            message = tr("Verranno eliminati definitivamente persone, cose, promemoria, manutenzioni e storico. ") +
                tr("Se vuoi conservarli, esporta prima un backup."),
            confirmLabel = tr("Continua"),
            destructive = true,
            onConfirm = { confirmDelete = 2 },
            onDismiss = { if (confirmDelete == 1) confirmDelete = 0 },
        )
        2 -> ConfirmDialog(
            title = tr("Sei sicuro?"),
            message = tr("Questa operazione non si può annullare."),
            confirmLabel = tr("Elimina tutto"),
            destructive = true,
            onConfirm = viewModel::deleteEverything,
            onDismiss = { confirmDelete = 0 },
        )
    }

    if (showPrivacy) {
        Dialog(
            onDismissRequest = { showPrivacy = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            TermsScreen(onAccept = null, onDecline = { showPrivacy = false })
        }
    }

    if (showExportChoices || showImportChoices) {
        BackupDialog(
            export = showExportChoices,
            hasTarget = settings.backupTargetUri != null,
            onDismiss = { showExportChoices = false; showImportChoices = false },
            onOverwrite = viewModel::exportOverwrite,
            onNewVersion = startNewFile,
            onShare = viewModel::export,
            onChooseFile = startImport,
        )
    }

    if (showSectionsTutorial) TutorialDialog(SectionTutorialPages, onDismiss = { showSectionsTutorial = false })
    if (showNews) com.ricordella.app.feature.news.NewsDialog(com.ricordella.app.feature.news.AllNews, onDismiss = { showNews = false })

    if (showCalendarImport) {
        val importViewModel = appViewModel { c, _ -> CalendarImportViewModel(c.calendarImporter, c.settingsRepository, c.housekeeping) }
        Dialog(
            onDismissRequest = { showCalendarImport = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                CalendarImportStep(importViewModel, onDone = { showCalendarImport = false }, doneLabel = tr("Chiudi"))
            }
        }
    }

    if (pickBackupTime) {
        TimePickerDialogFor(
            initial = settings.autoBackupTime ?: LocalTime.of(22, 0),
            onDismiss = { pickBackupTime = false },
            onConfirm = { time ->
                pickBackupTime = false
                viewModel.update { it.copy(autoBackupTime = time) }
            },
        )
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
    if (newVersion) "remindella-backup-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))}.zip"
    else "remindella-backup.zip"

private const val SUPPORT_EMAIL = "lannilab.support@gmail.com"
private const val BACKUP_EXPORT = "export"
private const val BACKUP_IMPORT = "import"

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

/**
 * Gruppo di impostazioni chiuso di default: la schermata resta corta e si apre solo ciò che serve.
 * Aprendosi diventa una scheda: lo sfondo sale dietro al titolo, la freccia gira, il contenuto
 * scende dall'alto e la schermata scorre quanto basta per mostrarlo.
 */
@Composable
private fun SettingsGroup(icon: ImageVector, title: String, subtitle: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val reduced = rememberReducedMotion()
    val long = if (reduced) 0 else RicordellaMotion.LONG
    val short = if (reduced) 0 else RicordellaMotion.SHORT
    val arrow by animateFloatAsState(if (open) 180f else 0f, tween(long, easing = RicordellaMotion.EaseOut), label = "groupArrow")
    val card by animateColorAsState(
        if (open) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0f),
        tween(short),
        label = "groupCard",
    )
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(open) {
        if (open) {
            delay(long.toLong())
            requester.bringIntoView()
        }
    }
    Column(
        Modifier
            .padding(vertical = 2.dp)
            .bringIntoViewRequester(requester)
            .clip(MaterialTheme.shapes.large)
            .background(card),
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            leadingContent = { Icon(icon, contentDescription = null) },
            trailingContent = { Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.rotate(arrow)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { open = !open },
        )
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(long, easing = RicordellaMotion.EaseOut), expandFrom = Alignment.Top) +
                fadeIn(tween(short, delayMillis = if (reduced) 0 else RicordellaMotion.MICRO)) +
                slideInVertically(tween(long, easing = RicordellaMotion.EaseOut)) { -it / 10 },
            exit = shrinkVertically(tween(short, easing = RicordellaMotion.EaseInOut), shrinkTowards = Alignment.Top) +
                fadeOut(tween(if (reduced) 0 else RicordellaMotion.MICRO)),
        ) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) { content() }
        }
    }
}

@Composable
internal fun SettingRow(
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
        title = tr("Permesso di notifica"),
        subtitle = if (isGranted) tr("Concesso") else tr("Non concesso: tocca per attivarlo"),
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
        title = tr("Promemoria puntuali"),
        subtitle = if (allowed) tr("Le notifiche con orario arrivano al minuto esatto.")
        else tr("Non consentito: le notifiche possono arrivare con qualche minuto di ritardo. Tocca per consentire."),
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

/** Lingue come pastiglie con la bandiera: quella scelta è gialla, come le altre selezioni dell'app. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LanguagePicker(selected: AppLanguage, onSelected: (AppLanguage) -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        AppLanguage.entries.forEach { language ->
            val isSelected = language == selected
            Row(
                Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (isSelected) colors.bolt else MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(1.dp, if (isSelected) colors.bolt else MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape)
                    .clickable { onSelected(language) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(language.flag, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (language == AppLanguage.SYSTEM) tr("Sistema") else language.nativeName,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) colors.onBolt else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

