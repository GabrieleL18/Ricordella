package com.ricordella.app.domain.text

import java.time.LocalDate

/** Dati letti da uno scontrino o da una fattura. */
data class ReceiptInfo(
    val store: String?,
    val date: LocalDate?,
    val totalCents: Long?,
    /** Prima riga che sembra un prodotto (es. "LAVATRICE SAMSUNG WW90"). */
    val product: String?,
)

/**
 * Ricava negozio, data, totale e prodotto dal testo riconosciuto su uno scontrino italiano
 * (va bene anche per molte ricevute in inglese). Il testo arriva riga per riga dal riconoscimento.
 */
object ReceiptParser {

    private val amount = Regex("""(?<![\d.,])(\d{1,3}(?:[.\s]\d{3})*|\d+)[.,](\d{2})(?!\d)""")
    private val date = Regex("""\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})\b""")
    private val totalWords = Regex("""\b(totale|tot\.?\s*complessivo|importo\s+pagato|pagato|total|amount\s+due|da\s+pagare)\b""", RegexOption.IGNORE_CASE)
    private val notProduct = Regex(
        """\b(totale|subtotale|sub\s*totale|iva|imponibile|contanti|carta|resto|bancomat|pagamento|sconto|euro|eur|documento|commerciale|scontrino|p\.?\s*iva|c\.?f\.?|tel|via|piazza|cassa|operatore|transazione|total|subtotal|tax|vat|cash|change|card)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val notStore = Regex("""\b(documento|commerciale|scontrino|fiscale|p\.?\s*iva|partita|c\.?f\.?|tel|telefono|www|http|vendita|receipt)\b""", RegexOption.IGNORE_CASE)

    fun parse(lines: List<String>, today: LocalDate = LocalDate.now()): ReceiptInfo {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }

        val store = clean.take(6).firstOrNull { line ->
            line.count { it.isLetter() } >= 3 && !notStore.containsMatchIn(line) && amount.find(line) == null && date.find(line) == null
        }?.let(::titleCase)

        val receiptDate = clean.asSequence()
            .flatMap { date.findAll(it) }
            .mapNotNull { m ->
                val year = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
                runCatching { LocalDate.of(year, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
            }
            .firstOrNull { !it.isAfter(today.plusDays(1)) && it.year > 1990 }

        // Totale: l'importo sulla riga "TOTALE" (o su quella subito dopo); se manca, l'importo più alto.
        val total = clean.indices.firstNotNullOfOrNull { i ->
            if (!totalWords.containsMatchIn(clean[i]) || clean[i].contains("sub", ignoreCase = true)) return@firstNotNullOfOrNull null
            cents(clean[i]) ?: clean.getOrNull(i + 1)?.let(::cents)
        } ?: clean.mapNotNull(::cents).maxOrNull()

        val product = clean.firstOrNull { line ->
            val text = amount.replace(line, "").trim()
            text.count { it.isLetter() } >= 4 && amount.find(line) != null && !notProduct.containsMatchIn(line)
        }?.let { amount.replace(it, "").replace(Regex("""[€*xX]\s*$|\s+\d+\s*[xX]\s*$"""), "").trim() }?.let(::titleCase)

        return ReceiptInfo(store, receiptDate, total, product)
    }

    private fun cents(line: String): Long? = amount.findAll(line).lastOrNull()?.let { m ->
        val euros = m.groupValues[1].replace(".", "").replace(" ", "").toLongOrNull() ?: return null
        euros * 100 + m.groupValues[2].toLong()
    }

    /** "MEDIAWORLD MILANO" → "Mediaworld Milano" (gli scontrini sono quasi sempre in maiuscolo). */
    private fun titleCase(text: String): String =
        text.lowercase().split(" ").filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
