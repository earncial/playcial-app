package com.playcial.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VideoStateEntity::class, RecycleBinEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PlaycialDatabase : RoomDatabase() {
    abstract fun videoStateDao(): VideoStateDao
    abstract fun recycleBinDao(): RecycleBinDao

    companion object {
        @Volatile private var instance: PlaycialDatabase? = null

        fun getInstance(context: Context): PlaycialDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlaycialDatabase::class.java,
                    "playcial.db"
                ).build().also { instance = it }
            }
        }
    }
}
