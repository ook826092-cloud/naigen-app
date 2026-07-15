package com.naigen.app.ui.screen.stylepicker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naigen.app.NaiApplication
import com.naigen.app.data.model.NsfwLevel
import com.naigen.app.data.model.StylePreset
import com.naigen.app.data.model.StyleSource
import com.naigen.app.data.prefs.SettingsStore
import com.naigen.app.data.styles.ArtistPresets
import com.naigen.app.data.styles.CommunityStyles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StylePickerState(
    val selectedKey: String = "2.5d",
    val nsfwEnabled: Boolean = false,
    val searchQuery: String = ""
)

class StylePickerViewModel(app: Application) : AndroidViewModel(app) {
    private val settings: SettingsStore = getApplication<NaiApplication>().settingsStore

    private val _selected = MutableStateFlow("2.5d")
    private val _nsfw = MutableStateFlow(false)
    private val _query = MutableStateFlow("")

    val state: StateFlow<StylePickerState> = combine(_selected, _nsfw, _query) { s, n, q ->
        StylePickerState(s, n, q)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StylePickerState())

    val builtinStyles: List<StylePreset> = ArtistPresets.ALL
    val communityStyles: List<StylePreset> = CommunityStyles.ALL

    init {
        viewModelScope.launch {
            _selected.value = settings.lastStyle.first()
            _nsfw.value = settings.nsfwEnabled.first()
        }
    }

    fun select(key: String) {
        _selected.value = key
        viewModelScope.launch { settings.setLastStyle(key) }
    }

    fun setNsfw(enabled: Boolean) {
        _nsfw.value = enabled
        viewModelScope.launch { settings.setNsfwEnabled(enabled) }
    }

    fun search(q: String) { _query.value = q }

    fun filter(styles: List<StylePreset>): List<StylePreset> {
        val q = _query.value.trim().lowercase()
        return styles.filter { s ->
            (state.value.nsfwEnabled || s.nsfw == NsfwLevel.SAFE) &&
            (q.isBlank() || s.name.lowercase().contains(q) || s.key.lowercase().contains(q))
        }
    }
}
