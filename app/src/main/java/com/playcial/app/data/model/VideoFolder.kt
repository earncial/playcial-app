package com.playcial.app.data.model

data class VideoFolder(
    val name: String,
    val path: String,
    val videoCount: Int,
    val totalSizeBytes: Long,
    val lastModified: Long,
    val coverVideoUri: String?,
    var isPinned: Boolean = false,
    var isHidden: Boolean = false
)
