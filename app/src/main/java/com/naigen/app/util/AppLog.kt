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
 * 应用日志工具 v4 —— 网络日志 + 应用日志分离。
 *
 * 两套独立的日志系统：
 *
 * 1. 网络日志 (network.log)
 *    - 单文件，记录每一条 HTTP 请求
 *    - 包含请求头/请求体/响应头/响应体/耗时
 *    - 可分享为 TXT
 *
 * 2. 应用日志 (app_YYYYMMDD.log)
 *    - 按天分文件，一天一个
 *    - 只记录业务逻辑层日志（不记网络细节）
 *    - 错误和警告额外写到单独文件 (error_YYYYMMDD.log / warn_YYYYMMDD.log)
 *    - 可分享/删除
 */
object AppLog {

    private const val TAG = "NaiGen"
    private const val MAX_BUFFER = 500
    private const val LOG_DIR = "logs"
    private const val NETWORK_FILE = "network.log"
    private const val APP_FILE_PREFIX = "app_"
    private const val ERROR_FILE_PREFIX = "error_"
    private const val WARN_FILE_PREFIX = "warn_"
    private const val FILE_SUFFIX = ".log"
    private const val MAX_NETWORK_SIZE = 200 * 1024 * 1024L  // 网络日志最大 200MB

    enum class Level { DEBUG, INFO, WARN, ERROR }
    enum class LogType { APP, NETWORK }

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null,
        val type: LogType = LogType.APP
    )

    /** 网络请求日志条目（点击可展开详情） */
    data class NetworkEntry(
        val timestamp: Long,
        val method: String,
        val url: String,
        val requestHeaders: Map<String, String>,
        val requestBody: String,
        val responseCode: Int,
        val responseHeaders: Map<String, String>,
        val responseBody: String,
        val durationMs: Long
    )

    private val appBuffer = ConcurrentLinkedDeque<Entry>()
    private val networkBuffer = ConcurrentLinkedDeque<NetworkEntry>()
    private var logDir: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)

    fun init(context: Context) {
        logDir = File(context.filesDir, LOG_DIR).apply { if (!exists()) mkdirs() }
        i("AppLog", "日志系统初始化")
        installCrashHandler(context)
    }

    // ── 应用日志方法 ──────────────────────────────────────────────────────

    fun d(tag: String, message: String) {
        Log.d(TAG, "[$tag] $message")
        addAppEntry(Level.DEBUG, tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(TAG, "[$tag] $message")
        addAppEntry(Level.INFO, tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(TAG, "[$tag] $message", throwable)
        addAppEntry(Level.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[$tag] $message", throwable)
        addAppEntry(Level.ERROR, tag, message, throwable)
    }

    /**
     * 网络请求日志 —— 记录到 network.log + 内存缓冲。
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
        val entry = NetworkEntry(
            timestamp = System.currentTimeMillis(),
            method = method,
            url = url,
            requestHeaders = requestHeaders,
            requestBody = requestBody,
            responseCode = responseCode,
            responseHeaders = responseHeaders,
            responseBody = responseBody,
            durationMs = durationMs
        )
        networkBuffer.addLast(entry)
        while (networkBuffer.size > MAX_BUFFER) networkBuffer.pollFirst()
        writeNetworkToFile(entry)
    }

    // ── 内部写入 ──────────────────────────────────────────────────────────

    private fun addAppEntry(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        val entry = Entry(System.currentTimeMillis(), level, tag, message, throwable, LogType.APP)
        appBuffer.addLast(entry)
        while (appBuffer.size > MAX_BUFFER) appBuffer.pollFirst()
        writeAppToFile(entry)
    }

    @Synchronized
    private fun writeAppToFile(entry: Entry) {
        val dir = logDir ?: return
        try {
            val day = dayFormat.format(Date(entry.timestamp))
            val formatted = formatAppEntry(entry) + "\n"

            // 写到每日应用日志
            File(dir, "${APP_FILE_PREFIX}${day}${FILE_SUFFIX}").appendText(formatted)

            // 警告额外写到 warn 文件
            if (entry.level == Level.WARN) {
                File(dir, "${WARN_FILE_PREFIX}${day}${FILE_SUFFIX}").appendText(formatted)
            }

            // 错误额外写到 error 文件
            if (entry.level == Level.ERROR) {
                File(dir, "${ERROR_FILE_PREFIX}${day}${FILE_SUFFIX}").appendText(formatted)
            }
        } catch (_: Throwable) {}
    }

    @Synchronized
    private fun writeNetworkToFile(entry: NetworkEntry) {
        val dir = logDir ?: return
        try {
            val file = File(dir, NETWORK_FILE)
            // 超过大小限制时截断
            if (file.exists() && file.length() > MAX_NETWORK_SIZE) {
                val content = file.readText()
                val keepFrom = (content.length * 0.5).toInt()
                file.writeText("...[截断, 保留最近部分]...\n" + content.substring(keepFrom))
            }
            file.appendText(formatNetworkEntry(entry) + "\n")
        } catch (_: Throwable) {}
    }

    // ── 查询 ──────────────────────────────────────────────────────────────

    fun getAppEntries(): List<Entry> = appBuffer.toList()
    fun getNetworkEntries(): List<NetworkEntry> = networkBuffer.toList()

    /** 获取所有日志文件（按日期倒序） */
    data class LogFileInfo(
        val file: File,
        val name: String,
        val size: Long,
        val type: String,  // "app" / "error" / "warn" / "network"
        val date: String,  // YYYYMMDD 或 "network"
        val modified: Long
    )

    fun getLogFiles(): List<LogFileInfo> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.name.endsWith(FILE_SUFFIX) }
            ?.map { f ->
                val name = f.name
                val type = when {
                    name.startsWith(NETWORK_FILE) -> "network"
                    name.startsWith(ERROR_FILE_PREFIX) -> "error"
                    name.startsWith(WARN_FILE_PREFIX) -> "warn"
                    name.startsWith(APP_FILE_PREFIX) -> "app"
                    else -> "other"
                }
                val date = when (type) {
                    "network" -> "network"
                    else -> name.substringAfter("_").substringBefore(".")
                }
                LogFileInfo(f, name, f.length(), type, date, f.lastModified())
            }
            ?.sortedByDescending { it.modified }
            ?: emptyList()
    }

    fun getFileContent(name: String): String {
        val dir = logDir ?: return ""
        val file = File(dir, name)
        return if (file.exists()) file.readText() else ""
    }

    fun deleteFile(name: String) {
        val dir = logDir ?: return
        File(dir, name).delete()
    }

    fun getFile(name: String): File? {
        val dir = logDir ?: return null
        val file = File(dir, name)
        return if (file.exists()) file else null
    }

    fun clearAppBuffer() { appBuffer.clear() }
    fun clearNetworkBuffer() { networkBuffer.clear() }

    fun clearAll() {
        appBuffer.clear()
        networkBuffer.clear()
        logDir?.listFiles()?.forEach { it.delete() }
        i("AppLog", "所有日志已清空")
    }

    fun getTotalSize(): Long {
        val dir = logDir ?: return 0
        return dir.listFiles()?.sumOf { it.length() } ?: 0
    }

    // ── 格式化 ────────────────────────────────────────────────────────────

    private fun formatAppEntry(entry: Entry): String {
        val time = dateFormat.format(Date(entry.timestamp))
        val levelStr = when (entry.level) {
            Level.DEBUG -> "D"
            Level.INFO -> "I"
            Level.WARN -> "W"
            Level.ERROR -> "E"
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

    private fun formatNetworkEntry(entry: NetworkEntry): String {
        val time = dateFormat.format(Date(entry.timestamp))
        val sb = StringBuilder()
        sb.append("[$time] ${entry.method} ${entry.url}")
        if (entry.durationMs > 0) sb.append(" (${entry.durationMs}ms)")
        sb.append("\n")
        if (entry.requestHeaders.isNotEmpty()) {
            sb.append("  请求头:\n")
            entry.requestHeaders.forEach { (k, v) -> sb.append("    $k: $v\n") }
        }
        if (entry.requestBody.isNotBlank()) {
            sb.append("  请求体: ${entry.requestBody.take(500)}\n")
        }
        if (entry.responseCode > 0) {
            sb.append("  响应: ${entry.responseCode}\n")
            if (entry.responseBody.isNotBlank()) {
                sb.append("  响应体: ${entry.responseBody.take(500)}\n")
            }
        }
        return sb.toString()
    }

    fun formatNetworkForDisplay(entry: NetworkEntry): String {
        val sb = StringBuilder()
        sb.append("${entry.method} ${entry.url}")
        if (entry.durationMs > 0) sb.append("  (${entry.durationMs}ms)")
        sb.append("\n\n── 请求头 ──\n")
        if (entry.requestHeaders.isNotEmpty()) {
            entry.requestHeaders.forEach { (k, v) -> sb.append("$k: $v\n") }
        } else {
            sb.append("(无)\n")
        }
        sb.append("\n── 请求体 ──\n")
        sb.append(if (entry.requestBody.isNotBlank()) formatJson(entry.requestBody) else "(无)")
        sb.append("\n\n── 响应 ${entry.responseCode} ──\n")
        if (entry.responseHeaders.isNotEmpty()) {
            sb.append("响应头:\n")
            entry.responseHeaders.forEach { (k, v) -> sb.append("$k: $v\n") }
            sb.append("\n")
        }
        sb.append("响应体:\n")
        sb.append(if (entry.responseBody.isNotBlank()) formatJson(entry.responseBody) else "(无)")
        return sb.toString()
    }

    private fun formatJson(json: String): String {
        return try {
            if (json.trim().startsWith("{") || json.trim().startsWith("[")) {
                val sb = StringBuilder()
                var indent = 0
                for (ch in json.trim()) {
                    when (ch) {
                        '{', '[' -> { sb.append(ch).append("\n"); indent++; sb.append("  ".repeat(indent)) }
                        '}', ']' -> { indent = (indent - 1).coerceAtLeast(0); sb.append("\n").append("  ".repeat(indent)).append(ch) }
                        ',' -> { sb.append(ch).append("\n").append("  ".repeat(indent)) }
                        else -> sb.append(ch)
                    }
                }
                sb.toString()
            } else json
        } catch (_: Throwable) { json }
    }

    // ── 崩溃捕获 ──────────────────────────────────────────────────────────

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    private fun installCrashHandler(context: Context) {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val day = dayFormat.format(Date())
                val sb = StringBuilder()
                sb.append("\n╔══════════════════════════════╗\n")
                sb.append("║      应用崩溃报告            ║\n")
                sb.append("╚══════════════════════════════╝\n")
                sb.append("时间: ${dateFormat.format(Date())}\n")
                sb.append("线程: ${thread.name}\n")
                sb.append("设备: ${android.os.Build.BRAND} ${android.os.Build.MODEL}\n")
                sb.append("系统: Android ${android.os.Build.VERSION.RELEASE}\n")
                sb.append("版本: ${com.naigen.app.BuildConfig.SEMVER}\n\n")
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                sb.append(sw.toString())
                sb.append("\n══════════════════════════════\n\n")

                logDir?.let { dir ->
                    File(dir, "${APP_FILE_PREFIX}${day}${FILE_SUFFIX}").appendText(sb.toString())
                    File(dir, "${ERROR_FILE_PREFIX}${day}${FILE_SUFFIX}").appendText(sb.toString())
                }
                appBuffer.addLast(Entry(System.currentTimeMillis(), Level.ERROR, "CRASH",
                    "崩溃: ${throwable.javaClass.simpleName}: ${throwable.message}", throwable))
            } catch (_: Throwable) {}
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
