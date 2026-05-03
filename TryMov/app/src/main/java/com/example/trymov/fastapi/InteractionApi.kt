package com.example.trymov.fastapi

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface InteractionApi {

    /**
     * Upsert a user-movie interaction.
     * FastAPI uses PUT semantics via put_item — calling this twice with the
     * same (user_id, movie_id) overwrites the previous record.
     */
    @POST("interaction")
    suspend fun addInteraction(
        @Body request: InteractionRequest
    ): MessageResponse

    /**
     * Return all movie interactions recorded for [userId], newest first.
     */
    @GET("interaction/{userId}")
    suspend fun getUserInteractions(
        @Path("userId") userId: String
    ): List<InteractionResponse>

    /**
     * Fetch a single movie record from the DynamoDB Movies table.
     */
    @GET("movie/{movieId}")
    suspend fun getMovie(
        @Path("movieId") movieId: String
    ): MovieDetailResponse

    @PUT("interaction/{movieId}")
    suspend fun updateInteraction(
        @Path("movieId") movieId: String,
        @Body request: InteractionRequest
    ): MessageResponse

    @DELETE("interaction/{movieId}")
    suspend fun deleteInteraction(
        @Path("movieId") movieId: String
    ): Unit

    /**
     * Personalized hybrid recommendations:
     * seeds content-based cosine engine with the user's high-rated history.
     * [topN] controls how many results are returned (default 10 on the server).
     */
    @GET("recommend/{userId}")
    suspend fun getRecommendations(
        @Path("userId") userId: String,
        @Query("top_n") topN: Int = 10
    ): PersonalizedRecommendResponse
}
