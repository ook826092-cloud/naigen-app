package com.naigen.app

import android.app.Application
import android.graphics.drawable.Drawable
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.naigen.app.data.api.NaiApiClient
import com.naigen.app.data.db.AppDatabase
import com.naigen.app.data.prefs.SettingsStore
import com.naigen.app.data.repository.FavoritesRepository
import com.naigen.app.data.repository.HistoryRepository
import com.naigen.app.data.repository.NaiRepository

/**
 * 应用入口。承担：
 *  - 单例 OkHttp / NaiRepository
 *  - 单例 Room Database + Dao → Repositories
 *  - 单例 DataStore
 *  - 全局 Coil ImageLoader（性能优化：内存 25% + 磁盘 50MB 缓存）
 *  - 通知渠道
 *
 * 不引入 Hilt，避免 ksp/kapt 配置复杂度。
 */
class NaiApplication : Application(), ImageLoaderFactory {

    lateinit var settingsStore: SettingsStore
        private set

    /** API 客户端实例。生产用单例，便于共享 OkHttp 连接池给 Coil ImageLoader。 */
    lateinit var naiApiClient: NaiApiClient
        private set

    lateinit var naiRepository: NaiRepository
        private set

    lateinit var historyRepository: HistoryRepository
        private set

    lateinit var favoritesRepository: FavoritesRepository
        private set

    lateinit var customStyleRepository: com.naigen.app.data.repository.CustomStyleRepository
        private set

    /** 供应商注册中心（内置 NAI 2 API + 用户自定义） */
    lateinit var providerRegistry: com.naigen.app.data.provider.ProviderRegistry
        private set

    override fun onCreate() {
        super.onCreate()

        // 0) 日志初始化（最先做）
        com.naigen.app.util.AppLog.init(this)
        com.naigen.app.util.AppLog.i("App", "NaiGen 启动")

        // 1) SettingsStore（DataStore + 加密 token）
        settingsStore = SettingsStore(this)

        // 2) 供应商注册中心（内置 NAI 2 API + 用户自定义 provider）
        providerRegistry = com.naigen.app.data.provider.ProviderRegistry(this)

        // 3) API client + Repository
        //    使用 NaiApiClient.shared 单例，与 Coil 共享 OkHttp 连接池
        naiApiClient = NaiApiClient.shared
        naiRepository = NaiRepository(naiApiClient, settingsStore)

        // 4) Room DB
        val db = AppDatabase.get(this)
        historyRepository = HistoryRepository(db.historyDao())
        favoritesRepository = FavoritesRepository(db.favoritesDao())
        customStyleRepository = com.naigen.app.data.repository.CustomStyleRepository(db.customStyleDao())

        // 5) 通知渠道（委托给 NotificationService，集中管理渠道 + 权限 + 诊断）
        com.naigen.app.service.NotificationService.createChannels(this)
    }

    /**
     * 全局 Coil ImageLoader 配置。
     *
     * 性能优化点：
     *  - 内存缓存：取 app maxMemory 的 25%（默认 25%，这里显式设置以示重视）
     *  - 磁盘缓存：50MB，存到 cacheDir/image_cache
     *  - 内存缓存策略开启，磁盘缓存策略开启
     *  - 共享 OkHttp 客户端（复用连接池）
     *  - crossfade 150ms（避免突兀加载）
     *
     * 加载 ByteArray 缩略图时也会走内存缓存，列表滚动更流畅。
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(naiApiClient.client)
            .crossfade(150)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    companion object {
        const val CHANNEL_GENERATION = "ch_generation"
        const val CHANNEL_RESULT = "ch_result"
        const val CHANNEL_ISLAND = "ch_island"
    }
}

val Application.app: NaiApplication
    get() = this as NaiApplication
