package com.example.trymov.fastapi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single source of truth for all FastAPI interactions.
 *
 * Accepts [MovieApi] as a constructor parameter so it can be swapped for a
 * fake/mock in tests without touching Retrofit wiring.
 *
 * When adding authentication (Cognito/JWT), attach the token inside the OkHttp
 * interceptor in [ApiClient] — nothing here needs to change.
 */
class FastApiRepository(private val api: MovieApi) {

    /**
     * Fetch movie recommendations from the FastAPI backend.
     *
     * @param title Exact or approximate movie title to seed recommendations.
     * @return [Result.success] containing [RecommendResponse], or
     *         [Result.failure] with a descriptive exception on network / API errors.
     */
    suspend fun getRecommendations(title: String): Result<RecommendResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.recommend(title)
                if (response.error != null) {
                    // FastAPI returned a business-level error (movie not found, etc.)
                    throw RecommendationException(response.error)
                }
                response
            }
        }

    suspend fun healthCheck(): Result<StatusResponse> =
        withContext(Dispatchers.IO) {
            runCatching { api.health() }
        }

    suspend fun addMovie(request: MovieRequest): Result<MessageResponse> =
        withContext(Dispatchers.IO) {
            runCatching { api.addMovie(request) }
        }

    suspend fun getMovies(): Result<List<MovieDetailResponse>> =
        withContext(Dispatchers.IO) {
            runCatching { api.getMovies() }
        }

    suspend fun getTmdbMovie(imdbId: String): Result<TmdbMovieResponse> =
        withContext(Dispatchers.IO) {
            runCatching { api.getTmdbMovie(imdbId) }
        }

    suspend fun deleteMovie(imdbId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { api.deleteMovie(imdbId) }
        }

    suspend fun getPoster(title: String): Result<PosterResponse> =
        withContext(Dispatchers.IO) {
            runCatching { api.getPoster(title) }
        }
}

/** Distinguishes FastAPI business errors from raw network/IO exceptions. */
class RecommendationException(message: String) : Exception(message)
