package com.ricordella.app.core.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.ricordella.app.domain.model.ReminderType
import java.time.LocalDate

/**
 * Azioni di navigazione disponibili alle schermate. Le schermate non conoscono il
 * NavController: la navigazione resta un'unica sorgente di verità gestita qui.
 */
class AppNavigator(private val navController: NavController) {

    fun openTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun back() {
        navController.popBackStack()
    }

    fun openSearch() = navController.navigate(SearchRoute)
    fun openSettings() = navController.navigate(SettingsRoute)
    fun openPotions() = navController.navigate(PotionsRoute) { launchSingleTop = true }
    fun openResolutions(year: Int, recap: Boolean = false) = navController.navigate(ResolutionsRoute(year, recap)) { launchSingleTop = true }
    fun openReminders() = openTopLevel(RemindersRoute)

    fun openReminder(id: String) = navController.navigate(ReminderDetailRoute(id))

    fun newReminder(
        type: ReminderType? = null,
        date: LocalDate? = null,
        itemId: String? = null,
        personId: String? = null,
    ) = navController.navigate(ReminderEditRoute(type = type?.name, epochDay = date?.toEpochDay(), itemId = itemId, personId = personId))

    fun editReminder(id: String) = navController.navigate(ReminderEditRoute(id = id))

    fun openItem(id: String) = navController.navigate(ItemDetailRoute(id))
    fun newItem(personId: String? = null) = navController.navigate(ItemEditRoute(personId = personId))
    fun editItem(id: String) = navController.navigate(ItemEditRoute(id = id))

    fun openPerson(id: String) = navController.navigate(PersonDetailRoute(id))
    fun newPerson() = navController.navigate(PersonEditRoute())
    fun editPerson(id: String) = navController.navigate(PersonEditRoute(id = id))

    fun openViewer(uri: String, mimeType: String?, name: String) = navController.navigate(ViewerRoute(uri, mimeType, name))
}
