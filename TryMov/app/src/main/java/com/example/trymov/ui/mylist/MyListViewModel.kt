package com.example.trymov.ui.mylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trymov.data.repository.MovieRepository
import com.example.trymov.model.MyListEntry
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
    val addError: String? = null,
    val listMode: ListMode = ListMode.LIST
)

class MyListViewModel(
    private val repo: MovieRepository
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
    }

    fun setListMode(mode: ListMode) {
        _uiState.update { it.copy(listMode = mode) }
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
        viewModelScope.launch { repo.updateEntry(entry) }
    }

    fun removeEntry(id: Int) {
        viewModelScope.launch { repo.removeEntry(id) }
    }

    fun toggleFavorite(id: Int) {
        viewModelScope.launch { repo.toggleFavorite(id) }
    }

    fun updateRating(id: Int, rating: Int) {
        viewModelScope.launch { repo.updateRating(id, rating) }
    }
}

class MyListViewModelFactory(
    private val repo: MovieRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MyListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MyListViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
