package com.lemonsquad.retrostash.data.remote

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.lemonsquad.retrostash.data.model.ArchiveFile
import kotlinx.serialization.json.Json

/**
 * AICoreEngine orchestrates the Triple-Agent AI system:
 * 1. AI Core (The Librarian) - Strategy and Intent
 * 2. AI Technical Specialist (The Engineer) - Extensions and Formats
 * 3. AI Grunt Worker (The Processor) - Filtering and Sorting
 */
object AICoreEngine {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Orchestrates the full multi-agent filtering pipeline.
     */
    suspend fun processRequest(
        apiKey: String,
        userRequest: String,
        rawFileList: List<ArchiveFile>
    ): List<String>? {
        if (apiKey.isBlank() || rawFileList.isEmpty()) return emptyList()

        try {
            // STEP 1: AI Core analyzes intent
            val strategy = getLibrarianStrategy(apiKey, userRequest)
            Log.d("AICoreEngine", "Librarian Strategy: $strategy")

            // STEP 2: AI Technical Specialist identifies extensions
            val technicalInfo = getEngineerTechnicalSpecs(apiKey, strategy.detected_platform)
            Log.d("AICoreEngine", "Engineer Specs: $technicalInfo")

            // STEP 3: AI Grunt Worker processes the data
            val results = runProcessorGruntWork(
                apiKey = apiKey,
                userRequest = userRequest,
                strategy = strategy,
                techSpecs = technicalInfo,
                rawFileList = rawFileList
            )
            
            return results
        } catch (e: Exception) {
            Log.e("AICoreEngine", "Triple-Agent Pipeline failed", e)
            return null
        }
    }

    private suspend fun getLibrarianStrategy(apiKey: String, userRequest: String): AIStrategyResponse {
        val schema = Schema.obj(
            "strategy",
            "Search strategy and platform identification",
            Schema.str("detected_platform", "The console/system identified (e.g., 'Nintendo 3DS', 'PlayStation 2')"),
            Schema.str("search_intent", "Brief summary of what the user is looking for")
        )

        val model = GenerativeModel(
            modelName = AIModelNames.CORE,
            apiKey = apiKey,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
                temperature = 0.1f
            },
            systemInstruction = content {
                text("You are the AI Core (Librarian). Your job is to analyze user requests for retro games and identify the target platform and specific search intent. Output only JSON.")
            }
        )

        val response = model.generateContent("Analyze this request: $userRequest")
        return json.decodeFromString<AIStrategyResponse>(response.text ?: throw Exception("Core returned empty"))
    }

    private suspend fun getEngineerTechnicalSpecs(apiKey: String, platform: String): AITechnicalResponse {
        val schema = Schema.obj(
            "technical_specs",
            "Technical file specifications for the platform",
            Schema.str("platform", "Confirmed platform name"),
            Schema.arr(
                "valid_extensions",
                "List of valid game file extensions (without dots)",
                Schema.str("ext", "extension")
            ),
            Schema.str("priority_extension", "The single best extension to prioritize if duplicates exist")
        )

        val model = GenerativeModel(
            modelName = AIModelNames.WORKER,
            apiKey = apiKey,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
                temperature = 0.0f
            },
            systemInstruction = content {
                text("You are the AI Technical Specialist (Engineer). You know every retro console's file formats. For a given platform, list ALL valid game file extensions and pick one to prioritize. Output only JSON.")
            }
        )

        val response = model.generateContent("Identify technical specs for platform: $platform")
        return json.decodeFromString<AITechnicalResponse>(response.text ?: throw Exception("Engineer returned empty"))
    }

    private suspend fun runProcessorGruntWork(
        apiKey: String,
        userRequest: String,
        strategy: AIStrategyResponse,
        techSpecs: AITechnicalResponse,
        rawFileList: List<ArchiveFile>
    ): List<String>? {
        val schema = Schema.obj(
            "approved_list",
            "Object containing list of approved filenames",
            Schema.arr(
                "approved_filenames",
                "List of filenames matching user criteria",
                Schema.str("filename", "The exact name of the file")
            )
        )

        val model = GenerativeModel(
            modelName = AIModelNames.WORKER,
            apiKey = apiKey,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
                temperature = 0.1f
            },
            systemInstruction = content {
                text("You are the AI Grunt Worker (Processor). Your job is to filter a list of filenames based on rules from the Librarian and Engineer. " +
                     "Rule 1 (Librarian Intent): ${strategy.search_intent}. " +
                     "Rule 2 (Engineer Extensions): Only allow these extensions: ${techSpecs.valid_extensions.joinToString(", ")}. " +
                     "Rule 3 (Engineer Priority): If duplicates exist, prefer ${techSpecs.priority_extension}. " +
                     "Sort the final list alphabetically. Output only JSON.")
            }
        )

        val fileListString = rawFileList.joinToString("\n") { "${it.name}|${it.format ?: "unknown"}|${it.size ?: "0"}" }

        val prompt = "Process these files based on the rules. Filter Request: \"$userRequest\". Files:\n$fileListString"

        return try {
            val response = model.generateContent(prompt)
            val responseText = response.text ?: return emptyList()
            val cleanedJson = responseText.trim().removePrefix("```json").removeSuffix("```").trim()
            json.decodeFromString<AIProcessorResponse>(cleanedJson).approved_filenames
        } catch (e: Exception) {
            Log.e("AICoreEngine", "Processor failed", e)
            null
        }
    }
}
