package com.lemonsquad.retrostash.data.remote

import com.lemonsquad.retrostash.data.model.ArchiveMetadata
import com.lemonsquad.retrostash.data.model.ArchiveSearchResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ArchiveApiService {
    @GET("metadata/{identifier}")
    suspend fun getMetadata(
        @Path("identifier") identifier: String
    ): ArchiveMetadata

    @GET("advancedsearch.php")
    suspend fun search(
        @Query("q") query: String,
        @Query("fl[]") fields: List<String> = listOf("identifier", "title", "uploader", "mediatype"),
        @Query("output") output: String = "json",
        @Query("rows") rows: Int = 50,
        @Query("page") page: Int = 1,
        @Query("sort[]") sort: String = "downloads desc"
    ): ArchiveSearchResponse
}
