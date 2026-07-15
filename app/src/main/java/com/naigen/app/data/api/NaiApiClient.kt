package com.naigen.app.data.api

import com.naigen.app.data.api.dto.BalanceResponse
import com.naigen.app.data.api.dto.CreateJobRequest
import com.naigen.app.data.api.dto.CreateJobResponse
import com.naigen.app.data.api.dto.JobStatusResponse
import com.naigen.app.util.AppLog
import kotlinx.coroutines.CancellationException
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
 * API 的 OkHttp 客户端。
 *
 * 设计要点：
 *   - 抽成 `class` 而非 `object`，便于在单测中注入指向 MockWebServer 的 [OkHttpClient]
 *   - 提供 [shared] 单例供生产代码使用（[NaiApplication] 会通过它共享给 Coil ImageLoader）
 *
 * 关键 bug 修复（vs 旧 `object NaiApiClient`）：
 *   - 旧代码用 `catch (e: Exception)` 把 [CancellationException] 一起吞掉，
 *     导致 Service 取消协程时网络请求不会真正中断 → 结构化并发被破坏。
 *     现在显式重抛 [CancellationException]。
 *   - 旧代码把 IOException（网络异常）和 API 返回 failed 混为同一个 `status = "failed"`，
 *     弱网下一次轮询失败就会误判任务失败。现在用 [JobStatusResponse.STATUS_NETWORK_ERROR]
 *     独立标记网络异常，让 [NaiRepository] 可以重试而不是直接判失败。
 */
class NaiApiClient(
    /** 注入的 OkHttpClient；默认为 [sharedClient]。测试时可指向 MockWebServer。 */
    client: OkHttpClient? = null,
    /** 注入的 Json；默认为 [sharedJson]。 */
    json: Json? = null
) {
    val client: OkHttpClient = client ?: sharedClient
    val json: Json = json ?: sharedJson

    /**
     * 下载专用 client：生图结果图较大，弱网下 60s 读取超时易断流，
     * 这里单独放宽 readTimeout 到 5 分钟。其余超时与主 client 一致。
     */
    private val downloadClient: OkHttpClient by lazy {
        client.newBuilder()
            .readTimeout(300, TimeUnit.SECONDS)
            .build()
    }

    /** POST /api/jobs */
    suspend fun createJob(baseUrl: String, body: CreateJobRequest): CreateJobResponse =
        withContext(Dispatchers.IO) {
            val payload = json.encodeToString(CreateJobRequest.serializer(), body)
            val fullUrl = "${baseUrl.trimEnd('/')}/api/jobs"
            val startTime = System.currentTimeMillis()

            AppLog.d("NaiApiClient", "createJob() → POST $fullUrl")
            AppLog.d("NaiApiClient", "createJob() request body: $payload")

            val req = Request.Builder()
                .url(fullUrl)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            try {
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    val duration = System.currentTimeMillis() - startTime
                    val respHeaders = resp.headers.associate { it.first to it.second }

                    AppLog.network(
                        method = "POST",
                        url = fullUrl,
                        requestHeaders = mapOf("Content-Type" to "application/json"),
                        requestBody = payload,
                        responseCode = resp.code,
                        responseHeaders = respHeaders,
                        responseBody = raw,
                        durationMs = duration
                    )

                    if (!resp.isSuccessful) {
                        AppLog.e("NaiApiClient", "createJob() 失败: HTTP ${resp.code}")
                        val parsed = runCatching {
                            json.decodeFromString(CreateJobResponse.serializer(), raw)
                        }.getOrNull()
                        parsed?.copy(error = parsed.error ?: "HTTP ${resp.code}: $raw")
                            ?: CreateJobResponse(error = "HTTP ${resp.code}: $raw")
                    } else {
                        val result = json.decodeFromString(CreateJobResponse.serializer(), raw)
                        AppLog.i("NaiApiClient", "createJob() 成功: jobId=${result.id}")
                        result
                    }
                }
            } catch (cancellation: CancellationException) {
                // 关键修复：不能吞掉取消信号，否则结构化并发失效
                throw cancellation
            } catch (e: Exception) {
                AppLog.e("NaiApiClient", "createJob() 异常: ${e.javaClass.simpleName}: ${e.message}", e)
                CreateJobResponse(error = "请求异常: ${e.message}")
            }
        }

    /**
     * GET /api/jobs/:id?token=...
     *
     * 失败语义：
     *   - HTTP 失败 / API 显式 failed：返回 [JobStatusResponse.STATUS_FAILED]
     *   - 网络异常（IOException）：返回 [JobStatusResponse.STATUS_NETWORK_ERROR]
     *     → [NaiRepository] 应当重试，而不是直接判任务失败
     */
    suspend fun pollJob(baseUrl: String, jobId: String, token: String): JobStatusResponse =
        withContext(Dispatchers.IO) {
            val fullUrl = "${baseUrl.trimEnd('/')}/api/jobs/$jobId?token=${token.encodeUrl()}"
            AppLog.d("NaiApiClient", "pollJob() → GET $fullUrl")

            val req = Request.Builder().url(fullUrl).get().build()
            try {
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    AppLog.d("NaiApiClient", "pollJob() 响应: ${resp.code} body=$raw")

                    if (!resp.isSuccessful) {
                        AppLog.w("NaiApiClient", "pollJob() HTTP ${resp.code}")
                        JobStatusResponse(
                            status = JobStatusResponse.STATUS_NETWORK_ERROR,
                            error = "HTTP ${resp.code}: $raw"
                        )
                    } else {
                        runCatching {
                            json.decodeFromString(JobStatusResponse.serializer(), raw)
                        }.getOrElse {
                            AppLog.e("NaiApiClient", "pollJob() JSON 解析失败: $raw")
                            JobStatusResponse(
                                status = JobStatusResponse.STATUS_NETWORK_ERROR,
                                error = "解析失败: $raw"
                            )
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                // 网络异常（IOException 等）→ 让上层重试，而非判任务失败
                AppLog.w("NaiApiClient", "pollJob() 网络异常（可重试）: ${e.message}")
                JobStatusResponse(
                    status = JobStatusResponse.STATUS_NETWORK_ERROR,
                    error = "请求异常: ${e.message}"
                )
            }
        }

    /** GET /api/me?token=... */
    suspend fun fetchBalance(baseUrl: String, token: String): BalanceResponse =
        withContext(Dispatchers.IO) {
            val fullUrl = "${baseUrl.trimEnd('/')}/api/me?token=${token.encodeUrl()}"
            AppLog.d("NaiApiClient", "fetchBalance() → GET $fullUrl")

            val req = Request.Builder().url(fullUrl).get().build()
            try {
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    AppLog.d("NaiApiClient", "fetchBalance() 响应: ${resp.code} body=$raw")

                    if (!resp.isSuccessful) {
                        BalanceResponse(error = "HTTP ${resp.code}: $raw")
                    } else {
                        runCatching {
                            json.decodeFromString(BalanceResponse.serializer(), raw)
                        }.getOrElse { BalanceResponse(error = "解析失败: $raw") }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                AppLog.e("NaiApiClient", "fetchBalance() 异常: ${e.message}", e)
                BalanceResponse(error = "请求异常: ${e.message}")
            }
        }

    /** 下载图片二进制 */
    suspend fun downloadImage(baseUrl: String, imageUrl: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val fullUrl = if (imageUrl.startsWith("http")) imageUrl else "${baseUrl.trimEnd('/')}$imageUrl"
            AppLog.d("NaiApiClient", "downloadImage() → GET $fullUrl")

            val req = Request.Builder().url(fullUrl).get().build()
            try {
                downloadClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        AppLog.e("NaiApiClient", "downloadImage() 失败: HTTP ${resp.code}")
                        null
                    } else {
                        val bytes = resp.body?.bytes()
                        AppLog.i("NaiApiClient", "downloadImage() 成功: ${bytes?.size ?: 0} bytes")
                        bytes
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                AppLog.e("NaiApiClient", "downloadImage() 异常: ${e.message}", e)
                null
            }
        }

    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, "UTF-8")

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** 共享 Json 实例（容忍未知字段，NaiApi 各接口字段并不完全统一） */
        val sharedJson: Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            isLenient = true
        }

        /** 共享 OkHttpClient（生产单例，Coil ImageLoader 也复用此连接池） */
        val sharedClient: OkHttpClient by lazy {
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

        /**
         * 生产环境单例。[NaiApplication] 与 [NaiRepository] 都使用它。
         */
        val shared: NaiApiClient by lazy { NaiApiClient() }
    }
}
