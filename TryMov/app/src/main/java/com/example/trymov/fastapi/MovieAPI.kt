package com.example.trymov.fastapi

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MovieApi {

    @GET("/")
    suspend fun health(): StatusResponse

    @GET("/items/{item}")
    suspend fun recommend(@Path("item") item: String): RecommendResponse

    @POST("/movies")
    suspend fun addMovie(@Body movie: MovieRequest): MessageResponse

    @GET("/movies")
    suspend fun getMovies(): List<MovieDetailResponse>

    @GET("/tmdb/{imdbId}")
    suspend fun getTmdbMovie(@Path("imdbId") imdbId: String): TmdbMovieResponse

    @DELETE("/movies/{movieId}")
    suspend fun deleteMovie(@Path("movieId") movieId: String): Unit

    @GET("/poster/{title}")
    suspend fun getPoster(@Path("title") title: String): PosterResponse
}
