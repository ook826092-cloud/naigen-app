package com.naigen.app.data.model

import androidx.compose.runtime.Immutable

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
 * 直接把 extra_params 的字段平铺，避免运行时 Map<String, Any> 的不类型安全。
 */
@Immutable
data class GenRequest(
    val prompt: String,
    val negativePrompt: String = "",
    val styleKey: String,        // 引用 StylePreset.key
    val sizeKey: String,         // 引用 SizeOption.key
    val steps: Int? = null,      // null = 使用风格默认
    val scale: Double? = null,
    val cfg: Double? = null,
    val sampler: String? = null,
    val customArtist: String = ""  // --artist 覆盖整个画风预设；非空时优先
)

/**
 * 单张图片的生成结果。
 */
data class GenImage(
    val bytes: ByteArray,
    val url: String              // 完整 URL，用于分享与内部跟踪
)

/**
 * 一次完整请求的结果（含元信息）。
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
