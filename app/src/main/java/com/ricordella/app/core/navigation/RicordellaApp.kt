package com.ricordella.app.core.navigation

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.feature.calendar.CalendarScreen
import com.ricordella.app.feature.home.HomeScreen
import com.ricordella.app.feature.items.ItemDetailScreen
import com.ricordella.app.feature.items.ItemEditScreen
import com.ricordella.app.feature.items.ItemListScreen
import com.ricordella.app.feature.people.PersonDetailScreen
import com.ricordella.app.feature.people.PersonEditScreen
import com.ricordella.app.feature.people.PersonListScreen
import com.ricordella.app.feature.reminders.ReminderDetailScreen
import com.ricordella.app.feature.reminders.ReminderEditScreen
import com.ricordella.app.feature.reminders.ReminderListScreen
import com.ricordella.app.feature.search.SearchScreen
import com.ricordella.app.feature.settings.SettingsScreen
import kotlin.reflect.KClass

private enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME(HomeRoute, HomeRoute::class, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
    CALENDAR(CalendarRoute, CalendarRoute::class, "Calendario", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    REMINDERS(RemindersRoute, RemindersRoute::class, "Promemoria", Icons.Outlined.Notifications, Icons.Rounded.Notifications),
    ITEMS(ItemsRoute, ItemsRoute::class, "Cose", Icons.Outlined.Inventory2, Icons.Rounded.Inventory2),
    PEOPLE(PeopleRoute, PeopleRoute::class, "Persone", Icons.Outlined.People, Icons.Rounded.People),
}

private enum class QuickAddKind { REMINDER, EVENT, ITEM, PERSON }

/**
 * Shell dell'app: navigazione adattiva (barra in basso su schermi compatti,
 * rail su schermi larghi), grafo di navigazione e pulsante "+" di creazione rapida.
 */
@Composable
fun RicordellaApp(reminderToOpen: String?, onReminderOpened: () -> Unit) {
    val navController = rememberNavController()
    val navigator = remember(navController) { AppNavigator(navController) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { top ->
        destination?.hierarchy?.any { it.hasRoute(top.routeClass) } == true
    }
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    val openQuickAdd = { showQuickAdd = true }

    LaunchedEffect(reminderToOpen) {
        if (reminderToOpen != null) {
            navigator.openReminder(reminderToOpen)
            onReminderOpened()
        }
    }

    val adaptiveType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    NavigationSuiteScaffold(
        layoutType = if (currentTopLevel != null) adaptiveType else NavigationSuiteType.None,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { top ->
                val selected = top == currentTopLevel
                item(
                    selected = selected,
                    onClick = { navigator.openTopLevel(top.route) },
                    icon = { Icon(if (selected) top.selectedIcon else top.icon, contentDescription = null) },
                    label = { Text(top.label) },
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = HomeRoute) {
            composable<HomeRoute> { HomeScreen(navigator, openQuickAdd) }
            composable<CalendarRoute> { CalendarScreen(navigator, openQuickAdd) }
            composable<RemindersRoute> { ReminderListScreen(navigator, openQuickAdd) }
            composable<ItemsRoute> { ItemListScreen(navigator, openQuickAdd) }
            composable<PeopleRoute> { PersonListScreen(navigator, openQuickAdd) }
            composable<SearchRoute> { SearchScreen(navigator) }
            composable<SettingsRoute> { SettingsScreen(onBack = navigator::back) }
            composable<ReminderDetailRoute> { ReminderDetailScreen(navigator) }
            composable<ReminderEditRoute> { ReminderEditScreen(onBack = navigator::back) }
            composable<ItemDetailRoute> { ItemDetailScreen(navigator) }
            composable<ItemEditRoute> { ItemEditScreen(onBack = navigator::back) }
            composable<PersonDetailRoute> { PersonDetailScreen(navigator) }
            composable<PersonEditRoute> { PersonEditScreen(navigator) }
        }
    }

    if (showQuickAdd) {
        QuickAddSheet(
            onDismiss = { showQuickAdd = false },
            onSelected = { kind ->
                showQuickAdd = false
                when (kind) {
                    QuickAddKind.REMINDER -> navigator.newReminder()
                    QuickAddKind.EVENT -> navigator.newReminder(type = ReminderType.EVENT)
                    QuickAddKind.ITEM -> navigator.newItem()
                    QuickAddKind.PERSON -> navigator.newPerson()
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(onDismiss: () -> Unit, onSelected: (QuickAddKind) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Cosa vuoi aggiungere?",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            QuickAddRow(Icons.Rounded.NotificationsActive, "Promemoria", "Qualcosa da fare o da non dimenticare") { onSelected(QuickAddKind.REMINDER) }
            QuickAddRow(Icons.Rounded.Event, "Evento", "Un appuntamento o una ricorrenza") { onSelected(QuickAddKind.EVENT) }
            QuickAddRow(Icons.Rounded.Inventory2, "Cosa", "Auto, elettrodomestico, dispositivo, documento") { onSelected(QuickAddKind.ITEM) }
            QuickAddRow(Icons.Rounded.PersonAdd, "Persona", "Qualcuno a cui collegare promemoria e cose") { onSelected(QuickAddKind.PERSON) }
        }
    }
}

@Composable
private fun QuickAddRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}
