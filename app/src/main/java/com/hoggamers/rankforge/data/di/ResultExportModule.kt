package com.hoggamers.rankforge.data.di

import android.content.Context
import com.hoggamers.rankforge.data.export.AndroidResultDocumentWriter
import com.hoggamers.rankforge.data.export.DefaultResultDownloadCoordinator
import com.hoggamers.rankforge.data.export.ResultDocumentWriter
import com.hoggamers.rankforge.data.export.ResultDownloadCoordinator
import com.hoggamers.rankforge.data.export.ResultFileSaver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ResultExportBindingsModule {
    @Binds
    @Singleton
    abstract fun bindResultDownloadCoordinator(
        coordinator: DefaultResultDownloadCoordinator,
    ): ResultDownloadCoordinator

    @Binds
    @Singleton
    abstract fun bindResultDocumentWriter(
        writer: AndroidResultDocumentWriter,
    ): ResultDocumentWriter
}

@Module
@InstallIn(SingletonComponent::class)
object ResultExportProvidersModule {
    @Provides
    @Singleton
    fun provideResultFileSaver(
        @ApplicationContext context: Context,
    ): ResultFileSaver = ResultFileSaver(context.contentResolver)

    @Provides
    @Singleton
    fun provideAndroidResultDocumentWriter(
        @ApplicationContext context: Context,
    ): AndroidResultDocumentWriter = AndroidResultDocumentWriter(context.contentResolver)
}
