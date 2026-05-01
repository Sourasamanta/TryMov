package com.example.trymov.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.trymov.data.local.entity.MovieEntity

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies WHERE imdbId = :imdbId")
    suspend fun getByImdbId(imdbId: String): MovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(movie: MovieEntity)
}
