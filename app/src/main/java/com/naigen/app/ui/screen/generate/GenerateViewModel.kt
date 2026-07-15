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
 * 生成页 UI 状态。
 *
 * 字段对齐教程 §5.5.15 cmd_nai2api 的所有参数。
 *
 * 注意：[isGenerating] 和 [progress] 和 [results] 现在直接从 [GenerationBus] 派生，
 * 这样无论 App 在前台还是后台，Service 写入 Bus 的状态都能立即同步到 UI。
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
    val customArtist: String = "",
    val variants: Int = 1,
    val autoStyle: Boolean = false,
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
        customArtist = customArtist
    )
}

class GenerateViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = getApplication<NaiApplication>().naiRepository
    private val settings = getApplication<NaiApplication>().settingsStore

    /** 用户输入态：prompt / negative / 风格 / 尺寸 / 高级参数 */
    private val _input = MutableStateFlow(GenerateUiState())

    /** Toast 事件 */
    private val _toast = MutableStateFlow<String?>(null)

    /** 派生态：把用户输入与 Service Bus 状态合并，最终给 UI 用 */
    val state: StateFlow<GenerateUiState> = combine(
        _input, GenerationBus.progress, GenerationBus.results, GenerationBus.isRunning, _toast
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val input = values[0] as GenerateUiState
        val progress = values[1] as GenProgress
        val results = values[2] as List<GenResult>
        val isRunning = values[3] as Boolean
        val toast = values[4] as String?
        input.copy(
            isGenerating = isRunning,
            progress = progress,
            results = results,
            toast = toast
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GenerateUiState())

    init {
        // 从 DataStore 恢复上次参数
        viewModelScope.launch {
            val lastStyle = settings.lastStyle.first()
            val lastSize = settings.lastSize.first()
            val lastPrompt = settings.lastPrompt.first()
            val lastNegative = settings.lastNegative.first()
            val nsfw = settings.nsfwEnabled.first()
            _input.update {
                it.copy(
                    styleKey = lastStyle,
                    sizeKey = lastSize,
                    prompt = lastPrompt,
                    negative = lastNegative,
                    nsfwEnabled = nsfw
                )
            }
        }

        // 订阅 Service 发出的事件（错误消息等）
        viewModelScope.launch {
            GenerationBus.events.collect { msg ->
                _toast.value = msg
            }
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
    fun updateCustomArtist(v: String) = _input.update { it.copy(customArtist = v) }
    fun updateVariants(v: Int) = _input.update { it.copy(variants = v.coerceIn(1, 6)) }
    fun toggleAutoStyle() = _input.update { it.copy(autoStyle = !it.autoStyle) }
    fun clearToast() { _toast.value = null }
    fun clearResults() {
        GenerationBus.reset()
        _input.update { it.copy(results = emptyList(), progress = GenProgress.Idle) }
    }

    /**
     * 触发生成 —— 启动前台 Service，UI 通过 [GenerationBus] 接收进度。
     *
     * 流程对齐教程 §5.5.15 cmd_nai2api：
     *   1) 持久化本次参数
     *   2) 自动风格检测（如开启，更新 UI 选中的风格）
     *   3) 启动 GenerationService，传入完整请求参数
     *   4) Service 跑 Job 异步流程，结果落盘 + 写历史 + 广播到 Bus
     */
    fun generate() {
        val current = _input.value
        if (GenerationBus.isRunning.value) return
        if (current.prompt.isBlank()) {
            _toast.value = "请输入提示词"
            return
        }

        viewModelScope.launch {
            // 1) 持久化
            settings.setLastPrompt(current.prompt)
            settings.setLastNegative(current.negative)
            settings.setLastStyle(current.styleKey)
            settings.setLastSize(current.sizeKey)

            // 2) 自动风格检测
            var effectiveStyleKey = current.styleKey
            if (current.autoStyle) {
                val detected = repo.autoDetectStyle(current.prompt)
                if (detected != null) {
                    effectiveStyleKey = detected
                    val name = StyleRegistry.get(detected)?.name ?: detected
                    _input.update { it.copy(styleKey = detected) }
                    _toast.value = "[auto-style] 命中: $name"
                } else {
                    _toast.value = "[auto-style] 未匹配，使用默认: ${current.styleKey}"
                }
            }

            // 3) 重置 Bus + 启动 Service
            GenerationBus.reset()
            GenerationService.start(
                getApplication(),
                prompt = current.prompt,
                negative = current.negative,
                styleKey = effectiveStyleKey,
                sizeKey = current.sizeKey,
                variants = current.variants
            )
        }
    }

    /**
     * 取消当前生成（停止 Service）。
     */
    fun cancel() {
        GenerationService.stop(getApplication())
        GenerationBus.markFinished()
        _toast.value = "已取消"
    }

    /**
     * 余额查询。
     */
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
