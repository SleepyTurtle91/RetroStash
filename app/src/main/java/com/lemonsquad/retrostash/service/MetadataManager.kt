package com.lemonsquad.retrostash.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.lemonsquad.retrostash.data.remote.AIFilterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.OutputStreamWriter

object MetadataManager {
    private val client = OkHttpClient()

    /**
     * Scans a directory and generates gameList.xml + downloads boxart.
     */
    suspend fun syncMetadata(
        context: Context,
        folderUri: Uri,
        apiKey: String,
        onProgress: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext false
            
            // Look for RetroStash subfolder first
            val retroStashDir = rootDoc.findFile("RetroStash") ?: rootDoc
            val files = retroStashDir.listFiles().filter { it.isFile && !it.name.isNullOrEmpty() && it.name != "gameList.xml" }
            
            if (files.isEmpty()) {
                onProgress("No files found to sync in ${retroStashDir.name}.")
                return@withContext true
            }

            onProgress("Analyzing filenames with AI...")
            val filenames = files.mapNotNull { it.name }
            val metadata = AIFilterEngine.identifySystemAndCleanTitles(apiKey, filenames)
            
            if (metadata == null) {
                onProgress("AI metadata analysis failed.")
                return@withContext false
            }

            onProgress("System identified: ${metadata.system_repo_name.replace("_", " ")}")
            
            // Create images directory inside retroStashDir
            var imagesDir = retroStashDir.findFile("images")
            if (imagesDir == null) {
                imagesDir = retroStashDir.createDirectory("images")
            }
            if (imagesDir == null) {
                onProgress("Failed to create images directory.")
                return@withContext false
            }

            val gameEntries = mutableListOf<String>()

            metadata.games.forEachIndexed { index, game ->
                onProgress("Processing ${index + 1}/${metadata.games.size}: ${game.clean_title}")
                
                // Fetch image
                // Libretro convention: replace invalid filename chars with underscores
                val safeTitle = game.clean_title
                    .replace(":", "_")
                    .replace("/", "_")
                    .replace("*", "_")
                    .replace("?", "_")
                    .replace("\"", "_")
                    .replace("<", "_")
                    .replace(">", "_")
                    .replace("|", "_")
                    .replace("&", "_")
                
                val imageUrl = "https://raw.githubusercontent.com/libretro-thumbnails/${metadata.system_repo_name}/master/Named_Boxarts/${safeTitle.replace(" ", "%20")}.png"
                val imageFile = downloadImage(context, imagesDir, game.clean_title, imageUrl)
                
                val imagePath = if (imageFile != null) "./images/${imageFile.name}" else ""
                
                gameEntries.add("""
                    <game>
                        <path>./${game.original_filename}</path>
                        <name>${game.clean_title}</name>
                        <image>$imagePath</image>
                    </game>
                """.trimIndent())
            }

            // Generate gameList.xml
            onProgress("Generating gameList.xml...")
            val xmlContent = StringBuilder()
            xmlContent.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            xmlContent.append("<gameList>\n")
            gameEntries.forEach { xmlContent.append(it).append("\n") }
            xmlContent.append("</gameList>")

            val xmlFile = retroStashDir.findFile("gameList.xml") ?: retroStashDir.createFile("text/xml", "gameList.xml")
            if (xmlFile != null) {
                context.contentResolver.openOutputStream(xmlFile.uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(xmlContent.toString())
                    }
                }
            }

            onProgress("Sync complete!")
            true
        } catch (e: Exception) {
            Log.e("MetadataManager", "Sync failed", e)
            onProgress("Sync failed: ${e.message}")
            false
        }
    }

    private fun downloadImage(context: Context, parentDir: DocumentFile, title: String, url: String): DocumentFile? {
        val fileName = "${title}.png"
        val existing = parentDir.findFile(fileName)
        if (existing != null) return existing

        val request = Request.Builder().url(url).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                
                val newFile = parentDir.createFile("image/png", fileName) ?: return null
                context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                    response.body?.byteStream()?.copyTo(outputStream)
                }
                newFile
            }
        } catch (e: Exception) {
            Log.e("MetadataManager", "Failed to download image: $url", e)
            null
        }
    }
}
