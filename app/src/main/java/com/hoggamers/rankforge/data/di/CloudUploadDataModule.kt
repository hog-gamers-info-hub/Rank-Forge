package com.hoggamers.rankforge.data.di

import com.hoggamers.rankforge.data.cloud.SupabaseTournamentCloudUploadRemoteDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseTournamentStandingsShareGateway
import com.hoggamers.rankforge.data.cloud.SupabaseTournamentStandingsShareRemoteDataSource
import com.hoggamers.rankforge.data.cloud.TournamentStandingsShareGateway
import com.hoggamers.rankforge.data.cloud.TournamentStandingsShareRemoteDataSource
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.AuthenticatedScreenshotStorageDownloader
import com.hoggamers.rankforge.data.cloud.SupabaseAuthenticatedScreenshotStorageDownloader
import com.hoggamers.rankforge.data.cloud.ScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.SupabaseMatchResultScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseMatchResultScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.SupabaseMatchLobbyScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseMatchLobbyScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.SupabaseScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.ScreenshotMetadataCloudDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseScreenshotMetadataCloudDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseTournamentCloudUploadRepository
import com.hoggamers.rankforge.data.cloud.SupabaseTournamentQuotaRepository
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
import com.hoggamers.rankforge.domain.tournament.TournamentQuotaRepository
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
import com.hoggamers.rankforge.data.cloud.SupabaseMatchScreenshotRestorationAction
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationRepository
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationAction
import com.hoggamers.rankforge.domain.tournament.RestoreMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.MatchScreenshotRestorationAction
import com.hoggamers.rankforge.domain.tournament.DraftConflictResolver
import com.hoggamers.rankforge.domain.tournament.ResolveDraftConflictUseCase
import com.hoggamers.rankforge.data.cloud.SupabaseTournamentRosterCloudReplacementRemoteDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseTournamentRosterCloudReplacementRepository
import com.hoggamers.rankforge.data.cloud.CloudStorageObjectDeleter
import com.hoggamers.rankforge.data.cloud.SupabaseCloudStorageObjectDeleter
import com.hoggamers.rankforge.data.cloud.CustomDesignImagePreparer
import com.hoggamers.rankforge.data.cloud.AndroidCustomDesignImagePreparer
import com.hoggamers.rankforge.data.cloud.CustomDesignStorageUploader
import com.hoggamers.rankforge.data.cloud.SupabaseCustomDesignStorageUploader
import com.hoggamers.rankforge.data.cloud.CustomDesignTemplateCloudDataSource
import com.hoggamers.rankforge.data.cloud.SupabaseCustomDesignTemplateCloudDataSource
import com.hoggamers.rankforge.data.cloud.CustomDesignSaveAction
import com.hoggamers.rankforge.data.cloud.CustomDesignSaveCoordinator
import com.hoggamers.rankforge.data.cloud.CustomDesignRestoreAction
import com.hoggamers.rankforge.data.cloud.CustomDesignRestoreCoordinator
import com.hoggamers.rankforge.data.cloud.CustomDesignDeleteAction
import com.hoggamers.rankforge.data.cloud.CustomDesignDeleteCoordinator
import com.hoggamers.rankforge.data.cloud.CustomDesignSavedIdDiscoveryAction
import com.hoggamers.rankforge.data.cloud.CustomDesignSavedIdDiscoveryCoordinator
import com.hoggamers.rankforge.data.cloud.SupabaseCloudDeletionRepository
import com.hoggamers.rankforge.domain.tournament.CloudDeletionRepository
import com.hoggamers.rankforge.data.cloud.TournamentRosterCloudReplacementRemoteDataSource
import com.hoggamers.rankforge.domain.tournament.ReplaceTournamentRosterInCloudUseCase
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementAction
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementRepository
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
    abstract fun bindCustomDesignImagePreparer(
        preparer: AndroidCustomDesignImagePreparer,
    ): CustomDesignImagePreparer

    @Binds
    @Singleton
    abstract fun bindCustomDesignStorageUploader(
        uploader: SupabaseCustomDesignStorageUploader,
    ): CustomDesignStorageUploader

    @Binds
    @Singleton
    abstract fun bindCustomDesignTemplateCloudDataSource(
        dataSource: SupabaseCustomDesignTemplateCloudDataSource,
    ): CustomDesignTemplateCloudDataSource

    @Binds
    @Singleton
    abstract fun bindCustomDesignSaveAction(
        coordinator: CustomDesignSaveCoordinator,
    ): CustomDesignSaveAction

    @Binds
    @Singleton
    abstract fun bindCustomDesignRestoreAction(
        coordinator: CustomDesignRestoreCoordinator,
    ): CustomDesignRestoreAction

    @Binds
    @Singleton
    abstract fun bindCustomDesignDeleteAction(
        coordinator: CustomDesignDeleteCoordinator,
    ): CustomDesignDeleteAction

    @Binds
    @Singleton
    abstract fun bindCustomDesignSavedIdDiscoveryAction(
        coordinator: CustomDesignSavedIdDiscoveryCoordinator,
    ): CustomDesignSavedIdDiscoveryAction

    @Binds
    @Singleton
    abstract fun bindCloudStorageObjectDeleter(
        deleter: SupabaseCloudStorageObjectDeleter,
    ): CloudStorageObjectDeleter

    @Binds
    @Singleton
    abstract fun bindCloudDeletionRepository(
        repository: SupabaseCloudDeletionRepository,
    ): CloudDeletionRepository

    @Binds
    @Singleton
    abstract fun bindMatchScreenshotRestorationAction(
        action: SupabaseMatchScreenshotRestorationAction,
    ): MatchScreenshotRestorationAction

    @Binds
    @Singleton
    abstract fun bindAuthenticatedScreenshotStorageDownloader(
        downloader: SupabaseAuthenticatedScreenshotStorageDownloader,
    ): AuthenticatedScreenshotStorageDownloader
    @Binds
    @Singleton
    abstract fun bindScreenshotStorageUploader(
        uploader: SupabaseScreenshotStorageUploader,
    ): ScreenshotStorageUploader

    @Binds
    @Singleton
    abstract fun bindScreenshotMetadataCloudDataSource(
        dataSource: SupabaseScreenshotMetadataCloudDataSource,
    ): ScreenshotMetadataCloudDataSource

    @Binds
    @Singleton
    abstract fun bindMatchResultScreenshotStorageUploader(
        uploader: SupabaseMatchResultScreenshotStorageUploader,
    ): MatchResultScreenshotStorageUploader

    @Binds
    @Singleton
    abstract fun bindMatchResultScreenshotAssetCloudDataSource(
        dataSource: SupabaseMatchResultScreenshotAssetCloudDataSource,
    ): MatchResultScreenshotAssetCloudDataSource

    @Binds
    @Singleton
    abstract fun bindMatchLobbyScreenshotStorageUploader(
        uploader: SupabaseMatchLobbyScreenshotStorageUploader,
    ): MatchLobbyScreenshotStorageUploader

    @Binds
    @Singleton
    abstract fun bindMatchLobbyScreenshotAssetCloudDataSource(
        dataSource: SupabaseMatchLobbyScreenshotAssetCloudDataSource,
    ): MatchLobbyScreenshotAssetCloudDataSource

    @Binds
    @Singleton
    abstract fun bindTournamentCloudUploadRemoteDataSource(
        dataSource: SupabaseTournamentCloudUploadRemoteDataSource,
    ): TournamentCloudUploadRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindTournamentStandingsShareGateway(
        gateway: SupabaseTournamentStandingsShareGateway,
    ): TournamentStandingsShareGateway

    @Binds
    @Singleton
    abstract fun bindTournamentStandingsShareRemoteDataSource(
        dataSource: SupabaseTournamentStandingsShareRemoteDataSource,
    ): TournamentStandingsShareRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindTournamentCloudUploadRepository(
        repository: SupabaseTournamentCloudUploadRepository,
    ): TournamentCloudUploadRepository

    @Binds
    @Singleton
    abstract fun bindTournamentQuotaRepository(
        repository: SupabaseTournamentQuotaRepository,
    ): TournamentQuotaRepository

    @Binds
    @Singleton
    abstract fun bindTournamentCloudUploadAction(
        useCase: UploadTournamentUseCase,
    ): TournamentCloudUploadAction

    @Binds
    @Singleton
    abstract fun bindTournamentRosterCloudReplacementRemoteDataSource(
        dataSource: SupabaseTournamentRosterCloudReplacementRemoteDataSource,
    ): TournamentRosterCloudReplacementRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindTournamentRosterCloudReplacementRepository(
        repository: SupabaseTournamentRosterCloudReplacementRepository,
    ): TournamentRosterCloudReplacementRepository

    @Binds
    @Singleton
    abstract fun bindTournamentRosterCloudReplacementAction(
        useCase: ReplaceTournamentRosterInCloudUseCase,
    ): TournamentRosterCloudReplacementAction

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
