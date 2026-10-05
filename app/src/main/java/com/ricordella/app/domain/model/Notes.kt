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

/** Una registrazione audio allegata a una nota: file nella cartella dei media e durata. */
@Serializable
data class NoteAudio(val id: String = newId(), val uri: String, val durationMs: Long = 0)

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
    val createdAt: Long = 0,
    /** Nota di tipo disegno: [drawing] è l'immagine (PNG) della tela. */
    val isDrawing: Boolean = false,
    val drawing: String? = null,
    /** Immagini caricate (copie compresse) e registrazioni audio allegate. */
    val images: List<String> = emptyList(),
    val audios: List<NoteAudio> = emptyList(),
) {
    val totalCents: Long get() = lines.sumOf { it.totalCents }
    val inCartCents: Long get() = lines.filter { it.done }.sumOf { it.totalCents }
    val isEmpty: Boolean
        get() = title.isBlank() && text.isBlank() && lines.none { it.text.isNotBlank() || it.priceCents != null } &&
            drawing == null && images.isEmpty() && audios.isEmpty()

    /** Tutti i file della nota (disegno, immagini, audio). */
    val fileUris: List<String> get() = listOfNotNull(drawing) + images + audios.map { it.uri }

    /** Sostituisce i riferimenti ai file secondo [mapping] (dopo un ripristino da backup). */
    fun withFileUris(mapping: Map<String, String>): Note = copy(
        drawing = drawing?.let { mapping[it] ?: it },
        images = images.map { mapping[it] ?: it },
        audios = audios.map { it.copy(uri = mapping[it.uri] ?: it.uri) },
    )

    /** Per la ricerca: il testo si cerca in titolo, testo e voci della lista. */
    fun matches(query: String): Boolean =
        title.contains(query, ignoreCase = true) || text.contains(query, ignoreCase = true) || lines.any { it.text.contains(query, ignoreCase = true) }

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
