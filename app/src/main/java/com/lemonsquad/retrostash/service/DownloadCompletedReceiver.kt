package com.lemonsquad.retrostash.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lemonsquad.retrostash.data.repository.SettingsRepository
import com.lemonsquad.retrostash.worker.UnzipWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class DownloadCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == -1L) return

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(id)
            val cursor: Cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIndex)

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val localUri = cursor.getString(localUriIndex)
                    
                    // Convert URI to file path
                    val filePath = localUri?.replace("file://", "")

                    if (filePath != null && (filePath.endsWith(".zip") || filePath.endsWith(".7z") || filePath.endsWith(".rar"))) {
                        enqueueUnzipWork(context, filePath)
                    }
                }
            }
            cursor.close()
        }
    }

    private fun enqueueUnzipWork(context: Context, filePath: String) {
        val settingsRepository = SettingsRepository(context)
        val treeUri = runBlocking { settingsRepository.sdCardUriFlow.first() }

        if (treeUri == null) {
            Log.e("DownloadReceiver", "No SD Card folder selected, cannot unzip")
            return
        }

        val fileName = filePath.substringAfterLast("/")
        val workData = Data.Builder()
            .putString("file_path", filePath)
            .putString("tree_uri", treeUri)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<UnzipWorker>()
            .setInputData(workData)
            .addTag("unzip")
            .addTag("unzip_$fileName")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d("DownloadReceiver", "Enqueued UnzipWorker for $filePath with tag unzip_$fileName")
    }
}
