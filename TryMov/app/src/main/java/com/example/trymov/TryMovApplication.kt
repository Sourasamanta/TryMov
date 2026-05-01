package com.example.trymov

import android.app.Application
import com.example.trymov.di.AppContainer

class TryMovApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
