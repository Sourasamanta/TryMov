package com.example.trymov.fastapi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InteractionRepository(private val api: InteractionApi) {

    /**
     * Upsert a user-movie interaction in DynamoDB.
     * user_id is extracted from the JWT on the backend — not sent in the body.
     */
    suspend fun saveInteraction(
        movieId: String,
        status: InteractionStatus,
        rating: Int?,
    ): Result<MessageResponse> = withContext(Dispatchers.IO) {
        require(movieId.isNotBlank()) { "movieId must not be blank" }
        if (rating != null) require(rating in 0..10) { "rating must be 0–10" }

        runCatching {
            api.addInteraction(
                InteractionRequest(
                    movieId = movieId,
                    status  = status.wire,
                    rating  = rating,
                )
            )
        }
    }

    suspend fun getUserHistory(userId: String): Result<List<InteractionResponse>> =
        withContext(Dispatchers.IO) {
            require(userId.isNotBlank()) { "userId must not be blank" }
            runCatching { api.getUserInteractions(userId) }
        }

    suspend fun getRecommendations(
        userId: String,
        topN: Int = 10,
    ): Result<PersonalizedRecommendResponse> = withContext(Dispatchers.IO) {
        require(userId.isNotBlank()) { "userId must not be blank" }
        runCatching { api.getRecommendations(userId, topN) }
    }

    suspend fun deleteInteraction(movieId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            require(movieId.isNotBlank()) { "movieId must not be blank" }
            runCatching { api.deleteInteraction(movieId) }
        }

    suspend fun getMovieDetails(movieId: String): Result<MovieDetailResponse> =
        withContext(Dispatchers.IO) {
            require(movieId.isNotBlank()) { "movieId must not be blank" }
            runCatching { api.getMovie(movieId) }
        }
}
