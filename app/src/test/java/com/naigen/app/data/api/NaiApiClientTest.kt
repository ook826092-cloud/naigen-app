package com.naigen.app.data.api

import com.naigen.app.data.api.dto.CreateJobRequest
import com.naigen.app.data.api.dto.JobStatusResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * [NaiApiClient] 单元测试 —— 使用 MockWebServer 验证 HTTP 行为。
 *
 * 重点验证：
 *   1. createJob / pollJob / fetchBalance 正常路径解析正确
 *   2. HTTP 错误时返回 error 字段
 *   3. **CancellationException 必须被重抛**（修复前的核心 bug）
 *   4. pollJob 网络异常时返回 [JobStatusResponse.STATUS_NETWORK_ERROR]
 *     而非 [JobStatusResponse.STATUS_FAILED] —— 让上层重试
 */
class NaiApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: NaiApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // 用一个短超时的 client，便于测试网络异常
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        client = NaiApiClient(client = okHttp)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val baseUrl: String get() = server.url("/").toString().trimEnd('/')

    // ── createJob ────────────────────────────────────────────────────────────

    @Test
    fun createJob_success_parsesJobId() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"job-abc-123"}""")
        )
        val req = CreateJobRequest(
            token = "STA1N-test", tag = "cat", artist = "default",
            size = "竖图", cost = 1, steps = 28, scale = 6.0, cfg = 0.0,
            sampler = "k_dpmpp_2m_sde", negative = ""
        )
        val resp = client.createJob(baseUrl, req)
        assertEquals("job-abc-123", resp.id)
        assertNull(resp.error)
    }

    @Test
    fun createJob_httpError_returnsErrorResponse() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":"invalid token"}""")
        )
        val req = CreateJobRequest(
            token = "STA1N-bad", tag = "cat", artist = "default",
            size = "竖图", cost = 1, steps = 28, scale = 6.0, cfg = 0.0,
            sampler = "k_dpmpp_2m_sde", negative = ""
        )
        val resp = client.createJob(baseUrl, req)
        assertNull(resp.id)
        assertTrue("error 应包含 HTTP 401", resp.error?.contains("401") == true)
    }

    @Test
    fun createJob_networkException_returnsErrorNotThrows() = runBlocking {
        // 指向一个不存在的端口，强制 connect 异常
        val badClient = NaiApiClient(
            client = OkHttpClient.Builder()
                .connectTimeout(500, TimeUnit.MILLISECONDS)
                .readTimeout(500, TimeUnit.MILLISECONDS)
                .build()
        )
        val req = CreateJobRequest(
            token = "STA1N-test", tag = "cat", artist = "default",
            size = "竖图", cost = 1, steps = 28, scale = 6.0, cfg = 0.0,
            sampler = "k_dpmpp_2m_sde", negative = ""
        )
        val resp = badClient.createJob("http://127.0.0.1:1", req)
        assertNull(resp.id)
        assertTrue("error 应非空", resp.error?.isNotBlank() == true)
    }

    // ── pollJob ──────────────────────────────────────────────────────────────

    @Test
    fun pollJob_doneStatus_returnsImageUrl() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"done","imageUrl":"/files/abc.png"}""")
        )
        val resp = client.pollJob(baseUrl, "job-1", "STA1N-test")
        assertEquals("done", resp.status)
        assertEquals("/files/abc.png", resp.imageUrl)
    }

    @Test
    fun pollJob_failedStatus_returnsFailed() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"failed","error":"content filter"}""")
        )
        val resp = client.pollJob(baseUrl, "job-1", "STA1N-test")
        assertEquals(JobStatusResponse.STATUS_FAILED, resp.status)
        assertEquals("content filter", resp.error)
    }

    @Test
    fun pollJob_httpError_returnsNetworkErrorNotFailed() = runBlocking {
        // 关键测试：HTTP 5xx 应当返回 network_error 而非 failed，
        // 让上层 NaiRepository 可以重试
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("internal error")
        )
        val resp = client.pollJob(baseUrl, "job-1", "STA1N-test")
        assertEquals(
            "HTTP 错误应为 network_error（可重试），而非 failed",
            JobStatusResponse.STATUS_NETWORK_ERROR,
            resp.status
        )
    }

    @Test
    fun pollJob_networkException_returnsNetworkErrorNotFailed() = runBlocking {
        // 关键测试：IOException（网络异常）应当返回 network_error 而非 failed
        val badClient = NaiApiClient(
            client = OkHttpClient.Builder()
                .connectTimeout(500, TimeUnit.MILLISECONDS)
                .readTimeout(500, TimeUnit.MILLISECONDS)
                .build()
        )
        val resp = badClient.pollJob("http://127.0.0.1:1", "job-1", "STA1N-test")
        assertEquals(
            "网络异常应为 network_error（可重试），而非 failed",
            JobStatusResponse.STATUS_NETWORK_ERROR,
            resp.status
        )
        assertNotEquals(
            "网络异常不应被误判为 failed",
            JobStatusResponse.STATUS_FAILED,
            resp.status
        )
    }

    // ── CancellationException 关键测试 ──────────────────────────────────────

    /**
     * **最关键测试**：协程取消时，[NaiApiClient.createJob] 必须重抛
     * [CancellationException]，而不是吞掉它返回错误响应。
     *
     * 修复前：catch (e: Exception) 会捕获 CancellationException，
     *        返回 CreateJobResponse(error=...)，导致 Service 的 cancel() 不生效
     * 修复后：catch (cancellation: CancellationException) { throw cancellation }
     */
    @Test(expected = CancellationException::class)
    fun createJob_whenCancelled_throwsCancellationException(): Unit = runBlocking {
        // 让 MockWebServer 延迟响应，制造可被取消的窗口
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"job-abc"}""")
                .setBodyDelay(2, TimeUnit.SECONDS)
        )
        coroutineScope {
            val deferred = async {
                val req = CreateJobRequest(
                    token = "STA1N-test", tag = "cat", artist = "default",
                    size = "竖图", cost = 1, steps = 28, scale = 6.0, cfg = 0.0,
                    sampler = "k_dpmpp_2m_sde", negative = ""
                )
                client.createJob(baseUrl, req)
            }
            // 等一会儿让请求发出去，再取消
            delay(100)
            cancel()
            deferred.await()
        }
    }

    /**
     * 同上，验证 pollJob 也正确重抛 CancellationException。
     */
    @Test(expected = CancellationException::class)
    fun pollJob_whenCancelled_throwsCancellationException(): Unit = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"running"}""")
                .setBodyDelay(2, TimeUnit.SECONDS)
        )
        coroutineScope {
            val deferred = async {
                client.pollJob(baseUrl, "job-1", "STA1N-test")
            }
            delay(100)
            cancel()
            deferred.await()
        }
    }

    // ── fetchBalance ─────────────────────────────────────────────────────────

    @Test
    fun fetchBalance_success_parsesPoints() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"points":12345}""")
        )
        val resp = client.fetchBalance(baseUrl, "STA1N-test")
        assertEquals(12345, resp.resolvePoints())
        assertNull(resp.error)
    }

    @Test
    fun fetchBalance_returnsFirstNonNullablePointsField() = runBlocking {
        // 同时给多个点数字段，应取第一个非空
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"points":100,"balance":200,"credit":300}""")
        )
        val resp = client.fetchBalance(baseUrl, "STA1N-test")
        assertEquals(100, resp.resolvePoints())
    }

    // ── downloadImage ────────────────────────────────────────────────────────

    @Test
    fun downloadImage_success_returnsBytes() = runBlocking {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(payload))
        )
        val bytes = client.downloadImage(baseUrl, "/files/test.png")
        assertTrue(bytes != null)
        assertEquals(payload.size, bytes!!.size)
        assertEquals(payload.toList(), bytes.toList())
    }

    @Test
    fun downloadImage_httpError_returnsNull() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("not found")
        )
        val bytes = client.downloadImage(baseUrl, "/missing.png")
        assertNull(bytes)
    }

    @Test
    fun downloadImage_acceptsAbsoluteUrl() = runBlocking {
        val payload = "hello".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(payload))
        )
        // 传完整 URL（非相对路径），不应再被拼接 baseUrl
        val fullUrl = server.url("/files/test.png").toString()
        val bytes = client.downloadImage(baseUrl, fullUrl)
        assertTrue(bytes != null)
    }

    @Test
    fun downloadImage_acceptsRelativePath() = runBlocking {
        val payload = "hi".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(payload))
        )
        // 传相对路径，应拼接 baseUrl
        val bytes = client.downloadImage(baseUrl, "/files/rel.png")
        assertFalse("相对路径应能下载到内容", bytes == null)
    }
}
