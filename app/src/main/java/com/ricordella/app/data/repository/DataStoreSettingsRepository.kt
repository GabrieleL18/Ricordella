package com.ricordella.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Preferenze dell'app salvate con DataStore.
 * Le impostazioni sono serializzate come JSON con valori predefiniti: campi nuovi o
 * mancanti ricadono sui default senza bisogno di migrazioni.
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        preferences[SETTINGS_KEY]?.let(::decode) ?: AppSettings()
    }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            val current = preferences[SETTINGS_KEY]?.let(::decode) ?: AppSettings()
            preferences[SETTINGS_KEY] = json.encodeToString(AppSettings.serializer(), transform(current))
        }
    }

    private fun decode(value: String): AppSettings? = try {
        json.decodeFromString(AppSettings.serializer(), value)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        val SETTINGS_KEY = stringPreferencesKey("app_settings")
    }
}
