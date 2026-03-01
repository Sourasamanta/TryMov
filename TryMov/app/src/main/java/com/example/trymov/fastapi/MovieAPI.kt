package com.example.trymov.fastapi

import retrofit2.http.GET
import retrofit2.http.Path

interface MovieApi {
    @GET("/")
    suspend fun health(): StatusResponse

    @GET("/items/{item}")
    suspend fun recommend(@Path("item") item: String): RecommendResponse
}