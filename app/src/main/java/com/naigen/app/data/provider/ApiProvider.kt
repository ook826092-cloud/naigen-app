package com.naigen.app.data.provider

import kotlinx.serialization.Serializable

/**
 * 生图供应商类型。
 *
 * - NAI_2：内置的 NAI 2 API（基于 /api/jobs 轮询，仅此一个内置）
 * - OPENAI_COMPATIBLE：用户自定义的 OpenAI 兼容生图（/v1/images/generations）
 * - CUSTOM：完全自定义请求（用户配置 method/url/headers/body 模板）
 */
@Serializable
enum class ProviderType { NAI_2, OPENAI_COMPATIBLE, CUSTOM }

/**
 * 一个生图供应商的完整定义。
 *
 * - 内置 NAI 2 API：[id] = "builtin_nai2"，[builtin] = true，不可删除
 * - 用户自定义：[id] = UUID，[builtin] = false，可增删改
 *
 * [iconUri] 支持三种形态：
 *   - 空/null：用默认图标
 *   - "material:<name>"：内置 Material 图标名（如 "material:AutoAwesome"）
 *   - "content://..." 或 "file://..."：用户从相册选择的图标 URI
 */
@Serializable
data class ApiProvider(
    val id: String,
    val name: String,
    val type: ProviderType,
    val baseUrl: String,
    /** Token 字段名（NAI 用 "token"，OpenAI 用 "Authorization: Bearer"） */
    val tokenHeader: String = "Authorization",
    /** Token 前缀（如 "Bearer "），空表示不加前缀 */
    val tokenPrefix: String = "Bearer ",
    /** 是否内置（内置 provider 不可删除，只能改 token） */
    val builtin: Boolean = false,
    /** 自定义图标 URI（空 = 用默认图标） */
    val iconUri: String = "",
    /** 备注（用户给自定义 provider 写的说明） */
    val note: String = "",
    /** 创建时间（用户自定义 provider 排序用） */
    val createdAt: Long = 0L
) {
    companion object {
        /** 内置 NAI 2 API 的固定 id */
        const val BUILTIN_NAI2_ID = "builtin_nai2"

        /**
         * 内置 NAI 2 API provider。
         * baseUrl 固定指向教程服务器，用户只能改 token。
         */
        val BUILTIN_NAI2 = ApiProvider(
            id = BUILTIN_NAI2_ID,
            name = "NAI 2 API",
            type = ProviderType.NAI_2,
            baseUrl = "https://nai.sta1n.cn",
            tokenHeader = "Authorization",
            tokenPrefix = "Bearer ",
            builtin = true,
            iconUri = "",
            note = "内置 NovelAI Diffusion 生图",
            createdAt = 0L
        )
    }
}
