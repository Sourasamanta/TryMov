package com.example.trymov.fastapi

// InteractionRequest / InteractionResponse / MovieDetailResponse live in InteractionModels.kt

// ── Recommendation (ML endpoint) ──────────────────────────────────────────────

data class Recommendation(
    val title: String,
    val score: Double
)

data class RecommendResponse(
    val movie: String,
    val top_n: Int,
    val recommendations: List<Recommendation> = emptyList(),
    val error: String? = null
)

data class StatusResponse(val status: String)

data class MessageResponse(val message: String)

// ── Movies (DynamoDB) ─────────────────────────────────────────────────────────

data class MovieRequest(
    val movie_id: String,
    val title: String,
    val genres: String,
    val overview: String
)
