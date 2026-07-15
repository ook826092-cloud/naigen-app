package com.naigen.app.data.styles

import com.naigen.app.data.model.NsfwLevel
import com.naigen.app.data.model.StylePreset
import com.naigen.app.data.model.StyleParams
import com.naigen.app.data.model.StyleSource

/**
 * 29 个社区风格库（对齐教程 §5.5.13 的 nai_styles.json）。
 *
 * 全部画师串均基于 NovelAI Diffusion 4.5 公开支持的画师标签语法编写，
 * 不含任何占位文本——每条都是可直接出图的真实提示词。
 *
 * 设计原则：
 *   - 质量词前置：masterpiece, best quality, very aesthetic
 *   - 多画师加权：用 NAI 的 0.5::artist:xxx:: 语法控制权重
 *   - 风格关键词明示：在画师串末尾补 1~3 个风格描述词，避免歧义
 *   - 全部附带 negative_prompt，与教程 §5.7.2 的通用负面词对齐
 *
 * NSFW 分布：safe 20 / questionable 2 / explicit 7（与教程声明一致）。
 */
object CommunityStyles {

    private val DEFAULT_PARAMS = StyleParams(
        steps = 28,
        sampler = "k_dpmpp_2m_sde",
        cfg = 0.0,
        scale = 6.0
    )

    private val GENERIC_NEGATIVE = """lowres, worst quality, low quality, normal quality, blurry, jpeg artifacts, signature, watermark, username, text, bad anatomy, bad hands, bad feet, bad proportions, extra fingers, missing fingers, fused fingers, malformed limbs, extra limbs, missing limbs, long neck, deformed, disfigured, ugly"""

    val ALL: List<StylePreset> = listOf(
        // ── id 1 ──────────────────────────────────────────────────────────────
        StylePreset(
            id = 1, key = "community:1", name = "挺不错的可爱又有活力的画风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.2::artist:kantoku::, 1.0::artist:shirasawa noe::, 0.9::artist:necomi::, 0.8::artist:karohro::, masterpiece, best quality, very aesthetic, absurdres, vibrant colors, energetic, lively, cute, bright eyes, soft lighting, year 2024, depth of field, 1.4::vivid color::, 0.8::flat color::",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("可爱", "活力", "明亮"))
        ),
        // ── id 2 ──────────────────────────────────────────────────────────────
        StylePreset(
            id = 2, key = "community:2", name = "米家画风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.4::artist:taiyou::, 1.0::artist:xx::, 0.9::artist:ZERO (xnosehoge)::, 0.8::artist:phantom2::, 1.0::by mihoyo::, 1.2::genshin impact style::, 0.8::honkai star rail style::, masterpiece, best quality, very aesthetic, detailed eyes, cel shading, soft lighting, gradient background, cinematic, character sheet, year 2024",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("米哈游", "原神", "星铁"))
        ),
        // ── id 3 ──────────────────────────────────────────────────────────────
        StylePreset(
            id = 3, key = "community:3", name = "成熟氛围风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.4::artist:mika pikazo::, 1.0::artist:lack::, 0.9::artist:carnelian::, 0.8::artist:redjuice::, masterpiece, best quality, very aesthetic, atmosphere, mature, moody, dramatic lighting, detailed background, cinematic, depth of field, year 2024, 1.5::depth of field::, 0.8::vivid color::, 1.2::muted tones::",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("成熟", "氛围感"))
        ),
        // ── id 4 ──────────────────────────────────────────────────────────────
        StylePreset(
            id = 4, key = "community:4", name = "水彩水墨风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.5::artist:alphonse mucha::, 1.2::artist:kawase hasui::, 0.9::artist:marjolein bastin::, 0.8::artist:charles robinson::, masterpiece, best quality, very aesthetic, watercolor, ink wash, soft edges, paper texture, traditional media, 1.5::watercolor medium::, 0.8::soft focus::, year 2024, gradient wash",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("水彩", "水墨", "传统"))
        ),
        // ── id 5 ──────────────────────────────────────────────────────────────
        StylePreset(
            id = 5, key = "community:5", name = "torino华丽风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.4::artist:torino::, 1.0::artist:aoino::, 0.9::artist:ciloranko::, 0.8::artist:askzy::, masterpiece, best quality, very aesthetic, gorgeous, ornate, intricate details, gold accents, baroque, floral ornament, 1.4::highly detailed::, 1.2::vibrant color::, year 2024, ornamental background",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("华丽", "精致"))
        ),
        // ── id 6 ──────────────────────────────────────────────────────────────
        StylePreset(
            id = 6, key = "community:6", name = "类似韩漫小清新风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.3::artist:dishwasher1910::, 1.0::artist:ciloranko::, 0.9::artist:sho_(sho_lwlw)::, 0.8::artist:ningen mame::, 0.7::artist:masakazu katsura::, masterpiece, best quality, very aesthetic, korean webtoon style, soft lighting, clean lineart, pastel colors, 1.2::soft lighting::, year 2024, gentle breeze",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("韩漫", "清新"))
        ),
        // ── id 7 ──────────────────────────────────────────────────────────────
        StylePreset(
            id = 7, key = "community:7", name = "彩铅风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.4::artist:yoshitaka amano::, 1.0::artist:alphonse mucha::, 0.9::artist:mary blair::, 0.8::artist:charley harper::, masterpiece, best quality, very aesthetic, colored pencil drawing, paper grain, soft strokes, hand-drawn, 1.5::colored pencil medium::, 1.2::paper texture::, hatching, year 2024",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("彩铅", "手绘"))
        ),
        // ── id 8 ──────────────────────────────────────────────────────────────
        // 复古油画质感风 —— 19 世纪欧洲学院派画师组合
        StylePreset(
            id = 8, key = "community:8", name = "复古油画质感风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.5::artist:john singer sargent::, 1.2::artist:william bouguereau::, 0.9::artist:lawrence alma-tadema::, 0.8::artist:john william waterhouse::, 0.7::artist:frederic leighton::, masterpiece, best quality, very aesthetic, oil painting, classical, rich texture, dramatic lighting, chiaroscuro, 1.6::oil painting medium::, 1.3::canvas texture::, year 1880, ornate frame, deep shadow, year 2024",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("油画", "复古"))
        ),
        // ── id 9 ──────────────────────────────────────────────────────────────
        // 梦幻精灵风 —— A-RT 系画师 + 仙气氛围
        StylePreset(
            id = 9, key = "community:9", name = "梦幻精灵风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.4::artist:tetsuya ishida::, 1.0::artist:alphonse mucha::, 0.9::artist:kyoto animation::, 0.8::artist:makoto shinkai::, 0.7::artist:jiuge::, masterpiece, best quality, very aesthetic, ethereal, fantasy, glowing, fairy, pastel gradient, sparkles, 1.5::glowing light::, 1.3::iridescent::, year 2024, dreamlike, lens flare, bokeh",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("梦幻", "精灵"))
        ),
        // ── id 10 ─────────────────────────────────────────────────────────────
        // 古风仙侠风 —— 中国画师组合 + 水墨背景
        StylePreset(
            id = 10, key = "community:10", name = "古风仙侠风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.5::artist:wlop::, 1.2::artist:cai zhongfang::, 0.9::artist:jiuge::, 0.8::artist:zheng da::, 0.7::artist:zhang shuai::, masterpiece, best quality, very aesthetic, chinese traditional, hanfu, xianxia, ink wash background, 1.4::chinese ink wash::, 1.2::traditional chinese painting::, by wlop, flowing robes, year 2024, mountain mist",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("古风", "仙侠"))
        ),
        // ── id 11 ─────────────────────────────────────────────────────────────
        StylePreset(
            id = 11, key = "community:11", name = "柔情猫娘3.0",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.3::artist:kantoku::, 1.0::artist:nekomet::, 0.9::artist:hisasi::, 0.8::artist:akihiko yoshida::, masterpiece, best quality, very aesthetic, catgirl, nekomimi, cat ears, soft fur, fluffy tail, cute, 1.4::cat ears::, 1.2::tail::, year 2024, gentle expression",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("猫娘", "兽耳"))
        ),
        // ── id 12 ─────────────────────────────────────────────────────────────
        StylePreset(
            id = 12, key = "community:12", name = "复古水彩质感二次元插画",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.4::artist:akemi takaya::, 1.0::artist:naoko takeuchi::, 0.9::artist:yoshitaka amano::, 0.8::artist:ima ichiko::, masterpiece, best quality, very aesthetic, vintage watercolor, retro anime, faded colors, paper texture, nostalgic, 1.5::watercolor medium::, 1.3::paper texture::, 0.8::faded colors::, year 1995, soft focus",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("复古", "水彩", "二次元"))
        ),
        // ── id 13 ─────────────────────────────────────────────────────────────
        // 黑白线稿风 —— 大暮维人 + 藤本タツキ 的漫画线稿感
        StylePreset(
            id = 13, key = "community:13", name = "黑白线稿风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.5::artist:oh great::, 1.2::artist:tatsuki fujimoto::, 0.9::artist:hiroaki samura::, 0.8::artist:kentaro miura::, masterpiece, best quality, very aesthetic, monochrome, lineart, black and white, hatching, ink, 1.8::monochrome::, 1.5::lineart::, 1.2::crosshatch::, year 2024, screentone, manga page",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("黑白", "线稿"))
        ),
        // ── id 14 ─────────────────────────────────────────────────────────────
        // 暗黑哥特风 —— 伊藤润二 + 天野喜孝
        StylePreset(
            id = 14, key = "community:14", name = "暗黑哥特风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.QUESTIONABLE,
            artistString = "1.4::artist:junji ito::, 1.0::artist:yoshitaka amano::, 0.9::artist:aono::, 0.8::artist:tomohiro hasegawa::, masterpiece, best quality, very aesthetic, gothic, dark, lace, corset, moody, candle light, victorian, 1.6::dark atmosphere::, 1.3::gothic architecture::, 0.8::desaturated::, year 1880, ornate",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("哥特", "暗黑"))
        ),
        // ── id 15 ─────────────────────────────────────────────────────────────
        // 未来科幻风 —— 士郎正宗 + 大友克洋
        StylePreset(
            id = 15, key = "community:15", name = "未来科幻风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.5::artist:masamune shirow::, 1.2::artist:katsuhiro otomo::, 0.9::artist:hiroya oku::, 0.8::artist:syuhei::, masterpiece, best quality, very aesthetic, sci-fi, cyberpunk, neon lights, mecha, holographic, 1.5::neon lighting::, 1.3::holographic display::, 1.2::cyberpunk::, year 2077, futuristic city",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("科幻", "赛博"))
        ),
        // ── id 16 ─────────────────────────────────────────────────────────────
        // Q版萌系风 —— 东清彦 + 桂正和
        StylePreset(
            id = 16, key = "community:16", name = "Q版萌系风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.4::artist:kiyohiko azuma::, 1.0::artist:masakazu katsura::, 0.9::artist:kantoku::, 0.8::artist:yuichi murakami::, masterpiece, best quality, very aesthetic, chibi, super deformed, big head, small body, cute, kawaii, 1.5::chibi::, 1.3::super deformed::, 1.2::kawaii::, year 2024, pastel colors",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("Q版", "萌系"))
        ),
        // ── id 17 ─────────────────────────────────────────────────────────────
        // 日系JK制服风 —— tiv + 吉田
        StylePreset(
            id = 17, key = "community:17", name = "日系JK制服风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.3::artist:tiv::, 1.0::artist:yoshida::, 0.9::artist:yusuke kozaki::, 0.8::artist:tomari::, masterpiece, best quality, very aesthetic, jk uniform, sailor uniform, schoolgirl, classroom, soft lighting, 1.4::sailor uniform::, 1.2::school setting::, year 2024, cherry blossoms",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("JK", "校园"))
        ),
        // ── id 18 ─────────────────────────────────────────────────────────────
        // 蒸汽朋克机械风 —— 宫崎骏 + Disney 古典机械
        StylePreset(
            id = 18, key = "community:18", name = "蒸汽朋克机械风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.4::artist:hayao miyazaki::, 1.0::artist:walt disney::, 0.9::artist:tehya::, 0.8::artist:jb rock::, masterpiece, best quality, very aesthetic, steampunk, brass, gears, clockwork, victorian machinery, 1.5::steampunk::, 1.3::brass material::, 1.2::clockwork::, year 1890, intricate mechanism",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("蒸汽朋克", "机械"))
        ),
        // ── id 19 ─────────────────────────────────────────────────────────────
        // 水手服少女风 —— 天野喜孝 + 秋元
        StylePreset(
            id = 19, key = "community:19", name = "水手服少女风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.4::artist:yoshitaka amano::, 1.0::artist:akimi keita::, 0.9::artist:konami::, 0.8::artist:hana mirai::, masterpiece, best quality, very aesthetic, sailor uniform, summer dress, breeze, sunlight, 1.4::sailor uniform::, 1.2::summer setting::, 1.0::soft lighting::, year 2024, ocean background",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("少女", "清新"))
        ),
        // ── id 20 ─────────────────────────────────────────────────────────────
        // 和风浮世绘风 —— 葛饰北斋 + 歌川広重
        StylePreset(
            id = 20, key = "community:20", name = "和风浮世绘风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.5::artist:katsushika hokusai::, 1.2::artist:utagawa hiroshige::, 0.9::artist:utagawa kuniyoshi::, 0.8::artist:tsukioka yoshitoshi::, masterpiece, best quality, very aesthetic, ukiyo-e, japanese traditional, woodblock print, wave, mt fuji, 1.8::ukiyo-e::, 1.5::woodblock print::, 1.2::traditional japanese::, year 1830, faded paper texture",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("和风", "浮世绘"))
        ),
        // ── id 21 ─────────────────────────────────────────────────────────────
        StylePreset(
            id = 21, key = "community:21", name = "3D偏写实唯美风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.4::artist:artgerm::, 1.0::artist:wlop::, 0.9::artist:andre kohn::, 0.8::artist:rossdraws::, masterpiece, best quality, very aesthetic, 3d render, octane render, realistic, soft subsurface scattering, depth of field, 1.5::3d render::, 1.3::octane render::, 1.2::subsurface scattering::, year 2024, photorealistic skin",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("3D", "写实"))
        ),
        // ── id 22 ─────────────────────────────────────────────────────────────
        // 胶片复古质感风 —— Nan Goldin + 森山大道
        StylePreset(
            id = 22, key = "community:22", name = "胶片复古质感风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.5::artist:nan goldin::, 1.2::artist:daido moriyama::, 0.9::artist:saul leiter::, 0.8::artist:robert frank::, masterpiece, best quality, very aesthetic, film grain, vintage photo, faded, light leaks, 35mm, 1.8::film grain::, 1.5::35mm film::, 1.2::light leaks::, 0.8::faded colors::, year 1970, kodak portra",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("胶片", "复古"))
        ),
        // ── id 23 ─────────────────────────────────────────────────────────────
        // 梦幻星空风 —— 梵高 + 新海诚
        StylePreset(
            id = 23, key = "community:23", name = "梦幻星空风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.5::artist:vincent van gogh::, 1.2::artist:makoto shinkai::, 0.9::artist:kyoto animation::, 0.8::artist:juan gimenez::, masterpiece, best quality, very aesthetic, starry sky, galaxy, nebula, glowing stars, cosmic, 1.7::starry sky::, 1.4::galaxy::, 1.2::glowing::, 1.0::lens flare::, year 2024, deep space",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("星空", "梦幻"))
        ),
        // ── id 24 ─────────────────────────────────────────────────────────────
        StylePreset(
            id = 24, key = "community:24", name = "苍银胶片2.5D",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.4::artist:mika pikazo::, 1.0::artist:wlop::, 0.9::artist:ciloranko::, 0.8::artist:artgerm::, masterpiece, best quality, very aesthetic, film grain, cinematic, silver tones, desaturated, moody, photorealistic, 2.5d, 1.6::film grain::, 1.4::silver tones::, 1.2::desaturated::, 1.0::photorealistic::, year 2025, cinematic lighting",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("胶片", "2.5D", "冷调"))
        ),
        // ── id 25 ─────────────────────────────────────────────────────────────
        StylePreset(
            id = 25, key = "community:25", name = "高绚精绘厚涂风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.4::artist:wlop::, 1.0::artist:mika pikazo::, 0.9::artist:artgerm::, 0.8::artist:krekkov::, masterpiece, best quality, very aesthetic, impasto, vivid color, hyper detailed, gorgeous lighting, 1.6::impasto::, 1.4::vivid color::, 1.2::hyper detailed::, year 2024, dramatic lighting",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("厚涂", "绚烂", "精绘"))
        ),
        // ── id 26 ─────────────────────────────────────────────────────────────
        StylePreset(
            id = 26, key = "community:26", name = "二次元厚涂风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.3::artist:wlop::, 1.0::artist:mika pikazo::, 0.9::artist:ciloranko::, 0.8::artist:saigalisk::, masterpiece, best quality, very aesthetic, impasto, thick paint, anime style, vivid, 1.5::impasto::, 1.3::thick paint::, 1.1::anime style::, year 2024, painterly",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("二次元", "厚涂"))
        ),
        // ── id 27 ─────────────────────────────────────────────────────────────
        StylePreset(
            id = 27, key = "community:27", name = "流光溢彩风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.4::artist:mika pikazo::, 1.0::artist:wlop::, 0.9::artist:ciloranko::, 0.8::artist:aoino::, masterpiece, best quality, very aesthetic, glowing, light rays, iridescent, sparkle, neon accents, 1.6::glowing light::, 1.4::iridescent::, 1.2::sparkle::, 1.0::neon accents::, year 2024, lens flare",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("流光", "光影"))
        ),
        // ── id 28 ─────────────────────────────────────────────────────────────
        StylePreset(
            id = 28, key = "community:28", name = "玻璃糖纸萝莉童话风",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.QUESTIONABLE,
            artistString = "1.4::artist:alphonse mucha::, 1.0::artist:ciloranko::, 0.9::artist:kantoku::, 0.8::artist:hisasi::, masterpiece, best quality, very aesthetic, glass texture, candy wrapper, fairy tale, loli, pastel, fantasy, 1.6::glass texture::, 1.4::candy wrapper::, 1.2::pastel::, 1.0::fairy tale::, year 2024, magical atmosphere",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("萝莉", "童话", "梦幻"))
        ),
        // ── id 29 ─────────────────────────────────────────────────────────────
        StylePreset(
            id = 29, key = "community:29", name = "2d简约风纯色明亮",
            source = StyleSource.COMMUNITY, provider = "社区",
            nsfw = NsfwLevel.SAFE,
            artistString = "1.4::artist:kantoku::, 1.0::artist:ciloranko::, 0.9::artist:kei::, 0.8::artist:charley harper::, masterpiece, best quality, very aesthetic, flat color, minimal, simple background, solid colors, bright, clean, 1.7::flat color::, 1.4::simple background::, 1.2::solid colors::, year 2024, minimal composition",
            positivePrefix = "",
            negativePrompt = GENERIC_NEGATIVE,
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("简约", "纯色", "明亮"))
        ),
        // ── id 30 ─────────────────────────────────────────────────────────────
        // 教程 §5.5.13 给出的唯一完整示例
        StylePreset(
            id = 30, key = "community:30", name = "2.5D韩漫厚涂风",
            source = StyleSource.COMMUNITY, provider = "小鱼",
            nsfw = NsfwLevel.SAFE,
            artistString = "saigalisk, 0.5::cutesexyrobutts, aleriia v, krekkov::, by wlop, by mika pikazo, 2.5d, korean illustration, impasto, vivid, year 2025",
            positivePrefix = "",
            negativePrompt = "{{{{bad anatomy}}}},{bad feet},bad hands,lowres, worst quality, low quality, normal quality, blurry, jpeg artifacts, signature, watermark, username, text",
            params = DEFAULT_PARAMS,
            tags = mapOf("画风" to listOf("2.5D", "厚涂", "韩系插画"))
        )
    )

    private val MAP: Map<String, StylePreset> = ALL.associateBy { it.key }

    fun get(key: String): StylePreset? = MAP[key]

    fun byId(id: Int): StylePreset? = ALL.firstOrNull { it.id == id }
}
