package com.naigen.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.naigen.app.data.db.entities.CustomStyleEntity
import com.naigen.app.data.db.entities.FavoriteEntity
import com.naigen.app.data.db.entities.HistoryEntity

/**
 * 应用数据库。
 *
 * 迁移策略（v2.4.x 起调整）：
 *   - [exportSchema] = true：Room 编译期会把 schema 导出为 JSON，供后续写 Migration 时对照
 *     （schema 输出目录由 app/build.gradle.kts 的 `ksp { arg("room.schemaLocation", ...) }` 配置）
 *   - 升级时**不再**用 `fallbackToDestructiveMigration()`：那种策略会在任何 schema 变更时清空
 *     用户的历史记录 / 收藏 / 自定义风格，对已发布 App 不可接受
 *   - 改用 `fallbackToDestructiveMigrationOnDowngrade()`：仅在用户降级安装时丢数据（罕见场景）
 *   - 后续每次 bump [version] 都必须配套写一个 [Migration] 并注册到 [Builder.addMigrations]
 *
 * 新增字段示例（参考用，未来加列时照此实现）：
 * ```kotlin
 *   val MIGRATION_3_4 = object : Migration(3, 4) {
 *       override fun migrate(db: SupportSQLiteDatabase) {
 *           db.execSQL("ALTER TABLE history ADD COLUMN newCol INTEGER NOT NULL DEFAULT 0")
 *       }
 *   }
 *   Room.databaseBuilder(...).addMigrations(MIGRATION_3_4).build()
 * ```
 */
@Database(
    entities = [HistoryEntity::class, FavoriteEntity::class, CustomStyleEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun customStyleDao(): CustomStyleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "naigen.db"
                )
                    // 已注册的迁移（暂无；下次 bump version 时在此追加）
                    .addMigrations()
                    // 仅降级时丢数据；升级必须走显式 Migration
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
