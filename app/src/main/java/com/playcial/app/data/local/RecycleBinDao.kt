package com.playcial.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecycleBinDao {

    @Query("SELECT * FROM recycle_bin ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<RecycleBinEntity>>

    @Insert
    suspend fun insert(entry: RecycleBinEntity): Long

    @Delete
    suspend fun delete(entry: RecycleBinEntity)

    @Query("SELECT * FROM recycle_bin WHERE deletedAt < :cutoff")
    suspend fun expiredBefore(cutoff: Long): List<RecycleBinEntity>
}
