package com.ricordella.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseProfilesTest {

    @Test
    fun `il rifornimento esiste solo per i veicoli`() {
        ItemKind.entries.forEach { kind ->
            val profile = ExpenseProfiles.of(kind, kind.group)
            assertEquals(kind.name, kind.group == ItemGroup.VEHICLES, ExpenseKind.FUEL in profile.kinds)
            assertTrue(kind.name, ExpenseKind.SERVICE in profile.kinds && ExpenseKind.OTHER in profile.kinds)
        }
    }

    @Test
    fun `suggerimenti e nomi adatti al tipo`() {
        val washing = ExpenseProfiles.of(ItemKind.WASHING_MACHINE, ItemGroup.HOME)
        assertFalse("Tagliando" in washing.serviceTitles)
        assertTrue("Pulizia filtro" in washing.serviceTitles)
        assertEquals(null, washing.nextKm)

        assertEquals(15_000, ExpenseProfiles.of(ItemKind.CAR, ItemGroup.VEHICLES).nextKm)
        assertEquals("Riparazione", ExpenseProfiles.of(ItemKind.SMARTPHONE, ItemGroup.ELECTRONICS).label(ExpenseKind.SERVICE))
        assertEquals("Rinnovo", ExpenseProfiles.of(ItemKind.PERSONAL_DOCUMENT, ItemGroup.DOCUMENTS).label(ExpenseKind.SERVICE))
    }
}
