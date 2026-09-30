package com.ricordella.app.core.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun RicordellaApp(reminderToOpen: String?, onReminderOpened: () -> Unit) {
    Text("Ricordella")
}
