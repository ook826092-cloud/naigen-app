package com.naigen.app.ui.screen.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naigen.app.NaiApplication
import com.naigen.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val token: String = "",
    val baseUrl: String = SettingsStore.DEFAULT_BASE_URL,
    val nsfwEnabled: Boolean = false,
    val lastStyleKey: String = "2.5d"
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val settings: SettingsStore = getApplication<NaiApplication>().settingsStore

    val state: StateFlow<SettingsUiState> = combine(
        settings.token, settings.baseUrl, settings.nsfwEnabled, settings.lastStyle
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        SettingsUiState(
            token = values[0] as String,
            baseUrl = values[1] as String,
            nsfwEnabled = values[2] as Boolean,
            lastStyleKey = values[3] as String
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setToken(v: String) = viewModelScope.launch { settings.setToken(v) }
    fun setBaseUrl(v: String) = viewModelScope.launch { settings.setBaseUrl(v) }
    fun setNsfw(v: Boolean) = viewModelScope.launch { settings.setNsfwEnabled(v) }
}
