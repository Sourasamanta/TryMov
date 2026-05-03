package com.example.trymov.fastapi

import com.google.gson.annotations.SerializedName

// ── Domain enum ───────────────────────────────────────────────────────────────

enum class InteractionStatus(val wire: String) {
    WATCHING("watching"),
    COMPLETED("completed"),
    PLAN_TO_WATCH("plan_to_watch");

    companion object {
        fun fromWire(value: String): InteractionStatus =
            entries.firstOrNull { it.wire == value } ?: PLAN_TO_WATCH
    }
}

// ── Request ───────────────────────────────────────────────────────────────────

data class InteractionRequest(
    @SerializedName("movie_id") val movieId: String,
    @SerializedName("status")   val status: String,   // use InteractionStatus.wire
    @SerializedName("rating")   val rating: Int? = null // 1–5, null = unrated
)

// ── Responses ─────────────────────────────────────────────────────────────────

data class InteractionResponse(
    @SerializedName("user_id")   val userId: String,
    @SerializedName("movie_id")  val movieId: String,
    @SerializedName("status")    val status: String,
    @SerializedName("rating")    val rating: Int,
    @SerializedName("timestamp") val timestamp: String
)

data class MovieDetailResponse(
    @SerializedName("movie_id") val movieId: String,
    @SerializedName("title")    val title: String,
    @SerializedName("genres")   val genres: String,
    @SerializedName("overview") val overview: String
)

data class TmdbMovieResponse(
    @SerializedName("movie_id")    val movieId: String,
    @SerializedName("title")       val title: String,
    @SerializedName("genres")      val genres: List<String> = emptyList(),
    @SerializedName("overview")    val overview: String,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("runtime")     val runtime: Int
)

data class PosterResponse(
    @SerializedName("poster") val poster: String?
)

data class PersonalizedRecommendItem(
    @SerializedName("movie_id") val movieId: String,
    @SerializedName("title")    val title: String,
    @SerializedName("score")    val score: Double
)

data class PersonalizedRecommendResponse(
    @SerializedName("user_id")         val userId: String,
    @SerializedName("recommendations") val recommendations: List<PersonalizedRecommendItem>
)
