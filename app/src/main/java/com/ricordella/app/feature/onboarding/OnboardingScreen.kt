package com.ricordella.app.feature.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.data.calendar.CalendarImporter
import com.ricordella.app.domain.repository.SettingsRepository
import com.ricordella.app.domain.usecase.Housekeeping
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarImportState(
    val permissionDenied: Boolean = false,
    val accounts: List<String>? = null,
    val selected: String? = null,
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
            val accounts = runCatching { importer.googleAccounts() }.getOrDefault(emptyList())
            _state.update { it.copy(permissionDenied = false, accounts = accounts, selected = accounts.firstOrNull()) }
        }
    }

    fun select(account: String) = _state.update { it.copy(selected = account) }

    fun import() {
        val account = _state.value.selected ?: return
        _state.update { it.copy(importing = true, error = false) }
        viewModelScope.launch {
            val count = runCatching { importer.import(account) }.getOrNull()
            _state.update { it.copy(importing = false, imported = count, error = count == null) }
        }
    }

    /** Chiude la configurazione iniziale e fa partire il conteggio del backup trimestrale. */
    fun finishOnboarding() {
        viewModelScope.launch {
            housekeeping.startBackupClockIfNeeded()
            settings.update { it.copy(onboardingDone = true) }
        }
    }
}

private enum class Stage { WELCOME, TUTORIAL, CALENDAR }

/** Configurazione iniziale: benvenuto, tutorial animato delle sezioni, import da Google Calendar. */
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
                Stage.TUTORIAL -> TutorialPager(SectionTutorialPages, onDone = { stage = 2 }, doneLabel = "Avanti")
                Stage.CALENDAR -> CalendarImportStep(viewModel, onDone = viewModel::finishOnboarding)
            }
        }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(RicordellaDimensions.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CrystalBallMascot(size = 160.dp)
        Spacer(Modifier.height(RicordellaDimensions.spaceXl))
        Text("Ciao, sono Ricordella", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(RicordellaDimensions.spaceS))
        Text(
            "Ricordo al posto tuo scadenze, eventi, visite, manutenzioni e compleanni. Ti faccio fare un giro veloce?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(RicordellaDimensions.spaceXl))
        PushButton("Iniziamo", onClick = onNext, icon = Icons.AutoMirrored.Rounded.ArrowForward)
    }
}

/** Import facoltativo: permesso calendario → scelta account Google → import. */
@Composable
fun CalendarImportStep(viewModel: CalendarImportViewModel, onDone: () -> Unit, doneLabel: String = "Inizia a usare Ricordella") {
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
        Text("Importa da Google Calendar", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            "Posso copiare eventi, feste, compleanni e promemoria dei prossimi 12 mesi di un tuo account Google già presente sul telefono. " +
                "Niente Internet: leggo solo il calendario del dispositivo.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        val accounts = state.accounts
        when {
            state.imported != null -> {
                Text(
                    if (state.imported == 0) "Nessun evento da importare." else "Fatto! Ho importato ${state.imported} eventi.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.ricordellaColors.mint.content,
                )
                PushButton(doneLabel, onClick = onDone, icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth())
                return@Column
            }
            state.importing -> CircularProgressIndicator()
            accounts == null -> {
                if (state.permissionDenied) {
                    Text("Senza il permesso al calendario non posso importare. Puoi farlo più tardi dalle Impostazioni.", textAlign = TextAlign.Center)
                }
                PushButton(
                    "Scegli l'account",
                    onClick = { permission.launch(Manifest.permission.READ_CALENDAR) },
                    icon = Icons.Rounded.CalendarMonth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            accounts.isEmpty() -> Text("Sul telefono non ci sono account Google con un calendario.", textAlign = TextAlign.Center)
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
                        Text(account, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (state.error) Text("Import non riuscito, riprova.", color = MaterialTheme.colorScheme.error)
                PushButton("Importa eventi", onClick = viewModel::import, icon = Icons.Rounded.Download, modifier = Modifier.fillMaxWidth())
            }
        }
        TextButton(onClick = onDone, enabled = !state.importing) { Text("Salta per ora") }
    }
}
