package com.naigen.app.data.repository

import com.naigen.app.data.db.FavoritesDao
import com.naigen.app.data.db.entities.FavoriteEntity
import kotlinx.coroutines.flow.Flow

class FavoritesRepository(private val dao: FavoritesDao) {

    fun observeAll(): Flow<List<FavoriteEntity>> = dao.observeAll()

    fun observeByTag(tag: String): Flow<List<FavoriteEntity>> = dao.observeByTag(tag)

    suspend fun insert(entity: FavoriteEntity): Long = dao.insert(entity)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun get(id: Long): FavoriteEntity? = dao.get(id)
}
