package com.naigen.app.data.repository

import com.naigen.app.data.db.CustomStyleDao
import com.naigen.app.data.db.entities.CustomStyleEntity
import com.naigen.app.data.model.NsfwLevel
import com.naigen.app.data.model.StylePreset
import com.naigen.app.data.model.StyleParams
import com.naigen.app.data.model.StyleSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CustomStyleRepository(private val dao: CustomStyleDao) {

    fun observeAll(): Flow<List<CustomStyleEntity>> = dao.observeAll()

    /**
     * 把自定义风格转成 StylePreset，与内置/社区风格统一处理。
     */
    fun observeAsPresets(): Flow<List<StylePreset>> = dao.observeAll().map { list ->
        list.map { e ->
            StylePreset(
                id = e.id.toInt(),
                key = "custom_${e.id}",
                name = e.name,
                source = StyleSource.COMMUNITY, // 复用社区类型，避免再加 enum
                provider = "自定义",
                nsfw = NsfwLevel.SAFE,
                artistString = e.artistString,
                positivePrefix = e.positivePrefix,
                negativePrompt = e.negativePrompt,
                params = StyleParams(
                    steps = e.steps,
                    sampler = e.sampler,
                    cfg = e.cfg,
                    scale = e.scale
                )
            )
        }
    }

    suspend fun insert(entity: CustomStyleEntity): Long = dao.insert(entity)
    suspend fun update(entity: CustomStyleEntity) = dao.update(entity)
    suspend fun delete(entity: CustomStyleEntity) = dao.delete(entity)
    suspend fun get(id: Long): CustomStyleEntity? = dao.get(id)
}
