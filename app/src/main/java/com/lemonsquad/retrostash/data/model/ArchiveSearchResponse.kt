package com.lemonsquad.retrostash.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArchiveSearchResponse(
    @SerialName("response") val response: SearchResponseData
)

@Serializable
data class SearchResponseData(
    @SerialName("docs") val docs: List<SearchDoc> = emptyList()
)

@Serializable
data class SearchDoc(
    @SerialName("identifier") val identifier: String,
    @SerialName("title") val title: String? = null
)
