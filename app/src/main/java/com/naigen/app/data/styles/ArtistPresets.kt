package com.naigen.app.data.styles

import com.naigen.app.data.model.NsfwLevel
import com.naigen.app.data.model.StylePreset
import com.naigen.app.data.model.StyleParams
import com.naigen.app.data.model.StyleSource

/**
 * 7 个内置画风预设（对齐教程 §5.7.3 的 7.12 版画师串）。
 *
 * 这里把每个风格的 artistString 完整内联进 Kotlin 常量——好处是：
 *  - 启动快（无需读 JSON 文件）
 *  - 编译期类型安全
 *  - 用户修改画风需要重新打包（这是用户在 AskUserQuestion 中明确选择的）
 *
 * 画师串的值全部来自教程 §5.7.3 标注 "(7.12版)" 的当前生效版本。
 */
object ArtistPresets {

    // ── 2.5d ──────────────────────────────────────────────────────────────────────
    // 7.12 版：多画师加权叠权 + pale aesthetic / silver-toned 冷调
    private val STYLE_25D = """
20::best quality, absurdres, very aesthetic, detailed, masterpiece::, 20::highly finished::, 10::ultra detailed::, 5::masterpiece::, 5::best quality::, 2.4::kidmo::, 1.2::omone hokoma agm::, 1.1::dino, wanke, liduke::, 0.8::rurudo, mignon, artist:pottsness, artist:toosaka asagi::, 0.7::misaka_12003-gou::, 0.6::artist:chocoan, artist:ciloranko, artist:rhasta, artist:sho_sho_lwlw::, dino_(dinoartforame), agoto, akakura, year 2025, textless version, no text, The image is highly intricate finished drawn. Only the character's face is in anime style, but their body is in realistic style. 1.35::A highly finished photo-style artwork that has graphic texture, realistic skin surface, and lifelike flesh with little obliques::, smooth line, glossy skin, realistic, 4k, 1.63::photorealistic::, 1.63::photo(medium)::, 3::simple background::, 2::depth of field::, 1.5::vivid color, lively color::, desaturated, muted tones, cinematic desaturation, pale aesthetic, silver-toned, -2::green::, -1.5::vibrant, colorful, saturated::
    """.trim()

    // ── fresh ─────────────────────────────────────────────────────────────────────
    // 7.12 版：加了 masterpiece / best quality 前缀 + soft lighting
    private val STYLE_FRESH = """
masterpiece, best quality,[[[artist:dishwasher1910]]], {{yd_(orange_maru)}}, [artist:ciloranko], [artist:sho_(sho_lwlw)], [ningen mame], soft lighting,year 2024
    """.trim()

    // ── doujin ────────────────────────────────────────────────────────────────────
    // 7.12 版：显示名改为「本子里番风」，画师串值不变
    private val STYLE_DOUJIN = """
1.4::asanagi::,{{{{{artist:asanagi}}}}},1.2::xiaoluo_xl::,1.3::Artist: misaka_12003-gou::,1.2::Artist:shexyo::,0.7::Artist:b.sa_(bbbs)::,1::Artist:qiandaiyiyu::,1.05::artist:natedecock::,1.05::artist:kunaboto::,0.75::artist:kandata_nijou::,1.05::artist:zer0.zer0 ::,1.05::artist:jasony::,0.75::misaka_12003-gou ::, dino_(dinoartforame), wanke, liduke, year 2025, realistic, 4k, -2::green ::, {textless version, The image is highly intricate finished drawn,write realistically,true to life}, 1.35::A highly finished photo-style artwork that has lively color, graphic texture, realistic skin surface, and lifelike flesh with little obliques::, 1.63::photorealistic::,3::age slider::,1.63::photo(medium)::, 2::best quality, absurdres, very aesthetic, detailed, masterpiece::,-4::Muscle definition, abs::
    """.trim()

    // ── galgame ───────────────────────────────────────────────────────────────────
    private val STYLE_GALGAME = """
artist:ningen_mame,, noyu_(noyu23386566),, toosaka asagi,, location,
20::best quality, absurdres, very aesthetic, detailed, masterpiece::,:,, very aesthetic, masterpiece, no text,
    """.trim()

    // ── comicDoujin ───────────────────────────────────────────────────────────────
    // 7.12 新增：clean cel shading 动画截图质感
    private val STYLE_COMIC_DOUJIN = """
masterpiece,best quality,ultra detailed,by 小田武士,by 内尾和正,by あずーる,TV anime screencap,clean cel shading,soft lineart,subtle bloom glow
    """.trim()

    // ── animeOld ──────────────────────────────────────────────────────────────────
    private val STYLE_ANIME_OLD = """
artist collaboration, 0.70::artist:necomi ::, 0.80::artist:tan (tangent) ::, 1.38::artist:kanda done ::, 1.22::artist:quasarcake ::, 1.22::artist:atdan ::, 0.94::artist:fuumi (radial engine) ::, 1.70::artist:john kafka ::, 0.60::artist:meisansan ::, 0.98::artist:ogipote ::, 0.44::artist:nixeu ::, 0.74::artist:mignon ::, 0.94::artist:rangu ::, 1.18::artist:hiten (hitenkei) ::, 1.24::artist:freng ::, 0.56::artist:miwabe sakura ::, year 2024, perspective
    """.trim()

    // ── realistic_loli ────────────────────────────────────────────────────────────
    // 7.12 版：值同步网站 lolita25d，lifelike skin → lifelike flesh，新增 pale aesthetic
    private val STYLE_REALISTIC_LOLI = """
20::best quality, absurdres, very aesthetic, detailed, masterpiece::, 20::highly finished::, 10::ultra detailed::, 5::masterpiece::, 5::best quality::, 2.4::kidmo::, 1.2::omone hokoma agm::, 1.1::dino, wanke, liduke::, 0.8::rurudo, mignon, artist:pottsness, artist:toosaka asagi::, 0.7::misaka_12003-gou::, 0.6::artist:chocoan, artist:ciloranko, artist:rhasta, artist:sho_sho_lwlw::, dino_(dinoartforame), agoto, akakura, 0.9::rurudo(Only body shape), mignon(Only body shape) :: year 2025, textless version, {{petite,loli}}, Petite figure, no text, The image is highly intricate finished drawn. Only the character's face is in anime style, but their body is in realistic style. 1.35::A highly finished photo-style artwork that has graphic texture, realistic skin surface, and lifelike flesh with little obliques::, smooth line, glossy skin, realistic, 4k, 1.63::photorealistic::, 1.63::photo(medium)::, 3::simple background::, 2::depth of field::, 1.5::vivid color, lively color::, desaturated, muted tones, cinematic desaturation, pale aesthetic, silver-toned, -2::green::, -1.5::vibrant, colorful, saturated::
    """.trim()

    /**
     * 7 个内置风格。顺序即 UI 默认显示顺序。
     */
    val ALL: List<StylePreset> = listOf(
        StylePreset(
            id = null, key = "2.5d", name = "2.5D 唯美风",
            source = StyleSource.BUILTIN,
            artistString = STYLE_25D,
            params = StyleParams()
        ),
        StylePreset(
            id = null, key = "fresh", name = "韩漫小清新风",
            source = StyleSource.BUILTIN,
            artistString = STYLE_FRESH,
            params = StyleParams()
        ),
        StylePreset(
            id = null, key = "doujin", name = "本子里番风",
            source = StyleSource.BUILTIN, nsfw = NsfwLevel.QUESTIONABLE,
            artistString = STYLE_DOUJIN,
            params = StyleParams()
        ),
        StylePreset(
            id = null, key = "galgame", name = "GalGame 风",
            source = StyleSource.BUILTIN,
            artistString = STYLE_GALGAME,
            params = StyleParams()
        ),
        StylePreset(
            id = null, key = "comicDoujin", name = "漫画同人风",
            source = StyleSource.BUILTIN,
            artistString = STYLE_COMIC_DOUJIN,
            params = StyleParams()
        ),
        StylePreset(
            id = null, key = "animeOld", name = "动漫风（旧）",
            source = StyleSource.BUILTIN,
            artistString = STYLE_ANIME_OLD,
            params = StyleParams()
        ),
        StylePreset(
            id = null, key = "realistic_loli", name = "2.5D 唯美风（萝）",
            source = StyleSource.BUILTIN, nsfw = NsfwLevel.QUESTIONABLE,
            artistString = STYLE_REALISTIC_LOLI,
            params = StyleParams()
        )
    )

    private val MAP: Map<String, StylePreset> = ALL.associateBy { it.key }

    fun get(key: String): StylePreset? = MAP[key]
}
