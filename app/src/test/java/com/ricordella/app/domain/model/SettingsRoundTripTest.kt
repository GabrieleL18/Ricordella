package com.ricordella.app.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Le impostazioni si salvano come JSON all'avvio: un campo che non si rilegge farebbe crashare l'app. */
class SettingsRoundTripTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun newFieldsSurviveSaveAndLoad() {
        val settings = AppSettings(
            potions = PotionSettings().drink(20_000, 250).drink(20_001, 500),
            cycleProfiles = listOf(CycleProfile("anna", 5, 28, true)),
            cycleLog = listOf(CycleEntry("anna", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5))),
            alarmPreNoticeMinutes = 60,
            alarmsInCalendar = AlarmsInCalendar.ALL,
            backupFolderUri = "content://folder",
        )
        assertEquals(settings, json.decodeFromString(AppSettings.serializer(), json.encodeToString(AppSettings.serializer(), settings)))
    }

    @Test
    fun oldSettingsWithoutNewFieldsStillLoad() {
        val loaded = json.decodeFromString(AppSettings.serializer(), "{}")
        assertEquals(emptyList<CycleProfile>(), loaded.cycleProfiles)
        assertEquals(30, loaded.alarmPreNoticeMinutes)
    }
}
