package com.example.trymov.ui.interaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trymov.fastapi.InteractionRepository
import com.example.trymov.fastapi.InteractionResponse
import com.example.trymov.fastapi.InteractionStatus
import com.example.trymov.fastapi.MessageResponse
import com.example.trymov.fastapi.MovieDetailResponse
import com.example.trymov.fastapi.PersonalizedRecommendItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SaveState {
    data object Idle    : SaveState
    data object Loading : SaveState
    data class  Success(val response: MessageResponse) : SaveState
    data class  Error(val message: String)             : SaveState
}

sealed interface HistoryState {
    data object Idle    : HistoryState
    data object Loading : HistoryState
    data class  Success(val interactions: List<InteractionResponse>) : HistoryState
    data class  Error(val message: String)                           : HistoryState
}

sealed interface RecommendState {
    data object Idle    : RecommendState
    data object Loading : RecommendState
    data class  Success(val items: List<PersonalizedRecommendItem>) : RecommendState
    data class  Error(val message: String)                          : RecommendState
}

sealed interface MovieDetailState {
    data object Idle    : MovieDetailState
    data object Loading : MovieDetailState
    data class  Success(val movie: MovieDetailResponse) : MovieDetailState
    data class  Error(val message: String)              : MovieDetailState
}

class InteractionViewModel(
    private val repository: InteractionRepository
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _historyState = MutableStateFlow<HistoryState>(HistoryState.Idle)
    val historyState: StateFlow<HistoryState> = _historyState.asStateFlow()

    private val _recommendState = MutableStateFlow<RecommendState>(RecommendState.Idle)
    val recommendState: StateFlow<RecommendState> = _recommendState.asStateFlow()

    private val _movieDetailState = MutableStateFlow<MovieDetailState>(MovieDetailState.Idle)
    val movieDetailState: StateFlow<MovieDetailState> = _movieDetailState.asStateFlow()

    /**
     * Persist or update a user-movie interaction.
     * user_id is resolved from the JWT on the backend — not passed here.
     */
    fun saveInteraction(
        movieId: String,
        status: InteractionStatus,
        rating: Int? = null,
    ) {
        viewModelScope.launch {
            _saveState.value = SaveState.Loading
            repository.saveInteraction(movieId, status, rating)
                .onSuccess { _saveState.value = SaveState.Success(it) }
                .onFailure { _saveState.value = SaveState.Error(it.message ?: "Save failed") }
        }
    }

    fun loadUserHistory(userId: String) {
        viewModelScope.launch {
            _historyState.value = HistoryState.Loading
            repository.getUserHistory(userId)
                .onSuccess { _historyState.value = HistoryState.Success(it) }
                .onFailure { _historyState.value = HistoryState.Error(it.message ?: "Load failed") }
        }
    }

    fun getRecommendations(userId: String, topN: Int = 10) {
        viewModelScope.launch {
            _recommendState.value = RecommendState.Loading
            repository.getRecommendations(userId, topN)
                .onSuccess { _recommendState.value = RecommendState.Success(it.recommendations) }
                .onFailure { _recommendState.value = RecommendState.Error(it.message ?: "Recommendation failed") }
        }
    }

    fun getMovieDetails(movieId: String) {
        viewModelScope.launch {
            _movieDetailState.value = MovieDetailState.Loading
            repository.getMovieDetails(movieId)
                .onSuccess { _movieDetailState.value = MovieDetailState.Success(it) }
                .onFailure { _movieDetailState.value = MovieDetailState.Error(it.message ?: "Movie not found") }
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }
}
