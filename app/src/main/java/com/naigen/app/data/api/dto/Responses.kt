package com.naigen.app.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * POST /api/jobs 的请求体。字段对齐教程 §5.5.11 + NAI 4.5 完整参数。
 *
 * - [tag] 用户提示词
 * - [artist] 画风路由后的画师串（_get_artist_string 的输出）
 * - [size] 中文尺寸字符串，见 SizeOptions
 * - [cost] 该尺寸的点数消耗
 * - [token] STA1N-... 格式的 API 密钥
 * - [seed] 随机种子（可选）
 * - [sm] / [smDynamic] SDE-DPM 求解器开关
 * - [uncondScale] 负面提示词权重
 * - [varietyPlus] NAI 4.5 多样性增强
 */
@Serializable
data class CreateJobRequest(
    val token: String,
    val tag: String,
    val model: String = "nai-diffusion-4-5-full",
    val artist: String,
    val size: String,
    val cost: Int,
    val steps: Int,
    val scale: Double,
    val cfg: Double,
    val sampler: String,
    val negative: String,
    val nocache: String = "1",
    @SerialName("noise_schedule") val noiseSchedule: String = "karras",
    val seed: Long? = null,
    @SerialName("sm") val sm: Boolean? = null,
    @SerialName("sm_dyn") val smDynamic: Boolean? = null,
    @SerialName("uncond_scale") val uncondScale: Double? = null,
    @SerialName("variety_plus") val varietyPlus: Boolean = false
)

/**
 * POST /api/jobs 的响应。API 仅返回 [id] 即任务编号，
 * 失败时可能直接返回 [error] 字段。
 */
@Serializable
data class CreateJobResponse(
    val id: String? = null,
    val error: String? = null
)

/**
 * GET /api/jobs/:id?token=... 的轮询响应。
 *
 * 状态机：
 *   queued / running → 继续轮询
 *   done             → 取 [imageUrl] 下载
 *   failed           → 取 [error] 报错
 *
 * 额外状态（仅由客户端产生，API 不会返回）：
 *   network_error    → 网络异常（IOException / HTTP 5xx），
 *                      [NaiRepository] 应当重试而不是判任务失败
 */
@Serializable
data class JobStatusResponse(
    val status: String = "",
    val imageUrl: String? = null,
    val error: String? = null
) {
    companion object {
        /** API 真正判任务失败 */
        const val STATUS_FAILED = "failed"
        /** 客户端网络异常，可重试 */
        const val STATUS_NETWORK_ERROR = "network_error"
        /** 任务完成 */
        const val STATUS_DONE = "done"
    }
}

/**
 * GET /api/me?token=... 的余额响应。
 *
 * API 可能返回多种字段名: points / point / balance / credit / credits
 * 这里全部声明为可选，解析时取第一个非空的。
 */
@Serializable
data class BalanceResponse(
    val token: String? = null,
    val points: Int? = null,
    val point: Int? = null,
    val balance: Int? = null,
    val credit: Int? = null,
    val credits: Int? = null,
    val subscription: SubscriptionInfo? = null,
    val error: String? = null
) {
    /** 取第一个非空的点数字段 */
    fun resolvePoints(): Int? = points ?: point ?: balance ?: credit ?: credits
        ?: subscription?.perks?.memoryOptimization ?: subscription?.fixedTrainingStepsLeft
}

@Serializable
data class SubscriptionInfo(
    val active: Boolean? = null,
    val tier: String? = null,
    val perks: PerksInfo? = null,
    val fixedTrainingStepsLeft: Int? = null
)

@Serializable
data class PerksInfo(
    val memoryOptimization: Int? = null,
    val priorityGeneration: Boolean? = null,
    val concurrentRequests: Int? = null,
    val priorityTraining: Boolean? = null
)
