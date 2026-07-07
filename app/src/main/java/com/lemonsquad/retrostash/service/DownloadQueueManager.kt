package com.lemonsquad.retrostash.service

import android.app.DownloadManager
import android.content.Context
import android.util.Log
import com.lemonsquad.retrostash.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class QueuedDownload(
    val identifier: String,
    val filename: String,
    var downloadId: Long? = null
)

class DownloadQueueManager(private val context: Context) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val archiveDownloadManager = ArchiveDownloadManager(context)
    private val settingsRepository = SettingsRepository(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pendingQueue = MutableStateFlow<List<QueuedDownload>>(emptyList())
    val pendingQueue: StateFlow<List<QueuedDownload>> = _pendingQueue.asStateFlow()

    private val _activeDownloads = MutableStateFlow<Map<String, Long>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, Long>> = _activeDownloads.asStateFlow()

    fun enqueue(identifier: String, filename: String) {
        val newItem = QueuedDownload(identifier, filename)
        _pendingQueue.value = _pendingQueue.value + newItem
        processQueue()
    }

    fun cancel(filename: String) {
        // Remove from pending queue
        _pendingQueue.value = _pendingQueue.value.filter { it.filename != filename }
        
        // Remove from active downloads and cancel in DownloadManager
        val downloadId = _activeDownloads.value[filename]
        if (downloadId != null) {
            downloadManager.remove(downloadId)
            _activeDownloads.value = _activeDownloads.value - filename
        }
        processQueue()
    }

    fun onDownloadCompleted(downloadId: Long) {
        val entry = _activeDownloads.value.entries.find { it.value == downloadId }
        if (entry != null) {
            _activeDownloads.value = _activeDownloads.value - entry.key
            processQueue()
        }
    }

    private fun processQueue() {
        scope.launch {
            val maxActive = settingsRepository.maxActiveDownloadsFlow.first()
            val currentActiveCount = _activeDownloads.value.size
            
            if (currentActiveCount < maxActive && _pendingQueue.value.isNotEmpty()) {
                val nextToDownload = _pendingQueue.value.first()
                _pendingQueue.value = _pendingQueue.value.drop(1)
                
                Log.d("DownloadQueue", "Starting download for ${nextToDownload.filename}")
                val downloadId = archiveDownloadManager.enqueueDownload(nextToDownload.identifier, nextToDownload.filename)
                _activeDownloads.value = _activeDownloads.value + (nextToDownload.filename to downloadId)
                
                // Recursively process if we still have slots
                processQueue()
            }
        }
    }
}
