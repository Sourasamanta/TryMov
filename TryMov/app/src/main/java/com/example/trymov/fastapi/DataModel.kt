package com.example.trymov.fastapi

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

data class StatusResponse(
    val status: String
)