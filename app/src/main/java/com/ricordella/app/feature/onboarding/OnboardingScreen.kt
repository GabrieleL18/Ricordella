package com.ricordella.app.feature.onboarding

import androidx.compose.foundation.background
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import com.ricordella.app.feature.settings.OpenedBackupImport
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ricordella.app.core.ui.CrystalBallMascot
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.RicordellaMotion
import com.ricordella.app.core.ui.SectionTutorialPages
import com.ricordella.app.core.ui.TutorialPager
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.MagicTitle
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import androidx.compose.ui.unit.sp
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.data.calendar.CalendarImporter
import com.ricordella.app.domain.repository.SettingsRepository
import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.feature.settings.AutomationSettings
import com.ricordella.app.domain.usecase.Housekeeping
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarImportState(
    val permissionDenied: Boolean = false,
    val accounts: List<CalendarImporter.Account>? = null,
    val selected: CalendarImporter.Account? = null,
    val importing: Boolean = false,
    val imported: Int? = null,
    val error: Boolean = false,
)

class CalendarImportViewModel(
    private val importer: CalendarImporter,
    private val settings: SettingsRepository,
    private val housekeeping: Housekeeping,
) : ViewModel() {
    private val _state = MutableStateFlow(CalendarImportState())
    val state = _state.asStateFlow()

    fun onPermission(granted: Boolean) {
        if (!granted) {
            _state.update { it.copy(permissionDenied = true) }
            return
        }
        viewModelScope.launch {
            val accounts = runCatching { importer.accounts() }.getOrDefault(emptyList())
            _state.update { it.copy(permissionDenied = false, accounts = accounts, selected = accounts.firstOrNull()) }
        }
    }

    fun select(account: CalendarImporter.Account) = _state.update { it.copy(selected = account) }

    fun import() {
        val account = _state.value.selected ?: return
        _state.update { it.copy(importing = true, error = false) }
        viewModelScope.launch {
            val count = runCatching { importer.import(account) }.getOrNull()
            _state.update { it.copy(importing = false, imported = count, error = count == null) }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    /** Il file scelto diventa quello che il backup automatico sovrascrive. */
    fun saveBackupTo(uri: Uri) {
        viewModelScope.launch { runCatching { housekeeping.exportToNewFile(uri) } }
    }

    /** Chiude la configurazione iniziale e fa partire il conteggio del backup. */
    fun finishOnboarding() {
        viewModelScope.launch {
            housekeeping.startBackupClockIfNeeded()
            settings.update { it.copy(onboardingDone = true, newsSeenVersion = com.ricordella.app.BuildConfig.VERSION_CODE) }
        }
    }
}

private enum class Stage { WELCOME, TUTORIAL, PREFERENCES, CALENDAR }

/** Configurazione iniziale: benvenuto, tutorial animato delle sezioni, import da un calendario. */
@Composable
fun OnboardingScreen() {
    val viewModel = appViewModel { c, _ -> CalendarImportViewModel(c.calendarImporter, c.settingsRepository, c.housekeeping) }
    var stage by rememberSaveable { mutableIntStateOf(0) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = Stage.entries[stage],
            transitionSpec = {
                (slideInHorizontally(tween(RicordellaMotion.LONG, easing = RicordellaMotion.EaseOut)) { it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally(tween(RicordellaMotion.SHORT, easing = RicordellaMotion.EaseIn)) { -it / 3 } + fadeOut())
            },
            label = "onboarding",
        ) { current ->
            when (current) {
                Stage.WELCOME -> Welcome(onNext = { stage = 1 })
                Stage.TUTORIAL -> TutorialPager(SectionTutorialPages, onDone = { stage = 2 }, doneLabel = tr("Avanti"))
                Stage.PREFERENCES -> PreferencesStep(onUpdate = viewModel::updateSettings, onBackupFile = viewModel::saveBackupTo, onNext = { stage = 3 })
                Stage.CALENDAR -> CalendarImportStep(viewModel, onDone = viewModel::finishOnboarding)
            }
        }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    var backup by rememberSaveable { mutableStateOf<Uri?>(null) }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { backup = it } }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(RicordellaDimensions.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CrystalBallMascot(size = 160.dp)
        Spacer(Modifier.height(RicordellaDimensions.spaceXl))
        Text(tr("Ciao, sono"), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        MagicTitle(fontSize = 44.sp)
        Spacer(Modifier.height(RicordellaDimensions.spaceS))
        Text(
            tr("Ricordo al posto tuo scadenze, eventi, visite, manutenzioni e compleanni. Ti faccio fare un giro veloce?"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(RicordellaDimensions.spaceXl))
        PushButton(tr("Parti da zero"), onClick = onNext, icon = Icons.AutoMirrored.Rounded.ArrowForward)
        Spacer(Modifier.height(RicordellaDimensions.spaceS))
        TextButton(onClick = { pickBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
            Text(tr("Ho già un backup: ripristinalo"))
        }
    }
    // Se il ripristino va a buon fine le impostazioni del backup chiudono da sole la configurazione iniziale.
    backup?.let { OpenedBackupImport(it, onDone = { backup = null }, firstStart = true) }
}

/** Import facoltativo: permesso calendario → scelta calendario → import. */
@Composable
fun CalendarImportStep(viewModel: CalendarImportViewModel, onDone: () -> Unit, doneLabel: String = tr("Inizia a usare Remindella")) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), viewModel::onPermission)
    val tone = MaterialTheme.ricordellaColors.lavender
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .contentWidth()
            .padding(RicordellaDimensions.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
    ) {
        Spacer(Modifier.height(RicordellaDimensions.spaceXl))
        Surface(color = tone.container, shape = MaterialTheme.shapes.extraLarge) {
            androidx.compose.material3.Icon(Icons.Rounded.CalendarMonth, null, tint = tone.content, modifier = Modifier.padding(24.dp).height(56.dp).fillMaxWidth(0.3f))
        }
        Text(tr("Importa da un calendario"), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            tr("Posso copiare eventi, feste, compleanni e promemoria dei prossimi 12 mesi da un calendario già presente sul telefono: Google, Samsung Calendar o altri. ") +
                tr("Niente Internet: leggo solo il calendario del dispositivo."),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        val accounts = state.accounts
        when {
            state.imported != null -> {
                Text(
                    if (state.imported == 0) tr("Nessun evento da importare.") else trf("Fatto! Ho importato %1\$s eventi.", state.imported),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.ricordellaColors.mint.content,
                )
                PushButton(doneLabel, onClick = onDone, icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth())
                return@Column
            }
            state.importing -> CircularProgressIndicator()
            accounts == null -> {
                if (state.permissionDenied) {
                    Text(tr("Senza il permesso al calendario non posso importare. Puoi farlo più tardi dalle Impostazioni."), textAlign = TextAlign.Center)
                }
                PushButton(
                    tr("Scegli l'account"),
                    onClick = { permission.launch(Manifest.permission.READ_CALENDAR) },
                    icon = Icons.Rounded.CalendarMonth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            accounts.isEmpty() -> Text(tr("Sul telefono non ci sono calendari da importare."), textAlign = TextAlign.Center)
            else -> {
                accounts.forEach { account ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = account == state.selected, role = Role.RadioButton) { viewModel.select(account) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = account == state.selected, onClick = null)
                        Text(account.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (state.error) Text(tr("Import non riuscito, riprova."), color = MaterialTheme.colorScheme.error)
                PushButton(tr("Importa eventi"), onClick = viewModel::import, icon = Icons.Rounded.Download, modifier = Modifier.fillMaxWidth())
            }
        }
        TextButton(onClick = onDone, enabled = !state.importing) { Text(tr("Salta per ora")) }
    }
}

/** Scelte sulle operazioni periodiche (feste, pulizia annuale, backup), modificabili poi nelle Impostazioni. */
@Composable
private fun PreferencesStep(onUpdate: ((AppSettings) -> AppSettings) -> Unit, onBackupFile: (Uri) -> Unit, onNext: () -> Unit) {
    val chooseBackupFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(onBackupFile) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .contentWidth()
            .padding(RicordellaDimensions.spaceXl),
        verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceL),
    ) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.ricordellaColors.pear.container, MaterialTheme.shapes.large).padding(RicordellaDimensions.spaceL),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(tr("Il mio consiglio"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.ricordellaColors.pear.content)
            Text(
                tr("Salva il backup su Drive (o online): se cambi o perdi il telefono ritrovi tutto. Lo fai subito qui sotto, oppure dopo in Impostazioni › Dati e backup."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.ricordellaColors.pear.content,
            )
        }
        Text(tr("Come preferisci?"), style = MaterialTheme.typography.headlineMedium)
        Text(
            tr("Alcune cose le faccio una volta l'anno o ogni tanto. Scegli se farle da sola, chiedertelo prima o mai. Tocca ? per vedere cosa succede. Potrai cambiare idea nelle Impostazioni."),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AutomationSettings(LocalAppSettings.current, onUpdate, includeBackup = true)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Dove salvo il backup"), style = MaterialTheme.typography.titleMedium)
            Text(
                if (LocalAppSettings.current.backupTargetUri != null) tr("Nel file che hai scelto: lo aggiorno da sola.")
                else tr("In Download/Remindella sul telefono. Per non perderlo se cambi telefono, sceglilo su Drive."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { chooseBackupFile.launch("remindella-backup.zip") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(RicordellaDimensions.spaceS))
                Text(tr("Scegli dove salvarlo (es. Drive)"))
            }
        }
        PushButton(tr("Avanti"), onClick = onNext, icon = Icons.AutoMirrored.Rounded.ArrowForward, modifier = Modifier.fillMaxWidth())
    }
}
