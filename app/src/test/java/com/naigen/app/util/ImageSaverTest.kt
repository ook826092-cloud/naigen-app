package com.naigen.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ImageSaver] 单元测试 —— 纯 JVM 边界测试。
 *
 * 注意：[ImageSaver.makeThumbnail] 内部用 [android.graphics.BitmapFactory]，
 * 在纯 JVM 单测下 `testOptions.unitTests.isReturnDefaultValues = true` 会让
 * `BitmapFactory.decodeByteArray` 返回 null，函数会回退返回原 bytes。
 * 因此这里只验证边界行为，不验证实际缩略图尺寸（那需要 instrumented 测试）。
 */
class ImageSaverTest {

    @Test
    fun makeThumbnail_emptyBytes_returnsOriginalBytes() {
        val empty = ByteArray(0)
        val out = ImageSaver.makeThumbnail(empty)
        // 空 bytes 时 decodeByteArray 返回 null，函数回退返回原 bytes
        assertEquals(0, out.size)
    }

    @Test
    fun makeThumbnail_nonNullBytes_returnsNonEmptyResult() {
        // 任意非空 bytes：decode 返回 null → 回退返回原 bytes
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val out = ImageSaver.makeThumbnail(bytes)
        assertTrue("回退路径应返回非空字节", out.isNotEmpty())
        assertEquals(bytes.size, out.size)
    }

    @Test
    fun makeThumbnail_customMaxWidth_doesNotThrow() {
        val bytes = byteArrayOf(0x89, 0x50, 0x4E, 0x47) // 伪 PNG 头
        // 不应抛异常，任意 maxWidth 都应正常返回
        val out1 = ImageSaver.makeThumbnail(bytes, maxWidth = 128)
        val out2 = ImageSaver.makeThumbnail(bytes, maxWidth = 512)
        assertTrue(out1.isNotEmpty())
        assertTrue(out2.isNotEmpty())
    }
}
