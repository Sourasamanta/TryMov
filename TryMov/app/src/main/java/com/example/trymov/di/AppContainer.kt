package com.example.trymov.di

import android.content.Context
import androidx.room.Room
import com.example.trymov.data.local.AppDatabase
import com.example.trymov.data.repository.MovieRepository
import com.example.trymov.fastapi.ApiClient
import com.example.trymov.fastapi.FastApiRepository
import com.example.trymov.fastapi.InteractionRepository

class AppContainer(appContext: Context) {

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "trymov.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val fastApiRepository: FastApiRepository by lazy {
        FastApiRepository(ApiClient.api)
    }

    val movieRepository: MovieRepository by lazy {
        MovieRepository(
            movieDao = database.movieDao(),
            myListDao = database.myListDao(),
            fastApiRepository = fastApiRepository
        )
    }

    val interactionRepository: InteractionRepository by lazy {
        InteractionRepository(ApiClient.interactionApi)
    }
}
