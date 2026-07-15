package com.naigen.app.data.styles

import com.naigen.app.data.model.StylePreset

/**
 * 自动风格检测关键词表（对齐教程 §5.5.4 的 AUTO_STYLE_KEYWORDS）。
 *
 * 顺序即优先级，具体词优先于宽泛词。
 *
 * 每行格式：([关键词列表], 社区风格ID 或 null, 内置 key 或风格名)
 *   - communityId != null → 命中后返回 "community:<id>"
 *   - communityId == null → 命中后返回 builtinKey 字符串
 */
data class AutoStyleRule(
    val keywords: List<String>,
    val communityId: Int?,
    val builtinKey: String?
)

object AutoStyleKeywords {

    val RULES: List<AutoStyleRule> = listOf(
        // ── 高度具体的社区风格 ──
        AutoStyleRule(listOf("猫娘", "catgirl", "nekomimi", "猫耳"), 11, null),
        AutoStyleRule(listOf("米哈游", "miHoYo", "原神", "genshin", "崩铁", "星铁", "honkai", "米家"), 2, null),
        AutoStyleRule(listOf("水彩", "水墨", "watercolor", "ink wash"), 4, null),
        AutoStyleRule(listOf("彩铅", "colored pencil", "pencil drawing"), 7, null),
        AutoStyleRule(listOf("童话", "fairy tale", "fantasy"), 28, null),
        AutoStyleRule(listOf("复古", "retro", "vintage", "怀旧"), 12, null),
        AutoStyleRule(listOf("华丽", "gorgeous", "华丽风", "torino"), 5, null),
        AutoStyleRule(listOf("胶片", "film", "film grain", "cinematic"), 24, null),
        AutoStyleRule(listOf("3D", "3d render", "写实3d"), 21, null),
        AutoStyleRule(listOf("厚涂", "impasto", "thick paint"), 26, null),
        AutoStyleRule(listOf("流光", "光影", "light effect", "glow"), 27, null),
        AutoStyleRule(listOf("绚烂", "vivid", "高绚", "colorful"), 25, null),
        AutoStyleRule(listOf("简约", "simple", "flat", "纯色", "minimal"), 29, null),
        AutoStyleRule(listOf("成熟", "mature", "氛围", "atmosphere"), 3, null),
        AutoStyleRule(listOf("韩漫", "korean", "manhwa", "webtoon"), 6, null),
        AutoStyleRule(listOf("萝莉", "loli"), 28, null),
        // ── 较宽泛的关键词放后面 ──
        AutoStyleRule(listOf("2.5D", "2.5d", "半写实", "semi-realistic"), 30, null),
        AutoStyleRule(listOf("可爱", "cute", "活力", "energetic", "kawaii"), 1, null),
        // ── 内置预设：越具体越靠前，避免被宽泛词截胡 ──
        AutoStyleRule(listOf("galgame", "gal", "视觉小说", "visual novel", "立绘"), null, "galgame"),
        AutoStyleRule(listOf("漫画同人", "comic doujin", "comicDoujin", "Comic同人", "动漫同人"), null, "comicDoujin"),
        AutoStyleRule(listOf("萝莉唯美", "loli 2.5d", "lolita25d", "萝莉2.5D"), null, "realistic_loli"),
        AutoStyleRule(listOf("写实萝莉", "realistic loli", "写实质感", "photo realistic", "photo(medium)"), null, "realistic_loli"),
        AutoStyleRule(listOf("本子", "doujin", "同人"), null, "doujin"),
        AutoStyleRule(listOf("动漫", "anime", "旧版", "classic anime"), null, "animeOld")
    )

    /**
     * 根据提示词匹配最佳风格。返回：
     *   - null              → 未匹配，调用方应使用默认风格
     *   - "community:<id>"  → 命中社区风格
     *   - "galgame" 等      → 命中内置预设
     *
     * @param includeNsfw 是否允许返回 NSFW 风格。false 时跳过 QUESTIONABLE / EXPLICIT 的社区风格，
     *                    继续向下匹配。
     */
    fun detect(prompt: String, includeNsfw: Boolean): String? {
        val lower = prompt.lowercase()
        for (rule in RULES) {
            // 命中关键词？
            val hit = rule.keywords.any { kw -> kw.lowercase() in lower }
            if (!hit) continue

            // 内置预设直接返回
            if (rule.communityId == null) {
                return rule.builtinKey
            }
            // 社区风格：检查 NSFW
            val style = CommunityStyles.byId(rule.communityId) ?: continue
            if (!includeNsfw && style.nsfw != com.naigen.app.data.model.NsfwLevel.SAFE) {
                continue  // NSFW 被过滤，继续匹配下一个
            }
            return style.key
        }
        return null
    }
}

/**
 * 风格统一访问入口：合并内置 7 + 社区 29 = 36 个风格。
 */
object StyleRegistry {

    val ALL: List<StylePreset> = ArtistPresets.ALL + CommunityStyles.ALL

    private val MAP: Map<String, StylePreset> = ALL.associateBy { it.key }

    /**
     * 根据 key 取风格预设。返回 null 时调用方应让用户用自定义画师串兜底。
     */
    fun get(key: String): StylePreset? = MAP[key]

    /**
     * 风格路由（对齐教程 §5.5.8 的 _get_artist_string）。
     *
     * 链路：
     *   1. registry 中找到 → 取预设的 artistString
     *   2. 找不到            → 原样返回（视为自定义画师串）
     *
     * 这是为了避免「不管传什么 style 出来都是 2.5d」的 bug —— 见教程 §5.5.17 诊断清单。
     */
    fun resolveArtistString(styleKey: String): String {
        return MAP[styleKey]?.artistString ?: styleKey
    }
}
