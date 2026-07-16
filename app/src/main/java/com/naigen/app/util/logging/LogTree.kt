package com.naigen.app.util.logging

/**
 * 日志树接口（参考 kelivo / Timber 的 Tree 架构）。
 *
 * 设计目标：
 *   - 接口与实现分离，便于单测 mock
 *   - 多 Tree 并行输出（Console + File + Crash + 自定义）
 *   - 统一的日志级别与格式化
 *
 * 实现方只需要重写 [log]，由 [LogService] 负责分发。
 */
interface LogTree {
    /**
     * 是否处理该级别。默认全部处理，子类可重写做过滤
     * （例如 release 构建里 ConsoleTree 只处理 WARN+）。
     */
    fun isLoggable(level: LogLevel): Boolean = true

    /**
     * 输出一条日志。实现方自行决定落盘 / 打印 / 上报的细节。
     *
     * @param level 日志级别
     * @param tag   业务标签（如 "Api"、"Repo"）
     * @param message 消息正文
     * @param throwable 可选异常（带堆栈）
     * @param timestamp 毫秒时间戳，由 [LogService] 统一传入，保证多 Tree 时间一致
     */
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?, timestamp: Long)
}

/**
 * 日志级别（对标 kelivo 的 Level）。
 *
 * 顺序：DEBUG < INFO < WARN < ERROR
 */
enum class LogLevel(val priority: Int) {
    DEBUG(10),
    INFO(20),
    WARN(30),
    ERROR(40);

    companion object {
        fun fromPriority(p: Int): LogLevel = entries.firstOrNull { it.priority >= p } ?: DEBUG
    }
}

/**
 * 一条结构化日志记录（用于内存缓冲 / UI 展示）。
 */
data class LogRecord(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
)
