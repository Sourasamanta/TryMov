package com.example.trymov.data.remote.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {

    @GET("find/{imdbId}")
    suspend fun findByImdbId(
        @Path("imdbId") imdbId: String,
        @Query("api_key") apiKey: String,
        @Query("external_source") source: String = "imdb_id"
    ): TmdbFindResponse

    @GET("movie/{movieId}")
    suspend fun movieDetails(
        @Path("movieId") movieId: Int,
        @Query("api_key") apiKey: String
    ): TmdbMovieDetails
}
