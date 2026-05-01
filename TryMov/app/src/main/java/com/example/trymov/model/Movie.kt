package com.example.trymov.model

data class Movie(
    val id: Int,
    val imdbId: String,
    val title: String,
    val poster: String?,
    val runtime: Int,
    val genres: List<String>
)
