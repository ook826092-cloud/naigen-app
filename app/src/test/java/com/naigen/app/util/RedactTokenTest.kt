package com.naigen.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * AppLog.redactToken 脱敏逻辑单测（internal 可见性，同模块 test 源集可访问）。
 */
class RedactTokenTest {

    @Test
    fun redact_tokenInUrlQuery_isMasked() {
        val url = "https://nai.sta1n.cn/api/jobs/abc?token=STA1N-SECRET123&other=1"
        val out = AppLog.redactToken(url)
        assertEquals(
            "https://nai.sta1n.cn/api/jobs/abc?token=***REDACTED***&other=1",
            out
        )
        assertFalse(out.contains("STA1N-SECRET123"))
    }

    @Test
    fun redact_tokenInJsonBody_isMasked() {
        val json = """{"token":"STA1N-SECRET123","tag":"cat"}"""
        val out = AppLog.redactToken(json)
        assertEquals(
            """{"token":"***REDACTED***","tag":"cat"}""",
            out
        )
        assertFalse(out.contains("STA1N-SECRET123"))
    }

    @Test
    fun redact_noToken_unchanged() {
        val plain = "https://nai.sta1n.cn/api/me?page=2"
        assertEquals(plain, AppLog.redactToken(plain))
    }

    @Test
    fun redact_emptyString_unchanged() {
        assertEquals("", AppLog.redactToken(""))
    }

    @Test
    fun redact_multipleTokens_allMasked() {
        val input = "token=A&token=B"
        val out = AppLog.redactToken(input)
        assertEquals("token=***REDACTED***&token=***REDACTED***", out)
    }
}
