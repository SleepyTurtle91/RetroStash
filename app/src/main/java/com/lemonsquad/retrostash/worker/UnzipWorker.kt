package com.lemonsquad.retrostash.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.zip.ZipInputStream

class UnzipWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val fileUriString = inputData.getString("file_uri") ?: return Result.failure()
        val treeUriString = inputData.getString("tree_uri") ?: return Result.failure()
        val fileUri = Uri.parse(fileUriString)

        val treeUri = Uri.parse(treeUriString)
        val pickedDir = DocumentFile.fromTreeUri(applicationContext, treeUri) ?: return Result.failure()

        // Create RetroStash subdirectory in picked directory
        val outputDir = pickedDir.findFile("RetroStash") ?: pickedDir.createDirectory("RetroStash")
        if (outputDir == null) {
            Log.e("UnzipWorker", "Failed to create/find RetroStash directory")
            return Result.failure()
        }

        return try {
            applicationContext.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
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
                                        val buffer = ByteArray(8192)
                                        var bytesRead: Int
                                        while (zis.read(buffer).also { bytesRead = it } != -1) {
                                            fos.write(buffer, 0, bytesRead)
                                        }
                                    }
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // Clean up source file
            try {
                applicationContext.contentResolver.delete(fileUri, null, null)
                Log.d("UnzipWorker", "Deleted source file: $fileUriString")
            } catch (e: Exception) {
                Log.w("UnzipWorker", "Failed to delete source file: $fileUriString")
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
