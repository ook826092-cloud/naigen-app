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
 * 单张图片的生成结果。
 *
 * 注意：[bytes] 是 [ByteArray]，Kotlin 默认引用相等，但这里我们想要内容相等
 * （便于 [GenImage] 在 Set / Map 中按内容比较）。
 *
 * 标注 [Stable]（而非 [Immutable]）：因为 [ByteArray] 内容可变，
 * 不满足 Compose 严格 Immutable 的要求；但作为已生成的图片数据，
 * 实际使用中不会被修改，[Stable] 足以让 Compose 正确处理重组。
 */
@Stable
data class GenImage(
    val bytes: ByteArray,
    val url: String              // 完整 URL，用于分享与内部跟踪
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GenImage) return false
        return bytes.contentEquals(other.bytes) && url == other.url
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + url.hashCode()
        return result
    }
}

/**
 * 一次完整请求的结果（含元信息）。
 *
 * 标注 [Stable]（而非 [Immutable]）：
 *   - [images] 是 [List]<[GenImage]>，而 [GenImage.bytes] 是可变的 [ByteArray]
 *   - 严格 [Immutable] 会误导 Compose 跳过重组，可能导致 bytes 更新不刷新
 *   - [Stable] 让 Compose 仍按引用比较，但允许内部 list 的「逻辑相等」更新
 */
@Stable
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
