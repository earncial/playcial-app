package com.playcial.app.di

import android.content.Context
import com.playcial.app.data.local.PlaycialDatabase
import com.playcial.app.data.repository.FileOperationsRepository
import com.playcial.app.data.repository.MediaRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PlaycialDatabase {
        return PlaycialDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideMediaRepository(@ApplicationContext context: Context): MediaRepository {
        return MediaRepository(context)
    }

    @Provides
    @Singleton
    fun provideFileOperationsRepository(@ApplicationContext context: Context): FileOperationsRepository {
        return FileOperationsRepository(context)
    }
}
