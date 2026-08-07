package com.playcial.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.playcial.app.data.local.PlaycialDatabase
import com.playcial.app.data.local.VideoStateEntity
import com.playcial.app.data.model.Video
import com.playcial.app.data.model.VideoFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepository(private val context: Context) {

    private val db = PlaycialDatabase.getInstance(context)

    suspend fun queryAllVideos(): List<Video> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<Video>()

        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val path = cursor.getString(dataCol) ?: continue
                val parentFile = File(path).parentFile
                val contentUri = ContentUris.withAppendedId(collection, id)

                videos.add(
                    Video(
                        id = id,
                        uri = contentUri.toString(),
                        displayName = cursor.getString(nameCol) ?: "Unknown",
                        durationMs = cursor.getLong(durationCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        folderName = parentFile?.name ?: "Unknown",
                        folderPath = parentFile?.absolutePath ?: "",
                        dateModified = cursor.getLong(modifiedCol) * 1000L,
                        dateAdded = cursor.getLong(addedCol) * 1000L,
                        mimeType = cursor.getString(mimeCol) ?: "video/*"
                    )
                )
            }
        }
        applyPersistedState(videos)
    }

    private suspend fun applyPersistedState(videos: List<Video>): List<Video> {
        val snapshot = db.videoStateDao().getAllSnapshot()
        val byId = snapshot.associateBy { it.videoId }
        return videos.map { video ->
            val state = byId[video.id] ?: return@map video
            video.copy(
                isFavorite = state.isFavorite,
                isHidden = state.isHidden,
                isPinned = state.isPinned,
                isLocked = state.isLocked,
                watchProgressMs = state.watchProgressMs
            )
        }
    }

    suspend fun toggleFavorite(video: Video) = withContext(Dispatchers.IO) {
        val current = db.videoStateDao().get(video.id)
        db.videoStateDao().upsert(
            (current ?: VideoStateEntity(video.id)).copy(isFavorite = !(current?.isFavorite ?: false))
        )
    }

    suspend fun setHidden(video: Video, hidden: Boolean) = withContext(Dispatchers.IO) {
        val current = db.videoStateDao().get(video.id)
        db.videoStateDao().upsert((current ?: VideoStateEntity(video.id)).copy(isHidden = hidden))
    }

    suspend fun setPinned(video: Video, pinned: Boolean) = withContext(Dispatchers.IO) {
        val current = db.videoStateDao().get(video.id)
        db.videoStateDao().upsert((current ?: VideoStateEntity(video.id)).copy(isPinned = pinned))
    }

    suspend fun setLocked(video: Video, locked: Boolean) = withContext(Dispatchers.IO) {
        val current = db.videoStateDao().get(video.id)
        db.videoStateDao().upsert((current ?: VideoStateEntity(video.id)).copy(isLocked = locked))
    }

    suspend fun updateWatchProgress(video: Video, positionMs: Long) = withContext(Dispatchers.IO) {
        val current = db.videoStateDao().get(video.id)
        db.videoStateDao().upsert(
            (current ?: VideoStateEntity(video.id)).copy(
                watchProgressMs = positionMs,
                lastPlayedAt = System.currentTimeMillis(),
                playCount = (current?.playCount ?: 0) + 1
            )
        )
    }

    suspend fun queryFolders(): List<VideoFolder> = withContext(Dispatchers.IO) {
        val videos = queryAllVideos()
        videos.groupBy { it.folderPath }
            .map { (path, list) ->
                VideoFolder(
                    name = list.first().folderName,
                    path = path,
                    videoCount = list.size,
                    totalSizeBytes = list.sumOf { it.sizeBytes },
                    lastModified = list.maxOf { it.dateModified },
                    coverVideoUri = list.maxByOrNull { it.dateModified }?.uri
                )
            }
            .sortedByDescending { it.lastModified }
    }
}
