package com.example.trymov.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey
    val imdbId: String,
    val tmdbId: Int,
    val title: String,
    val posterPath: String?,
    val runtime: Int,
    val genres: List<String>
)
