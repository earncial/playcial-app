package com.playcial.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_state")
data class VideoStateEntity(
    @PrimaryKey val videoId: Long,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val isPinned: Boolean = false,
    val isLocked: Boolean = false,
    val watchProgressMs: Long = 0L,
    val lastPlayedAt: Long = 0L,
    val playCount: Int = 0
)
