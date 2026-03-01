package com.example.trymov.omdb

import retrofit2.http.GET
import retrofit2.http.Query

data class OmdbMovieResponse(
    val Title: String? = null,
    val imdbID: String? = null,
    val Poster: String? = null,
    val Response: String? = null,
    val Error: String? = null
)

interface OmdbApi {
    @GET("/")
    suspend fun getByTitle(
        @Query("apikey") apiKey: String,
        @Query("t") title: String,
        @Query("y") year: String? = null
    ): OmdbMovieResponse

    @GET("/")
    suspend fun getByImdbId(
        @Query("apikey") apiKey: String,
        @Query("i") imdbId: String
    ): OmdbMovieResponse
}