package com.naigen.app.ui.screen.generate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.naigen.app.NaiApplication
import com.naigen.app.data.model.GenRequest
import com.naigen.app.data.model.GenResult
import com.naigen.app.data.repository.GenProgress
import com.naigen.app.data.styles.StyleRegistry
import com.naigen.app.service.GenerationBus
import com.naigen.app.service.GenerationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI 状态。字段对齐 NAI Diffusion 4.5 全部参数。
 */
data class GenerateUiState(
    val prompt: String = "",
    val negative: String = "",
    val styleKey: String = "2.5d",
    val sizeKey: String = "竖图",
    val steps: Int? = null,
    val scale: Double? = null,
    val cfg: Double? = null,
    val sampler: String? = null,
    val seed: Long? = null,
    val sm: Boolean? = null,
    val smDynamic: Boolean? = null,
    val uncondScale: Double? = null,
    val noiseSchedule: String? = null,
    val varietyPlus: Boolean = false,
    val customArtist: String = "",
    val variants: Int = 1,
    val nsfwEnabled: Boolean = false,
    val isGenerating: Boolean = false,
    val progress: GenProgress = GenProgress.Idle,
    val results: List<GenResult> = emptyList(),
    val toast: String? = null
) {
    fun toRequest(): GenRequest = GenRequest(
        prompt = prompt,
        negativePrompt = negative,
        styleKey = styleKey,
        sizeKey = sizeKey,
        steps = steps,
        scale = scale,
        cfg = cfg,
        sampler = sampler,
        seed = seed,
        sm = sm,
        smDynamic = smDynamic,
        uncondScale = uncondScale,
        noiseSchedule = noiseSchedule,
        varietyPlus = varietyPlus,
        customArtist = customArtist
    )
}

class GenerateViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = getApplication<NaiApplication>().naiRepository
    private val settings = getApplication<NaiApplication>().settingsStore
    private val customRepo = getApplication<NaiApplication>().customStyleRepository

    private val _input = MutableStateFlow(GenerateUiState())
    private val _toast = MutableStateFlow<String?>(null)

    /** 自定义风格 flow（订阅后用于下拉菜单显示） */
    val customStyles = customRepo.observeAsPresets()

    val state: StateFlow<GenerateUiState> = combine(
        _input, GenerationBus.progress, GenerationBus.results, GenerationBus.isRunning, _toast
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val input = values[0] as GenerateUiState
        val progress = values[1] as GenProgress
        val results = values[2] as List<GenResult>
        val isRunning = values[3] as Boolean
        val toast = values[4] as String?
        input.copy(isGenerating = isRunning, progress = progress, results = results, toast = toast)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GenerateUiState())

    init {
        viewModelScope.launch {
            val lastStyle = settings.lastStyle.first()
            val lastSize = settings.lastSize.first()
            val lastPrompt = settings.lastPrompt.first()
            val lastNegative = settings.lastNegative.first()
            val nsfw = settings.nsfwEnabled.first()
            _input.update {
                it.copy(
                    styleKey = lastStyle, sizeKey = lastSize,
                    prompt = lastPrompt, negative = lastNegative, nsfwEnabled = nsfw
                )
            }
        }
        viewModelScope.launch {
            GenerationBus.events.collect { msg -> _toast.value = msg }
        }
    }

    fun updatePrompt(v: String) = _input.update { it.copy(prompt = v) }
    fun updateNegative(v: String) = _input.update { it.copy(negative = v) }
    fun updateStyle(k: String) = _input.update { it.copy(styleKey = k) }
    fun updateSize(k: String) = _input.update { it.copy(sizeKey = k) }
    fun updateSteps(v: Int?) = _input.update { it.copy(steps = v) }
    fun updateScale(v: Double?) = _input.update { it.copy(scale = v) }
    fun updateCfg(v: Double?) = _input.update { it.copy(cfg = v) }
    fun updateSampler(v: String?) = _input.update { it.copy(sampler = v) }
    fun updateSeed(v: Long?) = _input.update { it.copy(seed = v) }
    fun updateSm(v: Boolean?) = _input.update { it.copy(sm = v) }
    fun updateSmDynamic(v: Boolean?) = _input.update { it.copy(smDynamic = v) }
    fun updateUncondScale(v: Double?) = _input.update { it.copy(uncondScale = v) }
    fun updateNoiseSchedule(v: String?) = _input.update { it.copy(noiseSchedule = v) }
    fun updateVarietyPlus(v: Boolean) = _input.update { it.copy(varietyPlus = v) }
    fun updateCustomArtist(v: String) = _input.update { it.copy(customArtist = v) }
    fun updateVariants(v: Int) = _input.update { it.copy(variants = v.coerceIn(1, 99)) }
    fun clearToast() { _toast.value = null }
    fun clearResults() {
        GenerationBus.reset()
        _input.update { it.copy(results = emptyList(), progress = GenProgress.Idle) }
    }

    fun generate() {
        val current = _input.value
        if (GenerationBus.isRunning.value) return
        if (current.prompt.isBlank()) {
            _toast.value = "请输入提示词"
            return
        }

        viewModelScope.launch {
            settings.setLastPrompt(current.prompt)
            settings.setLastNegative(current.negative)
            settings.setLastStyle(current.styleKey)
            settings.setLastSize(current.sizeKey)

            GenerationBus.reset()
            GenerationService.start(
                getApplication(),
                prompt = current.prompt,
                negative = current.negative,
                styleKey = current.styleKey,
                sizeKey = current.sizeKey,
                variants = current.variants
            )
        }
    }

    fun cancel() {
        GenerationService.stop(getApplication())
        GenerationBus.markFinished()
        _toast.value = "已取消"
    }

    fun checkBalance() {
        viewModelScope.launch {
            val (points, err) = repo.checkBalance()
            _toast.value = if (err != null) "余额查询失败: $err" else "余额: $points 点"
        }
    }

    companion object {
        val Factory = ViewModelProvider.AndroidViewModelFactory::class.java
    }
}
