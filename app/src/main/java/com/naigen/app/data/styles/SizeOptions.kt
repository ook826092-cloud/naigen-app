package com.naigen.app.data.styles

import com.naigen.app.data.model.SizeOption

/**
 * 9 个尺寸选项（对齐教程 §5.5.5 的 SIZE_OPTIONS）。
 *
 * 注意：API 接受的是中文字符串 key，width/height 仅用于本地宽高比推断和 UI 预览。
 */
object SizeOptions {

    val ALL: List<SizeOption> = listOf(
        SizeOption("竖图", "竖图 · 1点", 1, 832, 1216),
        SizeOption("横图", "横图 · 1点", 1, 1216, 832),
        SizeOption("方图", "方图 · 1点", 1, 1024, 1024),
        SizeOption("2K竖图", "2K 竖图 · 15点", 15, 832, 1216),
        SizeOption("2K横图", "2K 横图 · 15点", 15, 1216, 832),
        SizeOption("2K方图", "2K 方图 · 15点", 15, 1024, 1024),
        SizeOption("4K竖图", "4K 竖图 · 25点", 25, 832, 1216),
        SizeOption("4K横图", "4K 横图 · 25点", 25, 1216, 832),
        SizeOption("4K方图", "4K 方图 · 25点", 25, 1024, 1024)
    )

    private val MAP: Map<String, SizeOption> = ALL.associateBy { it.key }

    fun get(key: String): SizeOption =
        MAP[key] ?: ALL.first()

    fun costOf(key: String): Int = get(key).cost
}
