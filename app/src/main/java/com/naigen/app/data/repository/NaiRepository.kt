package com.naigen.app.data.repository

import com.naigen.app.data.api.NaiApiClient
import com.naigen.app.data.api.dto.CreateJobRequest
import com.naigen.app.data.model.GenImage
import com.naigen.app.data.model.GenRequest
import com.naigen.app.data.model.GenResult
import com.naigen.app.data.prefs.SettingsStore
import com.naigen.app.data.styles.AutoStyleKeywords
import com.naigen.app.data.styles.CommunityStyles
import com.naigen.app.data.styles.SizeOptions
import com.naigen.app.data.styles.StyleRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.coroutineContext

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
 * Nai2API 仓库。承担：
 *   - 从 SettingsStore 读取 token / baseUrl
 *   - 实现 §5.5.11 的 Job 异步流程（创建 → 轮询 → 下载）
 *   - 实现 §5.5.15 的并发变体生成（--variants N）
 *   - 实现余额查询
 *
 * 不引入 UseCase 中间层，因为业务足够薄。
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
        com.naigen.app.util.AppLog.i("NaiRepo", "━━━ generate() 开始 ━━━")
        com.naigen.app.util.AppLog.d("NaiRepo", "  prompt=${request.prompt.take(80)}")
        com.naigen.app.util.AppLog.d("NaiRepo", "  styleKey=${request.styleKey} customArtist=${request.customArtist.take(30)}")
        com.naigen.app.util.AppLog.d("NaiRepo", "  sizeKey=${request.sizeKey} steps=${request.steps} scale=${request.scale} cfg=${request.cfg}")
        com.naigen.app.util.AppLog.d("NaiRepo", "  sampler=${request.sampler} seed=${request.seed} variants=$variantIndex/$totalVariants")

        val token = settings.token.first()
        val baseUrl = settings.baseUrl.first()

        if (token.isBlank()) {
            com.naigen.app.util.AppLog.e("NaiRepo", "  ✗ Token 为空, 终止")
            return@coroutineScope GenResult(
                success = false,
                styleKey = request.styleKey,
                styleName = StyleRegistry.get(request.styleKey)?.name ?: request.styleKey,
                sizeKey = request.sizeKey,
                generationTimeMs = 0,
                errorMessage = "未配置 API Token，请在设置页填入 STA1N-... 格式的密钥"
            )
        }
        com.naigen.app.util.AppLog.d("NaiRepo", "  baseUrl=$baseUrl token=${token.take(10)}...")

        // ── 1) 风格路由 ──
        val styleKey = request.customArtist.ifBlank { request.styleKey }
        val preset = StyleRegistry.get(styleKey)
        val artistString = StyleRegistry.resolveArtistString(styleKey)
        com.naigen.app.util.AppLog.d("NaiRepo", "  [步骤1] 风格路由: styleKey=$styleKey → preset=${preset?.name ?: "自定义"} artistString=${artistString.take(60)}...")

        // ── 2) 参数合并 ──
        val size = SizeOptions.get(request.sizeKey)
        val defaultParams = preset?.params ?: com.naigen.app.data.model.StyleParams()
        val useSteps = request.steps ?: defaultParams.steps
        val useScale = request.scale ?: defaultParams.scale
        val useCfg = request.cfg ?: defaultParams.cfg
        val useSampler = request.sampler ?: defaultParams.sampler
        val useNoiseSchedule = request.noiseSchedule ?: "karras"
        com.naigen.app.util.AppLog.d("NaiRepo", "  [步骤2] 参数合并: steps=$useSteps scale=$useScale cfg=$useCfg sampler=$useSampler size=${size.key}(${size.cost}点)")

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
        com.naigen.app.util.AppLog.d("NaiRepo", "  [步骤3] 构建 CreateJobRequest 完成")

        try {
            // ── 4) 创建任务 ──
            onProgress(GenProgress.Creating(variantIndex, totalVariants))
            com.naigen.app.util.AppLog.d("NaiRepo", "  [步骤4] 调用 createJob()...")
            val createResp = client.createJob(baseUrl, body)
            val jobId = createResp.id
            if (jobId.isNullOrBlank()) {
                com.naigen.app.util.AppLog.e("NaiRepo", "  ✗ createJob 失败: ${createResp.error}")
                return@coroutineScope GenResult(
                    success = false, styleKey = styleKey, styleName = styleName,
                    sizeKey = request.sizeKey,
                    generationTimeMs = System.currentTimeMillis() - startTime,
                    errorMessage = createResp.error ?: "创建任务失败，未返回 job id"
                )
            }
            com.naigen.app.util.AppLog.i("NaiRepo", "  ✓ createJob 成功: jobId=$jobId")

            // ── 5) 轮询 ──
            com.naigen.app.util.AppLog.d("NaiRepo", "  [步骤5] 开始轮询, 间隔=${POLL_INTERVAL_MS}ms 超时=${MAX_POLL_TIME_MS}ms")
            var elapsed = 0
            while (elapsed * 1000L < MAX_POLL_TIME_MS) {
                coroutineContext.ensureActive()
                delay(POLL_INTERVAL_MS)
                elapsed += (POLL_INTERVAL_MS / 1000).toInt().coerceAtLeast(1)
                onProgress(GenProgress.Polling(variantIndex, totalVariants, jobId, elapsed))

                val status = client.pollJob(baseUrl, jobId, token)
                when (status.status) {
                    "done" -> {
                        com.naigen.app.util.AppLog.i("NaiRepo", "  ✓ 轮询完成: done (${elapsed}s)")
                        val imageUrl = status.imageUrl
                        if (imageUrl.isNullOrBlank()) {
                            com.naigen.app.util.AppLog.e("NaiRepo", "  ✗ done 但 imageUrl 为空")
                            return@coroutineScope GenResult(
                                success = false, styleKey = styleKey, styleName = styleName,
                                sizeKey = request.sizeKey,
                                generationTimeMs = System.currentTimeMillis() - startTime,
                                jobId = jobId, errorMessage = "任务完成但未返回 imageUrl"
                            )
                        }
                        com.naigen.app.util.AppLog.d("NaiRepo", "  [步骤6] 下载图片: $imageUrl")
                        onProgress(GenProgress.Downloading(variantIndex, totalVariants))
                        val bytes = client.downloadImage(baseUrl, imageUrl)
                        if (bytes == null) {
                            com.naigen.app.util.AppLog.e("NaiRepo", "  ✗ 下载失败")
                            return@coroutineScope GenResult(
                                success = false, styleKey = styleKey, styleName = styleName,
                                sizeKey = request.sizeKey,
                                generationTimeMs = System.currentTimeMillis() - startTime,
                                jobId = jobId, errorMessage = "下载生成图片失败"
                            )
                        }
                        val fullUrl = if (imageUrl.startsWith("http")) imageUrl else "${baseUrl.trimEnd('/')}$imageUrl"
                        val genTime = System.currentTimeMillis() - startTime
                        com.naigen.app.util.AppLog.i("NaiRepo", "  ✓ 生成成功! ${bytes.size} bytes, ${genTime}ms, styleName=$styleName")
                        com.naigen.app.util.AppLog.i("NaiRepo", "━━━ generate() 结束 ━━━")
                        return@coroutineScope GenResult(
                            success = true,
                            images = listOf(GenImage(bytes, fullUrl)),
                            styleKey = styleKey, styleName = styleName,
                            sizeKey = request.sizeKey,
                            generationTimeMs = genTime, jobId = jobId
                        )
                    }
                    "failed" -> {
                        com.naigen.app.util.AppLog.e("NaiRepo", "  ✗ 任务失败: ${status.error}")
                        return@coroutineScope GenResult(
                            success = false, styleKey = styleKey, styleName = styleName,
                            sizeKey = request.sizeKey,
                            generationTimeMs = System.currentTimeMillis() - startTime,
                            jobId = jobId, errorMessage = status.error ?: "任务失败"
                        )
                    }
                    else -> {
                        com.naigen.app.util.AppLog.d("NaiRepo", "  轮询中: status=${status.status} elapsed=${elapsed}s")
                    }
                }
            }
            com.naigen.app.util.AppLog.e("NaiRepo", "  ✗ 轮询超时 (${MAX_POLL_TIME_MS}ms)")
            com.naigen.app.util.AppLog.i("NaiRepo", "━━━ generate() 结束 (超时) ━━━")
            GenResult(
                success = false, styleKey = styleKey, styleName = styleName,
                sizeKey = request.sizeKey,
                generationTimeMs = System.currentTimeMillis() - startTime,
                jobId = jobId, errorMessage = "轮询超时（${MAX_POLL_TIME_MS / 1000}秒）"
            )
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            com.naigen.app.util.AppLog.e("NaiRepo", "  ✗ 异常: ${e.javaClass.simpleName}: ${e.message}", e)
            com.naigen.app.util.AppLog.i("NaiRepo", "━━━ generate() 结束 (异常) ━━━")
            GenResult(
                success = false, styleKey = styleKey, styleName = styleName,
                sizeKey = request.sizeKey,
                generationTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = "请求失败: ${e.message ?: e.javaClass.simpleName}"
            )
        }
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
        val n = count.coerceIn(1, 99)
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
        return resp.points to resp.error
    }

    /**
     * 自动风格检测。返回风格 key 或 null（使用默认）。
     */
    suspend fun autoDetectStyle(prompt: String): String? {
        val includeNsfw = settings.nsfwEnabled.first()
        return AutoStyleKeywords.detect(prompt, includeNsfw)
    }

    companion object {
        // 教程默认值：轮询 2 秒间隔，最长 180 秒
        const val POLL_INTERVAL_MS = 2000L
        const val MAX_POLL_TIME_MS = 180_000L
    }
}
