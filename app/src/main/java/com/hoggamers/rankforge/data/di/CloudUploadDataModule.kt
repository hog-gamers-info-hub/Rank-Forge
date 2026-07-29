package com.hoggamers.rankforge.data.di

import com.hoggamers.rankforge.data.cloud.SupabaseTournamentCloudUploadRemoteDataSource
import com.hoggamers.rankforge.data.cloud.ScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.SupabaseScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.SupabaseTournamentCloudUploadRepository
import com.hoggamers.rankforge.data.cloud.TournamentCloudUploadRemoteDataSource
import com.hoggamers.rankforge.data.cloud.DraftMatchCloudSyncRemoteDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseDraftMatchCloudSyncRemoteDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseDraftMatchCloudSyncRepository
import com.hoggamers.rankforge.data.cloud.FinalizedMatchCloudSyncRemoteDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseFinalizedMatchCloudSyncRemoteDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseFinalizedMatchCloudSyncRepository
import com.hoggamers.rankforge.data.cloud.SupabaseProtectedMatchCorrectionAction
import com.hoggamers.rankforge.data.cloud.SupabaseTournamentCloudRestorationRemoteDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseTournamentCloudRestorationRepository
import com.hoggamers.rankforge.data.cloud.TournamentCloudRestorationRemoteDataSource
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadRepository
import com.hoggamers.rankforge.domain.tournament.UploadTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRepository
import com.hoggamers.rankforge.domain.tournament.RestoreTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncRepository
import com.hoggamers.rankforge.domain.tournament.SyncDraftMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncRepository
import com.hoggamers.rankforge.domain.tournament.SyncFinalizedMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ProtectedMatchCorrectionAction
import com.hoggamers.rankforge.data.cloud.MatchCloudRestorationRemoteDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseMatchCloudRestorationRemoteDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseMatchCloudRestorationRepository
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationRepository
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationAction
import com.hoggamers.rankforge.domain.tournament.RestoreMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.DraftConflictResolver
import com.hoggamers.rankforge.domain.tournament.ResolveDraftConflictUseCase
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
    abstract fun bindScreenshotStorageUploader(
        uploader: SupabaseScreenshotStorageUploader,
    ): ScreenshotStorageUploader

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
    abstract fun bindDraftMatchCloudSyncRemoteDataSource(
        dataSource: SupabaseDraftMatchCloudSyncRemoteDataSource,
    ): DraftMatchCloudSyncRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindDraftMatchCloudSyncRepository(
        repository: SupabaseDraftMatchCloudSyncRepository,
    ): DraftMatchCloudSyncRepository

    @Binds
    @Singleton
    abstract fun bindDraftMatchCloudSyncAction(
        useCase: SyncDraftMatchesUseCase,
    ): DraftMatchCloudSyncAction

    @Binds
    @Singleton
    abstract fun bindFinalizedMatchCloudSyncRemoteDataSource(
        dataSource: SupabaseFinalizedMatchCloudSyncRemoteDataSource,
    ): FinalizedMatchCloudSyncRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindFinalizedMatchCloudSyncRepository(
        repository: SupabaseFinalizedMatchCloudSyncRepository,
    ): FinalizedMatchCloudSyncRepository

    @Binds
    @Singleton
    abstract fun bindFinalizedMatchCloudSyncAction(
        useCase: SyncFinalizedMatchesUseCase,
    ): FinalizedMatchCloudSyncAction

    @Binds
    @Singleton
    abstract fun bindProtectedMatchCorrectionAction(
        action: SupabaseProtectedMatchCorrectionAction,
    ): ProtectedMatchCorrectionAction

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

    @Binds @Singleton
    abstract fun bindMatchCloudRestorationRemoteDataSource(dataSource: SupabaseMatchCloudRestorationRemoteDataSource): MatchCloudRestorationRemoteDataSource

    @Binds @Singleton
    abstract fun bindMatchCloudRestorationRepository(repository: SupabaseMatchCloudRestorationRepository): MatchCloudRestorationRepository

    @Binds @Singleton
    abstract fun bindMatchCloudRestorationAction(useCase: RestoreMatchesUseCase): MatchCloudRestorationAction

    @Binds @Singleton
    abstract fun bindDraftConflictResolver(useCase: ResolveDraftConflictUseCase): DraftConflictResolver
}
