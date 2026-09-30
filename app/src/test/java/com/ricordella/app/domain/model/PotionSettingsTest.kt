package com.ricordella.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class PotionSettingsTest {

    private val potions = PotionSettings(everyMinutes = 90, from = LocalTime.of(9, 0), until = LocalTime.of(12, 0))
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 9, day, hour, minute)

    @Test
    fun nextReminderFollowsTheWindow() {
        assertEquals(at(30, 9), potions.nextReminder(at(30, 7)))
        assertEquals(at(30, 10, 30), potions.nextReminder(at(30, 9)))
        assertEquals(at(30, 12), potions.nextReminder(at(30, 11)))
        // Finita la fascia si passa al mattino dopo.
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 0), potions.nextReminder(at(30, 12)))
    }

    @Test
    fun drankResetsOnANewDay() {
        val today = potions.drink(100, 250).drink(100, 250)
        assertEquals(500, today.drankOn(100))
        assertEquals(0, today.drankOn(101))
        assertEquals(250, today.drink(101, 250).drankOn(101))
        assertEquals(0, today.drink(100, -1000).drankOn(100))
    }
}
