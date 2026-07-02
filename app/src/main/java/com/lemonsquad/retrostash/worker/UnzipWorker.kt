package com.lemonsquad.retrostash.worker

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class UnzipWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val filePath = inputData.getString("file_path") ?: return Result.failure()
        val treeUriString = inputData.getString("tree_uri") ?: return Result.failure()
        val zipFile = File(filePath)

        if (!zipFile.exists()) {
            Log.e("UnzipWorker", "File does not exist: $filePath")
            return Result.failure()
        }

        val treeUri = Uri.parse(treeUriString)
        val pickedDir = DocumentFile.fromTreeUri(applicationContext, treeUri) ?: return Result.failure()

        // Create RetroStash subdirectory in picked directory
        val outputDir = pickedDir.findFile("RetroStash") ?: pickedDir.createDirectory("RetroStash")
        if (outputDir == null) {
            Log.e("UnzipWorker", "Failed to create/find RetroStash directory")
            return Result.failure()
        }

        return try {
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.isDirectory) {
                        createDirectoryRecursive(outputDir, entry.name)
                    } else {
                        val fileName = entry.name.substringAfterLast("/")
                        val parentPath = if (entry.name.contains("/")) entry.name.substringBeforeLast("/") else ""
                        
                        val targetParentDir = if (parentPath.isNotEmpty()) {
                            createDirectoryRecursive(outputDir, parentPath)
                        } else {
                            outputDir
                        }

                        if (targetParentDir != null) {
                            val newFile = targetParentDir.createFile("application/octet-stream", fileName)
                            if (newFile != null) {
                                applicationContext.contentResolver.openOutputStream(newFile.uri)?.use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Clean up source file
            if (zipFile.delete()) {
                Log.d("UnzipWorker", "Deleted source file: $filePath")
            } else {
                Log.w("UnzipWorker", "Failed to delete source file: $filePath")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("UnzipWorker", "Error unzipping file", e)
            Result.failure()
        }
    }

    private fun createDirectoryRecursive(baseDir: DocumentFile, path: String): DocumentFile? {
        var currentDir = baseDir
        path.split("/").filter { it.isNotEmpty() }.forEach { part ->
            currentDir = currentDir.findFile(part) ?: currentDir.createDirectory(part) ?: return null
        }
        return currentDir
    }
}
