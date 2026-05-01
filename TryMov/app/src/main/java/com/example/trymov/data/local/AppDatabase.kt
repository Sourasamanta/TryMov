package com.example.trymov.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.trymov.data.local.dao.MovieDao
import com.example.trymov.data.local.dao.MyListDao
import com.example.trymov.data.local.entity.MovieEntity
import com.example.trymov.data.local.entity.MyListEntryEntity

@Database(
    entities = [
        MovieEntity::class,
        MyListEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun myListDao(): MyListDao
}
