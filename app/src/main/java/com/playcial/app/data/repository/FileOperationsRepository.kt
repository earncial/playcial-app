package com.playcial.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.playcial.app.data.local.PlaycialDatabase
import com.playcial.app.data.local.RecycleBinEntity
import com.playcial.app.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class FileOpResult {
    data class Success(val message: String) : FileOpResult()
    data class Failure(val message: String) : FileOpResult()
}

class FileOperationsRepository(private val context: Context) {

    private val db = PlaycialDatabase.getInstance(context)
    private val recycleBinDir: File by lazy {
        File(context.filesDir, "recycle_bin").apply { mkdirs() }
    }

    suspend fun rename(video: Video, newDisplayName: String): FileOpResult = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(video.uri)
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, newDisplayName)
            }
            val rows = context.contentResolver.update(uri, values, null, null)
            if (rows > 0) FileOpResult.Success("Renamed to $newDisplayName")
            else FileOpResult.Failure("Could not rename file")
        } catch (e: Exception) {
            FileOpResult.Failure(e.message ?: "Rename failed")
        }
    }

    /** Soft-delete: moves the physical file into an app-private recycle bin folder and
     * removes it from MediaStore, recording enough info to restore it within 30 days. */
    suspend fun moveToRecycleBin(video: Video): FileOpResult = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(video.folderPath, video.displayName)
            if (!sourceFile.exists()) return@withContext FileOpResult.Failure("File not found")

            val trashFile = File(recycleBinDir, "${video.id}_${video.displayName}")
            sourceFile.copyTo(trashFile, overwrite = true)

            val uri = Uri.parse(video.uri)
            val deletedRows = context.contentResolver.delete(uri, null, null)

            if (deletedRows > 0) {
                sourceFile.delete()
                db.recycleBinDao().insert(
                    RecycleBinEntity(
                        originalPath = sourceFile.absolutePath,
                        trashPath = trashFile.absolutePath,
                        displayName = video.displayName,
                        sizeBytes = video.sizeBytes,
                        deletedAt = System.currentTimeMillis()
                    )
                )
                FileOpResult.Success("Moved to Recently Deleted")
            } else {
                trashFile.delete()
                FileOpResult.Failure("Could not delete file")
            }
        } catch (e: Exception) {
            FileOpResult.Failure(e.message ?: "Delete failed")
        }
    }

    suspend fun restoreFromRecycleBin(entry: RecycleBinEntity): FileOpResult = withContext(Dispatchers.IO) {
        try {
            val trashFile = File(entry.trashPath)
            val restoredFile = File(entry.originalPath)
            if (!trashFile.exists()) return@withContext FileOpResult.Failure("Backup missing")

            trashFile.copyTo(restoredFile, overwrite = true)

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, entry.displayName)
                put(MediaStore.Video.Media.DATA, restoredFile.absolutePath)
            }
            context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

            trashFile.delete()
            db.recycleBinDao().delete(entry)
            FileOpResult.Success("Restored ${entry.displayName}")
        } catch (e: Exception) {
            FileOpResult.Failure(e.message ?: "Restore failed")
        }
    }

    suspend fun permanentlyDelete(entry: RecycleBinEntity): FileOpResult = withContext(Dispatchers.IO) {
        File(entry.trashPath).delete()
        db.recycleBinDao().delete(entry)
        FileOpResult.Success("Permanently deleted")
    }

    suspend fun copyTo(video: Video, targetDir: File): FileOpResult = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(video.folderPath, video.displayName)
            if (!sourceFile.exists()) return@withContext FileOpResult.Failure("File not found")
            targetDir.mkdirs()
            val destFile = File(targetDir, video.displayName)
            sourceFile.copyTo(destFile, overwrite = false)
            insertIntoMediaStore(destFile)
            FileOpResult.Success("Copied to ${targetDir.name}")
        } catch (e: Exception) {
            FileOpResult.Failure(e.message ?: "Copy failed")
        }
    }

    suspend fun moveTo(video: Video, targetDir: File): FileOpResult = withContext(Dispatchers.IO) {
        val copyResult = copyTo(video, targetDir)
        if (copyResult is FileOpResult.Success) {
            val sourceFile = File(video.folderPath, video.displayName)
            sourceFile.delete()
            context.contentResolver.delete(Uri.parse(video.uri), null, null)
            FileOpResult.Success("Moved to ${targetDir.name}")
        } else {
            copyResult
        }
    }

    fun buildShareIntent(video: Video): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = video.mimeType
            putExtra(Intent.EXTRA_STREAM, Uri.parse(video.uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildShareChooser(video: Video): Intent =
        Intent.createChooser(buildShareIntent(video), "Share ${video.displayName}")

    private fun insertIntoMediaStore(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.DATA, file.absolutePath)
        }
        context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
    }
}
