package com.naigen.app.util.logging

import android.util.Log

/**
 * 控制台（Logcat）日志树。
 *
 * 行为对标 kelivo 的 ConsoleLogSink：
 *   - DEBUG → Log.d
 *   - INFO  → Log.i
 *   - WARN  → Log.w
 *   - ERROR → Log.e
 *
 * release 构建可设置 [minLevel] = WARN，过滤掉噪音。
 */
class ConsoleLogTree(
    private val globalTag: String = "NaiGen",
    private val minLevel: LogLevel = LogLevel.DEBUG
) : LogTree {

    override fun isLoggable(level: LogLevel): Boolean = level.priority >= minLevel.priority

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?, timestamp: Long) {
        if (!isLoggable(level)) return
        val composed = "[$tag] $message"
        when (level) {
            LogLevel.DEBUG -> Log.d(globalTag, composed, throwable)
            LogLevel.INFO  -> Log.i(globalTag, composed, throwable)
            LogLevel.WARN  -> Log.w(globalTag, composed, throwable)
            LogLevel.ERROR -> Log.e(globalTag, composed, throwable)
        }
    }
}
