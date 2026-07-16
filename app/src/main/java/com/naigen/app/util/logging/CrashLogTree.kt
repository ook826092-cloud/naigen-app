package com.naigen.app.util.logging

import android.content.Context
import android.os.Build
import com.naigen.app.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志树 —— 对标 kelivo 的 installGlobalHandlers。
 *
 * 职责：
 *   - 安装全局 [Thread.UncaughtExceptionHandler]
 *   - 捕获未处理异常后，把完整崩溃报告同步写入文件树（进程即将退出，不能走异步队列）
 *   - 同时把崩溃作为一条 ERROR 记录塞进内存缓冲（供 UI 展示）
 *   - 调用前一个 handler，保证默认的「进程终止」行为不被破坏
 *
 * 注意：崩溃捕获不能依赖 [LogService] 的异步分发，必须直接同步写入文件，
 * 否则进程可能在写入完成前就被系统杀死。
 */
class CrashLogTree(
    private val context: Context,
    private val fileTree: FileLogTree
) : LogTree {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private var prevHandler: Thread.UncaughtExceptionHandler? = null

    /** CrashLogTree 只做崩溃捕获，不处理普通日志（返回 false 让 LogService 跳过） */
    override fun isLoggable(level: LogLevel): Boolean = false
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?, timestamp: Long) {
        // 普通日志不处理；崩溃走 UncaughtExceptionHandler
    }

    /**
     * 安装全局异常处理器。应在 Application.onCreate 早期调用。
     */
    fun install() {
        prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildReport(thread, throwable)
                fileTree.writeCrashReport(report)
            } catch (_: Throwable) {}
            prevHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildReport(thread: Thread, throwable: Throwable): String {
        val sb = StringBuilder()
        sb.append("\n╔══════════════════════╗\n")
        sb.append("║    应用崩溃报告      ║\n")
        sb.append("╚══════════════════════╝\n")
        sb.append("时间: ${dateFormat.format(Date())}\n")
        sb.append("线程: ${thread.name}\n")
        sb.append("设备: ${Build.BRAND} ${Build.MODEL}\n")
        sb.append("系统: Android ${Build.VERSION.RELEASE}\n")
        sb.append("版本: ${BuildConfig.SEMVER}\n\n")
        val sw = StringWriter(); val pw = PrintWriter(sw); throwable.printStackTrace(pw)
        sb.append(sw.toString())
        sb.append("\n══════════════════════\n\n")
        return sb.toString()
    }
}
