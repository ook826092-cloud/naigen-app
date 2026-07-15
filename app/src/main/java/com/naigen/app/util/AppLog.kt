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
 * AppLog v5 —— 应用日志 + 网络日志分离。
 *
 * 应用日志: app_YYYYMMDD.log (按天分文件)
 *   - 业务逻辑层日志
 *   - 警告额外写 warn_YYYYMMDD.log
 *   - 错误额外写 error_YYYYMMDD.log
 *
 * 网络日志: net_YYYYMMDD_HHmmss_NNN.txt (每条请求一个文件)
 *   - 完整的请求头/请求体/响应头/响应体
 */
object AppLog {

    private const val TAG = "NaiGen"
    private const val MAX_BUFFER = 500
    private const val LOG_DIR = "logs"
    private const val NET_DIR = "logs/net"
    private const val APP_PREFIX = "app_"
    private const val ERROR_PREFIX = "error_"
    private const val WARN_PREFIX = "warn_"
    private const val NET_PREFIX = "net_"
    private const val FILE_SUFFIX = ".txt"
    private const val LOG_SUFFIX = ".log"

    /** 自动删除设置（0 = 不限制） */
    var maxAgeMs: Long = 0
    var maxSizeBytes: Long = 0

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    )

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

    private val appBuffer = ConcurrentLinkedDeque<Entry>()
    private val networkBuffer = ConcurrentLinkedDeque<NetworkEntry>()
    private var logDir: File? = null
    private var netDir: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val netFileFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private var netCounter = 0

    /**
     * 脱敏：把 URL / JSON 请求体中的 token 值遮蔽，避免密钥通过日志文件外泄。
     *
     * 覆盖两种形态：
     *   - URL query：  token=STA1N-xxxx        → token=***REDACTED***
     *   - JSON body：  "token":"STA1N-xxxx"     → "token":"***REDACTED***"
     * 仅做展示/落盘前的遮蔽，不影响真实网络请求。
     */
    internal fun redactToken(raw: String): String {
        if (raw.isEmpty()) return raw
        return raw
            .replace(Regex("((?:[?&]|^)token=)[^&#\\s\"]+"), "$1***REDACTED***")
            .replace(Regex("(\"token\"\\s*:\\s*\")[^\"]*(\")"), "$1***REDACTED***$2")
    }

    fun init(context: Context) {
        logDir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
        netDir = File(context.filesDir, NET_DIR).apply { mkdirs() }
        i("AppLog", "日志系统初始化")
        installCrashHandler(context)
    }

    // ── 应用日志 ──────────────────────────────────────────────────────────

    fun d(tag: String, message: String) { Log.d(TAG, "[$tag] $message"); addApp(Level.DEBUG, tag, message) }
    fun i(tag: String, message: String) { Log.i(TAG, "[$tag] $message"); addApp(Level.INFO, tag, message) }
    fun w(tag: String, message: String, t: Throwable? = null) { Log.w(TAG, "[$tag] $message", t); addApp(Level.WARN, tag, message, t) }
    fun e(tag: String, message: String, t: Throwable? = null) { Log.e(TAG, "[$tag] $message", t); addApp(Level.ERROR, tag, message, t) }

    private fun addApp(level: Level, tag: String, message: String, t: Throwable? = null) {
        val entry = Entry(System.currentTimeMillis(), level, tag, message, t)
        appBuffer.addLast(entry)
        while (appBuffer.size > MAX_BUFFER) appBuffer.pollFirst()
        writeAppFile(entry)
    }

    @Synchronized
    private fun writeAppFile(entry: Entry) {
        val dir = logDir ?: return
        try {
            val day = dayFormat.format(Date(entry.timestamp))
            val line = formatApp(entry) + "\n"
            File(dir, "${APP_PREFIX}${day}${LOG_SUFFIX}").appendText(line)
            if (entry.level == Level.WARN) File(dir, "${WARN_PREFIX}${day}${LOG_SUFFIX}").appendText(line)
            if (entry.level == Level.ERROR) File(dir, "${ERROR_PREFIX}${day}${LOG_SUFFIX}").appendText(line)
        } catch (_: Throwable) {}
    }

    // ── 网络日志 ──────────────────────────────────────────────────────────

    fun network(
        method: String, url: String,
        requestHeaders: Map<String, String> = emptyMap(), requestBody: String = "",
        responseCode: Int = 0, responseHeaders: Map<String, String> = emptyMap(), responseBody: String = "",
        durationMs: Long = 0
    ) {
        val ts = System.currentTimeMillis()
        netCounter++
        val fileName = "${NET_PREFIX}${netFileFormat.format(Date(ts))}_${String.format("%03d", netCounter)}${FILE_SUFFIX}"
        val entry = NetworkEntry(ts, method, url, requestHeaders, requestBody, responseCode, responseHeaders, responseBody, durationMs, fileName)
        networkBuffer.addLast(entry)
        while (networkBuffer.size > MAX_BUFFER) networkBuffer.pollFirst()
        writeNetFile(entry)
    }

    @Synchronized
    private fun writeNetFile(entry: NetworkEntry) {
        val dir = netDir ?: return
        // 落盘前脱敏 token，避免密钥随日志文件外泄（分享/导出时）
        val safeUrl = redactToken(entry.url)
        val safeBody = redactToken(entry.requestBody)
        try {
            val sb = StringBuilder()
            sb.append("时间: ${dateFormat.format(Date(entry.timestamp))}\n")
            sb.append("方法: ${entry.method}\n")
            sb.append("URL: $safeUrl\n")
            if (entry.durationMs > 0) sb.append("耗时: ${entry.durationMs}ms\n")
            sb.append("\n===== 请求头 =====\n")
            if (entry.requestHeaders.isNotEmpty()) entry.requestHeaders.forEach { (k, v) -> sb.append("$k: $v\n") } else sb.append("(空)\n")
            sb.append("\n===== 请求体 =====\n")
            sb.append(if (safeBody.isNotBlank()) safeBody else "(空)")
            sb.append("\n\n===== 响应 ${entry.responseCode} =====\n")
            sb.append("-- 响应头 --\n")
            if (entry.responseHeaders.isNotEmpty()) entry.responseHeaders.forEach { (k, v) -> sb.append("$k: $v\n") } else sb.append("(空)\n")
            sb.append("\n-- 响应体 --\n")
            sb.append(if (entry.responseBody.isNotBlank()) entry.responseBody else "(空)")
            sb.append("\n")
            File(dir, entry.fileName).writeText(sb.toString())
        } catch (_: Throwable) {}
    }

    // ── 查询 ──────────────────────────────────────────────────────────────

    fun getAppEntries(): List<Entry> = appBuffer.toList()
    fun getNetworkEntries(): List<NetworkEntry> = networkBuffer.toList()

    fun getNetworkFiles(): List<LogFileInfo> {
        val dir = netDir ?: return emptyList()
        return dir.listFiles()?.filter { it.name.endsWith(FILE_SUFFIX) }
            ?.map { LogFileInfo(it, it.name, it.length(), "network", it.lastModified()) }
            ?.sortedByDescending { it.modified } ?: emptyList()
    }

    fun getAppFiles(): List<LogFileInfo> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles()?.filter { it.name.endsWith(LOG_SUFFIX) }
            ?.map { f ->
                val type = when {
                    f.name.startsWith(ERROR_PREFIX) -> "error"
                    f.name.startsWith(WARN_PREFIX) -> "warn"
                    f.name.startsWith(APP_PREFIX) -> "app"
                    else -> "other"
                }
                LogFileInfo(f, f.name, f.length(), type, f.lastModified())
            }?.sortedByDescending { it.modified } ?: emptyList()
    }

    fun getFileContent(name: String, isNetwork: Boolean = false): String {
        val dir = if (isNetwork) netDir else logDir ?: return ""
        val file = File(dir, name)
        return if (file.exists()) file.readText() else ""
    }

    /**
     * 从网络日志文件内容解析出概览信息（方法/URL/响应码/耗时）。
     */
    data class NetworkSummary(
        val fileName: String,
        val method: String,
        val url: String,
        val responseCode: Int,
        val durationMs: Long,
        val timestamp: Long,
        val size: Long
    )

    fun getNetworkSummaries(): List<NetworkSummary> {
        val dir = netDir ?: return emptyList()
        return dir.listFiles()?.filter { it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { f ->
                val content = f.readText()
                val method = Regex("方法: (\\w+)").find(content)?.groupValues?.get(1) ?: ""
                val url = Regex("URL: (.+)").find(content)?.groupValues?.get(1)?.trim() ?: ""
                val duration = Regex("耗时: (\\d+)ms").find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val code = Regex("响应 (\\d+)").find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val time = f.lastModified()
                NetworkSummary(f.name, method, url, code, duration, time, f.length())
            } ?: emptyList()
    }

    /**
     * 从网络日志文件解析出 4 个部分（请求头/请求体/响应头/响应体）。
     */
    data class NetworkFileDetail(
        val requestHeaders: String,
        val requestBody: String,
        val responseHeaders: String,
        val responseBody: String
    )

    fun getNetworkFileDetail(fileName: String): NetworkFileDetail {
        val content = getFileContent(fileName, isNetwork = true)
        if (content.isBlank()) return NetworkFileDetail("", "", "", "")

        // 按 ===== 分隔符拆分
        val headerSection = extractSection(content, "===== 请求头 =====", "===== 请求体 =====")
        val bodySection = extractSection(content, "===== 请求体 =====", "===== 响应")
        val respHeaderSection = extractSection(content, "-- 响应头 --", "-- 响应体 --")
        val respBodySection = extractAfter(content, "-- 响应体 --")

        return NetworkFileDetail(
            requestHeaders = headerSection,
            requestBody = bodySection,
            responseHeaders = respHeaderSection,
            responseBody = respBodySection
        )
    }

    private fun extractSection(content: String, startMarker: String, endMarker: String): String {
        val startIdx = content.indexOf(startMarker)
        if (startIdx == -1) return "(空)"
        val afterStart = startIdx + startMarker.length
        val endIdx = content.indexOf(endMarker, afterStart)
        if (endIdx == -1) return content.substring(afterStart).trim()
        return content.substring(afterStart, endIdx).trim().ifBlank { "(空)" }
    }

    private fun extractAfter(content: String, marker: String): String {
        val idx = content.indexOf(marker)
        if (idx == -1) return "(空)"
        return content.substring(idx + marker.length).trim().ifBlank { "(空)" }
    }

    fun getFile(name: String, isNetwork: Boolean = false): File? {
        val dir = if (isNetwork) netDir else logDir ?: return null
        val file = File(dir, name)
        return if (file.exists()) file else null
    }

    fun deleteFile(name: String, isNetwork: Boolean = false) {
        val dir = if (isNetwork) netDir else logDir ?: return
        File(dir, name).delete()
    }

    fun clearAppBuffer() { appBuffer.clear() }
    fun clearNetworkBuffer() { networkBuffer.clear() }

    fun clearAll() {
        appBuffer.clear(); networkBuffer.clear()
        logDir?.listFiles()?.forEach { it.delete() }
        netDir?.listFiles()?.forEach { it.delete() }
        i("AppLog", "所有日志已清空")
    }

    fun getTotalSize(): Long {
        val l = logDir?.listFiles()?.sumOf { it.length() } ?: 0
        val n = netDir?.listFiles()?.sumOf { it.length() } ?: 0
        return l + n
    }

    // ── 格式化 ────────────────────────────────────────────────────────────

    fun formatAppEntry(entry: Entry): String = formatApp(entry)

    private fun formatApp(entry: Entry): String {
        val time = dateFormat.format(Date(entry.timestamp))
        val lv = when (entry.level) {
            Level.DEBUG -> "D"
            Level.INFO -> "I"
            Level.WARN -> "W"
            Level.ERROR -> "E"
        }
        val sb = StringBuilder()
        sb.append("[$time] [$lv] ${entry.message}")
        entry.throwable?.let { t ->
            sb.append("\n[$time] [Uncaught] ${t.javaClass.simpleName}: ${t.message}")
            // Stack trace with #1 #2 #3 numbering
            val elements = t.stackTrace
            for ((idx, element) in elements.withIndex()) {
                val num = idx + 1
                sb.append("\n[$time] [Uncaught] #$num      ${element.className}.${element.methodName} (${element.fileName}:${element.lineNumber})")
            }
            // Cause chain
            var cause = t.cause
            var causeIdx = 0
            while (cause != null) {
                causeIdx++
                sb.append("\n[$time] [Uncaught] Caused by: ${cause.javaClass.simpleName}: ${cause.message}")
                for ((idx, element) in cause.stackTrace.withIndex()) {
                    val num = idx + 1
                    sb.append("\n[$time] [Uncaught] #$num      ${element.className}.${element.methodName} (${element.fileName}:${element.lineNumber})")
                }
                cause = cause.cause
                if (causeIdx > 10) break  // 防止无限循环
            }
        }
        return sb.toString()
    }

    // ── 崩溃捕获 ──────────────────────────────────────────────────────────

    private var prevHandler: Thread.UncaughtExceptionHandler? = null

    private fun installCrashHandler(context: Context) {
        prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val day = dayFormat.format(Date())
                val sb = StringBuilder()
                sb.append("\n╔══════════════════════╗\n")
                sb.append("║    应用崩溃报告      ║\n")
                sb.append("╚══════════════════════╝\n")
                sb.append("时间: ${dateFormat.format(Date())}\n")
                sb.append("线程: ${thread.name}\n")
                sb.append("设备: ${android.os.Build.BRAND} ${android.os.Build.MODEL}\n")
                sb.append("系统: Android ${android.os.Build.VERSION.RELEASE}\n")
                sb.append("版本: ${com.naigen.app.BuildConfig.SEMVER}\n\n")
                val sw = StringWriter(); val pw = PrintWriter(sw); throwable.printStackTrace(pw)
                sb.append(sw.toString()); sb.append("\n══════════════════════\n\n")
                logDir?.let { File(it, "${APP_PREFIX}${day}${LOG_SUFFIX}").appendText(sb.toString()) }
                logDir?.let { File(it, "${ERROR_PREFIX}${day}${LOG_SUFFIX}").appendText(sb.toString()) }
                appBuffer.addLast(Entry(System.currentTimeMillis(), Level.ERROR, "CRASH", "崩溃: ${throwable.message}", throwable))
            } catch (_: Throwable) {}
            prevHandler?.uncaughtException(thread, throwable)
        }
    }
}
