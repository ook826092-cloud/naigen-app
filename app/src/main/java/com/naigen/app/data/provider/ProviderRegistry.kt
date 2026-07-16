package com.naigen.app.data.provider

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 供应商注册中心 —— 管理内置 + 用户自定义的生图供应商列表。
 *
 * 持久化：
 *   - 内置 NAI 2 API：硬编码在代码里，不入库（每次启动固定存在）
 *   - 用户自定义：序列化为 JSON 存到 DataStore 的 `custom_providers` key
 *   - 当前选中的 provider id：存到 `selected_provider` key
 *
 * 设计参考 kelivo 的 ProviderBalanceService：内置 + 自定义分离，UI 统一展示。
 */
private val Context.providerDataStore by preferencesDataStore(name = "providers")

class ProviderRegistry(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val CUSTOM_PROVIDERS = stringPreferencesKey("custom_providers_json")
        val SELECTED_PROVIDER = stringPreferencesKey("selected_provider_id")
    }

    /**
     * 所有 provider（内置 + 自定义），按内置在前、自定义按创建时间排序。
     */
    val providers: Flow<List<ApiProvider>> = context.providerDataStore.data
        .map { prefs ->
            val custom = parseCustomProviders(prefs[Keys.CUSTOM_PROVIDERS])
            listOf(ApiProvider.BUILTIN_NAI2) + custom.sortedBy { it.createdAt }
        }
        .flowOn(Dispatchers.IO)

    /**
     * 当前选中的 provider id。默认内置 NAI 2 API。
     */
    val selectedProviderId: Flow<String> = context.providerDataStore.data
        .map { it[Keys.SELECTED_PROVIDER] ?: ApiProvider.BUILTIN_NAI2_ID }
        .flowOn(Dispatchers.IO)

    /**
     * 当前选中的 provider 对象。
     */
    val selectedProvider: Flow<ApiProvider> = context.providerDataStore.data
        .map { prefs ->
            val id = prefs[Keys.SELECTED_PROVIDER] ?: ApiProvider.BUILTIN_NAI2_ID
            val custom = parseCustomProviders(prefs[Keys.CUSTOM_PROVIDERS])
            val all = listOf(ApiProvider.BUILTIN_NAI2) + custom
            all.firstOrNull { it.id == id } ?: ApiProvider.BUILTIN_NAI2
        }
        .flowOn(Dispatchers.IO)

    suspend fun setSelectedProvider(id: String) {
        context.providerDataStore.edit { it[Keys.SELECTED_PROVIDER] = id }
    }

    /**
     * 添加一个用户自定义 provider。
     * @return 新 provider 的 id
     */
    suspend fun addCustomProvider(
        name: String,
        type: ProviderType,
        baseUrl: String,
        tokenHeader: String = "authorization",
        tokenPrefix: String = "Bearer ",
        iconUri: String = "",
        note: String = ""
    ): String = withContext(Dispatchers.IO) {
        val id = "custom_${System.currentTimeMillis()}"
        val provider = ApiProvider(
            id = id,
            name = name,
            type = type,
            baseUrl = baseUrl,
            tokenHeader = tokenHeader,
            tokenPrefix = tokenPrefix,
            builtin = false,
            iconUri = iconUri,
            note = note,
            createdAt = System.currentTimeMillis()
        )
        context.providerDataStore.edit { prefs ->
            val current = parseCustomProviders(prefs[Keys.CUSTOM_PROVIDERS])
            prefs[Keys.CUSTOM_PROVIDERS] = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(ApiProvider.serializer()),
                current + provider
            )
        }
        id
    }

    /**
     * 更新一个 provider（内置只能改 token 相关；自定义可全改）。
     */
    suspend fun updateProvider(provider: ApiProvider) = withContext(Dispatchers.IO) {
        if (provider.builtin) {
            // 内置 provider 不入库，只更新选中的 id（如果切换到它）
            return@withContext
        }
        context.providerDataStore.edit { prefs ->
            val current = parseCustomProviders(prefs[Keys.CUSTOM_PROVIDERS])
            val updated = current.map { if (it.id == provider.id) provider else it }
            prefs[Keys.CUSTOM_PROVIDERS] = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(ApiProvider.serializer()),
                updated
            )
        }
    }

    /**
     * 删除一个用户自定义 provider。
     * 如果删除的是当前选中的，自动回退到内置 NAI 2 API。
     */
    suspend fun deleteProvider(id: String) = withContext(Dispatchers.IO) {
        context.providerDataStore.edit { prefs ->
            val current = parseCustomProviders(prefs[Keys.CUSTOM_PROVIDERS])
            val updated = current.filterNot { it.id == id }
            prefs[Keys.CUSTOM_PROVIDERS] = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(ApiProvider.serializer()),
                updated
            )
            // 如果删的是当前选中的，回退到内置
            if (prefs[Keys.SELECTED_PROVIDER] == id) {
                prefs[Keys.SELECTED_PROVIDER] = ApiProvider.BUILTIN_NAI2_ID
            }
        }
    }

    private fun parseCustomProviders(jsonStr: String?): List<ApiProvider> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(ApiProvider.serializer()),
                jsonStr
            )
        }.getOrDefault(emptyList())
    }
}
