package com.example.trymov.ui.mylist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trymov.auth.TokenManager
import com.example.trymov.data.repository.MovieRepository
import com.example.trymov.fastapi.InteractionRepository
import com.example.trymov.fastapi.InteractionStatus
import com.example.trymov.model.MyListEntry
import com.example.trymov.model.WatchStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ListMode { LIST, GRID }

data class MyListUiState(
    val entries: List<MyListEntry> = emptyList(),
    val isAdding: Boolean = false,
    val isSyncing: Boolean = false,
    val addError: String? = null,
    val listMode: ListMode = ListMode.LIST
)

class MyListViewModel(
    private val repo: MovieRepository,
    private val interactionRepo: InteractionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyListUiState())
    val uiState: StateFlow<MyListUiState> = _uiState.asStateFlow()

    private val events = Channel<String>(capacity = Channel.BUFFERED)
    val eventMessages = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            repo.observeMyList().collect { entries ->
                _uiState.update { it.copy(entries = entries) }
            }
        }
        pullFromRemote()
    }

    fun setListMode(mode: ListMode) {
        _uiState.update { it.copy(listMode = mode) }
    }

    private fun pullFromRemote() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                repo.syncFromRemote()
                val userId = TokenManager.getUserId()
                if (userId != null) {
                    interactionRepo.getUserHistory(userId)
                        .onSuccess { repo.applyInteractions(it) }
                        .onFailure { Log.w(TAG, "Interaction pull failed: ${it.message}") }
                }
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun syncAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                repo.pushToRemote()
                val entries = _uiState.value.entries
                for (entry in entries) {
                    val status = when (entry.status) {
                        WatchStatus.WATCHING  -> InteractionStatus.WATCHING
                        WatchStatus.COMPLETED -> InteractionStatus.COMPLETED
                        WatchStatus.PLANNED   -> InteractionStatus.PLAN_TO_WATCH
                        WatchStatus.DROPPED   -> InteractionStatus.PLAN_TO_WATCH
                    }
                    interactionRepo.saveInteraction(entry.imdbId, status, entry.rating.takeIf { it > 0 })
                        .onFailure { Log.w(TAG, "Push interaction failed for ${entry.imdbId}: ${it.message}") }
                }
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun addByImdbId(imdbId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAdding = true, addError = null) }

            when (val res = repo.addByImdbId(imdbId)) {
                is MovieRepository.AddByImdbResult.Success -> {
                    _uiState.update { it.copy(isAdding = false, addError = null) }
                    events.trySend("Added")
                }
                MovieRepository.AddByImdbResult.Duplicate -> {
                    _uiState.update { it.copy(isAdding = false, addError = "Already in your list") }
                }
                is MovieRepository.AddByImdbResult.Error -> {
                    _uiState.update { it.copy(isAdding = false, addError = res.message) }
                }
            }
        }
    }

    fun updateEntry(entry: MyListEntry) {
        viewModelScope.launch {
            repo.updateEntry(entry)
            syncInteraction(entry.imdbId, entry.status, entry.rating)
        }
    }

    fun removeEntry(id: Int) {
        val imdbId = _uiState.value.entries.find { it.id == id }?.imdbId
        viewModelScope.launch {
            repo.removeEntry(id)
            if (imdbId != null) {
                interactionRepo.deleteInteraction(imdbId)
                    .onFailure { Log.w(TAG, "Remote delete failed for $imdbId: ${it.message}") }
            }
        }
    }

    fun toggleFavorite(id: Int) {
        viewModelScope.launch { repo.toggleFavorite(id) }
    }

    fun updateRating(id: Int, rating: Int) {
        val entry = _uiState.value.entries.find { it.id == id } ?: return
        viewModelScope.launch {
            repo.updateRating(id, rating)
            syncInteraction(entry.imdbId, entry.status, rating)
        }
    }

    private fun syncInteraction(imdbId: String, status: WatchStatus, rating: Int) {
        val interactionStatus = when (status) {
            WatchStatus.WATCHING  -> InteractionStatus.WATCHING
            WatchStatus.COMPLETED -> InteractionStatus.COMPLETED
            WatchStatus.PLANNED   -> InteractionStatus.PLAN_TO_WATCH
            WatchStatus.DROPPED   -> InteractionStatus.PLAN_TO_WATCH
        }
        viewModelScope.launch {
            interactionRepo.saveInteraction(imdbId, interactionStatus, rating.takeIf { it > 0 })
                .onFailure { Log.w(TAG, "Interaction save failed for $imdbId: ${it.message}") }
        }
    }

    companion object {
        private const val TAG = "MyListViewModel"
    }
}

class MyListViewModelFactory(
    private val repo: MovieRepository,
    private val interactionRepo: InteractionRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MyListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MyListViewModel(repo, interactionRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
