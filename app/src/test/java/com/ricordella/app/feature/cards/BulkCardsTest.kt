package com.ricordella.app.feature.cards

import com.ricordella.app.domain.model.LoyaltyCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkCardsTest {

    @Test
    fun guessesTheNameFromPrintedText() {
        assertEquals("Decathlon", guessCardName(listOf("Decathlon", "0123456789")))
        // Salta indirizzi web, email, numeri e parole generiche; prende la prima riga che sembra un nome.
        assertEquals("Bottega del Borgo", guessCardName(listOf("www.bottega.it", "tessera", "1234 5678", "Bottega del Borgo", "Socio")))
        assertEquals("", guessCardName(listOf("0123456789", "ab", "info@negozio.it")))
        assertEquals("", guessCardName(emptyList()))
    }

    @Test
    fun recognizesKnownChainsAnywhereInTheText() {
        // Il marchio vince anche se una riga qualsiasi sopra sembra un nome.
        assertEquals("Esselunga", guessCardName(listOf("Presenta la tessera alla cassa", "ESSELUNGA S.p.A.", "0123456789")))
        assertEquals("Leroy Merlin", guessCardName(listOf("Carta Leroy-Merlin Italia")))
        assertEquals("Decathlon", guessCardName(listOf("DECATHLON ITALIA")))
        assertEquals("MD", guessCardName(listOf("MD S.p.A.", "tessera")))
        assertEquals("Coop", guessCardName(listOf("Carta Coop")))
    }

    @Test
    fun shortChainNamesMatchOnlyAsWholeWords() {
        // «Cooperativa» non è Coop, «Pamela» non è Pam, «Menu» non è Eni, «Capi» non è Api.
        assertEquals("Cooperativa Agricola", guessCardName(listOf("Cooperativa Agricola")))
        assertEquals("Pamela Store", guessCardName(listOf("Pamela Store")))
        assertEquals("Menu del giorno", guessCardName(listOf("Menu del giorno")))
        assertEquals("Capi sportivi", guessCardName(listOf("Capi sportivi")))
    }

    @Test
    fun skipsCodesAlreadySavedOrRepeated() {
        val saved = listOf(LoyaltyCard(name = "Lidl", code = "111", format = 0))
        val found = listOf(DraftCard("111", 1, "Lidl"), DraftCard("222", 1, ""), DraftCard("222", 1, "doppia"), DraftCard("333", 1, ""))
        assertEquals(listOf("222", "333"), newDrafts(found, saved).map { it.code })
    }

    @Test
    fun unnamedCardsGetNumberedNamesAndTheChosenCategory() {
        val drafts = listOf(DraftCard("1", 1, "  Coop "), DraftCard("2", 1, ""), DraftCard("3", 1, "   "))
        val cards = draftsToCards(drafts, "Spesa", now = 5L, unnamed = "Carta senza nome")
        assertEquals(listOf("Coop", "Carta senza nome 1", "Carta senza nome 2"), cards.map { it.name })
        assertTrue(cards.all { it.category == "Spesa" && it.updatedAt == 5L })
        assertEquals(3, cards.map { it.id }.toSet().size)
    }
}
