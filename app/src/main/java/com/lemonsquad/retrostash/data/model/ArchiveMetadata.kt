package com.lemonsquad.retrostash.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArchiveMetadata(
    @SerialName("files") val files: List<ArchiveFile> = emptyList()
)

@Serializable
data class ArchiveFile(
    @SerialName("name") val name: String,
    @SerialName("format") val format: String? = null,
    @SerialName("size") val size: String? = null,
    @SerialName("source") val source: String? = null
)
