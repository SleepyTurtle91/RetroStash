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
    private val KEY_SYNC_FOLDER_URI = stringPreferencesKey("sync_folder_uri")
    private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
    private val KEY_AI_AUDITOR_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("ai_auditor_enabled")
    private val KEY_MAX_ACTIVE_DOWNLOADS = androidx.datastore.preferences.core.intPreferencesKey("max_active_downloads")

    val sdCardUriFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_SD_CARD_URI]
        }

    val syncFolderUriFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_SYNC_FOLDER_URI]
        }

    val geminiApiKeyFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_GEMINI_API_KEY]
        }

    val isAiAuditorEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_AI_AUDITOR_ENABLED] ?: false
        }

    val maxActiveDownloadsFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_MAX_ACTIVE_DOWNLOADS] ?: 2
        }

    suspend fun saveSdCardUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SD_CARD_URI] = uri
        }
    }

    suspend fun saveSyncFolderUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SYNC_FOLDER_URI] = uri
        }
    }

    suspend fun saveGeminiApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_GEMINI_API_KEY] = key
        }
    }

    suspend fun setAiAuditorEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AI_AUDITOR_ENABLED] = enabled
        }
    }

    suspend fun setMaxActiveDownloads(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MAX_ACTIVE_DOWNLOADS] = count
        }
    }

    suspend fun clearGeminiApiKey() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_GEMINI_API_KEY)
        }
    }
}
