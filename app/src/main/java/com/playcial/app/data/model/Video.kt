package com.playcial.app.data.model

data class Video(
    val id: Long,
    val uri: String,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val folderName: String,
    val folderPath: String,
    val dateModified: Long,
    val dateAdded: Long,
    val mimeType: String,
    var isFavorite: Boolean = false,
    var isHidden: Boolean = false,
    var isPinned: Boolean = false,
    var isLocked: Boolean = false,
    var watchProgressMs: Long = 0L
) {
    val orientation: VideoOrientation
        get() = when {
            width == height -> VideoOrientation.SQUARE
            height > width -> VideoOrientation.PORTRAIT
            else -> VideoOrientation.LANDSCAPE
        }

    val resolutionLabel: String
        get() {
            val shortSide = minOf(width, height)
            return when {
                shortSide >= 2160 -> "4K"
                shortSide >= 1440 -> "2K"
                shortSide >= 1080 -> "Full HD"
                shortSide >= 720 -> "HD"
                else -> "SD"
            }
        }

    val progressPercent: Int
        get() = if (durationMs <= 0) 0 else ((watchProgressMs * 100) / durationMs).toInt().coerceIn(0, 100)
}

enum class VideoOrientation { PORTRAIT, LANDSCAPE, SQUARE }
