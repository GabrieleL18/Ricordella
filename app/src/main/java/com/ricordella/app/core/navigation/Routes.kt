package com.ricordella.app.core.navigation

import kotlinx.serialization.Serializable

/* Destinazioni di navigazione type-safe. */

@Serializable data object HomeRoute
@Serializable data object CalendarRoute
@Serializable data object RemindersRoute
@Serializable data object ItemsRoute
@Serializable data object PeopleRoute
@Serializable data object SettingsRoute
@Serializable data object SearchRoute

@Serializable data class ReminderDetailRoute(val id: String)

/**
 * Creazione (id null) o modifica di un promemoria. I parametri opzionali precompilano
 * il modulo: tipo (nome dell'enum), data (epochDay), cosa e persona collegate.
 */
@Serializable
data class ReminderEditRoute(
    val id: String? = null,
    val type: String? = null,
    val epochDay: Long? = null,
    val itemId: String? = null,
    val personId: String? = null,
)

@Serializable data class ItemDetailRoute(val id: String)
@Serializable data class ItemEditRoute(val id: String? = null, val personId: String? = null)
@Serializable data class PersonDetailRoute(val id: String)
@Serializable data class PersonEditRoute(val id: String? = null)

/** Richiesta arrivata dal widget del calendario: cosa aggiungere ed eventuale giorno toccato. */
data class WidgetRequest(val action: String, val date: java.time.LocalDate?)
