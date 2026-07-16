package com.naigen.app.util

/**
 * Token 脱敏器（策略模式，便于单测与自定义）。
 *
 * 把 URL / JSON 请求体中的 token 值遮蔽，避免密钥通过日志文件外泄。
 */
fun interface TokenRedactor {
    fun redact(raw: String): String

    companion object {
        val DEFAULT = TokenRedactor { raw ->
            if (raw.isEmpty()) raw
            else raw
                .replace(Regex("((?:[?&]|^)token=)[^&#\\s\"]+"), "$1***REDACTED***")
                .replace(Regex("(\"token\"\\s*:\\s*\")[^\"]*(\")"), "$1***REDACTED***$2")
        }
    }
}
