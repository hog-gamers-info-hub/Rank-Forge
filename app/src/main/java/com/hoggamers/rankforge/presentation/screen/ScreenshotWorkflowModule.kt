package com.hoggamers.rankforge.presentation.screen

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.hoggamers.rankforge.domain.sync.ForegroundScreenshotRecoveryAction
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScreenshotWorkflowModule {
    @Binds
    @Singleton
    abstract fun bindApplyLobbyTemplateAction(
        useCase: ApplyLobbyTemplateToMatchUseCase,
    ): ApplyLobbyTemplateAction

    @Binds
    @Singleton
    abstract fun bindMatchLobbyScreenshotUploadCheckpointAction(
        checkpoint: MatchLobbyScreenshotUploadCheckpoint,
    ): MatchLobbyScreenshotUploadCheckpointAction

    @Binds
    @Singleton
    abstract fun bindMatchResultScreenshotUploadCheckpointAction(
        checkpoint: MatchResultScreenshotUploadCheckpoint,
    ): MatchResultScreenshotUploadCheckpointAction

    @Binds
    @Singleton
    abstract fun bindForegroundScreenshotRecoveryAction(
        useCase: RecoverScreenshotAssetsOnForegroundConnectivityUseCase,
    ): ForegroundScreenshotRecoveryAction

    @Binds
    @Singleton
    abstract fun bindScreenshotOwnerProvider(
        provider: AuthStateScreenshotOwnerProvider,
    ): ScreenshotOwnerProvider

    @Binds
    @Singleton
    abstract fun bindRosterScreenshotLocalImageStore(
        store: LocalRosterScreenshotImageStore,
    ): RosterScreenshotLocalImageStore
}
