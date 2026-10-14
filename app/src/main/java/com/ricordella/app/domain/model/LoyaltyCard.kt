package com.ricordella.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Una carta fedeltà (Decathlon, Coop, Lidl...): si scansiona una volta e poi si mostra alla cassa.
 * Vive nelle impostazioni (e quindi nel backup), come i buoni propositi. [format] è la costante
 * `Barcode.FORMAT_*` di ML Kit con cui il codice è stato letto.
 */
@Serializable
data class LoyaltyCard(
    val id: String = newId(),
    val name: String,
    val code: String,
    val format: Int,
)
