package com.ricordella.app.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSummaryTest {

    @Test
    fun saysHowManyThingsTodayAndHowManyAreOverdue() {
        assertEquals("Tutto sotto controllo. Goditi la giornata.", summary(0, 0))
        assertEquals("Oggi hai 1 cosa.", summary(1, 0))
        assertEquals("Oggi hai 3 cose. 1 è scaduta.", summary(3, 1))
        assertEquals("Oggi niente in programma. 2 sono scadute.", summary(0, 2))
    }
}
