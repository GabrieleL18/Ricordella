package com.ricordella.app.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {

    private val today = LocalDate.of(2026, 10, 1)

    @Test
    fun readsAnItalianReceipt() {
        val info = ReceiptParser.parse(
            listOf(
                "MEDIAWORLD MILANO",
                "Via Roma 12 - P.IVA 01234567890",
                "DOCUMENTO COMMERCIALE",
                "di vendita o prestazione",
                "LAVATRICE SAMSUNG WW90  499,90",
                "INSTALLAZIONE  30,00",
                "TOTALE COMPLESSIVO  529,90",
                "di cui IVA  95,55",
                "PAGAMENTO ELETTRONICO  529,90",
                "28-09-2026 18:42",
            ),
            today,
        )
        assertEquals("Mediaworld Milano", info.store)
        assertEquals(LocalDate.of(2026, 9, 28), info.date)
        assertEquals(52_990L, info.totalCents)
        assertEquals("Lavatrice Samsung Ww90", info.product)
    }

    @Test
    fun totalOnNextLineAndThousands() {
        val info = ReceiptParser.parse(listOf("Unieuro", "Smartphone XYZ 1.299,00", "TOTALE", "EUR 1.299,00", "01/10/26"), today)
        assertEquals(129_900L, info.totalCents)
        assertEquals(LocalDate.of(2026, 10, 1), info.date)
    }

    @Test
    fun withoutTotalUsesHighestAmount() {
        val info = ReceiptParser.parse(listOf("Negozio", "Cavo 9,90", "Caricatore 24,50"), today)
        assertEquals(2_450L, info.totalCents)
    }
}
