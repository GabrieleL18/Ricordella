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
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.theme.RicordellaTheme
import com.ricordella.app.domain.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Promemoria da aprire perché l'utente ha toccato una notifica. */
    private val reminderToOpen = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val container = (application as RicordellaApplication).container

        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val pendingReminder by reminderToOpen.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppSettings provides settings) {
                RicordellaTheme(themeMode = settings.themeMode) {
                    RicordellaApp(
                        reminderToOpen = pendingReminder,
                        onReminderOpened = { reminderToOpen.value = null },
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
    }

    companion object {
        const val EXTRA_REMINDER_ID = "com.ricordella.app.extra.OPEN_REMINDER_ID"
    }
}
