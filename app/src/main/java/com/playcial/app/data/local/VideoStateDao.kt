package com.playcial.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoStateDao {

    @Query("SELECT * FROM video_state")
    fun observeAll(): Flow<List<VideoStateEntity>>

    @Query("SELECT * FROM video_state")
    suspend fun getAllSnapshot(): List<VideoStateEntity>

    @Query("SELECT * FROM video_state WHERE videoId = :videoId")
    suspend fun get(videoId: Long): VideoStateEntity?

    @Upsert
    suspend fun upsert(state: VideoStateEntity)

    @Query("UPDATE video_state SET isFavorite = :value WHERE videoId = :videoId")
    suspend fun setFavorite(videoId: Long, value: Boolean)

    @Query("UPDATE video_state SET isHidden = :value WHERE videoId = :videoId")
    suspend fun setHidden(videoId: Long, value: Boolean)

    @Query("UPDATE video_state SET isPinned = :value WHERE videoId = :videoId")
    suspend fun setPinned(videoId: Long, value: Boolean)

    @Query("UPDATE video_state SET isLocked = :value WHERE videoId = :videoId")
    suspend fun setLocked(videoId: Long, value: Boolean)

    @Query("UPDATE video_state SET watchProgressMs = :progressMs, lastPlayedAt = :playedAt WHERE videoId = :videoId")
    suspend fun updateProgress(videoId: Long, progressMs: Long, playedAt: Long)

    @Query("DELETE FROM video_state WHERE videoId = :videoId")
    suspend fun delete(videoId: Long)
}
