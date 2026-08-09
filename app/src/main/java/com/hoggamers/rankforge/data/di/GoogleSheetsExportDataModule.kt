package com.hoggamers.rankforge.data.di

import com.hoggamers.rankforge.data.export.GoogleSheetsExportHttpTransport
import com.hoggamers.rankforge.data.export.GoogleSheetsStandingsExportRemoteDataSource
import com.hoggamers.rankforge.data.export.SupabaseAccessTokenProvider
import com.hoggamers.rankforge.data.export.SupabaseGoogleSheetsStandingsExportRemoteDataSource
import com.hoggamers.rankforge.data.export.SupabaseSessionAccessTokenProvider
import com.hoggamers.rankforge.data.export.UrlConnectionGoogleSheetsExportHttpTransport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleSheetsExportDataModule {
    @Binds
    @Singleton
    abstract fun bindSupabaseAccessTokenProvider(
        provider: SupabaseSessionAccessTokenProvider,
    ): SupabaseAccessTokenProvider

    @Binds
    @Singleton
    abstract fun bindGoogleSheetsExportHttpTransport(
        transport: UrlConnectionGoogleSheetsExportHttpTransport,
    ): GoogleSheetsExportHttpTransport

    @Binds
    @Singleton
    abstract fun bindGoogleSheetsStandingsExportRemoteDataSource(
        dataSource: SupabaseGoogleSheetsStandingsExportRemoteDataSource,
    ): GoogleSheetsStandingsExportRemoteDataSource
}
