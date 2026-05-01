package com.example.trymov.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.example.trymov.data.local.dao.MovieDao
import com.example.trymov.data.local.dao.MyListDao
import com.example.trymov.data.local.entity.MovieEntity
import com.example.trymov.data.local.entity.MyListEntryEntity
import com.example.trymov.data.remote.tmdb.TmdbApi
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
    private val tmdbApi: TmdbApi,
    private val tmdbApiKey: String
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
        if (tmdbApiKey.isBlank()) {
            return@withContext AddByImdbResult.Error("TMDB API key is not configured")
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
            ensureMovieCached(imdbId)
            AddByImdbResult.Success(entryId = placeholderId)
        } catch (e: Exception) {
            myListDao.deleteById(placeholderId)
            AddByImdbResult.Error(e.message ?: "Movie not found")
        }
    }

    suspend fun removeEntry(id: Int) = withContext(Dispatchers.IO) {
        myListDao.deleteById(id)
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

    private suspend fun ensureMovieCached(imdbId: String) {
        if (movieDao.getByImdbId(imdbId) != null) return

        val find = tmdbApi.findByImdbId(imdbId = imdbId, apiKey = tmdbApiKey)
        val first = find.movieResults.firstOrNull() ?: throw IllegalStateException("Movie not found on TMDB")

        val details = tmdbApi.movieDetails(movieId = first.id, apiKey = tmdbApiKey)

        movieDao.upsert(
            MovieEntity(
                imdbId = imdbId,
                tmdbId = details.id,
                title = details.title,
                posterPath = details.posterPath,
                runtime = details.runtime ?: 0,
                genres = details.genres.map { it.name }
            )
        )
    }

    private fun MyListDao.MyListEntryWithMovie.toDomain(): MyListEntry {
        val m = movie
        val movieDomain = if (m == null) null else Movie(
            id = m.tmdbId,
            imdbId = m.imdbId,
            title = m.title,
            poster = m.posterPath?.toTmdbPosterUrl(),
            runtime = m.runtime,
            genres = m.genres
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
        private val imdbRegex = Regex("^tt\\d{7,10}$")
        fun isValidImdbId(imdbId: String): Boolean = imdbRegex.matches(imdbId)
    }
}
