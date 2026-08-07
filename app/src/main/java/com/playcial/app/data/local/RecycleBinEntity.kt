package com.playcial.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recycle_bin")
data class RecycleBinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalPath: String,
    val trashPath: String,
    val displayName: String,
    val sizeBytes: Long,
    val deletedAt: Long
)
