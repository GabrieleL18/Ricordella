package com.ricordella.app.feature.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceParsingTest {

    @Test
    fun `prezzi in formato italiano e internazionale`() {
        assertEquals(49_990L, parseCents("499,90"))
        assertEquals(49_990L, parseCents("499.90"))
        assertEquals(120_000L, parseCents("1.200,00"))
        assertEquals(32_000L, parseCents("€ 320"))
        assertNull(parseCents(""))
        assertEquals("499,90", formatCents(49_990))
    }
}
