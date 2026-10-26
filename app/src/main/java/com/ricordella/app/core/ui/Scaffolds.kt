package com.ricordella.app.core.ui

import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ricordella.app.core.i18n.tr

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.ui.theme.ricordellaColors
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import com.ricordella.app.core.navigation.AppNavigator
import androidx.compose.ui.unit.sp
import com.ricordella.app.core.ui.theme.RicordellaDimensions

/** Limita la larghezza dei contenuti su tablet e schermi larghi, centrandoli. */
fun Modifier.contentWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = RicordellaDimensions.maxContentWidth)

/**
 * Scaffold delle sezioni principali: titolo, ricerca, impostazioni e pulsante "+".
 * La barra di navigazione è gestita dalla shell dell'app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopLevelScaffold(
    title: String,
    navigator: AppNavigator,
    onAdd: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    /** True per il titolo "Remindella" della Home, scritto col carattere magico. */
    brandTitle: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // Il "+" si nasconde mentre si scorre verso il basso (non copre le card che si leggono) e torna appena si risale.
    var fabShown by remember { mutableStateOf(true) }
    val fabScroll = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y < -4f) fabShown = false else if (available.y > 4f) fabShown = true
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).nestedScroll(fabScroll),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = {
                    if (brandTitle) MagicTitle(fontSize = 28.sp)
                    else Text(title, style = MaterialTheme.typography.headlineSmall)
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                actions = {
                    // Tutte le sezioni che non stanno nella barra in basso, in un solo posto (in ogni sezione).
                    var sections by remember { mutableStateOf(false) }
                    IconButton(onClick = { sections = true }) {
                        Icon(Icons.Rounded.Apps, contentDescription = tr("Tutte le sezioni"), tint = MaterialTheme.ricordellaColors.cyan.solid)
                    }
                    DropdownMenu(expanded = sections, onDismissRequest = { sections = false }) {
                        val shown = com.ricordella.app.core.ui.LocalAppSettings.current
                        @Composable
                        fun Entry(section: com.ricordella.app.domain.model.AppSection?, label: String, icon: ImageVector, open: () -> Unit) {
                            if (section == null || shown.shows(section)) DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = { sections = false; open() })
                        }
                        Entry(com.ricordella.app.domain.model.AppSection.ALARMS, tr("Sveglie"), Icons.Rounded.Alarm, navigator::openAlarms)
                        Entry(com.ricordella.app.domain.model.AppSection.TIMERS, tr("Timer"), Icons.Rounded.Timer, navigator::openTimers)
                        Entry(com.ricordella.app.domain.model.AppSection.NOTES, tr("Note"), Icons.Rounded.StickyNote2, navigator::openNotes)
                        Entry(com.ricordella.app.domain.model.AppSection.CARDS, tr("Carte"), Icons.Rounded.CreditCard) { navigator.openCards() }
                        Entry(com.ricordella.app.domain.model.AppSection.POTIONS, tr("Pozioni"), Icons.Rounded.WaterDrop, navigator::openPotions)
                        Entry(com.ricordella.app.domain.model.AppSection.EXPENSES, tr("Spese"), Icons.Rounded.Payments, navigator::openExpenses)
                        Entry(com.ricordella.app.domain.model.AppSection.CYCLE, tr("Ciclo"), Icons.Rounded.WaterDrop, navigator::openCycle)
                        Entry(null, tr("Buoni propositi"), Icons.Rounded.EmojiEvents) { navigator.openResolutions(java.time.LocalDate.now().year) }
                    }
                    IconButton(onClick = navigator::openSearch) { Icon(Icons.Rounded.Search, contentDescription = tr("Cerca")) }
                    IconButton(onClick = navigator::openSettings) { Icon(Icons.Rounded.Settings, contentDescription = tr("Impostazioni")) }
                },
            )
        },
        floatingActionButton = {
            androidx.compose.animation.AnimatedVisibility(
                fabShown,
                enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
            ) { AddFab(onAdd) }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = content,
    )
}

/** True mentre il foglio di creazione rapida è aperto: il "+" ruota e diventa una "x". */
val LocalQuickAddOpen = compositionLocalOf { false }

/** Il "+" giallo-saetta: entra con una piccola molla e ruota quando il foglio "Aggiungi" è aperto. */
@Composable
private fun AddFab(onAdd: () -> Unit) {
    val colors = MaterialTheme.ricordellaColors
    val open = LocalQuickAddOpen.current
    val reduced = rememberReducedMotion()
    val entrance = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
    val rotation by animateFloatAsState(
        targetValue = if (open) 135f else 0f,
        animationSpec = tween(RicordellaMotion.LONG, easing = RicordellaMotion.EaseOut),
        label = "fabRotation",
    )
    FloatingActionButton(
        onClick = onAdd,
        containerColor = colors.bolt,
        contentColor = colors.onBolt,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.graphicsLayer {
            scaleX = entrance.value
            scaleY = entrance.value
        },
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = tr("Aggiungi"),
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

/** Il "indietro" della barra in alto: chi lo gestisce (es. tornare al foglio "Aggiungi") restituisce true. */
val LocalBackHook = androidx.compose.runtime.compositionLocalOf<() -> Boolean> { { false } }

/** Scaffold delle schermate secondarie (dettaglio, modifica) con freccia indietro. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val backHook = LocalBackHook.current
    Scaffold(
        // imePadding: con la tastiera aperta il contenuto si restringe e il campo in uso resta visibile.
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (!backHook()) onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Indietro"))
                    }
                },
                actions = { actions() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = content,
    )
}

/**
 * Dopo aver completato [entry] offre "Riapri". Solo per i promemoria semplici: uno che si ripete o a rate
 * avanza alla scadenza successiva, e riaprirlo non la rimetterebbe com'era.
 */
suspend fun SnackbarHostState.offerReopen(entry: com.ricordella.app.domain.model.ReminderWithLinks, reopen: () -> Unit) {
    val reminder = entry.reminder
    if (entry.recurrenceRule != null || reminder.plan != null || reminder.type == com.ricordella.app.domain.model.ReminderType.ALARM) return
    currentSnackbarData?.dismiss()
    if (showSnackbar(com.ricordella.app.core.i18n.tr("Completato"), com.ricordella.app.core.i18n.tr("Riapri")) == androidx.compose.material3.SnackbarResult.ActionPerformed) reopen()
}
