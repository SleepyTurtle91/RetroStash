package com.lemonsquad.retrostash.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lemonsquad.retrostash.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)

    val geminiApiKey: StateFlow<String?> = settingsRepository.geminiApiKeyFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val isAiAuditorEnabled: StateFlow<Boolean> = settingsRepository.isAiAuditorEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val maxActiveDownloads: StateFlow<Int> = settingsRepository.maxActiveDownloadsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 2
        )

    fun saveGeminiApiKey(key: String) {
        viewModelScope.launch {
            settingsRepository.saveGeminiApiKey(key)
        }
    }

    fun clearGeminiApiKey() {
        viewModelScope.launch {
            settingsRepository.clearGeminiApiKey()
        }
    }

    fun setAiAuditorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAiAuditorEnabled(enabled)
        }
    }

    fun setMaxActiveDownloads(count: Int) {
        viewModelScope.launch {
            settingsRepository.setMaxActiveDownloads(count)
        }
    }
}
