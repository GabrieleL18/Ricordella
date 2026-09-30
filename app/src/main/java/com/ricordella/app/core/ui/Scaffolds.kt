package com.ricordella.app.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import com.ricordella.app.core.navigation.AppNavigator
import com.ricordella.app.core.ui.theme.BrandTitleStyle
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
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        style = if (brandTitle) BrandTitleStyle.copy(color = MaterialTheme.ricordellaColors.boltEdge) else MaterialTheme.typography.headlineSmall,
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                actions = {
                    IconButton(onClick = navigator::openSearch) { Icon(Icons.Rounded.Search, contentDescription = "Cerca") }
                    IconButton(onClick = navigator::openSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Impostazioni") }
                },
            )
        },
        floatingActionButton = { AddFab(onAdd) },
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
            contentDescription = "Aggiungi",
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

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
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro")
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
