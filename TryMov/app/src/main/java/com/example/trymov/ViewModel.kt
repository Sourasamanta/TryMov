package com.example.trymov

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trymov.fastapi.ApiClient
import com.example.trymov.fastapi.Recommendation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MovieViewModel : ViewModel() {
    private val _status = MutableStateFlow("idle")
    val status: StateFlow<String> = _status

    private val _recommendations = MutableStateFlow<List<Recommendation>>(emptyList())
    val recommendations: StateFlow<List<Recommendation>> = _recommendations

    fun pingServer(){
        viewModelScope.launch {
            try {
                val res = ApiClient.api.health()
                _status.value = res.status
            } catch (e: Exception) {
                _status.value = "error: ${e.message}"
            }
        }
    }

    fun getRecommendations(movie: String) {
        viewModelScope.launch {
            try {
                val res = ApiClient.api.recommend(movie)
                if (res.error != null) {
                    _status.value = res.error
                    _recommendations.value = emptyList()
                } else {
                    _status.value = "ok"
                    _recommendations.value = res.recommendations
                }
            } catch (e: Exception) {
                _status.value = "error: ${e.message}"
            }
        }
    }
}