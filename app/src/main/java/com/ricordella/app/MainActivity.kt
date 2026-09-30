package com.ricordella.app

import android.content.Intent
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
import com.ricordella.app.domain.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /** Promemoria da aprire perché l'utente ha toccato una notifica. */
    private val reminderToOpen = MutableStateFlow<String?>(null)

    /** Azione richiesta dal widget del calendario (aggiungi promemoria, evento, cosa, persona). */
    private val widgetRequest = MutableStateFlow<WidgetRequest?>(null)

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
            CompositionLocalProvider(LocalAppSettings provides settings) {
                RicordellaTheme(themeMode = settings.themeMode) {
                    RicordellaApp(
                        reminderToOpen = pendingReminder,
                        onReminderOpened = { reminderToOpen.value = null },
                        widgetRequest = pendingWidget,
                        onWidgetRequestHandled = { widgetRequest.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_REMINDER_ID)?.let { reminderToOpen.value = it }
        intent?.getStringExtra(CalendarWidgetProvider.EXTRA_ACTION)?.let { action ->
            val epochDay = intent.getLongExtra(CalendarWidgetProvider.EXTRA_EPOCH_DAY, Long.MIN_VALUE)
            widgetRequest.value = WidgetRequest(action, epochDay.takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay))
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "com.ricordella.app.extra.OPEN_REMINDER_ID"
    }
}
