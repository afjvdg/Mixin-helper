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

    // 实时建议（搜索框下拉，最多 10 条）
    private val _suggestions = MutableStateFlow<List<MappingEntity>>(emptyList())
    val suggestions: StateFlow<List<MappingEntity>> = _suggestions.asStateFlow()

    // 最近搜索（会话内记忆）
    private val _recentQueries = MutableStateFlow<List<String>>(emptyList())
    val recentQueries: StateFlow<List<String>> = _recentQueries.asStateFlow()

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
                        _suggestions.value = emptyList()
                    } else {
                        _loading.value = true
                        _searchResults.value = repository.fuzzySearch(q, t, v)
                        _suggestions.value = repository.suggest(q, t, v)
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

    /** 记录一次「确定」的搜索词（IME 搜索键 / 点击建议），用于最近搜索。 */
    fun commitQuery(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return
        _recentQueries.value = (listOf(trimmed) + _recentQueries.value.filter { it != trimmed }).take(8)
    }

    fun clearRecentQueries() {
        _recentQueries.value = emptyList()
    }
}
