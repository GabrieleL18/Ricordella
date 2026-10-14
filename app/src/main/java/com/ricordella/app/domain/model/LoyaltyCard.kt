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
    /** Categoria scelta a piacere ("Spesa", "Sport"...); vuota = senza categoria. */
    val category: String = "",
    /** In cima all'elenco, sopra le categorie. */
    val favorite: Boolean = false,
    /** Ultima modifica (millisecondi): serve a unire le modifiche nel file condiviso. */
    val updatedAt: Long = 0,
)

/** Una carta spedita come testo: righe leggibili più un link `remindella://card?...` che l'app sa riaprire. */
object CardLink {
    private const val PREFIX = "remindella://card?"

    fun encode(card: LoyaltyCard): String = PREFIX + listOf("n" to card.name, "c" to card.code, "f" to card.format.toString(), "k" to card.category)
        .joinToString("&") { (key, value) -> key + "=" + java.net.URLEncoder.encode(value, "UTF-8") }

    /** La carta contenuta in [text] (un link da solo o dentro un messaggio), o null. Ha un id nuovo. */
    fun parse(text: String): LoyaltyCard? {
        val link = Regex(Regex.escape(PREFIX) + "\\S+").find(text)?.value ?: return null
        val values = link.removePrefix(PREFIX).split('&').mapNotNull { part ->
            part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to java.net.URLDecoder.decode(it[1], "UTF-8") }
        }.toMap()
        val name = values["n"]?.takeIf { it.isNotBlank() } ?: return null
        val code = values["c"]?.takeIf { it.isNotBlank() } ?: return null
        return LoyaltyCard(name = name, code = code, format = values["f"]?.toIntOrNull() ?: 0, category = values["k"].orEmpty(), updatedAt = System.currentTimeMillis())
    }
}
