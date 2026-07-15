package com.naigen.app.service

import com.naigen.app.data.model.GenResult
import com.naigen.app.data.repository.GenProgress
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨进程共享的生成状态总线。
 *
 * - [Service] 写入：进度事件 + 最终结果
 * - [GenerateViewModel] 读取：驱动 UI 更新
 * - [GenerateViewModel] 写入：发起请求时清空上一次状态
 *
 * 单例 object，避免 ViewModel 与 Service 之间复杂的 IPC。
 */
object GenerationBus {

    private val _progress = MutableStateFlow<GenProgress>(GenProgress.Idle)
    val progress: StateFlow<GenProgress> = _progress.asStateFlow()

    private val _results = MutableStateFlow<List<GenResult>>(emptyList())
    val results: StateFlow<List<GenResult>> = _results.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /**
     * 开始一次新任务时调用，清空旧状态。
     */
    fun reset() {
        _progress.value = GenProgress.Idle
        _results.value = emptyList()
        _isRunning.value = true
    }

    fun publishProgress(p: GenProgress) {
        _progress.value = p
    }

    fun publishResults(rs: List<GenResult>) {
        _results.value = rs
        _isRunning.value = false
        _progress.value = GenProgress.AllDone(rs)
    }

    fun publishEvent(msg: String) {
        _events.tryEmit(msg)
    }

    fun markFinished() {
        _isRunning.value = false
    }
}
