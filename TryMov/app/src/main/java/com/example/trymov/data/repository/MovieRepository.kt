package com.example.trymov.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.example.trymov.data.local.dao.MovieDao
import com.example.trymov.data.local.dao.MyListDao
import com.example.trymov.data.local.entity.MovieEntity
import com.example.trymov.data.local.entity.MyListEntryEntity
import com.example.trymov.fastapi.FastApiRepository
import com.example.trymov.fastapi.MovieRequest
import com.example.trymov.model.Movie
import com.example.trymov.model.MyListEntry
import com.example.trymov.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MovieRepository(
    private val movieDao: MovieDao,
    private val myListDao: MyListDao,
    private val fastApiRepository: FastApiRepository
) {
    sealed interface AddByImdbResult {
        data class Success(val entryId: Int) : AddByImdbResult
        data object Duplicate : AddByImdbResult
        data class Error(val message: String) : AddByImdbResult
    }

    fun observeMyList(): Flow<List<MyListEntry>> =
        myListDao.observeEntriesWithMovies().map { rows ->
            rows.map { it.toDomain() }
        }

    suspend fun addByImdbId(rawImdbId: String): AddByImdbResult = withContext(Dispatchers.IO) {
        val imdbId = rawImdbId.trim()
        if (!isValidImdbId(imdbId)) {
            return@withContext AddByImdbResult.Error("Invalid IMDB ID — use format tt1375666")
        }
        if (myListDao.existsByImdbId(imdbId)) {
            return@withContext AddByImdbResult.Duplicate
        }

        val placeholderId: Int = try {
            val rowId = myListDao.insert(
                MyListEntryEntity(
                    imdbId = imdbId,
                    status = WatchStatus.PLANNED
                )
            )
            rowId.toInt()
        } catch (_: SQLiteConstraintException) {
            return@withContext AddByImdbResult.Duplicate
        }

        try {
            val movie = ensureMovieCached(imdbId)
            fastApiRepository.addMovie(
                MovieRequest(
                    movie_id = imdbId,
                    title = movie.title,
                    genres = movie.genres.joinToString(", "),
                    overview = movie.overview ?: ""
                )
            ).onFailure { Log.w(TAG, "DynamoDB sync failed for $imdbId: ${it.message}") }
            AddByImdbResult.Success(entryId = placeholderId)
        } catch (e: Exception) {
            myListDao.deleteById(placeholderId)
            AddByImdbResult.Error(e.message ?: "Movie not found")
        }
    }

    suspend fun syncFromRemote(): Unit = withContext(Dispatchers.IO) {
        val remoteMovies = fastApiRepository.getMovies().getOrElse {
            Log.w(TAG, "Failed to fetch movies from DynamoDB: ${it.message}")
            return@withContext
        }
        for (remote in remoteMovies) {
            if (myListDao.existsByImdbId(remote.movieId)) continue
            try {
                ensureMovieCached(remote.movieId)
                myListDao.insert(MyListEntryEntity(imdbId = remote.movieId, status = WatchStatus.PLANNED))
            } catch (e: Exception) {
                Log.w(TAG, "Could not restore ${remote.movieId}: ${e.message}")
            }
        }
    }

    suspend fun applyInteractions(interactions: List<com.example.trymov.fastapi.InteractionResponse>) =
        withContext(Dispatchers.IO) {
            for (interaction in interactions) {
                val entry = myListDao.getByImdbId(interaction.movieId) ?: continue
                val status = when (interaction.status.lowercase()) {
                    "watching"      -> WatchStatus.WATCHING
                    "completed"     -> WatchStatus.COMPLETED
                    "plan_to_watch", "planned" -> WatchStatus.PLANNED
                    "dropped"       -> WatchStatus.DROPPED
                    else            -> WatchStatus.PLANNED
                }
                myListDao.updateStatus(entry.id, status)
                myListDao.updateRating(entry.id, interaction.rating.coerceIn(0, 10))
            }
        }

    suspend fun pushToRemote(): Unit = withContext(Dispatchers.IO) {
        val rows = myListDao.getAllEntriesWithMovies()
        for (row in rows) {
            val movie = row.movie ?: continue
            fastApiRepository.addMovie(
                MovieRequest(
                    movie_id = row.entry.imdbId,
                    title = movie.title,
                    genres = movie.genres.joinToString(", "),
                    overview = movie.overview ?: ""
                )
            ).onFailure { Log.w(TAG, "Push movie failed for ${row.entry.imdbId}: ${it.message}") }
        }
    }

    suspend fun removeEntry(id: Int) = withContext(Dispatchers.IO) {
        val imdbId = myListDao.getById(id)?.imdbId
        myListDao.deleteById(id)
        if (imdbId != null) {
            fastApiRepository.deleteMovie(imdbId)
                .onFailure { Log.w(TAG, "Remote movie delete failed for $imdbId: ${it.message}") }
        }
    }

    suspend fun toggleFavorite(id: Int) = withContext(Dispatchers.IO) {
        myListDao.toggleFavorite(id)
    }

    suspend fun updateRating(id: Int, rating: Int) = withContext(Dispatchers.IO) {
        myListDao.updateRating(id, rating.coerceIn(0, 10))
    }

    suspend fun updateEntry(entry: MyListEntry) = withContext(Dispatchers.IO) {
        val existing = myListDao.getById(entry.id) ?: return@withContext
        myListDao.update(
            existing.copy(
                rating = entry.rating.coerceIn(0, 10),
                status = entry.status,
                progressMinutes = entry.progressMinutes.coerceAtLeast(0),
                isFavorite = entry.isFavorite
            )
        )
    }

    private suspend fun ensureMovieCached(imdbId: String): MovieEntity {
        movieDao.getByImdbId(imdbId)?.let { return it }

        val tmdb = fastApiRepository.getTmdbMovie(imdbId).getOrElse {
            throw IllegalStateException("Movie not found: ${it.message}")
        }

        val entity = MovieEntity(
            imdbId   = imdbId,
            tmdbId   = 0,
            title    = tmdb.title,
            overview = tmdb.overview.ifBlank { null },
            posterPath = tmdb.posterPath?.ifBlank { null },
            runtime  = tmdb.runtime,
            genres   = tmdb.genres.filter { it.isNotBlank() }
        )
        movieDao.upsert(entity)
        return entity
    }

    private fun MyListDao.MyListEntryWithMovie.toDomain(): MyListEntry {
        val m = movie
        val movieDomain = if (m == null) null else Movie(
            id = m.tmdbId,
            imdbId = m.imdbId,
            title = m.title,
            poster = m.posterPath?.toTmdbPosterUrl(),
            runtime = m.runtime,
            genres = m.genres,
            overview = m.overview
        )

        return MyListEntry(
            id = entry.id,
            imdbId = entry.imdbId,
            movie = movieDomain,
            rating = entry.rating,
            status = entry.status,
            progressMinutes = entry.progressMinutes,
            isFavorite = entry.isFavorite
        )
    }

    private fun String.toTmdbPosterUrl(): String {
        val path = if (startsWith("/")) this else "/$this"
        return "https://image.tmdb.org/t/p/w342$path"
    }

    companion object {
        private const val TAG = "MovieRepository"
        private val imdbRegex = Regex("^tt\\d{7,10}$")
        fun isValidImdbId(imdbId: String): Boolean = imdbRegex.matches(imdbId)
    }
}
