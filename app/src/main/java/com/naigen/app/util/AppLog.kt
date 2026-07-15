package com.naigen.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * 应用日志工具 v3 —— 单文件追加模式。
 *
 * 只写一个文件：filesDir/logs/app.log
 * 一直追加写下去，超过 maxSizeBytes 时自动从头截断（保留后半部分）。
 *
 * 记录的是代码运行日志：
 *   - 每个 suspend 函数的进入和退出
 *   - 每个网络请求的完整 request/response
 *   - 每个 Room 数据库操作
 *   - 每个 ViewModel 状态变更
 *   - 每个异常的完整堆栈
 */
object AppLog {

    private const val TAG = "NaiGen"
    private const val MAX_BUFFER = 1000
    private const val LOG_DIR = "logs"
    private const val LOG_FILE = "app.log"
    private const val DEFAULT_MAX_SIZE = 500 * 1024 * 1024L  // 默认 500MB

    enum class Level { DEBUG, INFO, WARN, ERROR, NETWORK }

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    )

    private val buffer = ConcurrentLinkedDeque<Entry>()
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    var maxSizeBytes: Long = DEFAULT_MAX_SIZE
    var maxAgeMs: Long = 0  // 0 = 不按时间删

    fun init(context: Context) {
        val dir = File(context.filesDir, LOG_DIR).apply { if (!exists()) mkdirs() }
        logFile = File(dir, LOG_FILE)
        i("AppLog", "=== 日志系统初始化, 文件: ${logFile?.absolutePath} ===")
        installCrashHandler(context)
    }

    // ── 基础日志方法 ──────────────────────────────────────────────────────

    fun d(tag: String, message: String) {
        Log.d(TAG, "[$tag] $message")
        addEntry(Level.DEBUG, tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(TAG, "[$tag] $message")
        addEntry(Level.INFO, tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(TAG, "[$tag] $message", throwable)
        addEntry(Level.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[$tag] $message", throwable)
        addEntry(Level.ERROR, tag, message, throwable)
    }

    /**
     * 网络请求日志 —— 完整记录请求和响应。
     */
    fun network(
        method: String,
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
        requestBody: String = "",
        responseCode: Int = 0,
        responseHeaders: Map<String, String> = emptyMap(),
        responseBody: String = "",
        durationMs: Long = 0
    ) {
        val sb = StringBuilder()
        sb.append("━━━ ${method} ${url}")
        if (durationMs > 0) sb.append("  (${durationMs}ms)")
        sb.append("\n")
        if (requestHeaders.isNotEmpty()) {
            sb.append("── 请求头 ──\n")
            requestHeaders.forEach { (k, v) -> sb.append("  $k: $v\n") }
        }
        if (requestBody.isNotBlank()) {
            sb.append("── 请求体 ──\n")
            // 格式化 JSON
            sb.append(formatJson(requestBody))
            sb.append("\n")
        }
        if (responseCode > 0) {
            sb.append("── 响应 $responseCode ──\n")
            if (responseHeaders.isNotEmpty()) {
                sb.append("响应头:\n")
                responseHeaders.forEach { (k, v) -> sb.append("  $k: $v\n") }
            }
            if (responseBody.isNotBlank()) {
                sb.append("响应体:\n")
                sb.append(formatJson(responseBody))
                sb.append("\n")
            }
        }
        sb.append("━━━")
        val msg = sb.toString()
        Log.i(TAG, "[NET] $msg")
        addEntry(Level.NETWORK, "NET", msg)
    }

    // ── 内部写入逻辑 ──────────────────────────────────────────────────────

    private fun addEntry(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        val entry = Entry(System.currentTimeMillis(), level, tag, message, throwable)
        buffer.addLast(entry)
        while (buffer.size > MAX_BUFFER) buffer.pollFirst()
        writeToFile(entry)
        checkAutoCleanup()
    }

    /**
     * 追加写入到单个文件。超过大小限制时截断保留后半部分。
     */
    @Synchronized
    private fun writeToFile(entry: Entry) {
        val file = logFile ?: return
        try {
            // 检查文件大小，超限时截断
            if (file.exists() && file.length() > maxSizeBytes) {
                // 读取后半部分保留
                val keepRatio = 0.5  // 保留后 50%
                val content = file.readText()
                val keepFrom = (content.length * keepRatio).toInt()
                val keepText = "...[日志已截断, 保留最近部分]...\n" + content.substring(keepFrom)
                file.writeText(keepText)
            }
            // 追加写入
            file.appendText(formatEntry(entry) + "\n")
        } catch (_: Throwable) {}
    }

    private fun checkAutoCleanup() {
        val file = logFile ?: return
        if (maxAgeMs > 0 && file.exists()) {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            // 单文件模式下，时间逻辑不直接删文件，而是在日志内容里标记
            // 真正的时间删除只影响旧格式的多文件模式
        }
    }

    // ── 查询 ──────────────────────────────────────────────────────────────

    fun getEntries(): List<Entry> = buffer.toList()

    fun getFileContent(): String {
        val file = logFile ?: return ""
        return if (file.exists()) file.readText() else ""
    }

    fun getFilePath(): String? = logFile?.absolutePath

    fun getFileSize(): Long {
        val file = logFile ?: return 0
        return if (file.exists()) file.length() else 0
    }

    fun clearBufferOnly() {
        buffer.clear()
    }

    fun clearAll() {
        buffer.clear()
        logFile?.let { if (it.exists()) it.writeText("") }
        i("AppLog", "=== 日志已清空 ===")
    }

    // ── 导出/分享 ────────────────────────────────────────────────────────

    fun exportAll(): String {
        val sb = StringBuilder()
        sb.appendLine("=== NaiGen 日志导出 ===")
        sb.appendLine("时间: ${dateFormat.format(Date())}")
        sb.appendLine("========================")
        sb.appendLine()
        buffer.forEach { sb.appendLine(formatEntry(it)) }
        return sb.toString()
    }

    fun getFile(): File? = logFile

    // ── 格式化 ────────────────────────────────────────────────────────────

    fun formatEntry(entry: Entry): String {
        val time = dateFormat.format(Date(entry.timestamp))
        val levelStr = when (entry.level) {
            Level.DEBUG -> "D"
            Level.INFO -> "I"
            Level.WARN -> "W"
            Level.ERROR -> "E"
            Level.NETWORK -> "N"
        }
        val sb = StringBuilder()
        sb.append("[$time] $levelStr/${entry.tag}: ${entry.message}")
        entry.throwable?.let { t ->
            sb.append("\n")
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            t.printStackTrace(pw)
            sb.append(sw.toString().trim())
        }
        return sb.toString()
    }

    private fun formatJson(json: String): String {
        return try {
            if (json.trim().startsWith("{") || json.trim().startsWith("[")) {
                val trimmed = json.trim()
                val sb = StringBuilder()
                var indent = 0
                for (ch in trimmed) {
                    when (ch) {
                        '{', '[' -> {
                            sb.append(ch).append("\n")
                            indent++
                            sb.append("  ".repeat(indent))
                        }
                        '}', ']' -> {
                            indent = (indent - 1).coerceAtLeast(0)
                            sb.append("\n").append("  ".repeat(indent)).append(ch)
                        }
                        ',' -> {
                            sb.append(ch).append("\n").append("  ".repeat(indent))
                        }
                        else -> sb.append(ch)
                    }
                }
                sb.toString()
            } else {
                json
            }
        } catch (_: Throwable) {
            json
        }
    }

    // ── 崩溃捕获 ──────────────────────────────────────────────────────────

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    private fun installCrashHandler(context: Context) {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sb = StringBuilder()
                sb.append("\n")
                sb.append("╔══════════════════════════════════════════╗\n")
                sb.append("║          应用崩溃报告                    ║\n")
                sb.append("╚══════════════════════════════════════════╝\n")
                sb.append("时间: ${dateFormat.format(Date())}\n")
                sb.append("线程: ${thread.name}\n")
                sb.append("设备: ${android.os.Build.BRAND} ${android.os.Build.MODEL}\n")
                sb.append("系统: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
                sb.append("版本: ${com.naigen.app.BuildConfig.SEMVER} (build ${com.naigen.app.BuildConfig.BUILD_NUMBER})\n")
                sb.append("\n--- Stack Trace ---\n")
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                sb.append(sw.toString())
                sb.append("\n══════════════════════════════════════════\n\n")
                
                logFile?.appendText(sb.toString())
                
                buffer.addLast(Entry(
                    timestamp = System.currentTimeMillis(),
                    level = Level.ERROR,
                    tag = "CRASH",
                    message = "应用崩溃: ${throwable.javaClass.simpleName}: ${throwable.message}",
                    throwable = throwable
                ))
            } catch (_: Throwable) {}
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
