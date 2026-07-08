package com.lemonsquad.retrostash.service

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log

class ArchiveDownloadManager(context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueueDownload(identifier: String, filename: String): Long {
        val url = "https://archive.org/download/$identifier/$filename"
        Log.i("ArchiveDownloadManager", "Enqueuing download: $url")
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading $filename")
            .setDescription("RetroStash is downloading your game")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .addRequestHeader("User-Agent", "RetroStash-App (Android; DownloadManager)")

        return downloadManager.enqueue(request)
    }
}
