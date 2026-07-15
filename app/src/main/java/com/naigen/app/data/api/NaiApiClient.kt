package com.naigen.app.data.api

import com.naigen.app.data.api.dto.BalanceResponse
import com.naigen.app.data.api.dto.CreateJobRequest
import com.naigen.app.data.api.dto.CreateJobResponse
import com.naigen.app.data.api.dto.JobStatusResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Nai2API 的 OkHttp 单例客户端 + 薄层 service。
 *
 * - 全 App 共享一个 OkHttpClient（连接池复用）
 * - JSON 用 kotlinx.serialization 解析
 * - 所有方法都是 suspend，跑在 [Dispatchers.IO]
 *
 * 端点：
 *   POST {base}/api/jobs          → 创建生成任务
 *   GET  {base}/api/jobs/:id      → 轮询任务状态
 *   GET  {base}/api/images/.../content → 下载图片（直接 GET imageUrl）
 *   GET  {base}/api/me?token=...  → 查询余额
 *
 * 不引入 Retrofit，因为端点只有 3 个 + 1 个二进制下载，OkHttp 直接写更轻量。
 */
object NaiApiClient {

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * 主客户端。超时配置：
     * - connect 15s（建立 TCP+TLS）
     * - read 60s（创建任务/下载图片，Nai2API 偶尔会慢）
     * - write 15s（POST body 不大）
     * - callTimeout 0（不限制整次调用，让上层 Repository 自己控轮询超时）
     */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    /** POST /api/jobs */
    suspend fun createJob(baseUrl: String, body: CreateJobRequest): CreateJobResponse =
        withContext(Dispatchers.IO) {
            val payload = json.encodeToString(CreateJobRequest.serializer(), body)
            val fullUrl = "${baseUrl.trimEnd('/')}/api/jobs"
            com.naigen.app.util.AppLog.network(
                method = "POST",
                url = fullUrl,
                requestHeaders = mapOf("Content-Type" to "application/json"),
                requestBody = payload,
                responseCode = 0,
                responseHeaders = emptyMap(),
                responseBody = ""
            )
            val req = Request.Builder()
                .url(fullUrl)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val respHeaders = resp.headers.associate { it.first to it.second }
                com.naigen.app.util.AppLog.network(
                    method = "POST",
                    url = fullUrl,
                    requestHeaders = mapOf("Content-Type" to "application/json"),
                    requestBody = payload,
                    responseCode = resp.code,
                    responseHeaders = respHeaders,
                    responseBody = raw
                )
                if (!resp.isSuccessful) {
                    val parsed = runCatching {
                        json.decodeFromString(CreateJobResponse.serializer(), raw)
                    }.getOrNull()
                    parsed?.copy(error = parsed.error ?: "HTTP ${resp.code}: $raw")
                        ?: CreateJobResponse(error = "HTTP ${resp.code}: $raw")
                } else {
                    json.decodeFromString(CreateJobResponse.serializer(), raw)
                }
            }
        }

    /** GET /api/jobs/:id?token=... */
    suspend fun pollJob(baseUrl: String, jobId: String, token: String): JobStatusResponse =
        // 日志在 withContext 内部
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/jobs/$jobId?token=${token.encodeUrl()}")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    JobStatusResponse(status = "failed", error = "HTTP ${resp.code}: $raw")
                } else {
                    runCatching {
                        json.decodeFromString(JobStatusResponse.serializer(), raw)
                    }.getOrElse {
                        JobStatusResponse(status = "failed", error = "解析失败: $raw")
                    }
                }
            }
        }

    /** GET /api/me?token=... */
    suspend fun fetchBalance(baseUrl: String, token: String): BalanceResponse =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/me?token=${token.encodeUrl()}")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    BalanceResponse(error = "HTTP ${resp.code}: $raw")
                } else {
                    runCatching {
                        json.decodeFromString(BalanceResponse.serializer(), raw)
                    }.getOrElse { BalanceResponse(error = "解析失败: $raw") }
                }
            }
        }

    /**
     * 下载图片二进制。返回 ByteArray，调用方负责落盘或转 Bitmap。
     *
     * 兼容 imageUrl 既可能以 http 开头也可能以 / 开头的两种格式。
     */
    suspend fun downloadImage(baseUrl: String, imageUrl: String): ByteArray? =
        // 日志在 withContext 内部
        withContext(Dispatchers.IO) {
            val fullUrl = if (imageUrl.startsWith("http")) {
                imageUrl
            } else {
                "${baseUrl.trimEnd('/')}$imageUrl"
            }
            val req = Request.Builder().url(fullUrl).get().build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }

    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
}
