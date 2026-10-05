package com.ricordella.app.domain.model

import kotlinx.serialization.Serializable

/** Una riga di una lista: a mano o della spesa (con prezzo e quantità). */
@Serializable
data class NoteLine(
    val id: String = newId(),
    val text: String = "",
    val done: Boolean = false,
    val priceCents: Long? = null,
    val qty: Int = 1,
) {
    /** Prezzo per quantità. */
    val totalCents: Long get() = (priceCents ?: 0L) * qty.coerceAtLeast(1)
}

/**
 * Una nota: testo libero oppure lista (anche della spesa, coi prezzi e il totale).
 * Le note vivono nelle impostazioni, e quindi nel backup, senza una tabella nel database.
 */
@Serializable
data class Note(
    val id: String = newId(),
    val title: String = "",
    val text: String = "",
    val lines: List<NoteLine> = emptyList(),
    val isList: Boolean = false,
    /** Lista con prezzi: mostra prezzo e quantità di ogni riga e il totale. */
    val prices: Boolean = false,
    val pinned: Boolean = false,
    val updatedAt: Long = 0,
) {
    val totalCents: Long get() = lines.sumOf { it.totalCents }
    val inCartCents: Long get() = lines.filter { it.done }.sumOf { it.totalCents }
    val isEmpty: Boolean get() = title.isBlank() && text.isBlank() && lines.none { it.text.isNotBlank() || it.priceCents != null }

    /** Contenuto di una nota in una frase: le prime righe del testo o l'avanzamento della lista. */
    val summary: String
        get() = if (isList) lines.filter { it.text.isNotBlank() }.let { l -> l.take(3).joinToString(" · ") { it.text } }
        else text.lineSequence().filter { it.isNotBlank() }.take(2).joinToString(" · ")

    /** Per riconoscere una nota già importata: stesso titolo e stesso contenuto. */
    val contentKey: String get() = title.trim() + "\u0000" + text.trim() + "\u0000" + lines.joinToString("\u0001") { it.text.trim() }
}

/** Da testo libero a lista (una riga per elemento) e ritorno. */
fun Note.toList(): Note = if (isList) this else copy(
    isList = true,
    lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { NoteLine(text = it) }.toList(),
    text = "",
)

fun Note.toText(): Note = if (!isList) this else copy(
    isList = false,
    text = lines.filter { it.text.isNotBlank() }.joinToString("\n") { it.text },
    lines = emptyList(),
    prices = false,
)
