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
    val lastStyleKey: String = "2.5d",
    val themeMode: String = "system"
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val settings: SettingsStore = getApplication<NaiApplication>().settingsStore

    val state: StateFlow<SettingsUiState> = combine(
        settings.token, settings.baseUrl, settings.nsfwEnabled, settings.lastStyle, settings.themeMode
    ) { token, baseUrl, nsfwEnabled, lastStyle, themeMode ->
        SettingsUiState(
            token = token,
            baseUrl = baseUrl,
            nsfwEnabled = nsfwEnabled,
            lastStyleKey = lastStyle,
            themeMode = themeMode
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setToken(v: String) = viewModelScope.launch { settings.setToken(v) }
    fun setBaseUrl(v: String) = viewModelScope.launch { settings.setBaseUrl(v) }
    fun setNsfw(v: Boolean) = viewModelScope.launch { settings.setNsfwEnabled(v) }
    fun setThemeMode(v: String) = viewModelScope.launch { settings.setThemeMode(v) }
}
