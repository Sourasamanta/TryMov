package com.example.trymov.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.trymov.data.local.entity.MovieEntity
import com.example.trymov.data.local.entity.MyListEntryEntity
import com.example.trymov.model.WatchStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MyListDao {

    data class MyListEntryWithMovie(
        @Embedded
        val entry: MyListEntryEntity,
        @Embedded(prefix = "movie_")
        val movie: MovieEntity?
    )

    @Query(
        """
        SELECT
            e.id,
            e.imdbId,
            e.rating,
            e.status,
            e.progressMinutes,
            e.isFavorite,
            e.createdAt,

            m.imdbId      AS movie_imdbId,
            m.tmdbId      AS movie_tmdbId,
            m.title       AS movie_title,
            m.posterPath  AS movie_posterPath,
            m.runtime     AS movie_runtime,
            m.genres      AS movie_genres
        FROM my_list_entries e
        LEFT JOIN movies m ON m.imdbId = e.imdbId
        ORDER BY e.isFavorite DESC, e.rating DESC, e.createdAt DESC
        """
    )
    fun observeEntriesWithMovies(): Flow<List<MyListEntryWithMovie>>

    @Query("SELECT EXISTS(SELECT 1 FROM my_list_entries WHERE imdbId = :imdbId)")
    suspend fun existsByImdbId(imdbId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: MyListEntryEntity): Long

    @Update
    suspend fun update(entry: MyListEntryEntity)

    @Query("SELECT * FROM my_list_entries WHERE id = :id")
    suspend fun getById(id: Int): MyListEntryEntity?

    @Query("DELETE FROM my_list_entries WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE my_list_entries SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Int)

    @Query("UPDATE my_list_entries SET rating = :rating WHERE id = :id")
    suspend fun updateRating(id: Int, rating: Int)

    @Query("UPDATE my_list_entries SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Int, status: WatchStatus)

    @Query("UPDATE my_list_entries SET progressMinutes = :minutes WHERE id = :id")
    suspend fun updateProgress(id: Int, minutes: Int)
}
