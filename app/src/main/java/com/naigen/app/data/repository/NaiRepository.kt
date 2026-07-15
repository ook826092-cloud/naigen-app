package com.naigen.app.data.repository

import com.naigen.app.data.api.NaiApiClient
import com.naigen.app.data.api.dto.CreateJobRequest
import com.naigen.app.data.api.dto.JobStatusResponse
import com.naigen.app.data.model.GenImage
import com.naigen.app.data.model.GenRequest
import com.naigen.app.data.model.GenResult
import com.naigen.app.data.model.StyleParams
import com.naigen.app.data.prefs.SettingsStore
import com.naigen.app.data.styles.SizeOptions
import com.naigen.app.data.styles.StyleRegistry
import com.naigen.app.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/**
 * 生成进度事件。ViewModel 据此驱动 UI。
 */
sealed class GenProgress {
    data object Idle : GenProgress()
    data class Creating(val variant: Int, val total: Int) : GenProgress()
    data class Polling(val variant: Int, val total: Int, val jobId: String, val elapsedSec: Int) : GenProgress()
    data class Downloading(val variant: Int, val total: Int) : GenProgress()
    data class OneDone(val variant: Int, val result: GenResult) : GenProgress()
    data class AllDone(val results: List<GenResult>) : GenProgress()
}

/**
 * API 仓库。承担：
 *   - 从 SettingsStore 读取 token / baseUrl
 *   - 实现的 Job 异步流程（创建 → 轮询 → 下载）
 *   - 实现的并发变体生成（--variants N）
 *   - 实现余额查询
 *
 * 关键改进（vs 旧版）：
 *   - 轮询改用**指数退避**：前 3 次快速（1s），后续指数退避到 5s 上限
 *     —— 既快感知任务完成，又减少请求量
 *   - 网络异常重试：[JobStatusResponse.STATUS_NETWORK_ERROR] 时重试 N 次，
 *     只有 API 显式 `failed` 才真正判任务失败
 *   - 进度时间上限与 [GenerationService] 共享 [MAX_POLL_TIME_SEC]，避免 60s 进度条上限
 *     与 180s 超时不一致的视觉问题
 *
 * 关键 bug 防护：风格路由必须走 [StyleRegistry.resolveArtistString]，
 * 不能直接用 defaultStyle 兜底——见教程 §5.5.17。
 */
class NaiRepository(
    private val client: NaiApiClient,
    private val settings: SettingsStore
) {

    /**
     * 单次生成（含完整 Job 流程）。返回一张图的结果。
     *
     * [onProgress] 用于报告轮询进度，调用方（ViewModel）据此更新 UI。
     */
    suspend fun generate(
        request: GenRequest,
        variantIndex: Int = 0,
        totalVariants: Int = 1,
        onProgress: (GenProgress) -> Unit = {}
    ): GenResult = coroutineScope {
        val startTime = System.currentTimeMillis()
        AppLog.i("NaiRepo", "generate() start: style=${request.styleKey} size=${request.sizeKey} variants=$variantIndex/$totalVariants")

        val token = settings.token.first()
        val baseUrl = settings.baseUrl.first()

        if (token.isBlank()) {
            AppLog.e("NaiRepo", "FAILED: token is empty")
            return@coroutineScope GenResult(
                success = false,
                styleKey = request.styleKey,
                styleName = StyleRegistry.get(request.styleKey)?.name ?: request.styleKey,
                sizeKey = request.sizeKey,
                generationTimeMs = 0,
                errorMessage = "未配置 API Token，请在设置页填入 STA1N-... 格式的密钥"
            )
        }

        // ── 1) 风格路由 ──
        val styleKey = request.customArtist.ifBlank { request.styleKey }
        val preset = StyleRegistry.get(styleKey)
        val artistString = StyleRegistry.resolveArtistString(styleKey)
        AppLog.i("NaiRepo", "style_route: $styleKey -> ${preset?.name ?: "custom_artist"}")

        // ── 2) 参数合并 ──
        val size = SizeOptions.get(request.sizeKey)
        val defaultParams = preset?.params ?: StyleParams()
        val useSteps = request.steps ?: defaultParams.steps
        val useScale = request.scale ?: defaultParams.scale
        val useCfg = request.cfg ?: defaultParams.cfg
        val useSampler = request.sampler ?: defaultParams.sampler
        val useNoiseSchedule = request.noiseSchedule ?: "karras"

        val useNegative = request.negativePrompt.ifBlank {
            preset?.negativePrompt?.ifBlank { "" } ?: ""
        }
        val usePrompt = preset?.let { p ->
            if (p.positivePrefix.isNotBlank()) "${p.positivePrefix}, ${request.prompt}" else request.prompt
        } ?: request.prompt
        val styleName = preset?.name ?: "自定义画师串"

        // ── 3) 构建 API 请求 ──
        val body = CreateJobRequest(
            token = token, tag = usePrompt, artist = artistString,
            size = size.key, cost = size.cost,
            steps = useSteps, scale = useScale, cfg = useCfg,
            sampler = useSampler, negative = useNegative,
            noiseSchedule = useNoiseSchedule,
            seed = request.seed, sm = request.sm, smDynamic = request.smDynamic,
            uncondScale = request.uncondScale, varietyPlus = request.varietyPlus
        )
        try {
            onProgress(GenProgress.Creating(variantIndex, totalVariants))
            val createResp = client.createJob(baseUrl, body)
            val jobId = createResp.id
            if (jobId.isNullOrBlank()) {
                AppLog.e("NaiRepo", "create_job_failed: ${createResp.error}")
                return@coroutineScope GenResult(
                    success = false, styleKey = styleKey, styleName = styleName,
                    sizeKey = request.sizeKey,
                    generationTimeMs = System.currentTimeMillis() - startTime,
                    errorMessage = createResp.error ?: "创建任务失败，未返回 job id"
                )
            }
            AppLog.i("NaiRepo", "job_created: jobId=$jobId")

            // ── 4) 轮询（指数退避 + 网络异常重试） ──
            var elapsed = 0
            var consecutiveNetworkErrors = 0
            while (elapsed < MAX_POLL_TIME_SEC) {
                coroutineContext.ensureActive()

                // 指数退避：
                //   - 前 3 次：1s（快速感知任务开始）
                //   - 第 4 次起：min(2^(n-1), MAX_POLL_INTERVAL_SEC) 秒（减少请求量）
                val intervalSec = if (elapsed < 3) 1
                    else min(pollIntervalSec(elapsed), MAX_POLL_INTERVAL_SEC).toInt()
                delay(intervalSec * 1000L)
                elapsed += intervalSec
                onProgress(GenProgress.Polling(variantIndex, totalVariants, jobId, elapsed))

                val status = client.pollJob(baseUrl, jobId, token)
                when (status.status) {
                    JobStatusResponse.STATUS_DONE -> {
                        val imageUrl = status.imageUrl
                        if (imageUrl.isNullOrBlank()) {
                            AppLog.e("NaiRepo", "done_but_no_imageUrl")
                            return@coroutineScope GenResult(
                                success = false, styleKey = styleKey, styleName = styleName,
                                sizeKey = request.sizeKey,
                                generationTimeMs = System.currentTimeMillis() - startTime,
                                jobId = jobId, errorMessage = "任务完成但未返回 imageUrl"
                            )
                        }
                        onProgress(GenProgress.Downloading(variantIndex, totalVariants))
                        val bytes = client.downloadImage(baseUrl, imageUrl)
                        if (bytes == null) {
                            AppLog.e("NaiRepo", "download_failed")
                            return@coroutineScope GenResult(
                                success = false, styleKey = styleKey, styleName = styleName,
                                sizeKey = request.sizeKey,
                                generationTimeMs = System.currentTimeMillis() - startTime,
                                jobId = jobId, errorMessage = "下载生成图片失败"
                            )
                        }
                        val fullUrl = if (imageUrl.startsWith("http")) imageUrl else "${baseUrl.trimEnd('/')}$imageUrl"
                        val genTime = System.currentTimeMillis() - startTime
                        AppLog.i("NaiRepo", "success: style=$styleName size=${bytes.size}B time=${genTime}ms")
                        return@coroutineScope GenResult(
                            success = true,
                            images = listOf(GenImage(bytes, fullUrl)),
                            styleKey = styleKey, styleName = styleName,
                            sizeKey = request.sizeKey,
                            generationTimeMs = genTime, jobId = jobId
                        )
                    }
                    JobStatusResponse.STATUS_FAILED -> {
                        AppLog.e("NaiRepo", "job_failed: ${status.error}")
                        return@coroutineScope GenResult(
                            success = false, styleKey = styleKey, styleName = styleName,
                            sizeKey = request.sizeKey,
                            generationTimeMs = System.currentTimeMillis() - startTime,
                            jobId = jobId, errorMessage = status.error ?: "任务失败"
                        )
                    }
                    JobStatusResponse.STATUS_NETWORK_ERROR -> {
                        // 网络异常：重试 MAX_NETWORK_RETRIES 次，超过则判失败
                        consecutiveNetworkErrors++
                        AppLog.w("NaiRepo", "network_error #${consecutiveNetworkErrors}/${MAX_NETWORK_RETRIES}: ${status.error}")
                        if (consecutiveNetworkErrors >= MAX_NETWORK_RETRIES) {
                            AppLog.e("NaiRepo", "network_error_exhausted")
                            return@coroutineScope GenResult(
                                success = false, styleKey = styleKey, styleName = styleName,
                                sizeKey = request.sizeKey,
                                generationTimeMs = System.currentTimeMillis() - startTime,
                                jobId = jobId,
                                errorMessage = "网络异常重试 ${MAX_NETWORK_RETRIES} 次仍失败：${status.error}"
                            )
                        }
                        // 否则继续循环（下次轮询前会再 delay）
                    }
                    // queued / running → 继续轮询，重置网络异常计数
                    else -> {
                        consecutiveNetworkErrors = 0
                    }
                }
            }
            AppLog.e("NaiRepo", "poll_timeout (${MAX_POLL_TIME_SEC}s)")
            GenResult(
                success = false, styleKey = styleKey, styleName = styleName,
                sizeKey = request.sizeKey,
                generationTimeMs = System.currentTimeMillis() - startTime,
                jobId = jobId, errorMessage = "轮询超时（${MAX_POLL_TIME_SEC}秒）"
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            AppLog.e("NaiRepo", "exception: ${e.message}", e)
            GenResult(
                success = false, styleKey = styleKey, styleName = styleName,
                sizeKey = request.sizeKey,
                generationTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = "请求失败: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    /**
     * 指数退避计算：返回应该 delay 的秒数（未应用 [MAX_POLL_INTERVAL_SEC] 上限）。
     *
     * 公式：2^(n-1)，n 是已轮询次数。
     *   n=4 → 8s, n=5 → 16s, n=6 → 32s
     * 实际应用 [MAX_POLL_INTERVAL_SEC]=5 上限后会变成 5s。
     */
    private fun pollIntervalSec(elapsedSec: Int): Long {
        // elapsedSec 即视为已轮询次数近似值，n ≈ elapsedSec/2 + 1
        val n = (elapsedSec / 2).coerceAtLeast(1)
        // 2^(n-1)，用 Long 防溢出
        var result = 1L
        repeat(n - 1) { result *= 2 }
        return result
    }

    /**
     * 并发变体生成。对齐教程 §5.7.4 的 --variants N。
     *
     * 同一 prompt 并发提交 N 个独立 Job，返回 N 张结果。
     * 耗时与单张接近（取决于服务器）。
     *
     * 进度合并策略：先发 Creating，所有完成后发 AllDone。
     * 单个 variant 内部的 Polling 进度被吞掉（多个并发时全报会刷屏）。
     */
    fun generateVariants(
        request: GenRequest,
        count: Int
    ): Flow<GenProgress> = flow {
        val n = count.coerceIn(1, MAX_VARIANTS)
        emit(GenProgress.Creating(0, n))

        val results = coroutineScope {
            (0 until n).map { idx ->
                async {
                    generate(request, idx, n) { /* 内部进度合并到 flow 里太频繁，这里静默 */ }
                }
            }.awaitAll()
        }
        emit(GenProgress.AllDone(results))
    }.flowOn(Dispatchers.IO)

    /**
     * 余额查询。返回 [points] 或 [error]。
     */
    suspend fun checkBalance(): Pair<Int?, String?> {
        val token = settings.token.first()
        if (token.isBlank()) return null to "未配置 API Token"
        val baseUrl = settings.baseUrl.first()
        val resp = client.fetchBalance(baseUrl, token)
        return resp.resolvePoints() to resp.error
    }

    /**
     * 自动风格检测。返回风格 key 或 null（使用默认）。
     */
    suspend fun autoDetectStyle(prompt: String): String? {
        val includeNsfw = settings.nsfwEnabled.first()
        return com.naigen.app.data.styles.AutoStyleKeywords.detect(prompt, includeNsfw)
    }

    companion object {
        /** 轮询最大总时长（秒），与 [GenerationService.expectedTotalSec] 共享 */
        const val MAX_POLL_TIME_SEC = 180
        const val MAX_POLL_TIME_MS = MAX_POLL_TIME_SEC * 1000L

        /** 单次轮询间隔上限：5 秒 */
        const val MAX_POLL_INTERVAL_SEC = 5L

        /** 连续网络异常重试上限：超过则判任务失败 */
        const val MAX_NETWORK_RETRIES = 5

        // 并发生成上限：文案声明「1-6 张」，UI / ViewModel / Service 统一引用此常量
        const val MAX_VARIANTS = 6

        /** 兼容旧引用（已废弃，请改用 [MAX_POLL_TIME_SEC]） */
        @Deprecated("Use MAX_POLL_TIME_SEC instead", ReplaceWith("MAX_POLL_TIME_SEC"))
        const val POLL_INTERVAL_MS = 2000L
    }
}
