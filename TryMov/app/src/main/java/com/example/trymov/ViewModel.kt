package com.example.trymov

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trymov.fastapi.FastApiRepository
import com.example.trymov.fastapi.Recommendation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MovieViewModel(private val repository: FastApiRepository) : ViewModel() {

    private val _status = MutableStateFlow("idle")
    val status: StateFlow<String> = _status

    private val _recommendations = MutableStateFlow<List<Recommendation>>(emptyList())
    val recommendations: StateFlow<List<Recommendation>> = _recommendations

    fun pingServer() {
        viewModelScope.launch {
            repository.healthCheck()
                .onSuccess { _status.value = it.status }
                .onFailure { _status.value = "error: ${it.message}" }
        }
    }

    fun getRecommendations(movie: String) {
        if (movie.isBlank()) return
        viewModelScope.launch {
            _status.value = "loading"
            _recommendations.value = emptyList()
            repository.getRecommendations(movie)
                .onSuccess { res ->
                    _status.value = "ok"
                    _recommendations.value = res.recommendations
                }
                .onFailure { e ->
                    _status.value = "error: ${e.message}"
                    _recommendations.value = emptyList()
                }
        }
    }
}
