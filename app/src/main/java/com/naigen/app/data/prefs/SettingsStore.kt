package com.naigen.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * 用户设置持久化。
 *
 * 存储分两层：
 *   - **非敏感设置**：用 Preferences DataStore（`settings.preferences_pb`）
 *     —— baseUrl、风格、尺寸、prompt、主题等
 *   - **敏感设置（API Token）**：用 [EncryptedSharedPreferences] 加密存储
 *     —— AES-256-GCM 加密，文件名 `secure_token.xml`
 *     —— 防 root 备份提取，防 Google 备份外泄（[backup_rules.xml] 已排除）
 *
 * 兼容老版本：
 *   - 旧版本 token 存在 DataStore 的 `api_token` key
 *   - 首次访问新代码会自动迁移：读旧值 → 写加密存储 → 删旧值
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        /** 旧 DataStore 中的 token key（仅用于迁移） */
        val LEGACY_TOKEN = stringPreferencesKey("api_token")
        val BASE_URL = stringPreferencesKey("api_base_url")
        val LAST_STYLE = stringPreferencesKey("last_style")
        val LAST_SIZE = stringPreferencesKey("last_size")
        val LAST_PROMPT = stringPreferencesKey("last_prompt")
        val LAST_NEGATIVE = stringPreferencesKey("last_negative")
        val NSFW_ENABLED = stringPreferencesKey("nsfw_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")  // "system" / "light" / "dark"
        val DYNAMIC_COLOR = stringPreferencesKey("dynamic_color")  // "1" 启用动态配色(默认)
    }

    /** 加密 SharedPreferences 内部用的 key */
    private val secureKeyToken = "api_token"

    // ── 非敏感设置（DataStore） ────────────────────────────────────────────

    val baseUrl: Flow<String> = context.dataStore.data.map { it[Keys.BASE_URL] ?: DEFAULT_BASE_URL }
    val lastStyle: Flow<String> = context.dataStore.data.map { it[Keys.LAST_STYLE] ?: "2.5d" }
    val lastSize: Flow<String> = context.dataStore.data.map { it[Keys.LAST_SIZE] ?: "竖图" }
    val lastPrompt: Flow<String> = context.dataStore.data.map { it[Keys.LAST_PROMPT] ?: "" }
    val lastNegative: Flow<String> = context.dataStore.data.map { it[Keys.LAST_NEGATIVE] ?: "" }
    val nsfwEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NSFW_ENABLED] == "1" }
    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] != "0" }

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

    // ── 敏感设置：API Token（EncryptedSharedPreferences） ───────────────────

    /**
     * 加密 SharedPreferences。延迟初始化，因为：
     *   - 首次创建会生成 MasterKey，耗时几百毫秒
     *   - 测试环境下不会访问 token，可避免初始化开销
     *
     * 用 @Volatile + 双检锁保证只创建一次。
     */
    @Volatile
    private var tokenStoreCache: SharedPreferences? = null

    private fun tokenStore(): SharedPreferences {
        tokenStoreCache?.let { return it }
        synchronized(this) {
            tokenStoreCache?.let { return it }
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val store = EncryptedSharedPreferences.create(
                context,
                "secure_token",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            tokenStoreCache = store
            return store
        }
    }

    /**
     * 一次性迁移：把旧 DataStore 中的 token 迁到加密存储。
     * 已迁移过则空操作。
     */
    private suspend fun migrateTokenFromDataStoreIfNeeded() {
        val store = withContext(Dispatchers.IO) { tokenStore() }
        if (store.contains(secureKeyToken)) return
        // 读旧值
        val legacy = context.dataStore.data.first()[Keys.LEGACY_TOKEN]
        if (!legacy.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                store.edit().putString(secureKeyToken, legacy).apply()
            }
            // 删旧值
            context.dataStore.edit { it.remove(Keys.LEGACY_TOKEN) }
        }
    }

    /**
     * API Token 流。
     *
     * 首次访问会触发迁移（如有）。后续通过 [SharedPreferences.OnSharedPreferenceChangeListener]
     * 监听变化，保证外部修改也能感知。
     */
    val token: Flow<String> = callbackFlow {
        migrateTokenFromDataStoreIfNeeded()
        val store = tokenStore()
        // 发送当前值
        trySendBlocking(store.getString(secureKeyToken, "") ?: "")
        // 监听后续变化
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == secureKeyToken || key == null) {
                trySendBlocking(sp.getString(secureKeyToken, "") ?: "")
            }
        }
        store.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { store.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun setToken(value: String) {
        val trimmed = value.trim()
        withContext(Dispatchers.IO) {
            tokenStore().edit().putString(secureKeyToken, trimmed).apply()
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://nai.sta1n.cn"
    }
}
