package com.naigen.app.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.naigen.app.MainActivity
import com.naigen.app.NaiApplication
import com.naigen.app.data.db.entities.HistoryEntity
import com.naigen.app.data.model.GenRequest
import com.naigen.app.data.model.GenResult
import com.naigen.app.data.repository.GenProgress
import com.naigen.app.data.repository.NaiRepository
import com.naigen.app.data.styles.SizeOptions
import com.naigen.app.data.styles.StyleRegistry
import com.naigen.app.util.AppLog
import com.naigen.app.util.DateUtils
import com.naigen.app.util.ImageSaver
import kotlinx.coroutines.CancellationException
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
 * 通知构建委托给 [IslandNotifier]，自动适配：
 *   - 小米超级岛（澎湃OS 2/3，含 miui.focus.param 模板5 + 进度组件）
 *   - Android 16+ ProgressStyle（OPPO ColorOS 16 流体云 / 原生 Android 16）
 *   - 老安卓 / 不支持上岛的厂商：标准进度通知降级
 *
 * Android 15+ 适配：
 *   - 必须用 [startForeground] 的带 foregroundServiceType 重载
 *   - foregroundServiceType 必须在 Manifest 声明（已声明 dataSync）
 *   - 启动后必须 6 秒内显示通知（Android 16 强制）
 */
class GenerationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    /**
     * 当前任务总预计秒数（用于进度条最大值）。
     *
     * 与 [NaiRepository.MAX_POLL_TIME_SEC] 共享，避免之前 60s 进度条上限与
     * 180s 轮询超时不一致导致进度条顶满后给用户「卡住」的错觉。
     */
    private val expectedTotalSec = NaiRepository.MAX_POLL_TIME_SEC

    /** 厂商灵动岛 / 流体云 / 标准通知统一适配器 */
    private lateinit var islandNotifier: IslandNotifier

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        islandNotifier = IslandNotifier(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 诊断日志：记录通知权限状态
        val notifGranted = MainActivity.isNotificationPermissionGranted(this)
        AppLog.i("GenService", "onStartCommand: notifGranted=$notifGranted intent=${intent != null}")

        // 立即启动前台通知（Android 16 要求 6 秒内）
        try {
            startForegroundCompat(
                NOTIF_ID,
                islandNotifier.buildProgressNotification("准备生成…", 0, expectedTotalSec)
            )
        } catch (e: Exception) {
            AppLog.e("GenService", "startForeground 失败！notifGranted=$notifGranted", e)
            // 即使 startForeground 失败也继续，避免 Service 崩溃
        }

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
            .coerceIn(1, NaiRepository.MAX_VARIANTS)
        AppLog.i("GenService", "onStartCommand: variants=$variants style=$styleKey size=$sizeKey")

        val app = applicationContext as NaiApplication
        val req = GenRequest(
            prompt = prompt,
            negativePrompt = negative,
            styleKey = styleKey,
            sizeKey = sizeKey,
            steps = intent.getIntExtra(EXTRA_STEPS, 0).let { if (it > 0) it else null },
            scale = intent.getDoubleExtra(EXTRA_SCALE, 0.0).let { if (it > 0) it else null },
            cfg = intent.getDoubleExtra(EXTRA_CFG, -1.0).let { if (it >= 0) it else null },
            sampler = intent.getStringExtra(EXTRA_SAMPLER),
            seed = intent.getLongExtra(EXTRA_SEED, 0).let { if (it > 0) it else null },
            customArtist = intent.getStringExtra(EXTRA_CUSTOM_ARTIST) ?: ""
        )

        currentJob = scope.launch {
            try {
                if (variants <= 1) {
                    updateProgress("创建任务中…", 0, "")
                    val result = app.naiRepository.generate(req, 0, 1) { p ->
                        GenerationBus.publishProgress(p)
                        when (p) {
                            is GenProgress.Creating -> updateProgress("创建任务中…", 1, "")
                            is GenProgress.Polling -> updateProgress(
                                "生成中… ${p.elapsedSec}s · job ${p.jobId.take(8)}",
                                p.elapsedSec.coerceAtMost(expectedTotalSec),
                                p.jobId.take(8)
                            )
                            is GenProgress.Downloading -> updateProgress("下载图片中…", expectedTotalSec, "")
                            else -> {}
                        }
                    }
                    finishWith(req, result)
                } else {
                    updateProgress("并发出 $variants 张…", 0, "")
                    app.naiRepository.generateVariants(req, variants).collectLatest { p ->
                        when (p) {
                            is GenProgress.OneDone -> {
                                GenerationBus.publishProgress(p)
                                val ok = p.result.success
                                updateProgress(
                                    "已出 ${p.variant + 1}/$variants 张" + if (!ok) "（失败）" else "",
                                    (p.variant + 1).coerceAtMost(variants),
                                    ""
                                )
                            }
                            is GenProgress.AllDone -> {
                                val okCount = p.results.count { it.success }
                                // 全部成功才写历史，部分失败也存（便于排查）
                                p.results.forEach { persistResult(req, it) }
                                GenerationBus.publishResults(p.results)
                                islandNotifier.notifyDone(
                                    if (okCount == variants) "生成完成" else "部分完成",
                                    "$okCount/$variants 张成功 · ${StyleRegistry.get(req.styleKey)?.name ?: req.styleKey}"
                                )
                                stopSelf()
                            }
                            else -> {}
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.e("GenService", "异常: ${e.message}", e)
                if (e is CancellationException) {
                    GenerationBus.markFinished()
                    islandNotifier.cancelAll()
                    stopSelf()
                    return@launch
                }
                val existingResults = GenerationBus.results.value
                if (existingResults.isNotEmpty() && existingResults.any { it.success }) {
                    val okCount = existingResults.count { it.success }
                    GenerationBus.publishEvent("部分完成: $okCount/${existingResults.size} 张成功")
                    islandNotifier.notifyDone("部分完成", "$okCount/${existingResults.size} 张成功")
                } else {
                    GenerationBus.publishEvent("生成失败: ${e.message}")
                    islandNotifier.notifyDone("生成失败", e.message ?: "未知错误")
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
    private suspend fun finishWith(req: GenRequest, result: GenResult) {
        AppLog.i("GenService", "finishWith: success=${result.success} time=${result.generationTimeMs}ms")
        persistResult(req, result)
        GenerationBus.publishResults(listOf(result))
        if (result.success) {
            islandNotifier.notifyDone(
                "生成完成",
                "${result.styleName} · ${DateUtils.duration(result.generationTimeMs)}"
            )
        } else {
            islandNotifier.notifyDone("生成失败", result.errorMessage ?: "未知错误")
        }
        stopSelf()
    }

    /**
     * 把单张结果落盘 + 写历史表。
     */
    private suspend fun persistResult(req: GenRequest, result: GenResult) {
        val app = applicationContext as NaiApplication
        if (!result.success) {
            app.historyRepository.insert(
                HistoryEntity(
                    prompt = req.prompt,
                    negativePrompt = req.negativePrompt,
                    styleKey = result.styleKey,
                    styleName = result.styleName,
                    sizeKey = result.sizeKey,
                    sizeCost = SizeOptions.costOf(result.sizeKey),
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
            HistoryEntity(
                prompt = req.prompt,
                negativePrompt = req.negativePrompt,
                styleKey = result.styleKey,
                styleName = result.styleName,
                sizeKey = result.sizeKey,
                sizeCost = SizeOptions.costOf(result.sizeKey),
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

    // ── Notification 控制 ────────────────────────────────────────────────────

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
     * 更新进度通知 —— 委托给 [IslandNotifier]，
     * 自动按厂商选择上岛 / ProgressStyle / 标准进度通知。
     */
    private fun updateProgress(text: String, current: Int, jobBrief: String) {
        islandNotifier.notifyProgress(text, current, expectedTotalSec, jobBrief)
    }

    companion object {
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_NEGATIVE = "negative"
        const val EXTRA_STYLE = "style"
        const val EXTRA_SIZE = "size"
        const val EXTRA_VARIANTS = "variants"
        const val EXTRA_STEPS = "steps"
        const val EXTRA_SCALE = "scale"
        const val EXTRA_CFG = "cfg"
        const val EXTRA_SAMPLER = "sampler"
        const val EXTRA_SEED = "seed"
        const val EXTRA_CUSTOM_ARTIST = "customArtist"

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
                steps?.let { putExtra(EXTRA_STEPS, it) }
                scale?.let { putExtra(EXTRA_SCALE, it) }
                cfg?.let { putExtra(EXTRA_CFG, it) }
                sampler?.let { putExtra(EXTRA_SAMPLER, it) }
                seed?.let { putExtra(EXTRA_SEED, it) }
                if (customArtist.isNotBlank()) putExtra(EXTRA_CUSTOM_ARTIST, customArtist)
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
