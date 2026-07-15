package com.naigen.app.util.keepalive

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Shizuku 辅助工具。
 *
 * Shizuku 允许 App 在不 root 的情况下使用 ADB 级别权限。
 * 用户需要：
 *   1. 安装 Shizuku App（https://shizuku.rikka.app/）
 *   2. 通过 ADB 或 Root 启动 Shizuku 服务
 *   3. 在 Shizuku App 里授权本 App
 *
 * 启动后本 App 可以：
 *   - 通过 `am start` 命令启动厂商的隐藏设置 Activity（绕过 exported=false 限制）
 *   - 直接调用系统隐藏 API（如电池优化白名单）
 *
 * 如果 Shizuku 没运行，所有方法返回 false，调用方 fallback 到普通 startActivity。
 */
object ShizukuHelper {

    /** Shizuku 服务是否已启动（用户在 Shizuku App 里点了启动） */
    fun isRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    /** Shizuku 是否已授权本 App（用户在 Shizuku App 里同意了权限请求） */
    fun isGranted(): Boolean {
        if (!isRunning()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    /** Shizuku App 是否已安装 */
    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 请求 Shizuku 权限。
     * 会弹出 Shizuku 的权限对话框，用户同意后 [isGranted] 返回 true。
     */
    fun requestPermission(requestCode: Int = 0) {
        if (!isRunning()) return
        try {
            if (!isGranted()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (_: Throwable) {}
    }

    /**
     * 通过 Shizuku 启动一个 Activity（即使 exported=false 也能启动）。
     *
     * 实现方式：用 Shizuku 的 newProcess 执行 `am start` 命令。
     *
     * @param intent 要启动的 Intent（必须有 component）
     * @return true 表示命令执行成功
     */
    fun startActivity(context: Context, intent: Intent): Boolean {
        if (!isGranted()) return false
        val component = intent.component ?: return false

        return try {
            // 构造 am start 命令
            // am start -n pkg/.ClassName
            val cmd = mutableListOf("am", "start", "-n", "${component.packageName}/${component.className}")

            // 传 Extra
            intent.extras?.let { bundle ->
                bundle.keySet().forEach { key ->
                    val value = bundle.get(key)
                    when (value) {
                        is String -> {
                            cmd.add("--es")
                            cmd.add(key)
                            cmd.add(value)
                        }
                        is Int -> {
                            cmd.add("--ei")
                            cmd.add(key)
                            cmd.add(value.toString())
                        }
                        is Boolean -> {
                            cmd.add("--ez")
                            cmd.add(key)
                            cmd.add(value.toString())
                        }
                    }
                }
            }

            // 通过 Shizuku 执行
            val process = Shizuku.newProcess(cmd.toTypedArray(), null, null, null)
            val exitCode = process.waitFor()
            // exitCode 0 = 成功
            exitCode == 0
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * 通过 Shizuku 直接把 App 加入电池优化白名单（无需用户手动操作）。
     *
     * 这等价于在系统设置里点「不优化电池使用」。
     * 命令：dumpsys deviceidle whitelist +com.naigen.app
     */
    fun addToBatteryWhitelist(context: Context): Boolean {
        if (!isGranted()) return false
        return try {
            val process = Shizuku.newProcess(
                arrayOf("dumpsys", "deviceidle", "whitelist", "+com.naigen.app"),
                null, null, null
            )
            process.waitFor() == 0
        } catch (e: Throwable) {
            false
        }
    }
}
