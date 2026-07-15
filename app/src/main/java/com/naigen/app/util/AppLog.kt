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
 * 应用日志工具 v2 —— 完整版。
 *
 * 功能：
 *   1. 内存缓冲（最近 500 条，UI 实时查看）
 *   2. 按日期分文件持久化（filesDir/logs/log_YYYYMMDD_HHmmss.txt）
 *   3. 崩溃自动捕获（Thread.UncaughtExceptionHandler）
 *   4. 网络请求日志格式化（请求头/请求体/响应头/响应体分开）
 *   5. 自动删除：
 *      - 时间逻辑：超过用户设置的保留期自动删文件
 *      - 存储逻辑：总大小超过用户设置的阈值自动删最旧文件
 *      - 综合逻辑：哪个先触发删哪个
 *
 * 日志文件格式：
 *   log_20260715_103045.txt
 *   crash_20260715_103045.txt（崩溃专用）
 *
 * 每条日志格式：
 *   [2026-07-15 10:30:45.123] D/Tag: 消息内容
 */
object AppLog {

    private const val TAG = "NaiGen"
    private const val MAX_BUFFER = 500
    private const val LOG_DIR = "logs"
    private const val FILE_PREFIX = "log_"
    private const val CRASH_PREFIX = "crash_"

    enum class Level { DEBUG, INFO, WARN, ERROR, NETWORK }

    /** 网络请求日志的数据结构 */
    data class NetworkLog(
        val method: String,
        val url: String,
        val requestHeaders: Map<String, String> = emptyMap(),
        val requestBody: String = "",
        val responseCode: Int = 0,
        val responseHeaders: Map<String, String> = emptyMap(),
        val responseBody: String = ""
    )

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null,
        val networkLog: NetworkLog? = null
    )

    /** 日志文件元信息（UI 展示用） */
    data class LogFile(
        val file: File,
        val name: String,
        val size: Long,
        val isCrash: Boolean,
        val createdTime: Long
    )

    private val buffer = ConcurrentLinkedDeque<Entry>()
    private var logDir: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /** 自动删除设置 */
    var maxAgeMs: Long = 0  // 0 = 不按时间删
    var maxSizeBytes: Long = 0  // 0 = 不按大小删

    fun init(context: Context) {
        logDir = File(context.filesDir, LOG_DIR).apply { if (!exists()) mkdirs() }

        // 安装全局崩溃捕获
        installCrashHandler(context)
    }

    // ── 日志写入 ──────────────────────────────────────────────────────────

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
     * 网络请求日志（格式化展示）。
     */
    fun network(
        method: String,
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
        requestBody: String = "",
        responseCode: Int = 0,
        responseHeaders: Map<String, String> = emptyMap(),
        responseBody: String = ""
    ) {
        val netLog = NetworkLog(method, url, requestHeaders, requestBody, responseCode, responseHeaders, responseBody)
        val msg = formatNetworkMessage(netLog)
        Log.i(TAG, "[NET] $msg")
        addEntry(Level.NETWORK, "NET", msg, networkLog = netLog)
    }

    private fun addEntry(
        level: Level,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        networkLog: NetworkLog? = null
    ) {
        val entry = Entry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable,
            networkLog = networkLog
        )
        buffer.addLast(entry)
        while (buffer.size > MAX_BUFFER) buffer.pollFirst()
        writeToFile(entry)
        autoCleanup()
    }

    // ── 文件写入 ──────────────────────────────────────────────────────────

    private fun writeToFile(entry: Entry) {
        val dir = logDir ?: return
        try {
            val fileName = FILE_PREFIX + fileDateFormat.format(Date(entry.timestamp)) + ".txt"
            val file = File(dir, fileName)
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            pw.println(formatEntry(entry))
            entry.throwable?.printStackTrace(pw)
            file.appendText(sw.toString())

            // 如果是错误或崩溃，额外写 crash 文件
            if (entry.level == Level.ERROR && entry.throwable != null) {
                val crashFile = File(dir, CRASH_PREFIX + fileDateFormat.format(Date(entry.timestamp)) + ".txt")
                val csw = StringWriter()
                val cpw = PrintWriter(csw)
                cpw.println("=== NaiGen 崩溃报告 ===")
                cpw.println("时间: ${dateFormat.format(Date(entry.timestamp))}")
                cpw.println("Tag: ${entry.tag}")
                cpw.println("Message: ${entry.message}")
                cpw.println()
                cpw.println("=== Stack Trace ===")
                entry.throwable.printStackTrace(cpw)
                cpw.println()
                cpw.println("=== Device Info ===")
                cpw.println("Brand: ${android.os.Build.BRAND}")
                cpw.println("Model: ${android.os.Build.MODEL}")
                cpw.println("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                crashFile.writeText(csw.toString())
            }
        } catch (_: Throwable) {}
    }

    // ── 自动清理 ──────────────────────────────────────────────────────────

    private fun autoCleanup() {
        val dir = logDir ?: return
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return

        // 时间逻辑：删除超过 maxAgeMs 的文件
        if (maxAgeMs > 0) {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            files.forEach { f ->
                if (f.lastModified() < cutoff) {
                    f.delete()
                }
            }
        }

        // 存储逻辑：总大小超过 maxSizeBytes 时删除最旧文件
        if (maxSizeBytes > 0) {
            val currentFiles = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
            var totalSize = currentFiles.sumOf { it.length() }
            for (f in currentFiles) {
                if (totalSize <= maxSizeBytes) break
                totalSize -= f.length()
                f.delete()
            }
        }
    }

    // ── 查询 ──────────────────────────────────────────────────────────────

    fun getEntries(): List<Entry> = buffer.toList()

    fun getLogFiles(): List<LogFile> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.name.endsWith(".txt") }
            ?.map { f ->
                LogFile(
                    file = f,
                    name = f.name,
                    size = f.length(),
                    isCrash = f.name.startsWith(CRASH_PREFIX),
                    createdTime = f.lastModified()
                )
            }
            ?.sortedByDescending { it.createdTime }
            ?: emptyList()
    }

    fun getLogFileContent(name: String): String {
        val dir = logDir ?: return ""
        val file = File(dir, name)
        return if (file.exists()) file.readText() else ""
    }

    fun deleteFile(name: String) {
        val dir = logDir ?: return
        File(dir, name).delete()
    }

    fun clearAll() {
        buffer.clear()
        val dir = logDir ?: return
        dir.listFiles()?.forEach { it.delete() }
    }

    fun clearBufferOnly() {
        buffer.clear()
    }

    // ── 导出 ──────────────────────────────────────────────────────────────

    fun exportAll(): String {
        val sb = StringBuilder()
        sb.appendLine("=== NaiGen 日志导出 ===")
        sb.appendLine("时间: ${dateFormat.format(Date())}")
        sb.appendLine("条数: ${buffer.size}")
        sb.appendLine("========================")
        sb.appendLine()
        buffer.forEach { sb.appendLine(formatEntry(it)) }
        return sb.toString()
    }

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
            val pw = PrintWriter(sb)
            t.printStackTrace(pw)
        }
        return sb.toString()
    }

    fun formatNetworkMessage(netLog: NetworkLog): String {
        val sb = StringBuilder()
        sb.append("${netLog.method} ${netLog.url}")
        if (netLog.requestHeaders.isNotEmpty()) {
            sb.append("\n── 请求头 ──")
            netLog.requestHeaders.forEach { (k, v) -> sb.append("\n$k: $v") }
        }
        if (netLog.requestBody.isNotBlank()) {
            sb.append("\n── 请求体 ──\n${netLog.requestBody}")
        }
        if (netLog.responseCode > 0) {
            sb.append("\n── 响应 ($${netLog.responseCode}) ──")
            if (netLog.responseHeaders.isNotEmpty()) {
                sb.append("\n响应头:")
                netLog.responseHeaders.forEach { (k, v) -> sb.append("\n$k: $v") }
            }
            if (netLog.responseBody.isNotBlank()) {
                sb.append("\n响应体:\n${netLog.responseBody}")
            }
        }
        return sb.toString()
    }

    // ── 崩溃捕获 ──────────────────────────────────────────────────────────

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    private fun installCrashHandler(context: Context) {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = logDir ?: return@setDefaultUncaughtExceptionHandler
                val crashFile = File(dir, CRASH_PREFIX + fileDateFormat.format(Date()) + ".txt")
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                pw.println("=== NaiGen 崩溃报告 ===")
                pw.println("时间: ${dateFormat.format(Date())}")
                pw.println("线程: ${thread.name}")
                pw.println()
                pw.println("=== Stack Trace ===")
                throwable.printStackTrace(pw)
                pw.println()
                pw.println("=== Device Info ===")
                pw.println("Brand: ${android.os.Build.BRAND}")
                pw.println("Model: ${android.os.Build.MODEL}")
                pw.println("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                pw.println("App Version: ${com.naigen.app.BuildConfig.SEMVER} (build ${com.naigen.app.BuildConfig.BUILD_NUMBER})")
                crashFile.writeText(sw.toString())

                // 也写到内存缓冲
                buffer.addLast(Entry(
                    timestamp = System.currentTimeMillis(),
                    level = Level.ERROR,
                    tag = "CRASH",
                    message = "应用崩溃: ${throwable.javaClass.simpleName}: ${throwable.message}",
                    throwable = throwable
                ))
            } catch (_: Throwable) {}

            // 交给系统默认处理器（会弹"应用已停止"对话框）
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    // ── 存储统计 ──────────────────────────────────────────────────────────

    fun getTotalSize(): Long {
        val dir = logDir ?: return 0
        return dir.listFiles()?.sumOf { it.length() } ?: 0
    }

    fun getLogFileCount(): Int {
        val dir = logDir ?: return 0
        return dir.listFiles()?.count { it.name.endsWith(".txt") } ?: 0
    }
}
