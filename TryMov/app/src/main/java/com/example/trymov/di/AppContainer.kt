package com.example.trymov.di

import android.content.Context
import androidx.room.Room
import com.example.trymov.BuildConfig
import com.example.trymov.data.local.AppDatabase
import com.example.trymov.data.remote.tmdb.TmdbApi
import com.example.trymov.data.repository.MovieRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppContainer(appContext: Context) {

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "trymov.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    private val tmdbApi: TmdbApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbApi::class.java)
    }

    val movieRepository: MovieRepository by lazy {
        MovieRepository(
            movieDao = database.movieDao(),
            myListDao = database.myListDao(),
            tmdbApi = tmdbApi,
            tmdbApiKey = BuildConfig.TMDB_API_KEY
        )
    }
}
