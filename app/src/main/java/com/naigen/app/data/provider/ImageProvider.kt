package com.naigen.app.data.provider

import com.naigen.app.data.model.GenImage
import com.naigen.app.data.model.GenRequest
import com.naigen.app.data.model.GenResult

/**
 * 生图供应商接口。
 *
 * 不同 provider 的 API 协议差异很大：
 *   - NAI 2：异步 Job（POST /api/jobs → 轮询 → 下载 zip）
 *   - OpenAI 兼容：同步（POST /v1/images/generations → 直接返回 url 或 b64）
 *   - 自定义：用户配置的任意请求模板
 *
 * 这个接口屏蔽差异，让 [com.naigen.app.data.repository.NaiRepository] 不用关心具体协议。
 * 实现：
 *   - [Nai2ImageProvider]：包装现有 NaiApiClient + 轮询逻辑
 *   - OpenAICompatibleImageProvider：（后续实现）调 /v1/images/generations
 */
interface ImageProvider {

    /** provider 类型标识 */
    val type: ProviderType

    /**
     * 执行一次生图请求。
     *
     * @param provider 供应商配置（含 baseUrl / token 等）
     * @param token    API token（从加密存储读取，已脱敏传入）
     * @param request  生图参数
     * @param onProgress 进度回调
     * @return 生成结果（含图片字节）
     */
    suspend fun generate(
        provider: ApiProvider,
        token: String,
        request: GenRequest,
        onProgress: (String) -> Unit = {}
    ): GenResult
}
