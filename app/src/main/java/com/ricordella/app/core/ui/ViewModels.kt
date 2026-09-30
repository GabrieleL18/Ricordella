package com.ricordella.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.AppContainer
import com.ricordella.app.domain.model.AppSettings

/** Impostazioni correnti, disponibili a tutta la UI (formato data, primo giorno della settimana...). */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

/**
 * Crea un ViewModel con accesso al contenitore delle dipendenze e al SavedStateHandle
 * (che contiene gli argomenti della destinazione di navigazione).
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (container: AppContainer, savedState: SavedStateHandle) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as RicordellaApplication).container
    return viewModel(
        factory = viewModelFactory {
            initializer { create(container, createSavedStateHandle()) }
        },
    )
}
