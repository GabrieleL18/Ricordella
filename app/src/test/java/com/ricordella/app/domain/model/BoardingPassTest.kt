package com.ricordella.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BoardingPassTest {

    private val today = LocalDate.of(2026, 9, 30)

    @Test
    fun readsSingleLeg() {
        // Esempio IATA: volo AZ 0123 da FCO a JFK, giorno 300 (27 ottobre), posto 012A.
        val raw = "M1ROSSI/MARIO         EABC123 FCOJFKAZ 0123 300Y012A0045 100"
        val leg = BoardingPass.parse(raw, today)!!.single()
        assertEquals("AZ", leg.carrier)
        assertEquals("AZ123", leg.code)
        assertEquals("FCO", leg.from)
        assertEquals("JFK", leg.to)
        assertEquals(LocalDate.of(2026, 10, 27), leg.date)
        assertEquals("12A", leg.seat)
        assertEquals("ABC123", leg.bookingCode)
    }

    @Test
    fun readsTwoLegsAndRollsYear() {
        // Giorno 010: già passato quest'anno, quindi è il 10 gennaio 2027. Il primo volo ha 2 caratteri condizionali.
        val raw = "M2BIANCHI/ANNA        EXYZ789 MXPFRAFR 4567 010Y003C0001 102ABXYZ789 FRALHRLH 0901 010Y021F0002 100"
        val legs = BoardingPass.parse(raw, today)!!
        assertEquals(2, legs.size)
        assertEquals(LocalDate.of(2027, 1, 10), legs[0].date)
        assertEquals("FRA", legs[1].from)
        assertEquals("LH901", legs[1].code)
        assertEquals("21F", legs[1].seat)
    }

    @Test
    fun rejectsOtherCodes() {
        assertNull(BoardingPass.parse("https://example.com", today))
    }
}
