package com.hoggamers.rankforge.data.di

import com.hoggamers.rankforge.data.cloud.SupabaseTournamentCloudUploadRemoteDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseTournamentCloudUploadRepository
import com.hoggamers.rankforge.data.cloud.TournamentCloudUploadRemoteDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseTournamentCloudRestorationRemoteDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseTournamentCloudRestorationRepository
import com.hoggamers.rankforge.data.cloud.TournamentCloudRestorationRemoteDataSource
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadRepository
import com.hoggamers.rankforge.domain.tournament.UploadTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRepository
import com.hoggamers.rankforge.domain.tournament.RestoreTournamentUseCase
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

    @Binds
    @Singleton
    abstract fun bindTournamentCloudRestorationRemoteDataSource(
        dataSource: SupabaseTournamentCloudRestorationRemoteDataSource,
    ): TournamentCloudRestorationRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindTournamentCloudRestorationRepository(
        repository: SupabaseTournamentCloudRestorationRepository,
    ): TournamentCloudRestorationRepository

    @Binds
    @Singleton
    abstract fun bindTournamentCloudRestorationAction(
        useCase: RestoreTournamentUseCase,
    ): TournamentCloudRestorationAction
}
