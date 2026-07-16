package com.naigen.app.service

import com.naigen.app.data.model.GenResult
import com.naigen.app.data.repository.GenProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [GenerationBus] 单元测试 —— 纯 JVM，验证状态机流转。
 *
 * 重点验证：
 *   1. reset() 把 isRunning 置 true、清空旧结果
 *   2. publishResults() 把 isRunning 置 false、发出 AllDone
 *   3. markFinished() 只置 isRunning，不动 results
 *   4. releaseResults() 清空 results 释放 ByteArray 内存
 */
class GenerationBusTest {

    @Before
    fun setUp() {
        // 每个用例前重置总线到干净状态
        GenerationBus.reset()
        GenerationBus.releaseResults()
        GenerationBus.markFinished()
    }

    @Test
    fun reset_setsRunningTrue_andClearsResults() {
        // 先放点结果进去
        GenerationBus.publishResults(listOf(fakeResult()))
        assertTrue(GenerationBus.results.value.isNotEmpty())

        GenerationBus.reset()

        assertTrue(GenerationBus.isRunning.value)
        assertTrue(GenerationBus.results.value.isEmpty())
        assertEquals(GenProgress.Idle, GenerationBus.progress.value)
    }

    @Test
    fun publishResults_setsRunningFalse_andEmitsAllDone() {
        GenerationBus.reset()
        val results = listOf(fakeResult(success = true), fakeResult(success = false))

        GenerationBus.publishResults(results)

        assertFalse(GenerationBus.isRunning.value)
        assertEquals(results, GenerationBus.results.value)
        assertTrue(GenerationBus.progress.value is GenProgress.AllDone)
    }

    @Test
    fun markFinished_setsRunningFalse_butKeepsResults() {
        GenerationBus.reset()
        val results = listOf(fakeResult())
        GenerationBus.publishResults(results)

        GenerationBus.markFinished()

        assertFalse(GenerationBus.isRunning.value)
        // results 应该保留，markFinished 不应清空
        assertEquals(results, GenerationBus.results.value)
    }

    @Test
    fun releaseResults_clearsResultsButNotProgress() {
        GenerationBus.reset()
        val results = listOf(fakeResult())
        GenerationBus.publishResults(results)
        // 此时 progress 是 AllDone
        assertTrue(GenerationBus.progress.value is GenProgress.AllDone)

        GenerationBus.releaseResults()

        // results 被清空
        assertTrue(GenerationBus.results.value.isEmpty())
        // progress 不应被动（按设计，releaseResults 只清 results）
        assertTrue(GenerationBus.progress.value is GenProgress.AllDone)
    }

    @Test
    fun publishProgress_updatesProgressValue() {
        GenerationBus.reset()
        val polling = GenProgress.Polling(variant = 0, total = 1, jobId = "job-1", elapsedSec = 5)
        GenerationBus.publishProgress(polling)
        assertEquals(polling, GenerationBus.progress.value)
    }

    private fun fakeResult(success: Boolean = true): GenResult = GenResult(
        success = success,
        styleKey = "2.5d",
        styleName = "2.5d",
        sizeKey = "竖图",
        generationTimeMs = 1000L,
        errorMessage = if (success) null else "test error"
    )
}
