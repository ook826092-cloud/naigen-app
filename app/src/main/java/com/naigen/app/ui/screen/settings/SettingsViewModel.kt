package com.naigen.app.ui.screen.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naigen.app.NaiApplication
import com.naigen.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val token: String = "",
    val baseUrl: String = SettingsStore.DEFAULT_BASE_URL,
    val nsfwEnabled: Boolean = false,
    val balancePoints: Int? = null,
    val balanceError: String? = null,
    val checking: Boolean = false
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val settings: SettingsStore = getApplication<NaiApplication>().settingsStore
    private val repo = getApplication<NaiApplication>().naiRepository

    private val _balance = MutableStateFlow<Pair<Int?, String?>?>(null)
    private val _checking = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(
        settings.token, settings.baseUrl, settings.nsfwEnabled, _balance, _checking
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val token = values[0] as String
        val baseUrl = values[1] as String
        val nsfw = values[2] as Boolean
        val bal = values[3] as Pair<Int?, String?>?
        val checking = values[4] as Boolean
        SettingsUiState(
            token = token,
            baseUrl = baseUrl,
            nsfwEnabled = nsfw,
            balancePoints = bal?.first,
            balanceError = bal?.second,
            checking = checking
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setToken(v: String) = viewModelScope.launch { settings.setToken(v) }
    fun setBaseUrl(v: String) = viewModelScope.launch { settings.setBaseUrl(v) }
    fun setNsfw(v: Boolean) = viewModelScope.launch { settings.setNsfwEnabled(v) }

    fun checkBalance() {
        viewModelScope.launch {
            _checking.value = true
            _balance.value = repo.checkBalance()
            _checking.value = false
        }
    }
}
