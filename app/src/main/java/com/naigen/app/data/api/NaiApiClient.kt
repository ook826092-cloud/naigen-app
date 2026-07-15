package com.naigen.app.data.api

import com.naigen.app.data.api.dto.BalanceResponse
import com.naigen.app.data.api.dto.CreateJobRequest
import com.naigen.app.data.api.dto.CreateJobResponse
import com.naigen.app.data.api.dto.JobStatusResponse
import com.naigen.app.util.AppLog
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
 * Nai2API 的 OkHttp 单例客户端。
 */
object NaiApiClient {

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

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
            } catch (e: Exception) {
                AppLog.e("NaiApiClient", "createJob() 异常: ${e.javaClass.simpleName}: ${e.message}", e)
                CreateJobResponse(error = "请求异常: ${e.message}")
            }
        }

    /** GET /api/jobs/:id?token=... */
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
                        JobStatusResponse(status = "failed", error = "HTTP ${resp.code}: $raw")
                    } else {
                        runCatching {
                            json.decodeFromString(JobStatusResponse.serializer(), raw)
                        }.getOrElse {
                            AppLog.e("NaiApiClient", "pollJob() JSON 解析失败: $raw")
                            JobStatusResponse(status = "failed", error = "解析失败: $raw")
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.e("NaiApiClient", "pollJob() 异常: ${e.message}", e)
                JobStatusResponse(status = "failed", error = "请求异常: ${e.message}")
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
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        AppLog.e("NaiApiClient", "downloadImage() 失败: HTTP ${resp.code}")
                        null
                    } else {
                        val bytes = resp.body?.bytes()
                        AppLog.i("NaiApiClient", "downloadImage() 成功: ${bytes?.size ?: 0} bytes")
                        bytes
                    }
                }
            } catch (e: Exception) {
                AppLog.e("NaiApiClient", "downloadImage() 异常: ${e.message}", e)
                null
            }
        }

    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
}
