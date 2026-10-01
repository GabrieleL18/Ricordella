package com.ricordella.app.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Un buon proposito dell'anno [year]. Sono pochi e brevi: vivono nelle impostazioni
 * (e quindi nel backup) senza bisogno di una tabella nel database.
 */
@Serializable
data class Resolution(
    val id: String = newId(),
    val year: Int,
    val text: String,
    /** Rispettato: si segna durante l'anno o nel recap di fine anno. */
    val kept: Boolean = false,
)

enum class ResolutionsPrompt { WRITE, RECAP }

/** Da questo giorno di dicembre la Home propone il recap dell'anno. */
const val RESOLUTIONS_RECAP_FROM_DAY = 15

/**
 * Invito da mostrare in Home il giorno [today], con l'anno a cui si riferisce:
 * prima il recap dell'anno che finisce (da metà dicembre a fine gennaio), poi,
 * a gennaio, la proposta di scrivere i propositi dell'anno nuovo. Null = niente.
 */
fun AppSettings.resolutionsPrompt(today: LocalDate): Pair<ResolutionsPrompt, Int>? {
    val recapYear = when {
        today.monthValue == 12 && today.dayOfMonth >= RESOLUTIONS_RECAP_FROM_DAY -> today.year
        today.monthValue == 1 -> today.year - 1
        else -> null
    }
    if (recapYear != null && (resolutionsRecapYear ?: 0) < recapYear && resolutions.any { it.year == recapYear }) {
        return ResolutionsPrompt.RECAP to recapYear
    }
    if (today.monthValue == 1 && (resolutionsAskedYear ?: 0) < today.year && resolutions.none { it.year == today.year }) {
        return ResolutionsPrompt.WRITE to today.year
    }
    return null
}
