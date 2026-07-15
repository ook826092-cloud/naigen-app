package com.naigen.app.data.styles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * StyleRegistry / AutoStyleKeywords / SizeOptions 纯逻辑单测。
 * 这些类是 object 单例、无 Android 依赖，适合 JVM 单测。
 */
class StylesLogicTest {

    // ── StyleRegistry.resolveArtistString ──────────────────────────────────

    @Test
    fun resolveArtistString_knownKey_returnsPresetArtistString() {
        // 取一个确实存在的社区风格 key，确认路由到预设 artistString 而非原样返回
        val key = StyleRegistry.ALL.first().key
        val preset = StyleRegistry.get(key)
        assertEquals(preset!!.artistString, StyleRegistry.resolveArtistString(key))
    }

    @Test
    fun resolveArtistString_unknownKey_returnsOriginal() {
        // 找不到预设时视为自定义画师串，原样返回
        val custom = "some-custom-artist-string"
        assertEquals(custom, StyleRegistry.resolveArtistString(custom))
    }

    // ── AutoStyleKeywords.detect ───────────────────────────────────────────

    @Test
    fun detect_catgirl_hitsCommunityRule() {
        // "猫娘" 在 RULES 里映射到社区风格 11
        val result = AutoStyleKeywords.detect("一只可爱的猫娘在散步", includeNsfw = true)
        assertEquals("community:11", result)
    }

    @Test
    fun detect_animeHitsBuiltinPreset() {
        val result = AutoStyleKeywords.detect("动漫风格的少女", includeNsfw = false)
        assertEquals("animeOld", result)
    }

    @Test
    fun detect_blankPrompt_returnsNull() {
        assertNull(AutoStyleKeywords.detect("", includeNsfw = true))
        assertNull(AutoStyleKeywords.detect("    ", includeNsfw = true))
    }

    @Test
    fun detect_noMatch_returnsNull() {
        assertNull(AutoStyleKeywords.detect("纯风景照片没有人物", includeNsfw = true))
    }

    // ── SizeOptions.costOf ─────────────────────────────────────────────────

    @Test
    fun costOf_knownSizes_matchDefinition() {
        assertEquals(1, SizeOptions.costOf("竖图"))
        assertEquals(15, SizeOptions.costOf("2K竖图"))
        assertEquals(25, SizeOptions.costOf("4K方图"))
    }

    @Test
    fun costOf_unknownKey_fallsBackToFirst() {
        // 未知 key 回落到 ALL.first()（"竖图"，成本 1），不应抛异常
        assertEquals(SizeOptions.costOf("竖图"), SizeOptions.costOf("不存在的尺寸key"))
    }
}
