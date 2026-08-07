package com.example.minecraftmixinhelper.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minecraftmixinhelper.data.local.MappingEntity
import com.example.minecraftmixinhelper.data.local.VersionLoaderRow
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

    // 实时搜索结果（直接在结果区域呈现，无单独联想菜单）
    private val _searchResults = MutableStateFlow<List<MappingEntity>>(emptyList())
    val searchResults: StateFlow<List<MappingEntity>> = _searchResults.asStateFlow()

    // 最近搜索（会话内记忆）
    private val _recentQueries = MutableStateFlow<List<String>>(emptyList())
    val recentQueries: StateFlow<List<String>> = _recentQueries.asStateFlow()

    // 已下载的「版本 + 加载器」对（版本范围选择数据源）
    private val _versionLoaders = MutableStateFlow<List<VersionLoaderRow>>(emptyList())
    val versionLoaders: StateFlow<List<VersionLoaderRow>> = _versionLoaders.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // query / type / version / loader 状态流，250ms 防抖后触发搜索
    private val _query = MutableStateFlow("")
    private val _type = MutableStateFlow("CLASS")
    private val _version = MutableStateFlow("")
    private val _loader = MutableStateFlow("")

    init {
        viewModelScope.launch {
            combine(_query, _type, _version, _loader) { q, t, v, l -> Quad(q, t, v, l) }
                .debounce(250)
                .collect { (q, t, v, l) ->
                    if (q.isBlank()) {
                        _searchResults.value = emptyList()
                        _loading.value = false
                    } else {
                        _loading.value = true
                        _searchResults.value = repository.fuzzySearch(q, t, v, l)
                        _loading.value = false
                    }
                }
        }
        viewModelScope.launch {
            _versionLoaders.value = repository.getDownloadedVersionLoaders()
        }
    }

    fun setQuery(q: String) { _query.value = q }
    fun setType(t: String) { _type.value = t }

    /** 选择某个「版本 + 加载器」作为搜索范围；传 null 表示不限定。 */
    fun setVersionLoader(row: VersionLoaderRow?) {
        _version.value = row?.version ?: ""
        _loader.value = row?.loader ?: ""
    }

    /** 记录一次「确定」的搜索词（IME 搜索键），用于最近搜索。 */
    fun commitQuery(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return
        _recentQueries.value = (listOf(trimmed) + _recentQueries.value.filter { it != trimmed }).take(8)
    }

    fun clearRecentQueries() {
        _recentQueries.value = emptyList()
    }

    private data class Quad(
        val query: String,
        val type: String,
        val version: String,
        val loader: String
    )
}
