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
 * 应用日志工具。
 *
 * 同时输出到：
 *   1. Android Logcat（开发调试用）
 *   2. 内存环形缓冲（最近 500 条，UI 查看用）
 *   3. 文件 filesDir/logs/app.log（持久化，最多 1MB 滚动）
 *
 * 用法：
 *   AppLog.d("NaiRepository", "create job: $jobId")
 *   AppLog.e("GenerateViewModel", "生成失败", exception)
 *   AppLog.network("POST", "/api/jobs", requestBody, responseCode, responseBody)
 */
object AppLog {

    private const val TAG = "NaiGen"
    private const val MAX_BUFFER = 500
    private const val MAX_FILE_SIZE = 1 * 1024 * 1024  // 1MB

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

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").apply { if (!exists()) mkdirs() }
        logFile = File(dir, "app.log")
    }

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
     * 网络请求日志（专用，单独标记）。
     */
    fun network(method: String, url: String, request: String = "", responseCode: Int = 0, response: String = "") {
        val msg = buildString {
            append("$method $url")
            if (request.isNotBlank()) append("\n→ $request")
            if (responseCode > 0) append("\n← $responseCode")
            if (response.isNotBlank()) append("\n← $response")
        }
        Log.i(TAG, "[NET] $msg")
        addEntry(Level.NETWORK, "NET", msg)
    }

    private fun addEntry(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        val entry = Entry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )
        buffer.addLast(entry)
        while (buffer.size > MAX_BUFFER) buffer.pollFirst()
        writeToFile(entry)
    }

    private fun writeToFile(entry: Entry) {
        val file = logFile ?: return
        try {
            // 滚动：超过 1MB 时重命名 .old 并新建
            if (file.exists() && file.length() > MAX_FILE_SIZE) {
                File(file.parentFile, "app.log.old").let { old ->
                    if (old.exists()) old.delete()
                    file.renameTo(old)
                }
            }
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            pw.println(formatEntry(entry))
            entry.throwable?.printStackTrace(pw)
            file.appendText(sw.toString())
        } catch (_: Throwable) {
            // 日志失败不能影响主流程
        }
    }

    fun getEntries(): List<Entry> = buffer.toList()

    fun clear() {
        buffer.clear()
        logFile?.let { if (it.exists()) it.writeText("") }
        File(logFile?.parentFile, "app.log.old").let { if (it.exists()) it.delete() }
    }

    fun formatEntry(entry: Entry): String {
        val time = dateFormat.format(Date(entry.timestamp))
        val levelStr = when (entry.level) {
            Level.DEBUG -> "D"
            Level.INFO -> "I"
            Level.WARN -> "W"
            Level.ERROR -> "E"
            Level.NETWORK -> "N"
        }
        val sw = StringWriter()
        sw.append("[$time] $levelStr/${entry.tag}: ${entry.message}")
        entry.throwable?.let { t ->
            sw.append("\n")
            val pw = PrintWriter(sw)
            t.printStackTrace(pw)
        }
        return sw.toString()
    }

    /**
     * 导出全部日志为文本（用于分享）。
     */
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
}
