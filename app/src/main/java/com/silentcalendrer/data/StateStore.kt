package com.silentcalendrer.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.silentcalendrer.core.EngineState
import com.silentcalendrer.core.StatePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Persists [EngineState] in device-protected storage, so it can be read right after a reboot,
 * before the user unlocks the phone. It holds only times and flags, never meeting titles.
 * Must be a single instance per process (DataStore requirement); AppContainer guarantees that.
 */
class StateStore(context: Context) : StatePort {

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = {
            context.applicationContext.createDeviceProtectedStorageContext().preferencesDataStoreFile("engine_state")
        },
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    val states: Flow<EngineState> = dataStore.data.map { decode(it[KEY]) }

    override suspend fun read(): EngineState = states.first()

    override suspend fun write(state: EngineState) {
        dataStore.edit { it[KEY] = json.encodeToString(EngineState.serializer(), state) }
    }

    private fun decode(raw: String?): EngineState {
        if (raw == null) return EngineState()
        return try {
            json.decodeFromString(EngineState.serializer(), raw)
        } catch (e: Exception) {
            Log.w("StateStore", "Discarding unreadable state", e)
            EngineState()
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("engine_state_json")
    }
}
