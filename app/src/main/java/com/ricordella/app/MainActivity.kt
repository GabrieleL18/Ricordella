package com.ricordella.app

import com.ricordella.app.feature.legal.TERMS_VERSION
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.navigation.RicordellaApp
import com.ricordella.app.core.navigation.WidgetRequest
import com.ricordella.app.core.widget.CalendarWidgetProvider
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.theme.RicordellaTheme
import com.ricordella.app.feature.settings.OpenedBackupImport
import com.ricordella.app.domain.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /** Promemoria da aprire perché l'utente ha toccato una notifica. */
    private val reminderToOpen = MutableStateFlow<String?>(null)

    /** Azione richiesta dal widget del calendario (aggiungi promemoria, evento, cosa, persona). */
    private val widgetRequest = MutableStateFlow<WidgetRequest?>(null)

    /** File di backup aperto con l'app (da file manager, Drive, email): si propone il ripristino. */
    private val backupToOpen = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val container = (application as RicordellaApplication).container

        setContent {
            // null finché le preferenze non sono lette: evita di mostrare per un attimo la configurazione iniziale.
            val loaded by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val settings = loaded ?: return@setContent
            val pendingReminder by reminderToOpen.collectAsStateWithLifecycle()
            val pendingWidget by widgetRequest.collectAsStateWithLifecycle()
            val pendingBackup by backupToOpen.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppSettings provides settings) {
                RicordellaTheme(themeMode = settings.themeMode) {
                    RicordellaApp(
                        reminderToOpen = pendingReminder,
                        onReminderOpened = { reminderToOpen.value = null },
                        widgetRequest = pendingWidget,
                        onWidgetRequestHandled = { widgetRequest.value = null },
                    )
                    if (BuildConfig.DEBUG) com.ricordella.app.core.ui.DebugBadge()
                    if (settings.termsAcceptedVersion >= TERMS_VERSION) pendingBackup?.let { OpenedBackupImport(it, onDone = { backupToOpen.value = null }) }
                }
            }
        }
    }

    // File condiviso in automatico: si aggiorna quando si apre l'app (e ogni due minuti mentre è aperta,
    // per vedere le novità dell'altra persona) e quando la si chiude (per mandarle le proprie).
    override fun onStart() {
        super.onStart()
        (application as RicordellaApplication).container.sharedSpace.onForeground(true)
    }

    override fun onStop() {
        super.onStop()
        (application as RicordellaApplication).container.sharedSpace.onForeground(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Solo i file (content/file): le notifiche usano ACTION_VIEW con ricordella://, non sono backup.
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.takeIf { it.scheme == "content" || it.scheme == "file" }?.let { backupToOpen.value = it }
        intent?.getStringExtra(EXTRA_REMINDER_ID)?.let { reminderToOpen.value = it }
        intent?.getStringExtra(CalendarWidgetProvider.EXTRA_ACTION)?.let { action ->
            val epochDay = intent.getLongExtra(CalendarWidgetProvider.EXTRA_EPOCH_DAY, Long.MIN_VALUE)
            widgetRequest.value = WidgetRequest(action, epochDay.takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay))
        }
        // Tocco sulla notifica delle pozioni: si apre la loro schermata.
        if (intent?.getBooleanExtra(EXTRA_OPEN_POTIONS, false) == true) widgetRequest.value = WidgetRequest(ACTION_OPEN_POTIONS, null)
        if (intent?.getBooleanExtra(EXTRA_OPEN_CYCLE, false) == true) widgetRequest.value = WidgetRequest(ACTION_OPEN_CYCLE, null)
        // Widget sveglia: "+" apre la creazione di una sveglia.
        if (intent?.getBooleanExtra(EXTRA_NEW_ALARM, false) == true) widgetRequest.value = WidgetRequest(ACTION_NEW_ALARM, null)
        // Tocco sul widget dei buoni propositi: si apre la loro schermata.
        if (intent?.getBooleanExtra(EXTRA_OPEN_RESOLUTIONS, false) == true) widgetRequest.value = WidgetRequest(ACTION_OPEN_RESOLUTIONS, null)
    }

    companion object {
        const val EXTRA_REMINDER_ID = "com.ricordella.app.extra.OPEN_REMINDER_ID"
        const val EXTRA_OPEN_CYCLE = "com.ricordella.app.extra.OPEN_CYCLE"
        const val ACTION_OPEN_CYCLE = "cycle"
        const val EXTRA_NEW_ALARM = "com.ricordella.app.extra.NEW_ALARM"
        const val ACTION_NEW_ALARM = "new_alarm"
        const val EXTRA_OPEN_POTIONS = "com.ricordella.app.extra.OPEN_POTIONS"
        const val ACTION_OPEN_POTIONS = "potions"
        const val EXTRA_OPEN_RESOLUTIONS = "com.ricordella.app.extra.OPEN_RESOLUTIONS"
        const val ACTION_OPEN_RESOLUTIONS = "resolutions"
    }
}
