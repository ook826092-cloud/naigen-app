package com.naigen.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.naigen.app.MainActivity
import com.naigen.app.NaiApplication
import com.naigen.app.R
import com.naigen.app.data.model.GenRequest
import com.naigen.app.data.repository.GenProgress
import com.naigen.app.util.ImageSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 前台服务：执行 API Job 轮询，保障 App 退到后台后任务不中断。
 *
 * Android 15+ 适配：
 *   - 必须用 [startForeground] 的带 foregroundServiceType 重载
 *   - foregroundServiceType 必须在 Manifest 声明（已声明 dataSync）
 *   - 启动后必须 6 秒内显示通知（Android 16 强制）
 *
 * 实时进度通知：
 *   - 轮询每 2 秒一次，每次轮询同步更新通知 text + progress
 *   - 完成时切换到 result channel 发一条普通通知（带点击跳转 MainActivity）
 *   - 失败时同样切换到 result channel
 *
 * 后台保活策略：
 *   - 即使 App 在前台，也优先用 Service 跑生成任务（统一异步路径）
 *   - 用户切到后台时 Service 仍在前台运行（foregroundServiceType 保护）
 *   - 配合 KeepAlive 引导页的厂商保活设置，可做到锁屏也不被杀
 */
class GenerationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    /** 当前任务总预计秒数（用于进度条最大值） */
    private val expectedTotalSec = 60

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 立即启动前台通知（Android 16 要求 6 秒内）
        startForegroundCompat(NOTIF_ID, buildProgressNotification("准备生成…", 0, expectedTotalSec))

        if (intent == null) {
            // Service 被系统重启，没有任务可跑，立即停掉
            stopSelf()
            return START_NOT_STICKY
        }

        val prompt = intent.getStringExtra(EXTRA_PROMPT) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val negative = intent.getStringExtra(EXTRA_NEGATIVE).orEmpty()
        val styleKey = intent.getStringExtra(EXTRA_STYLE) ?: "2.5d"
        val sizeKey = intent.getStringExtra(EXTRA_SIZE) ?: "竖图"
        val variants = intent.getIntExtra(EXTRA_VARIANTS, 1)
        com.naigen.app.util.AppLog.i("GenService", "onStartCommand: variants=" + variants + " style=" + styleKey + " size=" + sizeKey)

        val app = applicationContext as NaiApplication
        val req = GenRequest(
            prompt = prompt,
            negativePrompt = negative,
            styleKey = styleKey,
            sizeKey = sizeKey,
            steps = intent.getIntExtra("steps", 0).let { if (it > 0) it else null },
            scale = intent.getDoubleExtra("scale", 0.0).let { if (it > 0) it else null },
            cfg = intent.getDoubleExtra("cfg", -1.0).let { if (it >= 0) it else null },
            sampler = intent.getStringExtra("sampler"),
            seed = intent.getLongExtra("seed", 0).let { if (it > 0) it else null },
            customArtist = intent.getStringExtra("customArtist") ?: ""
        )

        currentJob = scope.launch {
            try {
                if (variants <= 1) {
                    updateProgress("创建任务中…", 0)
                    val result = app.naiRepository.generate(req, 0, 1) { p ->
                        GenerationBus.publishProgress(p)
                        when (p) {
                            is GenProgress.Creating -> updateProgress("创建任务中…", 1)
                            is GenProgress.Polling -> updateProgress(
                                "生成中… ${p.elapsedSec}s · job ${p.jobId.take(8)}",
                                p.elapsedSec.coerceAtMost(expectedTotalSec)
                            )
                            is GenProgress.Downloading -> updateProgress("下载图片中…", expectedTotalSec)
                            else -> {}
                        }
                    }
                    finishWith(req, result)
                } else {
                    updateProgress("并发出 $variants 张…", 0)
                    app.naiRepository.generateVariants(req, variants).collectLatest { p ->
                        when (p) {
                            is GenProgress.OneDone -> {
                                GenerationBus.publishProgress(p)
                                val ok = p.result.success
                                updateProgress(
                                    "已出 ${p.variant + 1}/$variants 张" + if (!ok) "（失败）" else "",
                                    (p.variant + 1).coerceAtMost(variants)
                                )
                            }
                            is GenProgress.AllDone -> {
                                val okCount = p.results.count { it.success }
                                // 全部成功才写历史，部分失败也存（便于排查）
                                p.results.forEach { persistResult(req, it) }
                                GenerationBus.publishResults(p.results)
                                notifyDone(
                                    if (okCount == variants) "生成完成" else "部分完成",
                                    "$okCount/$variants 张成功 · ${com.naigen.app.data.styles.StyleRegistry.get(req.styleKey)?.name ?: req.styleKey}"
                                )
                                stopSelf()
                            }
                            else -> {}
                        }
                    }
                }
            } catch (e: Exception) {
                com.naigen.app.util.AppLog.e("GenService", "异常: ${e.message}", e)
                if (e is kotlinx.coroutines.CancellationException) {
                    GenerationBus.markFinished()
                    stopSelf()
                    return@launch
                }
                val existingResults = GenerationBus.results.value
                if (existingResults.isNotEmpty() && existingResults.any { it.success }) {
                    val okCount = existingResults.count { it.success }
                    GenerationBus.publishEvent("部分完成: $okCount/${existingResults.size} 张成功")
                    notifyDone("部分完成", "$okCount/${existingResults.size} 张成功")
                } else {
                    GenerationBus.publishEvent("生成失败: ${e.message}")
                    notifyDone("生成失败", e.message ?: "未知错误")
                }
                GenerationBus.markFinished()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 单张生成完成后的处理。
     */
    private suspend fun finishWith(req: GenRequest, result: com.naigen.app.data.model.GenResult) {
        com.naigen.app.util.AppLog.i("GenService", "finishWith: success=${result.success} time=${result.generationTimeMs}ms")
        persistResult(req, result)
        GenerationBus.publishResults(listOf(result))
        if (result.success) {
            notifyDone(
                "生成完成",
                "${result.styleName} · ${com.naigen.app.util.DateUtils.duration(result.generationTimeMs)}"
            )
        } else {
            notifyDone("生成失败", result.errorMessage ?: "未知错误")
        }
        stopSelf()
    }

    /**
     * 把单张结果落盘 + 写历史表。
     */
    private suspend fun persistResult(req: GenRequest, result: com.naigen.app.data.model.GenResult) {
        val app = applicationContext as NaiApplication
        if (!result.success) {
            app.historyRepository.insert(
                com.naigen.app.data.db.entities.HistoryEntity(
                    prompt = req.prompt,
                    negativePrompt = req.negativePrompt,
                    styleKey = result.styleKey,
                    styleName = result.styleName,
                    sizeKey = result.sizeKey,
                    sizeCost = com.naigen.app.data.styles.SizeOptions.costOf(result.sizeKey),
                    imageUrl = "",
                    imagePath = "",
                    thumbBytes = ByteArray(0),
                    generationTimeMs = result.generationTimeMs,
                    success = false,
                    errorMessage = result.errorMessage
                )
            )
            return
        }
        val img = result.images.firstOrNull() ?: return
        val relative = ImageSaver.savePrivate(this, img.bytes)
        val thumb = ImageSaver.makeThumbnail(img.bytes)
        app.historyRepository.insert(
            com.naigen.app.data.db.entities.HistoryEntity(
                prompt = req.prompt,
                negativePrompt = req.negativePrompt,
                styleKey = result.styleKey,
                styleName = result.styleName,
                sizeKey = result.sizeKey,
                sizeCost = com.naigen.app.data.styles.SizeOptions.costOf(result.sizeKey),
                imageUrl = img.url,
                imagePath = relative,
                thumbBytes = thumb,
                generationTimeMs = result.generationTimeMs,
                success = true,
                errorMessage = null
            )
        )
    }

    override fun onDestroy() {
        currentJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    // ── Notification 构造 ────────────────────────────────────────────────────

    /**
     * Android 15+ 必须用带 foregroundServiceType 的 startForeground 重载。
     * 这里按 SDK 版本分流。
     */
    private fun startForegroundCompat(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ (API 34)
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(id, notification)
        }
    }

    /**
     * 实时进度通知 —— 适配各厂商灵动岛/超级岛/原子岛。
     *
     * 关键点：
     *   1. setLocusId — Android 12+ 用于关联通知到"场景"，各厂商岛功能靠此识别
     *   2. setCategory(Notification.CATEGORY_PROGRESS) — 标记为进度通知
     *   3. setOngoing(true) — 持续通知，不会被滑动清除
     *   4. setProgress — 进度条，各厂商岛会展示
     *   5. setShortCriticalText — Android 15+ 短文本，岛展示用（如果支持）
     *   6. setFlag(Notification.FLAG_ONGOING_EVENT) — 确保持续
     *
     * 小米超级岛：识别 ongoing + category=progress 的通知
     * OPPO 灵动岛：识别 ongoing + 有 LocusId 的通知
     * vivo 原子岛：识别 ongoing + category=progress 的通知
     */
    private fun buildProgressNotification(text: String, current: Int, total: Int): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val indeterminate = current <= 0
        val builder = NotificationCompat.Builder(this, NaiApplication.CHANNEL_GENERATION)
            .setContentTitle("NaiGen")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setOngoing(true)
            .setContentIntent(pi)
            .setProgress(total, current, indeterminate)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        // Android 12+ 加 LocusId（各厂商岛功能靠此识别）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                builder.setLocusId(androidx.core.app.LocusIdCompat("naigen_generation"))
            } catch (_: Throwable) {}
        }

        return builder.build()
    }

    private fun updateProgress(text: String, current: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildProgressNotification(text, current, expectedTotalSec))
    }

    private fun notifyDone(title: String, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 先取消进度通知
        nm.cancel(NOTIF_ID)

        val pi = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, NaiApplication.CHANNEL_RESULT)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(NOTIF_RESULT_ID, notif)
    }

    companion object {
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_NEGATIVE = "negative"
        const val EXTRA_STYLE = "style"
        const val EXTRA_SIZE = "size"
        const val EXTRA_VARIANTS = "variants"

        const val NOTIF_ID = 1001
        const val NOTIF_RESULT_ID = 1002

        fun start(
            ctx: Context,
            prompt: String,
            negative: String,
            styleKey: String,
            sizeKey: String,
            variants: Int,
            steps: Int? = null,
            scale: Double? = null,
            cfg: Double? = null,
            sampler: String? = null,
            seed: Long? = null,
            customArtist: String = ""
        ) {
            val intent = Intent(ctx, GenerationService::class.java).apply {
                putExtra(EXTRA_PROMPT, prompt)
                putExtra(EXTRA_NEGATIVE, negative)
                putExtra(EXTRA_STYLE, styleKey)
                putExtra(EXTRA_SIZE, sizeKey)
                putExtra(EXTRA_VARIANTS, variants)
                steps?.let { putExtra("steps", it) }
                scale?.let { putExtra("scale", it) }
                cfg?.let { putExtra("cfg", it) }
                sampler?.let { putExtra("sampler", it) }
                seed?.let { putExtra("seed", it) }
                if (customArtist.isNotBlank()) putExtra("customArtist", customArtist)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }

        /**
         * 主动停止当前生成（用户取消时调用）。
         */
        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, GenerationService::class.java))
        }
    }
}
