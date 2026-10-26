package com.ricordella.app.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class MostUsedFirstTest {

    private val options = listOf("a", "b", "c", "d", "e", "f")

    @Test
    fun withoutUsesKeepsListOrder() =
        assertEquals(listOf("a", "b", "c", "d") to listOf("e", "f"), mostUsedFirst(options, emptyMap()) { it })

    @Test
    fun mostUsedGoOnTopAndTheRestKeepsItsOrder() =
        assertEquals(listOf("f", "c", "a", "b") to listOf("d", "e"), mostUsedFirst(options, mapOf("f" to 5, "c" to 2)) { it })
}
