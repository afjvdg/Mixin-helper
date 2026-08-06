package com.example.minecraftmixinhelper.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minecraftmixinhelper.data.local.MappingEntity
import com.example.minecraftmixinhelper.data.repository.MappingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MappingRepository
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<MappingEntity>>(emptyList())
    val searchResults: StateFlow<List<MappingEntity>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    private var searchJob: Job? = null

    fun search(query: String, type: String = "CLASS") {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _hasSearched.value = false
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            // 防抖 300ms
            delay(300)
            try {
                _searchResults.value = repository.fuzzySearch(query, type)
                _hasSearched.value = true
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                _hasSearched.value = true
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clear() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _hasSearched.value = false
        _isSearching.value = false
    }
}
