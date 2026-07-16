package com.naigen.app.util

import android.content.Context
import com.naigen.app.util.logging.FileLogTree
import com.naigen.app.util.logging.LogLevel
import com.naigen.app.util.logging.LogRecord
import com.naigen.app.util.logging.LogService
import java.io.File

/**
 * AppLog —— 门面对象（v7，kelivo-style Tree 架构）。
 *
 * 这一层只做转发，实际逻辑在 [LogService] + 各 [com.naigen.app.util.logging.LogTree] 里。
 * 保留旧 API 是为了不动调用方（7 个文件 + LogsScreen + 单测），降低迁移风险。
 *
 * 架构：
 * ```
 * AppLog (门面)
 *   └─ LogService (分发中枢)
 *        ├─ ConsoleLogTree  → Logcat
 *        ├─ FileLogTree     → 文件 + 内存缓冲
 *        └─ CrashLogTree    → 全局崩溃捕获
 * ```
 *
 * 新代码建议直接用 [LogService]（更清晰），AppLog 主要给老代码和 UI 层用。
 */
object AppLog {

    /** 兼容旧代码的 Entry 类型（映射到 LogRecord） */
    data class Entry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    )

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class NetworkEntry(
        val timestamp: Long,
        val method: String,
        val url: String,
        val requestHeaders: Map<String, String>,
        val requestBody: String,
        val responseCode: Int,
        val responseHeaders: Map<String, String>,
        val responseBody: String,
        val durationMs: Long,
        val fileName: String
    )

    data class LogFileInfo(
        val file: File,
        val name: String,
        val size: Long,
        val type: String,
        val modified: Long
    )

    data class NetworkSummary(
        val fileName: String,
        val method: String,
        val url: String,
        val responseCode: Int,
        val durationMs: Long,
        val timestamp: Long,
        val size: Long
    )

    data class NetworkFileDetail(
        val requestHeaders: String,
        val requestBody: String,
        val responseHeaders: String,
        val responseBody: String
    )

    // ── 初始化 ───────────────────────────────────────────────────────────

    fun init(context: Context) {
        LogService.init(context)
        i("AppLog", "日志系统初始化 (v7, kelivo-style Tree 架构)")
    }

    // ── 落盘开关 / 自动删除（委托 FileLogTree）────────────────────────────

    fun isEnabled(): Boolean = LogService.fileTree?.isEnabled() ?: false
    fun setEnabled(v: Boolean) { LogService.fileTree?.setEnabled(v) }

    var maxAgeMs: Long
        get() = LogService.fileTree?.maxAgeMs ?: 0
        set(value) { LogService.fileTree?.maxAgeMs = value }

    var maxSizeBytes: Long
        get() = LogService.fileTree?.maxSizeBytes ?: 0
        set(value) { LogService.fileTree?.maxSizeBytes = value }

    // ── 日志写入（委托 LogService 分发）──────────────────────────────────

    fun d(tag: String, message: String) = LogService.d(tag, message)
    fun i(tag: String, message: String) = LogService.i(tag, message)
    fun w(tag: String, message: String, t: Throwable? = null) = LogService.w(tag, message, t)
    fun e(tag: String, message: String, t: Throwable? = null) = LogService.e(tag, message, t)

    fun network(
        method: String, url: String,
        requestHeaders: Map<String, String> = emptyMap(), requestBody: String = "",
        responseCode: Int = 0, responseHeaders: Map<String, String> = emptyMap(), responseBody: String = "",
        durationMs: Long = 0
    ) = LogService.network(
        method = method, url = url,
        requestHeaders = requestHeaders, requestBody = requestBody,
        responseCode = responseCode, responseHeaders = responseHeaders,
        responseBody = responseBody, durationMs = durationMs
    )

    // ── 脱敏（委托 TokenRedactor，供单测访问）─────────────────────────────

    internal fun redactToken(raw: String): String = TokenRedactor.DEFAULT.redact(raw)

    // ── 内存缓冲查询（委托 FileLogTree）──────────────────────────────────

    fun getAppEntries(): List<Entry> =
        LogService.fileTree?.appBufferView?.map { it.toEntry() } ?: emptyList()

    fun getNetworkEntries(): List<NetworkEntry> =
        LogService.fileTree?.networkBufferView?.map {
            NetworkEntry(
                it.timestamp, it.method, it.url, it.requestHeaders, it.requestBody,
                it.responseCode, it.responseHeaders, it.responseBody, it.durationMs, it.fileName
            )
        } ?: emptyList()

    // ── 文件查询（委托 FileLogTree）──────────────────────────────────────

    fun getNetworkFiles(): List<LogFileInfo> =
        LogService.fileTree?.getNetworkFiles()?.map { it.toLogFileInfo() } ?: emptyList()

    fun getAppFiles(): List<LogFileInfo> =
        LogService.fileTree?.getAppFiles()?.map { it.toLogFileInfo() } ?: emptyList()

    fun getFileContent(name: String, isNetwork: Boolean = false): String =
        LogService.fileTree?.getFileContent(name, isNetwork) ?: ""

    fun getNetworkSummaries(): List<NetworkSummary> =
        LogService.fileTree?.getNetworkSummaries()?.map {
            NetworkSummary(it.fileName, it.method, it.url, it.responseCode, it.durationMs, it.timestamp, it.size)
        } ?: emptyList()

    fun getNetworkFileDetail(fileName: String): NetworkFileDetail {
        val d = LogService.fileTree?.getNetworkFileDetail(fileName)
            ?: return NetworkFileDetail("", "", "", "")
        return NetworkFileDetail(d.requestHeaders, d.requestBody, d.responseHeaders, d.responseBody)
    }

    fun getFile(name: String, isNetwork: Boolean = false): File? =
        LogService.fileTree?.getFile(name, isNetwork)

    fun deleteFile(name: String, isNetwork: Boolean = false) {
        LogService.fileTree?.deleteFile(name, isNetwork)
    }

    fun clearAppBuffer() { LogService.fileTree?.clearBuffers() }
    fun clearNetworkBuffer() { LogService.fileTree?.clearBuffers() }

    fun clearAll() {
        LogService.fileTree?.clearBuffers()
        LogService.fileTree?.clearAllFiles()
        i("AppLog", "所有日志已清空")
    }

    fun getTotalSize(): Long = LogService.fileTree?.getTotalSize() ?: 0

    // ── 格式化（委托 LogFormatter）───────────────────────────────────────

    fun formatAppEntry(entry: Entry): String =
        LogFormatter.DEFAULT.format(entry.toRecord())

    // ── 内部转换 ─────────────────────────────────────────────────────────

    private fun LogRecord.toEntry(): Entry = Entry(
        timestamp, when (level) {
            LogLevel.DEBUG -> Level.DEBUG
            LogLevel.INFO  -> Level.INFO
            LogLevel.WARN  -> Level.WARN
            LogLevel.ERROR -> Level.ERROR
        }, tag, message, throwable
    )

    private fun Entry.toRecord(): LogRecord = LogRecord(
        timestamp, when (level) {
            Level.DEBUG -> LogLevel.DEBUG
            Level.INFO  -> LogLevel.INFO
            Level.WARN  -> LogLevel.WARN
            Level.ERROR -> LogLevel.ERROR
        }, tag, message, throwable
    )

    private fun FileLogTree.LogFileInfo.toLogFileInfo(): LogFileInfo =
        LogFileInfo(file, name, size, type, modified)
}
