package com.naigen.app.util

import com.naigen.app.util.logging.LogLevel
import com.naigen.app.util.logging.LogRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志格式化器（策略模式，便于单测与自定义）。
 *
 * 对标 kelivo 的 LogFormatter：把 [LogRecord] 格式化成单行字符串用于落盘。
 */
fun interface LogFormatter {
    fun format(record: LogRecord): String

    companion object {
        val DEFAULT = LogFormatter { record ->
            val time = FORMAT.format(Date(record.timestamp))
            val lv = when (record.level) {
                LogLevel.DEBUG -> "D"
                LogLevel.INFO  -> "I"
                LogLevel.WARN  -> "W"
                LogLevel.ERROR -> "E"
            }
            val sb = StringBuilder()
            sb.append("[$time] [$lv] ${record.message}")
            record.throwable?.let { appendThrowable(sb, it, time) }
            sb.toString()
        }

        private val FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        private fun appendThrowable(sb: StringBuilder, t: Throwable, time: String) {
            sb.append("\n[$time] [Uncaught] ${t.javaClass.simpleName}: ${t.message}")
            t.stackTrace.forEachIndexed { idx, element ->
                sb.append("\n[$time] [Uncaught] #${idx + 1}      ${element.className}.${element.methodName} (${element.fileName}:${element.lineNumber})")
            }
            var cause = t.cause
            var causeIdx = 0
            while (cause != null) {
                causeIdx++
                sb.append("\n[$time] [Uncaught] Caused by: ${cause.javaClass.simpleName}: ${cause.message}")
                cause.stackTrace.forEachIndexed { idx, element ->
                    sb.append("\n[$time] [Uncaught] #${idx + 1}      ${element.className}.${element.methodName} (${element.fileName}:${element.lineNumber})")
                }
                cause = cause.cause
                if (causeIdx > 10) break
            }
        }
    }
}
