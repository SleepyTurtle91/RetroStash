package com.lemonsquad.retrostash.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val KEY_SD_CARD_URI = stringPreferencesKey("sd_card_uri")
    private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")

    val sdCardUriFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_SD_CARD_URI]
        }

    val geminiApiKeyFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_GEMINI_API_KEY]
        }

    suspend fun saveSdCardUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SD_CARD_URI] = uri
        }
    }

    suspend fun saveGeminiApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_GEMINI_API_KEY] = key
        }
    }

    suspend fun clearGeminiApiKey() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_GEMINI_API_KEY)
        }
    }
}
