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
import com.lemonsquad.retrostash.data.remote.ArchiveCategory
import com.lemonsquad.retrostash.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import com.lemonsquad.retrostash.data.model.ArchiveFile
import com.lemonsquad.retrostash.data.model.ArchiveMetadata
import com.lemonsquad.retrostash.data.remote.ArchiveApiService
import com.lemonsquad.retrostash.data.remote.ArchiveQueryBuilder
import com.lemonsquad.retrostash.service.ArchiveDownloadManager
import com.lemonsquad.retrostash.service.DownloadQueueManager
import com.lemonsquad.retrostash.service.MetadataManager
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

    private val _isSyncingMetadata = MutableStateFlow(false)
    val isSyncingMetadata: StateFlow<Boolean> = _isSyncingMetadata.asStateFlow()

    private val _syncProgress = MutableStateFlow("")
    val syncProgress: StateFlow<String> = _syncProgress.asStateFlow()

    private val _aiFilterEvent = MutableStateFlow<AiFilterEvent?>(null)
    val aiFilterEvent: StateFlow<AiFilterEvent?> = _aiFilterEvent.asStateFlow()

    private val queueManager = DownloadQueueManager(application)
    private val workManager = WorkManager.getInstance(application)
    private val settingsRepository = SettingsRepository(application)

    private val _selectedFolderUri = MutableStateFlow<String?>(null)
    val selectedFolderUri: StateFlow<String?> = _selectedFolderUri.asStateFlow()

    private val _syncFolderUri = MutableStateFlow<String?>(null)
    val syncFolderUri: StateFlow<String?> = _syncFolderUri.asStateFlow()

    private val _selectedCategory = MutableStateFlow(ArchiveCategory.ALL)
    val selectedCategory: StateFlow<ArchiveCategory> = _selectedCategory.asStateFlow()

    init {
        viewModelScope.launch {
            _selectedFolderUri.value = settingsRepository.sdCardUriFlow.first()
            _syncFolderUri.value = settingsRepository.syncFolderUriFlow.first()
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
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags)
        
        viewModelScope.launch {
            settingsRepository.saveSdCardUri(uri.toString())
            _selectedFolderUri.value = uri.toString()
        }
    }

    fun saveSyncFolderUri(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags)
        
        viewModelScope.launch {
            settingsRepository.saveSyncFolderUri(uri.toString())
            _syncFolderUri.value = uri.toString()
        }
    }

    fun selectCategory(category: ArchiveCategory) {
        _selectedCategory.value = category
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

    fun loadCollection(identifierOrSearch: String) {
        if (identifierOrSearch.isBlank()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            _uiState.value = emptyList() // Clear old results immediately
            try {
                // 1. Try to load as a direct identifier first
                val targetIdentifier = try {
                    val metadata = apiService.getMetadata(identifierOrSearch)
                    if (metadata.files.isEmpty()) throw Exception("Empty metadata")
                    identifierOrSearch
                } catch (e: Exception) {
                    // 2. If metadata fails, treat as a search query
                    val query = ArchiveQueryBuilder.buildOptimizedQuery(identifierOrSearch, _selectedCategory.value)
                    val searchResponse = apiService.search(query, rows = 150) // Increased row count
                    
                    val results = searchResponse.response.docs
                    if (results.isEmpty()) throw Exception("No matches found for $identifierOrSearch")
                    
                    // If multiple results, you might want to show a list of collections, 
                    // but for now we follow existing logic of picking the first one.
                    results.first().identifier
                }

                _currentIdentifier.value = targetIdentifier
                val metadata = apiService.getMetadata(targetIdentifier)
                val fileItemStates = metadata.files.map { file ->
                    FileItemState(file = file, identifier = targetIdentifier)
                }

                val isAuditorEnabled = settingsRepository.isAiAuditorEnabledFlow.first()
                if (isAuditorEnabled && fileItemStates.isNotEmpty()) {
                    val apiKey = settingsRepository.geminiApiKeyFlow.first()
                    if (!apiKey.isNullOrBlank()) {
                        _isAiFiltering.value = true
                        try {
                            val currentFiles = fileItemStates.map { it.file }
                            val filteredFilenames = withContext(Dispatchers.Default) {
                                AIFilterEngine.filterCollection(
                                    apiKey = apiKey,
                                    userRequest = "Exclude junk files, manuals, duplicate regions (keep USA/En), and non-game files.",
                                    rawFileList = currentFiles
                                )
                            }
                            if (filteredFilenames != null && filteredFilenames.isNotEmpty()) {
                                _uiState.value = fileItemStates.filter { state ->
                                    filteredFilenames.contains(state.file.name)
                                }
                            } else {
                                _uiState.value = fileItemStates
                                _aiFilterEvent.value = AiFilterEvent.Error("AI Auditor returned empty or failed. Showing all files.")
                            }
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "AI Auditor failed", e)
                            _uiState.value = fileItemStates
                            _aiFilterEvent.value = AiFilterEvent.Error("AI Auditor failed: ${e.message}")
                        } finally {
                            _isAiFiltering.value = false
                        }
                    } else {
                        _uiState.value = fileItemStates
                    }
                } else {
                    _uiState.value = fileItemStates
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading collection", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun downloadFile(fileItem: FileItemState) {
        val folderUriStr = _selectedFolderUri.value ?: return
        val folderUri = Uri.parse(folderUriStr)
        val fileName = fileItem.file.name

        // Logic to trigger download via ArchiveDownloadManager (which uses DownloadManager)
        val downloadManager = ArchiveDownloadManager(getApplication())
        val downloadId = downloadManager.enqueueDownload(
            identifier = fileItem.identifier,
            filename = fileName
        )

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

    fun syncMetadata() {
        val folderUriStr = _syncFolderUri.value ?: _selectedFolderUri.value ?: return
        val folderUri = Uri.parse(folderUriStr)
        
        viewModelScope.launch {
            val apiKey = settingsRepository.geminiApiKeyFlow.first()
            if (apiKey.isNullOrBlank()) {
                _aiFilterEvent.value = AiFilterEvent.MissingKey
                return@launch
            }

            _isSyncingMetadata.value = true
            MetadataManager.syncMetadata(
                context = getApplication(),
                folderUri = folderUri,
                apiKey = apiKey,
                onProgress = { message ->
                    _syncProgress.value = message
                }
            )
            _isSyncingMetadata.value = false
        }
    }
}
