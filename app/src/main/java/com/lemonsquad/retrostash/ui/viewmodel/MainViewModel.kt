package com.lemonsquad.retrostash.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import android.content.Intent
import android.net.Uri
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.lemonsquad.retrostash.data.remote.AIFilterEngine
import com.lemonsquad.retrostash.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import com.lemonsquad.retrostash.data.model.ArchiveFile
import com.lemonsquad.retrostash.data.remote.ArchiveApiService
import com.lemonsquad.retrostash.service.ArchiveDownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

enum class FileStatus {
    IDLE, DOWNLOADING, EXTRACTING, COMPLETED, ERROR
}

data class FileItemState(
    val file: ArchiveFile,
    val status: FileStatus = FileStatus.IDLE,
)

sealed class AiFilterEvent {
    object MissingKey : AiFilterEvent()
    data class Error(val message: String) : AiFilterEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<List<FileItemState>>(emptyList())
    val uiState: StateFlow<List<FileItemState>> = _uiState.asStateFlow()

    private val _isLoading = MutableStateFlow(value = false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isAiFiltering = MutableStateFlow(false)
    val isAiFiltering: StateFlow<Boolean> = _isAiFiltering.asStateFlow()

    private val _aiFilterEvent = MutableStateFlow<AiFilterEvent?>(null)
    val aiFilterEvent: StateFlow<AiFilterEvent?> = _aiFilterEvent.asStateFlow()

    private val downloadManager = ArchiveDownloadManager(application)
    private val workManager = WorkManager.getInstance(application)
    private val settingsRepository = SettingsRepository(application)

    private val _selectedFolderUri = MutableStateFlow<String?>(null)
    val selectedFolderUri: StateFlow<String?> = _selectedFolderUri.asStateFlow()

    init {
        viewModelScope.launch {
            _selectedFolderUri.value = settingsRepository.sdCardUriFlow.first()
        }
    }

    fun consumeAiFilterEvent() {
        _aiFilterEvent.value = null
    }

    fun applyAiFilter(userRequest: String) {
        val currentFiles = _uiState.value.map { it.file }
        if (currentFiles.isEmpty()) return

        viewModelScope.launch {
            val apiKey = settingsRepository.geminiApiKeyFlow.first()
            if (apiKey.isNullOrBlank()) {
                _aiFilterEvent.value = AiFilterEvent.MissingKey
                return@launch
            }

            _isAiFiltering.value = true
            try {
                val filteredFilenames = AIFilterEngine.filterCollection(
                    apiKey = apiKey,
                    userRequest = userRequest,
                    rawFileList = currentFiles
                )

                if (filteredFilenames.isNotEmpty()) {
                    _uiState.value = _uiState.value.filter { state ->
                        filteredFilenames.contains(state.file.name)
                    }
                } else {
                    _aiFilterEvent.value = AiFilterEvent.Error("AI found no matches or encountered an error.")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "AI Filtering failed", e)
                _aiFilterEvent.value = AiFilterEvent.Error("AI Filtering failed: ${e.message}")
            } finally {
                _isAiFiltering.value = false
            }
        }
    }

    fun saveFolderUri(uri: Uri) {
        val context = getApplication<Application>()
        val takeFlags: Int = (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        
        viewModelScope.launch {
            settingsRepository.saveSdCardUri(uri.toString())
            _selectedFolderUri.value = uri.toString()
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor { message -> 
            Log.i("ArchiveApi", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://archive.org/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val apiService = retrofit.create(ArchiveApiService::class.java)

    private val _currentIdentifier = MutableStateFlow<String?>(null)
    val currentIdentifier: StateFlow<String?> = _currentIdentifier.asStateFlow()

    fun loadCollection(query: String) {
        if (query.isBlank()) return
        Log.i("MainViewModel", "loadCollection query: $query")
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Try direct identifier first
                var targetIdentifier = query
                Log.d("MainViewModel", "Attempting direct identifier: $targetIdentifier")
                var metadata = try {
                    apiService.getMetadata(query)
                } catch (e: Exception) {
                    null
                }

                // If direct identifier fails or has no files, try search
                if (metadata == null || metadata.files.isEmpty()) {
                    Log.d("MainViewModel", "Direct identifier failed or empty, trying search")
                    val searchQuery = "$query mediatype:software"
                    val searchResponse = apiService.search(searchQuery)
                    Log.d("MainViewModel", "Search found ${searchResponse.response.docs.size} documents")
                    
                    // Try top results until we find one with archive files
                    for (doc in searchResponse.response.docs) {
                        targetIdentifier = doc.identifier
                        Log.d("MainViewModel", "Checking search result identifier: $targetIdentifier")
                        val potentialMetadata = try {
                            apiService.getMetadata(targetIdentifier)
                        } catch (e: Exception) {
                            null
                        }
                        
                        val allowedExtensions = setOf("iso", "sfc", "bin", "smd", "gba", "nes", "n64", "chd", "cue")
                        if (potentialMetadata != null && potentialMetadata.files.any { file ->
                            val ext = file.name.substringAfterLast('.', "").lowercase()
                            ext in allowedExtensions
                        }) {
                            Log.i("MainViewModel", "Found valid collection in search result: $targetIdentifier")
                            metadata = potentialMetadata
                            break
                        }
                    }
                }

                if (metadata != null) {
                    _currentIdentifier.value = targetIdentifier
                    Log.i("MainViewModel", "Loaded metadata for identifier: $targetIdentifier. Total files: ${metadata.files.size}")
                    
                    val allowedExtensions = setOf("iso", "sfc", "bin", "smd", "gba", "nes", "n64", "chd", "cue")
                    val blockedExtensions = setOf("xml", "txt", "json", "png", "jpg", "zip", "7z", "rar")
                    
                    val archiveFiles = metadata.files.asSequence()
                        .filter { file ->
                            val ext = file.name.substringAfterLast('.', "").lowercase()
                            val lowerName = file.name.lowercase()
                            
                            // Refined filtering criteria
                            val isAllowedExt = ext in allowedExtensions
                            val isBlockedExt = ext in blockedExtensions
                            val isThumb = lowerName.contains("__ia_thumb")
                            val isMetadataDir = lowerName.startsWith("_")
                            
                            if (isAllowedExt && !isBlockedExt && !isThumb && !isMetadataDir) {
                                 Log.d("MainViewModel", "Including file: ${file.name} (Source: ${file.source})")
                                 true
                            } else {
                                 false
                            }
                        }
                        .sortedWith(
                            compareByDescending<ArchiveFile> { it.source == "original" }
                                .thenBy { it.name }
                        )
                        .toList()

                    Log.i("MainViewModel", "Filtered ROM files: ${archiveFiles.size}")
                    _uiState.value = archiveFiles.map { FileItemState(it) }
                } else {
                    Log.w("MainViewModel", "No metadata found for query: $query")
                    _uiState.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading collection for query: $query", e)
                _uiState.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun downloadFile(fileItem: FileItemState) {
        val identifier = _currentIdentifier.value ?: return
        val folderUri = _selectedFolderUri.value ?: return
        val fileName = fileItem.file.name
        downloadManager.enqueueDownload(identifier, fileName)
        
        // Update state to Downloading
        updateFileStatus(fileName, FileStatus.DOWNLOADING)
        
        // Observe WorkManager for unzipping status
        viewModelScope.launch {
            workManager.getWorkInfosByTagFlow("unzip_$fileName").collect { workInfos ->
                val workInfo = workInfos.firstOrNull()
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> updateFileStatus(fileName, FileStatus.EXTRACTING)
                        WorkInfo.State.SUCCEEDED -> updateFileStatus(fileName, FileStatus.COMPLETED)
                        WorkInfo.State.FAILED -> updateFileStatus(fileName, FileStatus.ERROR)
                        else -> {}
                    }
                }
            }
        }
    }

    private fun updateFileStatus(fileName: String, status: FileStatus) {
        _uiState.value = _uiState.value.map {
            if (it.file.name == fileName) it.copy(status = status) else it
        }
    }
}
