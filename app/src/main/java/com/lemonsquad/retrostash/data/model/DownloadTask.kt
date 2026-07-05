package com.lemonsquad.retrostash.data.model

enum class DownloadStatus {
    QUEUED, DOWNLOADING, EXTRACTING, COMPLETED, FAILED
}

data class DownloadTask(
    val id: String,
    val fileName: String,
    val status: DownloadStatus,
    val progress: Float, // 0.0 to 1.0
    val totalSize: Long = 0,
    val downloadedSize: Long = 0
)
