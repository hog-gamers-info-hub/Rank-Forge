package com.hoggamers.rankforge.data.di

import com.hoggamers.rankforge.data.cloud.SupabaseTournamentCloudUploadRemoteDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseTournamentCloudUploadRepository
import com.hoggamers.rankforge.data.cloud.TournamentCloudUploadRemoteDataSource
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadRepository
import com.hoggamers.rankforge.domain.tournament.UploadTournamentUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudUploadDataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindTournamentCloudUploadRemoteDataSource(
        dataSource: SupabaseTournamentCloudUploadRemoteDataSource,
    ): TournamentCloudUploadRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindTournamentCloudUploadRepository(
        repository: SupabaseTournamentCloudUploadRepository,
    ): TournamentCloudUploadRepository

    @Binds
    @Singleton
    abstract fun bindTournamentCloudUploadAction(
        useCase: UploadTournamentUseCase,
    ): TournamentCloudUploadAction
}
