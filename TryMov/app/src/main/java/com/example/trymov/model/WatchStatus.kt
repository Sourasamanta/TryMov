package com.example.trymov.model

enum class WatchStatus {
    WATCHING,
    COMPLETED,
    DROPPED,
    PLANNED;

    fun displayName(): String = when (this) {
        WATCHING -> "Watching"
        COMPLETED -> "Completed"
        DROPPED -> "Dropped"
        PLANNED -> "Planned"
    }
}
