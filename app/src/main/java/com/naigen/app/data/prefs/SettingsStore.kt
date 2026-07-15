package com.naigen.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 用户设置持久化。基于 Preferences DataStore。
 *
 * 存储内容：
 *   - API token (STA1N-...)
 *   - API base URL（默认 https://API 服务器）
 *   - 上次使用的风格 key
 *   - 上次使用的尺寸 key
 *   - 上次使用的 prompt（用于「快速生图」小组件）
 *   - 是否允许显示 NSFW 风格
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val TOKEN = stringPreferencesKey("api_token")
        val BASE_URL = stringPreferencesKey("api_base_url")
        val LAST_STYLE = stringPreferencesKey("last_style")
        val LAST_SIZE = stringPreferencesKey("last_size")
        val LAST_PROMPT = stringPreferencesKey("last_prompt")
        val LAST_NEGATIVE = stringPreferencesKey("last_negative")
        val NSFW_ENABLED = stringPreferencesKey("nsfw_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")  // "system" / "light" / "dark"
        val DYNAMIC_COLOR = stringPreferencesKey("dynamic_color")  // "1" 启用动态配色(默认)
    }

    val baseUrl: Flow<String> = context.dataStore.data.map { it[Keys.BASE_URL] ?: DEFAULT_BASE_URL }
    val token: Flow<String> = context.dataStore.data.map { it[Keys.TOKEN] ?: "" }
    val lastStyle: Flow<String> = context.dataStore.data.map { it[Keys.LAST_STYLE] ?: "2.5d" }
    val lastSize: Flow<String> = context.dataStore.data.map { it[Keys.LAST_SIZE] ?: "竖图" }
    val lastPrompt: Flow<String> = context.dataStore.data.map { it[Keys.LAST_PROMPT] ?: "" }
    val lastNegative: Flow<String> = context.dataStore.data.map { it[Keys.LAST_NEGATIVE] ?: "" }
    val nsfwEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NSFW_ENABLED] == "1" }
    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] != "0" }

    suspend fun setToken(value: String) = context.dataStore.edit { it[Keys.TOKEN] = value.trim() }
    suspend fun setBaseUrl(value: String) = context.dataStore.edit { it[Keys.BASE_URL] = value.trim() }
    suspend fun setLastStyle(value: String) = context.dataStore.edit { it[Keys.LAST_STYLE] = value }
    suspend fun setLastSize(value: String) = context.dataStore.edit { it[Keys.LAST_SIZE] = value }
    suspend fun setLastPrompt(value: String) = context.dataStore.edit { it[Keys.LAST_PROMPT] = value }
    suspend fun setThemeMode(value: String) = context.dataStore.edit { it[Keys.THEME_MODE] = value }
    suspend fun setLastNegative(value: String) = context.dataStore.edit { it[Keys.LAST_NEGATIVE] = value }
    suspend fun setNsfwEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.NSFW_ENABLED] = if (value) "1" else "0" }

    suspend fun setDynamicColor(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = if (enabled) "1" else "0" }

    companion object {
        const val DEFAULT_BASE_URL = "https://nai.sta1n.cn"
    }
}
