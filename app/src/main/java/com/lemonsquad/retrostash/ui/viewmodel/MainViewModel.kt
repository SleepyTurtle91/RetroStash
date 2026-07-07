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
import com.lemonsquad.retrostash.data.model.ArchiveMetadata
import com.lemonsquad.retrostash.data.remote.ArchiveApiService
import com.lemonsquad.retrostash.service.ArchiveDownloadManager
import com.lemonsquad.retrostash.service.DownloadQueueManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
    val identifier: String,
    val uploader: String? = null
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

    private val queueManager = DownloadQueueManager(application)
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
                // Perform filtering in a background thread to avoid blocking UI during large list processing
                val filteredFilenames = withContext(Dispatchers.Default) {
                    AIFilterEngine.filterCollection(
                        apiKey = apiKey,
                        userRequest = userRequest,
                        rawFileList = currentFiles
                    )
                }

                if (filteredFilenames == null) {
                    _aiFilterEvent.value = AiFilterEvent.Error("AI Filtering failed. Keeping existing results.")
                } else if (filteredFilenames.isNotEmpty()) {
                    _uiState.value = _uiState.value.filter { state ->
                        filteredFilenames.contains(state.file.name)
                    }
                } else {
                    _aiFilterEvent.value = AiFilterEvent.Error("AI found no matches.")
                }
            } catch (t: Throwable) {
                Log.e("MainViewModel", "AI Filtering failed", t)
                _aiFilterEvent.value = AiFilterEvent.Error("AI Filtering failed: ${t.message}")
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
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
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

                val allFoundFileStates = mutableListOf<FileItemState>()

                // If direct identifier worked, convert its files to states
                if (metadata != null && metadata.files.isNotEmpty()) {
                    val allowedExtensions = setOf(
                        "iso", "sfc", "bin", "smd", "gba", "nes", "n64", "chd", "cue",
                        "zip", "7z", "rar", "z64", "v64", "nds", "3ds", "pbp", "cso", "rvz"
                    )
                    val blockedExtensions = setOf("xml", "txt", "json", "png", "jpg")
                    
                    val validFiles = metadata.files.filter { file ->
                        val ext = file.name.substringAfterLast('.', "").lowercase()
                        val lowerName = file.name.lowercase()
                        ext in allowedExtensions && ext !in blockedExtensions && 
                        !lowerName.contains("__ia_thumb") && !lowerName.startsWith("_")
                    }
                    
                    allFoundFileStates.addAll(validFiles.map { 
                        FileItemState(file = it, identifier = targetIdentifier) 
                    })
                }

                // If direct identifier failed or we want more (matching archive.org search), perform search
                if (allFoundFileStates.isEmpty() || !query.contains(":")) {
                    Log.d("MainViewModel", "Performing optimized search for: $query")
                    val searchQuery = com.lemonsquad.retrostash.data.remote.ArchiveQueryBuilder.buildOptimizedQuery(query)
                    val searchResponse = apiService.search(searchQuery)
                    Log.d("MainViewModel", "Search found ${searchResponse.response.docs.size} documents")
                    
                    val allowedExtensions = setOf(
                        "iso", "sfc", "bin", "smd", "gba", "nes", "n64", "chd", "cue",
                        "zip", "7z", "rar", "z64", "v64", "nds", "3ds", "pbp", "cso", "rvz"
                    )

                    // Collect files from top results
                    for (doc in searchResponse.response.docs.take(10)) { 
                        val resultIdentifier = doc.identifier
                        val resultUploader = doc.uploader
                        Log.d("MainViewModel", "Checking search result identifier: $resultIdentifier")
                        val resultMetadata = try {
                            apiService.getMetadata(resultIdentifier)
                        } catch (e: Exception) {
                            null
                        }
                        
                        if (resultMetadata != null) {
                            val validFiles = resultMetadata.files.filter { file ->
                                val ext = file.name.substringAfterLast('.', "").lowercase()
                                val lowerName = file.name.lowercase()
                                ext in allowedExtensions && !lowerName.contains("__ia_thumb") && !lowerName.startsWith("_")
                            }
                            if (validFiles.isNotEmpty()) {
                                if (allFoundFileStates.isEmpty()) {
                                    targetIdentifier = resultIdentifier 
                                }
                                allFoundFileStates.addAll(validFiles.map { 
                                    FileItemState(file = it, identifier = resultIdentifier, uploader = resultUploader) 
                                })
                                Log.i("MainViewModel", "Added ${validFiles.size} files from $resultIdentifier")
                            }
                        }
                    }
                }

                if (allFoundFileStates.isNotEmpty()) {
                    _currentIdentifier.value = targetIdentifier
                    
                    val sortedStates = allFoundFileStates.sortedWith(
                        compareByDescending<FileItemState> { it.file.source == "original" }
                            .thenBy { it.file.name }
                    )

                    Log.i("MainViewModel", "Total Filtered ROM files: ${sortedStates.size}")
                    _uiState.value = sortedStates

                    // Silent AI Auditor Trigger (Fallback logic included)
                    val apiKey = settingsRepository.geminiApiKeyFlow.first()
                    val isAuditorEnabled = settingsRepository.isAiAuditorEnabledFlow.first()
                    
                    if (!apiKey.isNullOrBlank() && isAuditorEnabled) {
                        applyAiFilter(query)
                    } else {
                        Log.i("MainViewModel", "Skipping AI Audit: API Key missing or Auditor disabled.")
                    }
                } else {
                    Log.w("MainViewModel", "No valid files found for query: $query")
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
        val identifier = fileItem.identifier
        val fileName = fileItem.file.name
        queueManager.enqueue(identifier, fileName)
        
        // Update state to Downloading (or queued)
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
