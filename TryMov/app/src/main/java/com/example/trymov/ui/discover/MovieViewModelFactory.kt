package com.example.trymov.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.trymov.MovieViewModel
import com.example.trymov.fastapi.FastApiRepository

class MovieViewModelFactory(
    private val fastApiRepository: FastApiRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MovieViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return MovieViewModel(fastApiRepository) as T
    }
}
