package com.naigen.app.ui.screen.settings.styles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naigen.app.NaiApplication
import com.naigen.app.data.db.entities.CustomStyleEntity
import com.naigen.app.data.model.StylePreset
import com.naigen.app.data.prefs.SettingsStore
import com.naigen.app.data.styles.ArtistPresets
import com.naigen.app.data.styles.CommunityStyles
import com.naigen.app.data.styles.StyleRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StyleManagerState(
    val selectedKey: String = "2.5d",
    val nsfwEnabled: Boolean = false,
    val searchQuery: String = "",
    val customStyles: List<StylePreset> = emptyList(),
    val allStyles: Map<String, StylePreset> = emptyMap()
)

class StyleManagerViewModel(app: Application) : AndroidViewModel(app) {
    private val settings: SettingsStore = getApplication<NaiApplication>().settingsStore
    private val customRepo = getApplication<NaiApplication>().customStyleRepository

    private val _selected = MutableStateFlow("2.5d")
    private val _nsfw = MutableStateFlow(false)
    private val _query = MutableStateFlow("")

    val state: StateFlow<StyleManagerState> = combine(
        _selected, _nsfw, _query, customRepo.observeAsPresets()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val selected = values[0] as String
        val nsfw = values[1] as Boolean
        val query = values[2] as String
        val customs = values[3] as List<StylePreset>

        val merged = StyleRegistry.mergedWith(customs)
        StyleManagerState(
            selectedKey = selected,
            nsfwEnabled = nsfw,
            searchQuery = query,
            customStyles = customs,
            allStyles = merged
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StyleManagerState())

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

    fun addCustomStyle(
        name: String,
        artistString: String,
        positivePrefix: String,
        negativePrompt: String,
        steps: Int,
        scale: Double,
        cfg: Double,
        sampler: String
    ) = viewModelScope.launch {
        customRepo.insert(
            CustomStyleEntity(
                name = name,
                artistString = artistString,
                positivePrefix = positivePrefix,
                negativePrompt = negativePrompt,
                steps = steps,
                scale = scale,
                cfg = cfg,
                sampler = sampler
            )
        )
    }

    fun deleteCustomStyle(style: StylePreset) = viewModelScope.launch {
        // 从 key 提取 id：key 格式 "custom_<id>"
        val id = style.key.removePrefix("custom_").toLongOrNull() ?: return@launch
        customRepo.get(id)?.let { customRepo.delete(it) }
    }

    val builtinStyles: List<StylePreset> = ArtistPresets.ALL
    val communityStyles: List<StylePreset> = CommunityStyles.ALL
}
