package com.naigen.app.ui.screen.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naigen.app.NaiApplication
import com.naigen.app.data.provider.ApiProvider
import com.naigen.app.data.provider.ProviderRegistry
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
    val themeMode: String = "system",
    val dynamicColor: Boolean = true
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val settings: SettingsStore = getApplication<NaiApplication>().settingsStore
    val providerRegistry: ProviderRegistry = getApplication<NaiApplication>().providerRegistry

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
    }.combine(settings.dynamicColor) { state, dynamicColor ->
        state.copy(dynamicColor = dynamicColor)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    // ── 供应商管理 ───────────────────────────────────────────────────────

    /** 所有 provider 列表（内置 + 自定义） */
    val providers = providerRegistry.providers.stateIn(
        viewModelScope, SharingStarted.Eagerly, listOf(ApiProvider.BUILTIN_NAI2)
    )

    /** 当前选中的 provider id */
    val selectedProviderId = providerRegistry.selectedProviderId.stateIn(
        viewModelScope, SharingStarted.Eagerly, ApiProvider.BUILTIN_NAI2_ID
    )

    fun setSelectedProvider(id: String) = viewModelScope.launch {
        providerRegistry.setSelectedProvider(id)
    }

    fun addProvider(provider: ApiProvider) = viewModelScope.launch {
        // 新增：直接把整个 provider 写入（id/createdAt 已由 UI 生成）
        providerRegistry.addCustomProvider(
            name = provider.name,
            type = provider.type,
            baseUrl = provider.baseUrl,
            iconUri = provider.iconUri,
            note = provider.note
        )
    }

    fun updateProvider(provider: ApiProvider) = viewModelScope.launch {
        providerRegistry.updateProvider(provider)
    }

    fun deleteProvider(id: String) = viewModelScope.launch {
        providerRegistry.deleteProvider(id)
    }

    // ── 旧设置 ───────────────────────────────────────────────────────────

    fun setToken(v: String) = viewModelScope.launch { settings.setToken(v) }
    fun setBaseUrl(v: String) = viewModelScope.launch { settings.setBaseUrl(v) }
    fun setNsfw(v: Boolean) = viewModelScope.launch { settings.setNsfwEnabled(v) }
    fun setThemeMode(v: String) = viewModelScope.launch { settings.setThemeMode(v) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { settings.setDynamicColor(v) }
}
