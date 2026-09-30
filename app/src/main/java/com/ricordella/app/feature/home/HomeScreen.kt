package com.ricordella.app.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.navigation.AppNavigator
import com.ricordella.app.core.ui.EmptyState
import com.ricordella.app.core.ui.ReminderCard
import com.ricordella.app.core.ui.ReminderDateMode
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.TopLevelScaffold
import com.ricordella.app.core.ui.appViewModel
import com.ricordella.app.core.ui.contentWidth
import com.ricordella.app.core.ui.theme.RicordellaDimensions

@Composable
fun HomeScreen(navigator: AppNavigator, onAdd: () -> Unit) {
    val viewModel = appViewModel { c, _ -> HomeViewModel(c.reminderRepository, c.completeReminder, c.time) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TopLevelScaffold(title = "Ricordella", navigator = navigator, onAdd = onAdd) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().contentWidth(),
            contentPadding = PaddingValues(
                start = RicordellaDimensions.screenPadding,
                end = RicordellaDimensions.screenPadding,
                top = padding.calculateTopPadding(),
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
        ) {
            item(key = "greeting") { Greeting(state) }
            item(key = "permission") { NotificationPermissionCard() }

            if (!state.isLoading && state.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.EventAvailable,
                        title = "Nessun promemoria",
                        message = "Non c'è nulla da ricordare nei prossimi giorni.",
                        actionLabel = "Aggiungi",
                        onAction = onAdd,
                    )
                }
            }

            items(state.attention, key = { "attention-" + it.reminder.id }) { entry ->
                ReminderCard(
                    entry = entry,
                    now = state.now,
                    onClick = { navigator.openReminder(entry.reminder.id) },
                    onToggleComplete = { viewModel.onToggleComplete(entry.reminder.id) },
                    dateMode = ReminderDateMode.RELATIVE,
                    highlighted = true,
                )
            }

            if (state.today.isNotEmpty()) {
                item(key = "today-header") { SectionHeader("Oggi") }
                items(state.today, key = { "today-" + it.reminder.id }) { entry ->
                    ReminderCard(
                        entry = entry,
                        now = state.now,
                        onClick = { navigator.openReminder(entry.reminder.id) },
                        onToggleComplete = { viewModel.onToggleComplete(entry.reminder.id) },
                        dateMode = ReminderDateMode.TIME_ONLY,
                    )
                }
            }

            if (state.upcoming.isNotEmpty()) {
                item(key = "upcoming-header") {
                    SectionHeader("Prossimamente") {
                        TextButton(onClick = navigator::openReminders) { Text("Vedi tutto") }
                    }
                }
                items(state.upcoming, key = { "upcoming-" + it.reminder.id }) { entry ->
                    ReminderCard(
                        entry = entry,
                        now = state.now,
                        onClick = { navigator.openReminder(entry.reminder.id) },
                        onToggleComplete = { viewModel.onToggleComplete(entry.reminder.id) },
                        dateMode = ReminderDateMode.RELATIVE,
                    )
                }
            }
        }
    }
}


@Composable
private fun Greeting(state: HomeUiState) {
    Column(Modifier.padding(vertical = RicordellaDimensions.spaceS)) {
        Text("${DateTexts.greeting(state.now.toLocalTime())} 👋", style = MaterialTheme.typography.headlineMedium)
        val count = state.attention.size
        val subtitle = when {
            state.isLoading -> ""
            count == 0 -> "Tutto sotto controllo."
            count == 1 -> "1 cosa richiede attenzione"
            else -> "$count cose richiedono attenzione"
        }
        Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (granted) return

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(
            modifier = Modifier.padding(RicordellaDimensions.spaceL),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM),
        ) {
            Icon(Icons.Rounded.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Column(Modifier.weight(1f)) {
                Text("Notifiche disattivate", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    "Attivale per ricevere i promemoria anche con l'app chiusa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            FilledTonalButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Attiva") }
        }
    }
}
