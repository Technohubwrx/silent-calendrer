package com.silentcalendrer.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.silentcalendrer.core.SettingsPort
import com.silentcalendrer.core.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * User settings, in device-protected storage so the engine can use the chosen minutes right
 * after a reboot, before the first unlock. One instance per process (AppContainer).
 */
class SettingsStore(context: Context) : SettingsPort {

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = {
            context.applicationContext.createDeviceProtectedStorageContext().preferencesDataStoreFile("settings")
        },
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settings: Flow<UserSettings> = dataStore.data.map { decode(it[KEY]) }

    override suspend fun read(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { prefs ->
            val next = transform(decode(prefs[KEY]))
            prefs[KEY] = json.encodeToString(UserSettings.serializer(), next)
        }
    }

    private fun decode(raw: String?): UserSettings {
        if (raw == null) return UserSettings()
        return try {
            json.decodeFromString(UserSettings.serializer(), raw)
        } catch (e: Exception) {
            Log.w("SettingsStore", "Discarding unreadable settings", e)
            UserSettings()
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("settings_json")
    }
}
