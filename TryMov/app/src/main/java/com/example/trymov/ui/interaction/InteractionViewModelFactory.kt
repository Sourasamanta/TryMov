package com.example.trymov.ui.interaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.trymov.fastapi.InteractionRepository

class InteractionViewModelFactory(
    private val repository: InteractionRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(InteractionViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return InteractionViewModel(repository) as T
    }
}
