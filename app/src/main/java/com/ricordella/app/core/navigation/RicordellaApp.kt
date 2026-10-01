package com.ricordella.app.core.navigation

import com.ricordella.app.feature.legal.TERMS_VERSION
import com.ricordella.app.feature.legal.TermsScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.ricordella.app.core.i18n.tr

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.navigation.NavDestination
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.LocalQuickAddOpen
import com.ricordella.app.core.widget.CalendarWidgetProvider
import com.ricordella.app.feature.onboarding.OnboardingScreen
import com.ricordella.app.core.ui.RicordellaMotion
import com.ricordella.app.core.ui.pressScale
import com.ricordella.app.core.ui.rememberReducedMotion
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
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
import com.ricordella.app.feature.viewer.DocumentViewerScreen
import androidx.navigation.toRoute
import com.ricordella.app.feature.settings.SettingsScreen
import com.ricordella.app.feature.potions.PotionsScreen
import com.ricordella.app.feature.resolutions.ResolutionsScreen
import kotlin.reflect.KClass

private enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    private val italianLabel: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME(HomeRoute, HomeRoute::class, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
    CALENDAR(CalendarRoute, CalendarRoute::class, "Calendario", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    REMINDERS(RemindersRoute, RemindersRoute::class, "Promemoria", Icons.Outlined.Notifications, Icons.Rounded.Notifications),
    ITEMS(ItemsRoute, ItemsRoute::class, "Cose", Icons.Outlined.Inventory2, Icons.Rounded.Inventory2),
    PEOPLE(PeopleRoute, PeopleRoute::class, "Persone", Icons.Outlined.People, Icons.Rounded.People);

    val label: String get() = tr(italianLabel)
}

private enum class QuickAddKind { REMINDER, EVENT, ALARM, VACATION, MEDICAL_VISIT, ITEM, PERSON }

/**
 * Shell dell'app: navigazione adattiva (barra in basso su schermi compatti,
 * rail su schermi larghi), grafo di navigazione e pulsante "+" di creazione rapida.
 */
@Composable
fun RicordellaApp(
    reminderToOpen: String?,
    onReminderOpened: () -> Unit,
    widgetRequest: WidgetRequest? = null,
    onWidgetRequestHandled: () -> Unit = {},
) {
    if (LocalAppSettings.current.termsAcceptedVersion < TERMS_VERSION) {
        val container = (LocalContext.current.applicationContext as com.ricordella.app.RicordellaApplication).container
        val activity = LocalContext.current as? android.app.Activity
        val scope = rememberCoroutineScope()
        TermsScreen(
            onAccept = {
                scope.launch {
                    container.settingsRepository.update {
                        it.copy(termsAcceptedVersion = TERMS_VERSION, termsAcceptedEpochDay = container.time.today().toEpochDay())
                    }
                }
            },
            onDecline = { activity?.finishAndRemoveTask() },
        )
        return
    }
    if (!LocalAppSettings.current.onboardingDone) {
        OnboardingScreen()
        return
    }
    // Primo avvio dopo un aggiornamento: le novità della versione (una volta sola).
    val news = com.ricordella.app.feature.news.newsSince(LocalAppSettings.current.newsSeenVersion)
        .filter { it.versionCode <= com.ricordella.app.BuildConfig.VERSION_CODE }
    if (news.isNotEmpty()) {
        val container = (LocalContext.current.applicationContext as com.ricordella.app.RicordellaApplication).container
        val scope = rememberCoroutineScope()
        com.ricordella.app.feature.news.NewsDialog(news, onDismiss = {
            scope.launch { container.settingsRepository.update { it.copy(newsSeenVersion = com.ricordella.app.BuildConfig.VERSION_CODE) } }
        })
    }
    val navController = rememberNavController()
    val navigator = remember(navController) { AppNavigator(navController) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { top ->
        destination?.hierarchy?.any { it.hasRoute(top.routeClass) } == true
    }
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    // Giorno a cui legare promemoria ed eventi creati dal "+" (quello selezionato nel Calendario).
    var quickAddDate by rememberSaveable { mutableStateOf<java.time.LocalDate?>(null) }
    val openQuickAdd = { quickAddDate = null; showQuickAdd = true }
    val openQuickAddOn = { date: java.time.LocalDate -> quickAddDate = date; showQuickAdd = true }

    LaunchedEffect(reminderToOpen) {
        if (reminderToOpen != null) {
            navigator.openReminder(reminderToOpen)
            onReminderOpened()
        }
    }

    LaunchedEffect(widgetRequest) {
        val request = widgetRequest ?: return@LaunchedEffect
        when (request.action) {
            CalendarWidgetProvider.ACTION_EVENT -> navigator.newReminder(type = ReminderType.EVENT, date = request.date)
            CalendarWidgetProvider.ACTION_ITEM -> navigator.newItem()
            CalendarWidgetProvider.ACTION_PERSON -> navigator.newPerson()
            CalendarWidgetProvider.ACTION_REMINDER -> navigator.newReminder(date = request.date)
            com.ricordella.app.MainActivity.ACTION_OPEN_POTIONS -> navigator.openPotions()
            com.ricordella.app.MainActivity.ACTION_OPEN_RESOLUTIONS -> navigator.openResolutions(java.time.LocalDate.now().year)
        }
        onWidgetRequestHandled()
    }

    com.ricordella.app.feature.settings.OwnershipQuestionHost()
    val adaptiveType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    CompositionLocalProvider(LocalQuickAddOpen provides showQuickAdd) {
    NavigationSuiteScaffold(
        layoutType = if (currentTopLevel != null) adaptiveType else NavigationSuiteType.None,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            navigationRailContainerColor = MaterialTheme.colorScheme.background,
        ),
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { top ->
                val selected = top == currentTopLevel
                item(
                    selected = selected,
                    onClick = { navigator.openTopLevel(top.route) },
                    icon = { NavIcon(top, selected) },
                    label = {
                        // Parole lunghe (es. "Erinnerungen") in corpo più piccolo, così non vengono tagliate.
                        Text(
                            top.label,
                            maxLines = 1,
                            softWrap = false,
                            style = when {
                                top.label.length > 11 -> MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                top.label.length > 9 -> MaterialTheme.typography.labelSmall
                                else -> MaterialTheme.typography.labelMedium
                            },
                        )
                    },
                )
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            enterTransition = {
                if (initialState.destination.isTopLevel() && targetState.destination.isTopLevel()) fadeThroughIn()
                else slideIntoContainer(SlideDirection.Start, tween(RicordellaMotion.LONG, easing = RicordellaMotion.EaseOut)) { it / 4 } +
                    fadeIn(tween(RicordellaMotion.SHORT))
            },
            exitTransition = {
                if (initialState.destination.isTopLevel() && targetState.destination.isTopLevel()) fadeOut(tween(90))
                else slideOutOfContainer(SlideDirection.Start, tween(RicordellaMotion.LONG, easing = RicordellaMotion.EaseOut)) { it / 10 } +
                    fadeOut(tween(RicordellaMotion.SHORT))
            },
            popEnterTransition = {
                if (initialState.destination.isTopLevel() && targetState.destination.isTopLevel()) fadeThroughIn()
                else slideIntoContainer(SlideDirection.End, tween(RicordellaMotion.LONG, easing = RicordellaMotion.EaseOut)) { it / 10 } +
                    fadeIn(tween(RicordellaMotion.SHORT))
            },
            popExitTransition = {
                slideOutOfContainer(SlideDirection.End, tween(RicordellaMotion.SHORT, easing = RicordellaMotion.EaseIn)) { it / 4 } +
                    fadeOut(tween(RicordellaMotion.SHORT))
            },
        ) {
            composable<HomeRoute> { HomeScreen(navigator, openQuickAdd) }
            composable<CalendarRoute> { CalendarScreen(navigator, openQuickAddOn) }
            composable<RemindersRoute> { ReminderListScreen(navigator, openQuickAdd) }
            composable<ItemsRoute> { ItemListScreen(navigator, openQuickAdd) }
            composable<PeopleRoute> { PersonListScreen(navigator, openQuickAdd) }
            composable<SearchRoute> { SearchScreen(navigator) }
            composable<SettingsRoute> { SettingsScreen(onBack = navigator::back, onOpenResolutions = { navigator.openResolutions(it) }, onGoHome = navigator::openHome) }
            composable<PotionsRoute> { PotionsScreen(onBack = navigator::back) }
            composable<ExpensesRoute> { com.ricordella.app.feature.items.ExpensesScreen(navigator) }
            composable<ResolutionsRoute> { entry ->
                val route = entry.toRoute<ResolutionsRoute>()
                ResolutionsScreen(route.year, route.recap, onBack = navigator::back)
            }
            composable<ReminderDetailRoute> { ReminderDetailScreen(navigator) }
            composable<ReminderEditRoute> { ReminderEditScreen(onBack = navigator::back) }
            composable<ItemDetailRoute> { ItemDetailScreen(navigator) }
            composable<ItemEditRoute> { ItemEditScreen(onBack = navigator::back) }
            composable<PersonDetailRoute> { PersonDetailScreen(navigator) }
            composable<PersonEditRoute> { PersonEditScreen(navigator) }
            composable<ViewerRoute> { entry -> DocumentViewerScreen(entry.toRoute(), onBack = navigator::back) }
        }
    }
    }

    if (showQuickAdd) {
        QuickAddSheet(
            onDismiss = { showQuickAdd = false },
            onSelected = { kind ->
                showQuickAdd = false
                when (kind) {
                    QuickAddKind.REMINDER -> navigator.newReminder(date = quickAddDate)
                    QuickAddKind.EVENT -> navigator.newReminder(type = ReminderType.EVENT, date = quickAddDate)
                    QuickAddKind.ALARM -> navigator.newReminder(type = ReminderType.ALARM, date = quickAddDate)
                    QuickAddKind.VACATION -> navigator.newReminder(type = ReminderType.VACATION, date = quickAddDate)
                    QuickAddKind.MEDICAL_VISIT -> navigator.newReminder(type = ReminderType.MEDICAL_VISIT, date = quickAddDate)
                    QuickAddKind.ITEM -> navigator.newItem()
                    QuickAddKind.PERSON -> navigator.newPerson()
                }
            },
        )
    }
}

private val topLevelRouteClasses = TopLevelDestination.entries.map { it.routeClass }

private fun NavDestination.isTopLevel(): Boolean = topLevelRouteClasses.any { hasRoute(it) }

/** Passaggio tra sezioni principali: dissolvenza con un accenno di zoom. */
private fun fadeThroughIn(): EnterTransition =
    fadeIn(tween(RicordellaMotion.SHORT, delayMillis = 60)) +
        scaleIn(tween(RicordellaMotion.SHORT, delayMillis = 60, easing = RicordellaMotion.EaseOut), initialScale = 0.97f)

@Composable
private fun NavIcon(top: TopLevelDestination, selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = tween(RicordellaMotion.SHORT, easing = RicordellaMotion.EaseOut),
        label = "navIcon",
    )
    Icon(
        if (selected) top.selectedIcon else top.icon,
        contentDescription = null,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    )
}

private data class QuickAddOption(val kind: QuickAddKind, val icon: ImageVector, val title: String, val subtitle: String)

private val QuickAddOptions get() = listOf(
    QuickAddOption(QuickAddKind.REMINDER, Icons.Rounded.NotificationsActive, tr("Promemoria"), tr("Da fare o da non dimenticare")),
    QuickAddOption(QuickAddKind.EVENT, Icons.Rounded.Event, tr("Evento"), tr("Appuntamento o ricorrenza")),
    QuickAddOption(QuickAddKind.ALARM, Icons.Rounded.Alarm, tr("Sveglia"), tr("Suona a tutto schermo, anche ogni giorno")),
    QuickAddOption(QuickAddKind.VACATION, Icons.Rounded.BeachAccess, tr("Vacanza"), tr("Voli, navi e alloggio")),
    QuickAddOption(QuickAddKind.MEDICAL_VISIT, Icons.Rounded.MedicalServices, tr("Visita medica"), tr("Medico, esami, dentista")),
    QuickAddOption(QuickAddKind.ITEM, Icons.Rounded.Inventory2, tr("Cosa"), tr("Auto, casa, dispositivi, documenti")),
    QuickAddOption(QuickAddKind.PERSON, Icons.Rounded.PersonAdd, tr("Persona"), tr("A chi collegare promemoria e cose")),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(onDismiss: () -> Unit, onSelected: (QuickAddKind) -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(tr("Cosa vuoi aggiungere?"), style = MaterialTheme.typography.headlineSmall)
            QuickAddOptions.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEachIndexed { column, option ->
                        val index = row * 2 + column
                        QuickAddTile(
                            option = option,
                            tone = when (option.kind) {
                                QuickAddKind.REMINDER, QuickAddKind.MEDICAL_VISIT -> colors.cyan
                                QuickAddKind.EVENT -> colors.lavender
                                QuickAddKind.ALARM, QuickAddKind.PERSON -> colors.coral
                                QuickAddKind.VACATION -> colors.mint
                                QuickAddKind.ITEM -> colors.pear
                            },
                            index = index,
                            onClick = { onSelected(option.kind) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Riquadro colorato del foglio "Aggiungi": entra in cascata e si comprime al tocco. */
@Composable
private fun QuickAddTile(option: QuickAddOption, tone: Tone, index: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(index * 50L)
        appear.animateTo(1f, tween(RicordellaMotion.LONG, easing = RicordellaMotion.EaseOut))
    }
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .graphicsLayer {
                alpha = appear.value
                val scale = 0.85f + 0.15f * appear.value
                scaleX = scale
                scaleY = scale
                translationY = (1f - appear.value) * 24.dp.toPx()
            }
            .pressScale(interaction, pressedScale = 0.95f)
            .clip(MaterialTheme.shapes.large)
            .background(tone.container)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(tone.solid, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(option.icon, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(26.dp))
        }
        Text(option.title, style = MaterialTheme.typography.titleLarge, color = tone.content)
        Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = tone.content, minLines = 2)
    }
}
