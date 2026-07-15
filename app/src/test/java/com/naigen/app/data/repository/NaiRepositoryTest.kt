package com.naigen.app.data.repository

import com.naigen.app.data.api.dto.JobStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NaiRepository] 单元测试 —— 不需要 Context / 网络的纯逻辑测试。
 *
 * 重点验证：
 *   1. 常量值符合预期（MAX_POLL_TIME_SEC / MAX_NETWORK_RETRIES 等）
 *   2. [JobStatusResponse] 状态常量正确分类（done / failed / network_error）
 *
 * 完整的 generate() 流程测试需要 SettingsStore（依赖 Context），
 * 已在 [com.naigen.app.data.api.NaiApiClientTest] 中通过 MockWebServer 覆盖核心 HTTP 行为。
 */
class NaiRepositoryTest {

    @Test
    fun constants_maxPollTimeSec_is180() {
        assertEquals(180, NaiRepository.MAX_POLL_TIME_SEC)
    }

    @Test
    fun constants_maxPollTimeMs_consistentWithSeconds() {
        assertEquals(
            NaiRepository.MAX_POLL_TIME_SEC * 1000L,
            NaiRepository.MAX_POLL_TIME_MS
        )
    }

    @Test
    fun constants_maxNetworkRetries_is5() {
        assertEquals(5, NaiRepository.MAX_NETWORK_RETRIES)
    }

    @Test
    fun constants_maxVariants_is6() {
        assertEquals(6, NaiRepository.MAX_VARIANTS)
    }

    @Test
    fun constants_maxPollIntervalSec_is5() {
        assertEquals(5L, NaiRepository.MAX_POLL_INTERVAL_SEC)
    }

    @Test
    fun jobStatusResponse_doneConstant_isDone() {
        assertEquals("done", JobStatusResponse.STATUS_DONE)
    }

    @Test
    fun jobStatusResponse_failedConstant_isFailed() {
        assertEquals("failed", JobStatusResponse.STATUS_FAILED)
    }

    @Test
    fun jobStatusResponse_networkErrorConstant_isNetworkError() {
        assertEquals("network_error", JobStatusResponse.STATUS_NETWORK_ERROR)
    }

    @Test
    fun jobStatusResponse_failedAndNetworkError_areDistinct() {
        // 关键：failed 与 network_error 必须不同，
        // 否则 NaiRepository 无法区分「API 真失败」与「网络异常可重试」
        assertNotEqualsHelper(
            JobStatusResponse.STATUS_FAILED,
            JobStatusResponse.STATUS_NETWORK_ERROR
        )
    }

    @Test
    fun jobStatusResponse_doneIsDistinctFromFailed() {
        assertNotEqualsHelper(
            JobStatusResponse.STATUS_DONE,
            JobStatusResponse.STATUS_FAILED
        )
    }

    private fun assertNotEqualsHelper(a: String, b: String) {
        assertTrue("$a 与 $b 不应相等", a != b)
    }
}
