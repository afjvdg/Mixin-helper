package com.example.minecraftmixinhelper.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minecraftmixinhelper.data.repository.MappingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: MappingRepository
) : ViewModel() {

    val versions = repository.getVersions()

    fun fetchVersions() {
        viewModelScope.launch {
            repository.fetchAndCacheVersions()
        }
    }

    fun downloadMappingsForVersion(version: String, versionJsonUrl: String) {
        viewModelScope.launch {
            repository.downloadAndParseMojangMappings(version, versionJsonUrl)
        }
    }
}