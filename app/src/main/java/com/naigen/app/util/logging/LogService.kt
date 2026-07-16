package com.naigen.app.util.logging

import android.content.Context
import com.naigen.app.util.LogFormatter
import com.naigen.app.util.TokenRedactor
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 日志服务 —— 对标 kelivo 的 LogService。
 *
 * 职责：
 *   - 管理 [LogTree] 集合（plant / uproot，对标 Timber 的 forest）
 *   - 统一分发：每次 log 调用，把记录同步派发给所有 isLoggable 的 tree
 *   - 持有 [FileLogTree] 和 [CrashLogTree] 的引用，便于 AppLog 门面委托文件 / 网络相关查询
 *
 * 与 kelivo 的差异：
 *   - kelivo 用 Dart 的 Future 链做异步；这里 tree 内部各自决定同步/异步（ConsoleTree 同步，FileTree 内部用单线程 Executor）
 *   - 分发本身是同步的，保证 Logcat 立即可见；文件落盘由 FileTree 内部异步
 */
object LogService {

    private val trees = CopyOnWriteArrayList<LogTree>()

    @Volatile var fileTree: FileLogTree? = null
        private set
    @Volatile var crashTree: CrashLogTree? = null
        private set

    /**
     * 初始化默认 tree 集合：Console + File + Crash。
     * 应在 Application.onCreate 早期调用。
     */
    fun init(context: Context) {
        if (fileTree != null) return  // 防止重复初始化
        val file = FileLogTree(
            context = context,
            formatter = LogFormatter.DEFAULT,
            redactor = TokenRedactor.DEFAULT
        )
        val console = ConsoleLogTree()
        val crash = CrashLogTree(context, file)

        fileTree = file
        crashTree = crash

        plant(file)
        plant(console)
        crash.install()

        file.pruneOldLogs()
    }

    /** 种植一棵日志树（对标 Timber.plant） */
    fun plant(tree: LogTree) {
        trees.addIfAbsent(tree)
    }

    /** 移除一棵日志树（对标 Timber.uproot） */
    fun uproot(tree: LogTree) {
        trees.remove(tree)
    }

    /** 移除所有日志树 */
    fun clear() {
        trees.clear()
    }

    // ── 分发 ─────────────────────────────────────────────────────────────

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        dispatch(LogLevel.DEBUG, tag, message, throwable)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        dispatch(LogLevel.INFO, tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        dispatch(LogLevel.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        dispatch(LogLevel.ERROR, tag, message, throwable)
    }

    private fun dispatch(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (trees.isEmpty()) return
        val ts = System.currentTimeMillis()
        // CopyOnWriteArrayList 的迭代器是弱一致的，遍历期间不锁
        for (tree in trees) {
            if (tree.isLoggable(level)) {
                try {
                    tree.log(level, tag, message, throwable, ts)
                } catch (_: Throwable) {
                    // 单棵 tree 失败不能影响其他 tree
                }
            }
        }
    }

    /**
     * 写一条网络日志（委托给 FileLogTree，因为只有文件树关心网络日志）。
     */
    fun network(
        method: String, url: String,
        requestHeaders: Map<String, String> = emptyMap(), requestBody: String = "",
        responseCode: Int = 0, responseHeaders: Map<String, String> = emptyMap(), responseBody: String = "",
        durationMs: Long = 0
    ) {
        val file = fileTree ?: return
        val ts = System.currentTimeMillis()
        val fileName = file.nextNetworkFileName(ts)
        file.network(
            FileLogTree.NetworkEntry(
                timestamp = ts, method = method, url = url,
                requestHeaders = requestHeaders, requestBody = requestBody,
                responseCode = responseCode, responseHeaders = responseHeaders,
                responseBody = responseBody, durationMs = durationMs, fileName = fileName
            )
        )
    }
}
