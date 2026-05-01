package com.example.trymov.model

data class MyListEntry(
    val id: Int,
    val imdbId: String,
    val movie: Movie?,
    val rating: Int,
    val status: WatchStatus,
    val progressMinutes: Int,
    val isFavorite: Boolean
)
