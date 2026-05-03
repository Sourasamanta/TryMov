package com.example.trymov.data.remote.tmdb

import com.google.gson.annotations.SerializedName

data class TmdbFindResponse(
    @SerializedName("movie_results")
    val movieResults: List<TmdbMovieResult> = emptyList()
)

data class TmdbMovieResult(
    val id: Int,
    val title: String? = null,
    @SerializedName("poster_path")
    val posterPath: String? = null
)

data class TmdbMovieDetails(
    val id: Int,
    val title: String,
    val overview: String = "",
    @SerializedName("poster_path")
    val posterPath: String? = null,
    val runtime: Int? = null,
    val genres: List<TmdbGenre> = emptyList()
)

data class TmdbGenre(
    val id: Int,
    val name: String
)
