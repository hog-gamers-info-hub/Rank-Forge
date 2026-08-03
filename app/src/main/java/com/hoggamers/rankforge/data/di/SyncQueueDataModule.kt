package com.hoggamers.rankforge.data.di

import com.hoggamers.rankforge.data.sync.RoomPersistentSyncQueueRepository
import com.hoggamers.rankforge.data.connectivity.AndroidForegroundConnectivityObserver
import com.hoggamers.rankforge.data.connectivity.ForegroundConnectivityObserver
import com.hoggamers.rankforge.domain.sync.ForegroundConnectivityRetryAction
import com.hoggamers.rankforge.domain.sync.ForegroundSyncQueueRecoveryAction
import com.hoggamers.rankforge.domain.sync.ForegroundSyncQueueRetryCoordinator
import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import com.hoggamers.rankforge.domain.sync.QueueOperationRetryExecutor
import com.hoggamers.rankforge.domain.sync.RecoverForegroundSyncQueueUseCase
import com.hoggamers.rankforge.domain.sync.RecoverSyncQueueOnForegroundConnectivityUseCase
import com.hoggamers.rankforge.domain.sync.SyncQueueEntryRetryExecutor
import com.hoggamers.rankforge.domain.tournament.RestoreMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.RestoreTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.SyncDraftMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.SyncFinalizedMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.UploadTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ReplaceTournamentRosterInCloudUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncQueueDataModule {
    @Binds @Singleton abstract fun bindPersistentSyncQueueRepository(repository: RoomPersistentSyncQueueRepository): PersistentSyncQueueRepository

    @Binds @Singleton abstract fun bindForegroundSyncQueueRecoveryAction(useCase: RecoverForegroundSyncQueueUseCase): ForegroundSyncQueueRecoveryAction

    @Binds @Singleton abstract fun bindForegroundConnectivityRetryAction(useCase: RecoverSyncQueueOnForegroundConnectivityUseCase): ForegroundConnectivityRetryAction

    @Binds @Singleton abstract fun bindForegroundConnectivityObserver(observer: AndroidForegroundConnectivityObserver): ForegroundConnectivityObserver
}

@Module
@InstallIn(SingletonComponent::class)
object SyncQueueRetryModule {
    @Provides
    @Singleton
    fun provideRetryExecutor(
        uploadTournament: UploadTournamentUseCase,
        restoreTournament: RestoreTournamentUseCase,
        syncDraftMatches: SyncDraftMatchesUseCase,
        syncFinalizedMatches: SyncFinalizedMatchesUseCase,
        restoreMatches: RestoreMatchesUseCase,
        rosterReplacement: ReplaceTournamentRosterInCloudUseCase,
    ): SyncQueueEntryRetryExecutor = QueueOperationRetryExecutor(
        tournamentUpload = uploadTournament,
        tournamentRestoration = restoreTournament,
        draftMatchSync = syncDraftMatches,
        finalizedMatchSync = syncFinalizedMatches,
        matchRestoration = restoreMatches,
        rosterReplacement = rosterReplacement,
    )

    @Provides
    @Singleton
    fun provideForegroundRetryCoordinator(
        queueRepository: PersistentSyncQueueRepository,
        executor: SyncQueueEntryRetryExecutor,
    ): ForegroundSyncQueueRetryCoordinator = ForegroundSyncQueueRetryCoordinator(
        repository = queueRepository,
        executor = executor,
    )
}
