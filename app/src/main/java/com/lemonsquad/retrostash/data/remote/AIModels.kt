package com.lemonsquad.retrostash.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class AIStrategyResponse(
    val detected_platform: String,
    val search_intent: String,
    val recommended_extensions: List<String> = emptyList()
)

@Serializable
data class AITechnicalResponse(
    val platform: String,
    val valid_extensions: List<String>,
    val priority_extension: String? = null
)

@Serializable
data class AIProcessorResponse(
    val approved_filenames: List<String>
)

object AIModelNames {
    const val CORE = "gemini-3.1-flash"
    const val WORKER = "gemini-3.1-flash-lite"
}
