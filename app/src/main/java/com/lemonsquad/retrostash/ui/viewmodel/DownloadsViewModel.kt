package com.lemonsquad.retrostash.ui.viewmodel

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.lemonsquad.retrostash.data.model.DownloadStatus
import com.lemonsquad.retrostash.data.model.DownloadTask
import com.lemonsquad.retrostash.service.DownloadQueueManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadManager = application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val workManager = WorkManager.getInstance(application)
    private val queueManager = DownloadQueueManager(application)

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _workTasks = MutableStateFlow<List<DownloadTask>>(emptyList())

    init {
        startPolling()
        observeWorkManager()
    }

    private fun startPolling() {
        viewModelScope.launch {
            combine(
                flow { while(true) { emit(Unit); delay(1000) } },
                queueManager.pendingQueue
            ) { _, pending ->
                updateDownloadTasks(pending.map { it.filename })
            }.collect()
        }
    }

    private fun updateDownloadTasks(appQueuedFiles: List<String>) {
        val query = DownloadManager.Query()
        val cursor: Cursor? = try { downloadManager.query(query) } catch (e: Exception) { null }
        val currentTasks = mutableListOf<DownloadTask>()

        // Add App-level queued tasks first
        appQueuedFiles.forEach { fileName ->
            currentTasks.add(
                DownloadTask(
                    id = "queued_$fileName",
                    fileName = fileName,
                    status = DownloadStatus.QUEUED,
                    progress = 0f
                )
            )
        }

        if (cursor != null && cursor.moveToFirst()) {
            val idIdx = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
            val descIdx = cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)
            val titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val totalSizeIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val downloadedSizeIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)

            do {
                if (descIdx != -1) {
                    val description = cursor.getString(descIdx)

                    if (description == "RetroStash is downloading your game") {
                        val id = cursor.getLong(idIdx)
                        val title = cursor.getString(titleIdx)
                        val status = cursor.getInt(statusIdx)
                        val totalSize = cursor.getLong(totalSizeIdx)
                        val downloadedSize = cursor.getLong(downloadedSizeIdx)
                        
                        val fileName = title.replace("Downloading ", "")
                        
                        val downloadStatus = when (status) {
                            DownloadManager.STATUS_PENDING -> DownloadStatus.QUEUED
                            DownloadManager.STATUS_RUNNING -> DownloadStatus.DOWNLOADING
                            DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETED
                            DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                            else -> DownloadStatus.QUEUED
                        }

                        val progress = if (totalSize > 0) downloadedSize.toFloat() / totalSize else 0f

                        currentTasks.add(
                            DownloadTask(
                                id = id.toString(),
                                fileName = fileName,
                                status = downloadStatus,
                                progress = progress,
                                totalSize = totalSize,
                                downloadedSize = downloadedSize
                            )
                        )
                    }
                }
            } while (cursor.moveToNext())
            cursor.close()
        }

        _tasks.value = mergeTasks(currentTasks, _workTasks.value)
    }

    private fun observeWorkManager() {
        viewModelScope.launch {
            workManager.getWorkInfosByTagFlow("unzip").collect { workInfos ->
                val tasks = workInfos.map { workInfo ->
                    val fileNameTag = workInfo.tags.firstOrNull { it.startsWith("unzip_") }
                    val fileName = fileNameTag?.removePrefix("unzip_") ?: "Unknown"
                    val progress = workInfo.progress.getFloat("progress", 0f)
                    
                    val status = when (workInfo.state) {
                        WorkInfo.State.ENQUEUED -> DownloadStatus.QUEUED
                        WorkInfo.State.RUNNING -> DownloadStatus.EXTRACTING
                        WorkInfo.State.SUCCEEDED -> DownloadStatus.COMPLETED
                        WorkInfo.State.FAILED -> DownloadStatus.FAILED
                        else -> DownloadStatus.QUEUED
                    }

                    DownloadTask(
                        id = workInfo.id.toString(),
                        fileName = fileName,
                        status = status,
                        progress = progress
                    )
                }
                _workTasks.value = tasks
                _tasks.value = mergeTasks(_tasks.value, tasks)
            }
        }
    }

    private fun mergeTasks(dmTasks: List<DownloadTask>, wmTasks: List<DownloadTask>): List<DownloadTask> {
        val merged = mutableMapOf<String, DownloadTask>()

        dmTasks.forEach { task ->
            val existing = merged[task.fileName]
            // Favor DM status if it's already RUNNING or SUCCESSFUL
            if (existing == null || task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.COMPLETED) {
                merged[task.fileName] = task
            }
        }

        wmTasks.forEach { wmTask ->
            val existing = merged[wmTask.fileName]
            if (existing == null || wmTask.status == DownloadStatus.EXTRACTING || wmTask.status == DownloadStatus.COMPLETED) {
                 if (existing?.status == DownloadStatus.COMPLETED && wmTask.status == DownloadStatus.EXTRACTING) {
                     merged[wmTask.fileName] = wmTask
                 } else if (existing == null) {
                     merged[wmTask.fileName] = wmTask
                 } else if (wmTask.status == DownloadStatus.EXTRACTING) {
                     merged[wmTask.fileName] = wmTask
                 }
            }
        }

        return merged.values.toList().sortedBy { it.fileName }
    }

    fun cancelTask(task: DownloadTask) {
        queueManager.cancel(task.fileName)
    }
}
