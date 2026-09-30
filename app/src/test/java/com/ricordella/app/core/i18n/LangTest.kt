package com.ricordella.app.core.i18n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class LangTest {

    @After
    fun backToItalian() = Lang.forceEnglish(false)

    @Test
    fun translatesOnlyInEnglish() {
        assertEquals("Salva", tr("Salva"))
        Lang.forceEnglish(true)
        assertEquals("Save", tr("Salva"))
        assertEquals("Every 3 days", trf("Ogni %1\$s giorni", 3))
        // Senza traduzione resta il testo originale.
        assertEquals("Palermo", tr("Palermo"))
    }
}
