package com.naigen.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.naigen.app.data.db.entities.CustomStyleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomStyleDao {
    @Query("SELECT * FROM custom_styles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CustomStyleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CustomStyleEntity): Long

    @Update
    suspend fun update(entity: CustomStyleEntity)

    @Delete
    suspend fun delete(entity: CustomStyleEntity)

    @Query("SELECT * FROM custom_styles WHERE id = :id")
    suspend fun get(id: Long): CustomStyleEntity?
}
