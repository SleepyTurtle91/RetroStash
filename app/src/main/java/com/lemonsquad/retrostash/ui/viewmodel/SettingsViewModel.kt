package com.lemonsquad.retrostash.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lemonsquad.retrostash.data.remote.AIFilterEngine
import com.lemonsquad.retrostash.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)

    val sdCardUri: StateFlow<String?> = settingsRepository.sdCardUriFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val syncFolderUri: StateFlow<String?> = settingsRepository.syncFolderUriFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _apiHealthStatus = MutableStateFlow<Result<String>?>(null)
    val apiHealthStatus: StateFlow<Result<String>?> = _apiHealthStatus.asStateFlow()

    private val _isCheckingHealth = MutableStateFlow(false)
    val isCheckingHealth: StateFlow<Boolean> = _isCheckingHealth.asStateFlow()

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

    fun saveSdCardUri(uri: Uri) {
        viewModelScope.launch {
            settingsRepository.saveSdCardUri(uri.toString())
        }
    }

    fun saveSyncFolderUri(uri: Uri) {
        viewModelScope.launch {
            settingsRepository.saveSyncFolderUri(uri.toString())
        }
    }

    fun checkApiHealth(apiKey: String) {
        viewModelScope.launch {
            _isCheckingHealth.value = true
            _apiHealthStatus.value = null
            val result = AIFilterEngine.checkApiHealth(apiKey)
            _apiHealthStatus.value = result
            _isCheckingHealth.value = false
        }
    }

    fun clearApiHealthStatus() {
        _apiHealthStatus.value = null
    }
}
