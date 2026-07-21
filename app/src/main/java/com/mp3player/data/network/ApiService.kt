package com.mp3player.data.network

import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ApiService {

    @GET("search")
    suspend fun search(
        @Query("q") query: String
    ): List<SearchTrackDto>

    @POST("stream")
    suspend fun getStreamUrl(
        @Body request: StreamRequestDto
    ): StreamResponseDto

    @POST("download")
    @Streaming
    suspend fun downloadTrack(
        @Body request: DownloadRequestDto
    ): ResponseBody

    companion object {
        private var INSTANCE: ApiService? = null
        private var currentBaseUrl: String? = null

        fun getInstance(baseUrl: String): ApiService? {
            if (baseUrl.isBlank() || !baseUrl.startsWith("http")) return INSTANCE

            val formattedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            
            if (INSTANCE != null && currentBaseUrl == formattedUrl) {
                return INSTANCE
            }

            return try {
                Retrofit.Builder()
                    .baseUrl(formattedUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService::class.java).also {
                        INSTANCE = it
                        currentBaseUrl = formattedUrl
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                INSTANCE
            }
        }
    }
}

data class SearchTrackDto(
    val id: str = "",
    val title: String,
    val uploader: String,
    val duration: Int,
    val thumbnail: String
)

// Simple type alias to match python name
typealias str = String

data class StreamRequestDto(
    val video_id: String
)

data class StreamResponseDto(
    val stream_url: String,
    val title: String,
    val duration: Int
)

data class DownloadRequestDto(
    val video_id: String,
    val title: String?,
    val artist: String?,
    val album: String = "YouTube Downloads",
    val thumbnail_url: String?
)
