package com.naigen.app.data.repository

import com.naigen.app.data.db.HistoryDao
import com.naigen.app.data.db.entities.HistoryEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: HistoryDao) {

    fun observeAll(): Flow<List<HistoryEntity>> = dao.observeAll()

    suspend fun insert(entity: HistoryEntity): Long = dao.insert(entity)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun get(id: Long): HistoryEntity? = dao.get(id)

    suspend fun clearAll() = dao.clearAll()
}
