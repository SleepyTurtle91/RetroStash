package com.lemonsquad.retrostash.data.remote

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.lemonsquad.retrostash.data.model.ArchiveFile
import kotlinx.serialization.json.Json

/**
 * AIFilterEngine handles the intelligent filtering of archive files using Gemini 1.5 Flash.
 * It uses a strict JSON schema to ensure the model outputs a parseable list of filenames.
 */
object AIFilterEngine {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Filters a list of archive files based on a user's natural language request.
     *
     * @param apiKey The user's Gemini API key.
     * @param userRequest The natural language description of what files to filter (e.g., "all RPGs").
     * @param rawFileList The complete list of files available in the archive.
     * @return A list of filenames that the AI has identified as matching the criteria.
     */
    suspend fun filterCollection(
        apiKey: String,
        userRequest: String,
        rawFileList: List<ArchiveFile>
    ): List<String> {
        if (apiKey.isBlank() || rawFileList.isEmpty()) return emptyList()

        // Define a strict schema: a raw array of strings
        val schema = Schema.arr(
            name = "approved_filenames",
            description = "List of filenames matching user criteria",
            items = Schema.str("filename", "The exact name of the file")
        )

        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = schema
            temperature = 0.1f // Low temperature enforces deterministic filtering behavior
        }

        val systemInstruction = content {
            text(
                "You are a strict data processing engine. Analyze the provided list of entries from an archive collection. " +
                "Each line format matches: Filename|Format|Size. Based on the user request, return a JSON array containing " +
                "ONLY the exact, unaltered filenames of individual, playable game ROMs that match the criteria. " +
                "Ignore compressed multi-game bundles (.zip, .7z, .rar) unless explicitly asked. Completely exclude system text logs, " +
                "image artwork (.jpg, .png), and XML metadata files. Do not include any conversational text, descriptions, or markdown blocks."
            )
        }

        val model = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey,
            generationConfig = config,
            systemInstruction = systemInstruction
        )

        // Construct light context blueprint: Filename|Format|Size
        val fileListString = rawFileList.joinToString("\n") {
            "${it.name}|${it.format ?: "unknown"}|${it.size ?: "0"}"
        }
        val prompt = "User Target Instruction: $userRequest\n\nArchive Source Matrix:\n$fileListString"

        return try {
            val response = model.generateContent(prompt)
            val responseText = response.text ?: return emptyList()

            // Parsing logic to turn the AI's JSON string back into a Kotlin List<String>
            json.decodeFromString<List<String>>(responseText)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
