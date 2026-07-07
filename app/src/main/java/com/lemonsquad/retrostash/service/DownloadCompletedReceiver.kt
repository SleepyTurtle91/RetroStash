package com.lemonsquad.retrostash.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lemonsquad.retrostash.data.repository.SettingsRepository
import com.lemonsquad.retrostash.service.DownloadQueueManager
import com.lemonsquad.retrostash.worker.UnzipWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class DownloadCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == -1L) return

            // Notify Queue Manager
            val queueManager = DownloadQueueManager(context)
            queueManager.onDownloadCompleted(id)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(id)
            val cursor: Cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIndex)

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                    
                    val localUri = cursor.getString(localUriIndex)
                    val title = cursor.getString(titleIndex)

                    if (localUri != null && title != null && (title.endsWith(".zip") || title.endsWith(".7z") || title.endsWith(".rar"))) {
                        enqueueUnzipWork(context, localUri, title)
                    }
                }
            }
            cursor.close()
        }
    }

    private fun enqueueUnzipWork(context: Context, fileUri: String, fileName: String) {
        val settingsRepository = SettingsRepository(context)
        val treeUri = runBlocking { settingsRepository.sdCardUriFlow.first() }

        if (treeUri == null) {
            Log.e("DownloadReceiver", "No SD Card folder selected, cannot unzip")
            return
        }

        val workData = Data.Builder()
            .putString("file_uri", fileUri)
            .putString("file_name", fileName)
            .putString("tree_uri", treeUri)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<UnzipWorker>()
            .setInputData(workData)
            .addTag("unzip")
            .addTag("unzip_$fileName")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d("DownloadReceiver", "Enqueued UnzipWorker for $fileUri with tag unzip_$fileName")
    }
}
