package com.naigen.app.util.logging

import android.content.Context
import com.naigen.app.util.LogFormatter
import com.naigen.app.util.TokenRedactor
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 文件日志树 —— 对标 kelivo 的 FileLogService。
 *
 * 职责：
 *   1. 把应用日志按天写入 `app_YYYYMMDD.log`（活动文件），WARN 额外写 `warn_*.log`，ERROR 额外写 `error_*.log`
 *   2. 网络日志每请求一文件 `net_YYYYMMDD_HHmmss_NNN.txt`（保留 LogsScreen 4-Tab 解析依赖）
 *   3. 内存缓冲最近 [MAX_BUFFER] 条（供 LogsScreen「当前日志」实时展示）
 *   4. 串行化写入（单线程 Executor，避免并发 append 冲突）
 *   5. 按保留天数 / 总大小自动清理
 *   6. 单文件超 [MAX_FILE_SIZE_BYTES] 切片为 `.1`
 *
 * 与旧 AppLog 的差异：
 *   - 不再自己捕获崩溃（交给 [CrashLogTree]）
 *   - 不再直接调 Log.x（交给 [ConsoleLogTree]）
 *   - 只负责「文件 + 内存缓冲」一件事，符合单一职责
 */
class FileLogTree(
    context: Context,
    private val formatter: LogFormatter = LogFormatter.DEFAULT,
    private val redactor: TokenRedactor = TokenRedactor.DEFAULT
) : LogTree {

    private val logDir: File = File(context.filesDir, "logs").apply { mkdirs() }
    private val netDir: File = File(context.filesDir, "logs/net").apply { mkdirs() }

    /** 内存缓冲（应用日志 + 网络日志），供 UI 实时展示 */
    private val appBuffer = ConcurrentLinkedDeque<LogRecord>()
    private val networkBuffer = ConcurrentLinkedDeque<NetworkEntry>()
    val networkBufferView: List<NetworkEntry> get() = networkBuffer.toList()
    val appBufferView: List<LogRecord> get() = appBuffer.toList()

    /** 日志落盘开关 —— 关闭后所有写入变为空操作（对标 kelivo 的 enabled） */
    private val enabled = AtomicBoolean(true)
    fun isEnabled(): Boolean = enabled.get()
    fun setEnabled(v: Boolean) { enabled.set(v) }

    /** 自动删除设置（0 = 不限制） —— 用户可在设置页配置 */
    @Volatile var maxAgeMs: Long = 0
    @Volatile var maxSizeBytes: Long = 0

    /** 写入串行化执行器（对标 kelivo 的 _writeQueue Future 链） */
    private val writeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "FileLogTree-Writer").apply { isDaemon = true }
    }

    private val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val netFileFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var netCounter = 0

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?, timestamp: Long) {
        if (!enabled.get()) return
        val record = LogRecord(timestamp, level, tag, message, throwable)
        appBuffer.addLast(record)
        while (appBuffer.size > MAX_BUFFER) appBuffer.pollFirst()
        writeExecutor.execute { writeAppFile(record) }
    }

    // ── 网络日志 ──────────────────────────────────────────────────────────

    fun network(entry: NetworkEntry) {
        if (!enabled.get()) return
        networkBuffer.addLast(entry)
        while (networkBuffer.size > MAX_BUFFER) networkBuffer.pollFirst()
        writeExecutor.execute { writeNetFile(entry) }
    }

    private fun writeAppFile(record: LogRecord) {
        try {
            val day = dayFormat.format(Date(record.timestamp))
            val line = formatter.format(record) + "\n"
            val file = File(logDir, "${APP_PREFIX}${day}$LOG_SUFFIX")
            if (file.exists() && file.length() > MAX_FILE_SIZE_BYTES) {
                File(logDir, "${APP_PREFIX}${day}.1$LOG_SUFFIX").let { file.renameTo(it) }
            }
            file.appendText(line)
            if (record.level == LogLevel.WARN) {
                val warnFile = File(logDir, "${WARN_PREFIX}${day}$LOG_SUFFIX")
                if (warnFile.exists() && warnFile.length() > MAX_FILE_SIZE_BYTES) {
                    File(logDir, "${WARN_PREFIX}${day}.1$LOG_SUFFIX").let { warnFile.renameTo(it) }
                }
                warnFile.appendText(line)
            }
            if (record.level == LogLevel.ERROR) {
                val errFile = File(logDir, "${ERROR_PREFIX}${day}$LOG_SUFFIX")
                if (errFile.exists() && errFile.length() > MAX_FILE_SIZE_BYTES) {
                    File(logDir, "${ERROR_PREFIX}${day}.1$LOG_SUFFIX").let { errFile.renameTo(it) }
                }
                errFile.appendText(line)
            }
        } catch (_: Throwable) {}
    }

    private fun writeNetFile(entry: NetworkEntry) {
        try {
            val safeUrl = redactor.redact(entry.url)
            val safeBody = redactor.redact(entry.requestBody)
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
            File(netDir, entry.fileName).writeText(sb.toString())
        } catch (_: Throwable) {}
    }

    // ── 清理 ─────────────────────────────────────────────────────────────

    fun pruneOldLogs() {
        writeExecutor.execute {
            val now = System.currentTimeMillis()
            val maxAge = if (maxAgeMs > 0) maxAgeMs else RETENTION_DAYS * 24L * 60L * 60L * 1000L
            val cutoff = now - maxAge
            try {
                logDir.listFiles()?.forEach { f ->
                    val dayStr = parseDayFromAppFileName(f.name) ?: return@forEach
                    val fileTime = dayFormat.parse(dayStr)?.time ?: return@forEach
                    if (fileTime < cutoff && f.delete()) { /* 静默删除 */ }
                }
                netDir.listFiles()?.forEach { f ->
                    val dayStr = parseDayFromNetFileName(f.name) ?: return@forEach
                    val fileTime = dayFormat.parse(dayStr)?.time ?: return@forEach
                    if (fileTime < cutoff && f.delete()) { /* 静默删除 */ }
                }
                if (maxSizeBytes > 0) pruneByTotalSize(maxSizeBytes)
            } catch (_: Throwable) {}
        }
    }

    private fun pruneByTotalSize(maxBytes: Long) {
        val files = netDir.listFiles()?.filter { it.name.endsWith(FILE_SUFFIX) }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (f in files) {
            if (total <= maxBytes) break
            val size = f.length()
            if (f.delete()) total -= size
        }
    }

    private fun parseDayFromAppFileName(name: String): String? {
        if (!name.endsWith(LOG_SUFFIX)) return null
        for (p in listOf(APP_PREFIX, ERROR_PREFIX, WARN_PREFIX)) {
            if (name.startsWith(p)) {
                val day = name.removePrefix(p).removeSuffix(LOG_SUFFIX)
                if (day.length == 8 && day.all { it.isDigit() }) return day
            }
        }
        return null
    }

    private fun parseDayFromNetFileName(name: String): String? {
        if (!name.startsWith(NET_PREFIX) || !name.endsWith(FILE_SUFFIX)) return null
        val core = name.removePrefix(NET_PREFIX).removeSuffix(FILE_SUFFIX)
        val day = core.substringBefore('_')
        return if (day.length == 8 && day.all { it.isDigit() }) day else null
    }

    // ── 文件查询（供 LogsScreen 使用） ────────────────────────────────────

    fun nextNetworkFileName(timestamp: Long): String {
        netCounter++
        return "${NET_PREFIX}${netFileFormat.format(Date(timestamp))}_${String.format("%03d", netCounter)}$FILE_SUFFIX"
    }

    fun getAppFiles(): List<LogFileInfo> =
        logDir.listFiles()?.filter { it.name.endsWith(LOG_SUFFIX) }
            ?.map { f ->
                val type = when {
                    f.name.startsWith(ERROR_PREFIX) -> "error"
                    f.name.startsWith(WARN_PREFIX) -> "warn"
                    f.name.startsWith(APP_PREFIX) -> "app"
                    else -> "other"
                }
                LogFileInfo(f, f.name, f.length(), type, f.lastModified())
            }?.sortedByDescending { it.modified } ?: emptyList()

    fun getNetworkFiles(): List<LogFileInfo> =
        netDir.listFiles()?.filter { it.name.endsWith(FILE_SUFFIX) }
            ?.map { LogFileInfo(it, it.name, it.length(), "network", it.lastModified()) }
            ?.sortedByDescending { it.modified } ?: emptyList()

    fun getFileContent(name: String, isNetwork: Boolean): String {
        val dir = if (isNetwork) netDir else logDir
        val file = File(dir, name)
        return if (file.exists()) file.readText() else ""
    }

    fun getFile(name: String, isNetwork: Boolean): File? {
        val dir = if (isNetwork) netDir else logDir
        val file = File(dir, name)
        return if (file.exists()) file else null
    }

    fun deleteFile(name: String, isNetwork: Boolean) {
        val dir = if (isNetwork) netDir else logDir
        File(dir, name).delete()
    }

    fun clearBuffers() {
        appBuffer.clear()
        networkBuffer.clear()
    }

    fun clearAllFiles() {
        logDir.listFiles()?.forEach { it.delete() }
        netDir.listFiles()?.forEach { it.delete() }
    }

    fun getTotalSize(): Long {
        val l = logDir.listFiles()?.sumOf { it.length() } ?: 0
        val n = netDir.listFiles()?.sumOf { it.length() } ?: 0
        return l + n
    }

    /**
     * 把崩溃报告写入当天的 app / error 日志文件（由 [CrashLogTree] 调用）。
     * 不走 [writeExecutor] 是因为崩溃时进程即将退出，必须同步写完。
     */
    fun writeCrashReport(report: String) {
        try {
            val day = dayFormat.format(Date())
            File(logDir, "${APP_PREFIX}${day}$LOG_SUFFIX").appendText(report)
            File(logDir, "${ERROR_PREFIX}${day}$LOG_SUFFIX").appendText(report)
        } catch (_: Throwable) {}
    }

    // ── 伴随类型 ─────────────────────────────────────────────────────────

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

    fun getNetworkSummaries(): List<NetworkSummary> =
        netDir.listFiles()?.filter { it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { f ->
                val content = f.readText()
                val method = Regex("方法: (\\w+)").find(content)?.groupValues?.get(1) ?: ""
                val url = Regex("URL: (.+)").find(content)?.groupValues?.get(1)?.trim() ?: ""
                val duration = Regex("耗时: (\\d+)ms").find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val code = Regex("响应 (\\d+)").find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                NetworkSummary(f.name, method, url, code, duration, f.lastModified(), f.length())
            } ?: emptyList()

    data class NetworkFileDetail(
        val requestHeaders: String,
        val requestBody: String,
        val responseHeaders: String,
        val responseBody: String
    )

    fun getNetworkFileDetail(fileName: String): NetworkFileDetail {
        val content = getFileContent(fileName, isNetwork = true)
        if (content.isBlank()) return NetworkFileDetail("", "", "", "")
        return NetworkFileDetail(
            requestHeaders = extractSection(content, "===== 请求头 =====", "===== 请求体 ====="),
            requestBody = extractSection(content, "===== 请求体 =====", "===== 响应"),
            responseHeaders = extractSection(content, "-- 响应头 --", "-- 响应体 --"),
            responseBody = extractAfter(content, "-- 响应体 --")
        )
    }

    private fun extractSection(content: String, startMarker: String, endMarker: String): String {
        val startIdx = content.indexOf(startMarker)
        if (startIdx == -1) return "(空)"
        val afterStart = startIdx + startMarker.length
        val endIdx = content.indexOf(endMarker, afterStart)
        return if (endIdx == -1) content.substring(afterStart).trim()
        else content.substring(afterStart, endIdx).trim().ifBlank { "(空)" }
    }

    private fun extractAfter(content: String, marker: String): String {
        val idx = content.indexOf(marker)
        if (idx == -1) return "(空)"
        return content.substring(idx + marker.length).trim().ifBlank { "(空)" }
    }

    companion object {
        private const val MAX_BUFFER = 500
        private const val RETENTION_DAYS = 7L
        private const val MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024  // 5MB
        private const val APP_PREFIX = "app_"
        private const val ERROR_PREFIX = "error_"
        private const val WARN_PREFIX = "warn_"
        private const val NET_PREFIX = "net_"
        private const val FILE_SUFFIX = ".txt"
        private const val LOG_SUFFIX = ".log"
    }
}
