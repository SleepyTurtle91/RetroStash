package com.lemonsquad.retrostash.data.remote

import android.util.Log
import kotlinx.serialization.Serializable
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

    @Serializable
    data class MetadataSyncResponse(
        val system_repo_name: String,
        val games: List<GameMetadata>
    )

    @Serializable
    data class GameMetadata(
        val original_filename: String,
        val clean_title: String
    )

    @Serializable
    private data class ApprovedList(
        val approved_filenames: List<String>
    )

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
    ): List<String>? {
        if (apiKey.isBlank() || rawFileList.isEmpty()) return emptyList()

        // Define a strict schema: an object with a field 'approved_filenames'
        val schema = Schema.obj(
            "approved_list",
            "Object containing list of approved filenames",
            Schema.arr(
                "approved_filenames",
                "List of filenames matching user criteria",
                Schema.str("filename", "The exact name of the file")
            )
        )

        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = schema
            temperature = 0.1f // Low temperature enforces deterministic filtering behavior
        }

        val systemInstruction = content {
            text(
                "You are an expert retro gaming librarian and JSON data processor. Your sole task is to filter and SORT an array of raw Archive.org search results based on a user's natural language filter criteria.\n" +
                "\n" +
                "Strict Rules:\n" +
                "1. Analyze the 'Filename', 'Format', and 'Size' of each item to determine if it matches the user's filtering request.\n" +
                "2. Your goal is to identify HIGH-QUALITY game files (e.g., .iso, .chd, .zip, .3ds, .cia, .bin, .rvz).\n" +
                "3. If the user specifies \"Exclude duplicates\", look for files with identical game names but different regions/versions, and prioritize the cleanest/best region (e.g., USA/Global over JP/EU beta versions, or zip/iso over text/manuals).\n" +
                "4. SORT the resulting list alphabetically by game title.\n" +
                "5. Output your response ONLY as a valid JSON object matching the requested schema.\n" +
                "6. Do not include any markdown formatting wrappers (like ```json), explanations, or conversational text. Return only the raw JSON.\n" +
                "7. IMPORTANT: If there are valid game files in the input, you MUST include them. Do not return an empty list unless NOTHING in the input matches a game or the requested filter."
            )
        }

        val model = GenerativeModel(
            modelName = "gemini-3.1-flash-lite",
            apiKey = apiKey,
            generationConfig = config,
            systemInstruction = systemInstruction
        )

        // Construct light context blueprint: Filename|Format|Size
        val fileListString = rawFileList.joinToString("\n") {
            "${it.name}|${it.format ?: "unknown"}|${it.size ?: "0"}"
        }

        val prompt = """
            Follow these steps to process the provided gaming archive data:

            ### Step 1: Identify the User Filter Criteria
            Filter Request: "$userRequest" (e.g., "Only RPGs", "Exclude duplicates", "Only USA region games")

            ### Step 2: Analyze the Input Data
            Scan the following raw archive data. Pay close attention to filenames, formats, and identifiers.
            If the data is too large, prioritize the most relevant games matching the filter.
            
            Input Data:
            $fileListString

            ### Step 3: Filter and De-duplicate
            - Focus on finding actual game files (extensions like .zip, .iso, .3ds, .cia, .chd, .rvz, etc.).
            - Apply the Filter Request from Step 1 strictly using your knowledge of video game genres, regions, and platforms.
            - If requested to de-duplicate, keep only the definitive file for each unique game title (preferring USA/World regions).
            - IF NO FILES MATCH THE FILTER, BUT THERE ARE GAMES IN THE LIST, RETURN THE GAMES. ONLY RETURN EMPTY IF THE LIST IS COMPLETELY JUNK.

            ### Step 4: Format the Output
            Generate a JSON object containing a field "approved_filenames" which is an array of strings containing ONLY the exact, unaltered Filenames that matched the criteria.

            ### Output:
        """.trimIndent()

        return try {
            val response = model.generateContent(prompt)
            val responseText = response.text ?: return emptyList()

            // Robust cleaning of the response text to handle occasional markdown or whitespace
            val cleanedJson = responseText.trim()
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()
            
            Log.d("AIFilterEngine", "Raw AI Response: $responseText")
            Log.d("AIFilterEngine", "Cleaned AI Response: $cleanedJson")

            val parsed = json.decodeFromString<ApprovedList>(cleanedJson)
            Log.d("AIFilterEngine", "Parsed approved filenames count: ${parsed.approved_filenames.size}")
            
            parsed.approved_filenames
        } catch (e: Exception) {
            Log.e("AIFilterEngine", "AI Filtering failed or returned invalid JSON", e)
            null
        }
    }

    /**
     * Analyzes a list of filenames to identify the game system and provide clean titles.
     * 
     * @param filenames List of local filenames to process.
     * @return MetadataSyncResponse containing the system repo name and mapping of clean titles.
     */
    suspend fun identifySystemAndCleanTitles(
        apiKey: String,
        filenames: List<String>
    ): MetadataSyncResponse? {
        if (apiKey.isBlank() || filenames.isEmpty()) return null

        val schema = Schema.obj(
            "metadata_sync",
            "Information for Libretro thumbnail syncing",
            Schema.str("system_repo_name", "The Libretro GitHub repository name (e.g., Nintendo_-_Nintendo_3DS)"),
            Schema.arr(
                "games",
                "List of games with clean titles",
                Schema.obj(
                    "game",
                    "Individual game metadata",
                    Schema.str("original_filename", "The exact original filename"),
                    Schema.str("clean_title", "The clean title matching Libretro naming (e.g., 'Pokemon Ultra Moon')")
                )
            )
        )

        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = schema
            temperature = 0.1f
        }

        val systemInstruction = content {
            text(
                "You are an expert in Libretro/RetroArch metadata. Your task is to analyze a list of filenames and:\n" +
                "1. Identify the single most likely Libretro System Repository Name that covers these files (e.g., 'Sony_-_PlayStation_2', 'Nintendo_-_Nintendo_3DS', 'Sega_-_Mega_Drive_-_Genesis'). Use underscores instead of spaces.\n" +
                "2. For each filename, provide a 'clean_title' that exactly matches the Libretro Named_Boxarts convention. Remove all region tags, revision info, and bracketed text. Keep only the core game name.\n" +
                "3. Output only valid JSON matching the schema."
            )
        }

        val model = GenerativeModel(
            modelName = "gemini-3.1-flash-lite",
            apiKey = apiKey,
            generationConfig = config,
            systemInstruction = systemInstruction
        )

        val prompt = "Analyze these filenames and provide Libretro metadata: \n" + filenames.joinToString("\n")

        return try {
            val response = model.generateContent(prompt)
            val responseText = response.text ?: return null
            json.decodeFromString<MetadataSyncResponse>(responseText)
        } catch (e: Exception) {
            Log.e("AIFilterEngine", "Metadata analysis failed", e)
            null
        }
    }

    /**
     * Checks if the provided Gemini API key is valid and has connectivity.
     *
     * @param apiKey The API key to test.
     * @return Result with success status or error message.
     */
    suspend fun checkApiHealth(apiKey: String): Result<String> {
        if (apiKey.isBlank()) return Result.failure(Exception("API Key is empty"))

        val model = GenerativeModel(
            modelName = "gemini-3.1-flash-lite",
            apiKey = apiKey
        )

        return try {
            val response = model.generateContent("Respond with only the word 'OK' if you can read this.")
            val text = response.text?.trim()
            if (text?.contains("OK", ignoreCase = true) == true) {
                Result.success("Connected successfully!")
            } else {
                Result.failure(Exception("Unexpected response from AI"))
            }
        } catch (e: Exception) {
            Log.e("AIFilterEngine", "API Health check failed", e)
            Result.failure(e)
        }
    }
}
