package com.naigen.app.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

/**
 * 风格来源：内置预设 / 社区贡献
 */
enum class StyleSource { BUILTIN, COMMUNITY }

/**
 * NSFW 等级。
 * - SAFE：默认显示
 * - QUESTIONABLE / EXPLICIT：仅当用户在设置中打开 NSFW 时才显示
 */
enum class NsfwLevel { SAFE, QUESTIONABLE, EXPLICIT }

/**
 * 一个完整的风格预设（统一表示内置 + 社区）。
 *
 * - [key] UI 引用风格的唯一标识：
 *   - 内置：直接 key，例如 "2.5d"、"galgame"
 *   - 社区："community:<id>"，例如 "community:30"
 * - [artistString] 路由后的画师串，发到 API 的 artist 字段
 * - [positivePrefix] 社区风格的正向提示词前缀（内置为空）
 * - [negativePrompt] 社区风格的默认负面提示词（内置为空）
 * - [params] 风格默认采样参数（用户显式指定时优先）
 */
@Immutable
data class StylePreset(
    val id: Int?,                // 内置为 null，社区为正整数
    val key: String,             // "2.5d" 或 "community:30"
    val name: String,            // 显示名
    val source: StyleSource,
    val provider: String = "",
    val nsfw: NsfwLevel = NsfwLevel.SAFE,
    val tags: Map<String, List<String>> = emptyMap(),
    val artistString: String,
    val positivePrefix: String = "",
    val negativePrompt: String = "",
    val params: StyleParams = StyleParams()
) {
    val isCommunity: Boolean get() = source == StyleSource.COMMUNITY
}

/**
 * 风格默认采样参数。
 */
@Immutable
data class StyleParams(
    val steps: Int = 28,
    val sampler: String = "k_dpmpp_2m_sde",
    val cfg: Double = 0.0,
    val scale: Double = 6.0
)

/**
 * 尺寸选项。对应教程中的 SIZE_OPTIONS。
 */
@Immutable
data class SizeOption(
    val key: String,    // "竖图" / "2K横图" 等
    val label: String,  // "竖图(1点)"
    val cost: Int,      // 点数消耗
    val width: Int,     // 实际像素宽（仅用于宽高比推断，API 用 key 字符串）
    val height: Int
)

/**
 * 一次生成请求（对齐教程中的 ImageGenerationRequest + extra_params）。
 *
 * 字段对齐 NovelAI Diffusion 4.5 支持的全部参数：
 *   - steps (1-50, 推荐 28)
 *   - scale (1-20, 推荐 6)
 *   - cfg (0-1, Rescale CFG, 推荐 0)
 *   - sampler (6 种采样器)
 *   - seed (可选, null = 随机)
 *   - sm / smDynamic (SDE-DPM 求解器开关, 仅 k_dpmpp_sde 系列有效)
 *   - uncondScale (负面提示词权重, 1.0-5.0, 推荐 1.0)
 *   - noiseSchedule (karras / native, 默认 karras)
 *   - varietyPlus (NAI 4.5 新增, 增加多样性, 默认关)
 */
@Immutable
data class GenRequest(
    val prompt: String,
    val negativePrompt: String = "",
    val styleKey: String,
    val sizeKey: String,
    val steps: Int? = null,
    val scale: Double? = null,
    val cfg: Double? = null,
    val sampler: String? = null,
    val seed: Long? = null,
    val sm: Boolean? = null,
    val smDynamic: Boolean? = null,
    val uncondScale: Double? = null,
    val noiseSchedule: String? = null,
    val varietyPlus: Boolean = false,
    val customArtist: String = ""
)

/**
 * 不可变的字节包装。
 *
 * [ByteArray] 本身可变，直接放进 data class 会让 Compose 无法标 [Immutable]。
 * 这里用一层 value class 包装，约定构造后不再修改 [value]，
 * 从而让 [GenImage] / [GenResult] 可以安全标注 [Immutable]，
 * 让 Compose 跳过不必要的重组。
 *
 * equals / hashCode 按字节内容比较（对齐旧 [GenImage] 的行为）。
 */
@Immutable
@JvmInline
value class ImmutableBytes(val value: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImmutableBytes) return false
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()
}

/**
 * 单张图片的生成结果。
 *
 * [bytes] 用 [ImmutableBytes] 包装，标注 [Immutable]：
 * 作为已生成的图片数据，构造后不会被修改，Compose 可以安全跳过重组。
 */
@Immutable
data class GenImage(
    val bytes: ImmutableBytes,
    val url: String              // 完整 URL，用于分享与内部跟踪
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GenImage) return false
        return bytes == other.bytes && url == other.url
    }

    override fun hashCode(): Int = 31 * bytes.hashCode() + url.hashCode()
}

/**
 * 一次完整请求的结果（含元信息）。
 *
 * 标注 [Immutable]：所有字段均为不可变类型（[ImmutableBytes] / String / 基础类型），
 * [images] 列表构造后不再修改，Compose 可以安全跳过重组。
 */
@Immutable
data class GenResult(
    val success: Boolean,
    val images: List<GenImage> = emptyList(),
    val styleKey: String,
    val styleName: String,
    val sizeKey: String,
    val generationTimeMs: Long,
    val errorMessage: String? = null,
    val jobId: String? = null
)
