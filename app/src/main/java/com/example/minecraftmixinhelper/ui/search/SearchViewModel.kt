package com.example.minecraftmixinhelper.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minecraftmixinhelper.data.local.MappingEntity
import com.example.minecraftmixinhelper.data.repository.MappingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MappingRepository
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<MappingEntity>>(emptyList())
    val searchResults: StateFlow<List<MappingEntity>> = _searchResults.asStateFlow()

    private val _downloadedVersions = MutableStateFlow<List<String>>(emptyList())
    val downloadedVersions: StateFlow<List<String>> = _downloadedVersions.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // query / type / version 三状态流，250ms 防抖后触发搜索
    private val _query = MutableStateFlow("")
    private val _type = MutableStateFlow("ALL")
    private val _version = MutableStateFlow("")

    init {
        viewModelScope.launch {
            combine(_query, _type, _version) { q, t, v -> Triple(q, t, v) }
                .debounce(250)
                .collect { (q, t, v) ->
                    if (q.isBlank()) {
                        _searchResults.value = emptyList()
                    } else {
                        _loading.value = true
                        _searchResults.value = repository.fuzzySearch(q, t, v)
                        _loading.value = false
                    }
                }
        }
        viewModelScope.launch {
            _downloadedVersions.value = repository.getDownloadedVersions()
        }
    }

    fun setQuery(q: String) { _query.value = q }
    fun setType(t: String) { _type.value = t }
    fun setVersion(v: String) { _version.value = v }
}
