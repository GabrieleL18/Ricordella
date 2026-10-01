package com.ricordella.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ResolutionsTest {

    private val with2026 = AppSettings(resolutions = listOf(Resolution(year = 2026, text = "Leggere", kept = true)))

    @Test
    fun `a gennaio propone di scriverli, una volta sola`() {
        val jan = LocalDate.of(2027, 1, 5)
        assertEquals(ResolutionsPrompt.WRITE to 2027, AppSettings().resolutionsPrompt(jan))
        assertNull(AppSettings(resolutionsAskedYear = 2027).resolutionsPrompt(jan))
        assertNull(AppSettings(resolutions = listOf(Resolution(year = 2027, text = "Correre"))).resolutionsPrompt(jan))
    }

    @Test
    fun `il recap arriva da metà dicembre, prima dei propositi nuovi`() {
        assertNull(with2026.resolutionsPrompt(LocalDate.of(2026, 12, 14)))
        assertEquals(ResolutionsPrompt.RECAP to 2026, with2026.resolutionsPrompt(LocalDate.of(2026, 12, 15)))
        assertEquals(ResolutionsPrompt.RECAP to 2026, with2026.resolutionsPrompt(LocalDate.of(2027, 1, 3)))
        // Visto il recap, a gennaio tocca ai propositi del nuovo anno.
        assertEquals(ResolutionsPrompt.WRITE to 2027, with2026.copy(resolutionsRecapYear = 2026).resolutionsPrompt(LocalDate.of(2027, 1, 3)))
    }

    @Test
    fun `senza propositi niente recap, e durante l'anno nessun invito`() {
        assertNull(AppSettings().resolutionsPrompt(LocalDate.of(2026, 12, 28)))
        assertNull(with2026.resolutionsPrompt(LocalDate.of(2026, 6, 1)))
    }
}
