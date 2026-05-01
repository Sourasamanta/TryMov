package com.example.trymov.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.trymov.model.WatchStatus

@Entity(
    tableName = "my_list_entries",
    indices = [Index(value = ["imdbId"], unique = true)]
)
data class MyListEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val imdbId: String,
    val rating: Int = 0,
    val status: WatchStatus = WatchStatus.PLANNED,
    val progressMinutes: Int = 0,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
