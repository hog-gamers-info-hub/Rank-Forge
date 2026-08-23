package com.hoggamers.rankforge.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Singleton
import com.hoggamers.rankforge.data.local.MatchOcrEvidenceDao
import com.hoggamers.rankforge.data.local.MatchLobbyOcrCacheDao
import com.hoggamers.rankforge.data.local.MatchLobbyOcrCacheRepository
import com.hoggamers.rankforge.data.local.MatchResultOcrCacheDao
import com.hoggamers.rankforge.data.local.MatchResultOcrCacheRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetDao
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetDao
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.TournamentLobbyTemplateAssetRepository
import com.hoggamers.rankforge.data.local.RoomTournamentLobbyTemplateAssetRepository
import com.hoggamers.rankforge.data.local.TournamentLobbyTemplateAssetDao
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.data.local.ScreenshotMetadataDao
import com.hoggamers.rankforge.data.local.RosterScreenshotMetadataDao
import com.hoggamers.rankforge.data.local.RoomRosterScreenshotMetadataRepository
import com.hoggamers.rankforge.data.local.RoomMatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.RoomMatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.RosterScreenshotMetadataRepository
import com.hoggamers.rankforge.data.local.SyncQueueDao
import com.hoggamers.rankforge.data.local.DeletionIntentDao
import com.hoggamers.rankforge.data.local.RoomScreenshotMetadataRepository
import com.hoggamers.rankforge.data.local.ScreenshotMetadataRepository
import com.hoggamers.rankforge.data.local.RoomMatchResultOcrCacheRepository
import com.hoggamers.rankforge.data.local.RoomMatchLobbyOcrCacheRepository
import com.hoggamers.rankforge.data.tournament.RoomTournamentRepository
import com.hoggamers.rankforge.data.tournament.RoomDeletionIntentRepository
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrCacheCodec
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyOcrCacheCodec
import com.hoggamers.rankforge.data.ocr.MatchOcrCacheReader
import com.hoggamers.rankforge.data.ocr.RoomMatchOcrCacheReader
import com.hoggamers.rankforge.domain.tournament.CreateTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSummariesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterPlayersUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.CreateMatchUseCase
import com.hoggamers.rankforge.domain.tournament.CreateNextMatchUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsUseCase
import com.hoggamers.rankforge.domain.tournament.ConfirmTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.SaveRosterUseCase
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.RosterValidator
import com.hoggamers.rankforge.domain.tournament.ReplaceConfirmedTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.LocalDeletionRepository
import com.hoggamers.rankforge.domain.tournament.DeletionIntentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentRestorationLocalRepository
import com.hoggamers.rankforge.domain.tournament.MatchRestorationLocalRepository
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchUseCase
import com.hoggamers.rankforge.domain.tournament.StartMatchCorrectionUseCase
import com.hoggamers.rankforge.domain.tournament.SubmitMatchCorrectionUseCase
import com.hoggamers.rankforge.domain.tournament.ClearMatchCorrectionDraftUseCase
import com.hoggamers.rankforge.domain.tournament.ProtectedMatchCorrectionAction
import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStandingsEngine
import com.hoggamers.rankforge.domain.tournament.TieBreakRules
import com.hoggamers.rankforge.domain.auth.AuthRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class TournamentDataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindTournamentRepository(
        repository: RoomTournamentRepository,
    ): TournamentRepository

    @Binds
    @Singleton
    abstract fun bindLocalDeletionRepository(
        repository: RoomTournamentRepository,
    ): LocalDeletionRepository

    @Binds
    @Singleton
    abstract fun bindDeletionIntentRepository(
        repository: RoomDeletionIntentRepository,
    ): DeletionIntentRepository

    @Binds
    @Singleton
    abstract fun bindTournamentRestorationLocalRepository(
        repository: RoomTournamentRepository,
    ): TournamentRestorationLocalRepository

    @Binds
    @Singleton
    abstract fun bindMatchRestorationLocalRepository(
        repository: RoomTournamentRepository,
    ): MatchRestorationLocalRepository

    @Binds
    @Singleton
    abstract fun bindScreenshotMetadataRepository(
        repository: RoomScreenshotMetadataRepository,
    ): ScreenshotMetadataRepository

    @Binds
    @Singleton
    abstract fun bindRosterScreenshotMetadataRepository(
        repository: RoomRosterScreenshotMetadataRepository,
    ): RosterScreenshotMetadataRepository

    @Binds
    @Singleton
    abstract fun bindMatchResultScreenshotAssetRepository(
        repository: RoomMatchResultScreenshotAssetRepository,
    ): MatchResultScreenshotAssetRepository

    @Binds
    @Singleton
    abstract fun bindMatchLobbyScreenshotAssetRepository(
        repository: RoomMatchLobbyScreenshotAssetRepository,
    ): MatchLobbyScreenshotAssetRepository

    @Binds
    @Singleton
    abstract fun bindTournamentLobbyTemplateAssetRepository(
        repository: RoomTournamentLobbyTemplateAssetRepository,
    ): TournamentLobbyTemplateAssetRepository

    @Binds
    @Singleton
    abstract fun bindMatchOcrCacheReader(
        reader: RoomMatchOcrCacheReader,
    ): MatchOcrCacheReader
}

@Module
@InstallIn(SingletonComponent::class)
object TournamentDataProvidersModule {
    @Provides
    @Singleton
    fun provideRankForgeDatabase(
        @ApplicationContext context: Context,
    ): RankForgeDatabase = Room.databaseBuilder(
        context,
        RankForgeDatabase::class.java,
        "rank_forge.db",
    ).addMigrations(
        RankForgeDatabase.MIGRATION_1_2,
        RankForgeDatabase.MIGRATION_2_3,
        RankForgeDatabase.MIGRATION_3_4,
        RankForgeDatabase.MIGRATION_4_5,
        RankForgeDatabase.MIGRATION_5_6,
        RankForgeDatabase.MIGRATION_6_7,
        RankForgeDatabase.MIGRATION_7_8,
        RankForgeDatabase.MIGRATION_8_9,
        RankForgeDatabase.MIGRATION_9_10,
        RankForgeDatabase.MIGRATION_10_11,
        RankForgeDatabase.MIGRATION_11_12,
        RankForgeDatabase.MIGRATION_12_13,
        RankForgeDatabase.MIGRATION_13_14,
        RankForgeDatabase.MIGRATION_14_15,
        RankForgeDatabase.MIGRATION_15_16,
        RankForgeDatabase.MIGRATION_16_17,
        RankForgeDatabase.MIGRATION_17_18,
        RankForgeDatabase.MIGRATION_18_19,
    ).build()

    @Provides
    @Singleton
    fun provideSyncQueueDao(database: RankForgeDatabase): SyncQueueDao = database.syncQueueDao()

    @Provides
    @Singleton
    fun provideDeletionIntentDao(database: RankForgeDatabase): DeletionIntentDao = database.deletionIntentDao()

    @Provides
    @Singleton
    fun provideScreenshotMetadataDao(database: RankForgeDatabase): ScreenshotMetadataDao =
        database.screenshotMetadataDao()

    @Provides
    @Singleton
    fun provideRosterScreenshotMetadataDao(database: RankForgeDatabase): RosterScreenshotMetadataDao =
        database.rosterScreenshotMetadataDao()

    @Provides
    @Singleton
    fun provideMatchResultScreenshotAssetDao(database: RankForgeDatabase): MatchResultScreenshotAssetDao =
        database.matchResultScreenshotAssetDao()

    @Provides
    @Singleton
    fun provideMatchResultOcrCacheDao(database: RankForgeDatabase): MatchResultOcrCacheDao =
        database.matchResultOcrCacheDao()

    @Provides
    @Singleton
    fun provideMatchResultOcrCacheRepository(
        dao: MatchResultOcrCacheDao,
        codec: MatchResultOcrCacheCodec,
        clock: Clock,
    ): MatchResultOcrCacheRepository = RoomMatchResultOcrCacheRepository(dao, codec, clock)

    @Provides
    @Singleton
    fun provideMatchLobbyOcrCacheDao(database: RankForgeDatabase): MatchLobbyOcrCacheDao =
        database.matchLobbyOcrCacheDao()

    @Provides
    @Singleton
    fun provideMatchLobbyOcrCacheRepository(
        dao: MatchLobbyOcrCacheDao,
        codec: MatchLobbyOcrCacheCodec,
        clock: Clock,
    ): MatchLobbyOcrCacheRepository = RoomMatchLobbyOcrCacheRepository(dao, codec, clock)

    @Provides
    @Singleton
    fun provideMatchLobbyScreenshotAssetDao(database: RankForgeDatabase): MatchLobbyScreenshotAssetDao =
        database.matchLobbyScreenshotAssetDao()

    @Provides
    @Singleton
    fun provideTournamentLobbyTemplateAssetDao(database: RankForgeDatabase): TournamentLobbyTemplateAssetDao =
        database.tournamentLobbyTemplateAssetDao()

    @Provides
    @Singleton
    fun provideMatchOcrEvidenceDao(
        database: RankForgeDatabase,
    ): MatchOcrEvidenceDao = database.matchOcrEvidenceDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideCreateTournamentUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
        clock: Clock,
    ): CreateTournamentUseCase = CreateTournamentUseCase(repository, authRepository, clock)

    @Provides
    @Singleton
    fun provideObserveTournamentsUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): ObserveTournamentsUseCase = ObserveTournamentsUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideObserveTournamentSummariesUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): ObserveTournamentSummariesUseCase = ObserveTournamentSummariesUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideGetTournamentByIdUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): GetTournamentByIdUseCase = GetTournamentByIdUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideObserveTournamentSlotsUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): ObserveTournamentSlotsUseCase = ObserveTournamentSlotsUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideSaveTeamSlotNamesUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): SaveTeamSlotNamesUseCase = SaveTeamSlotNamesUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideObserveRosterPlayersUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): ObserveRosterPlayersUseCase = ObserveRosterPlayersUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideObserveRosterByTournamentUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): ObserveRosterByTournamentUseCase = ObserveRosterByTournamentUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideSaveRosterUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): SaveRosterUseCase = SaveRosterUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideReplaceConfirmedTournamentRosterUseCase(
        repository: TournamentRepository,
        rosterValidator: RosterValidator,
        authRepository: AuthRepository,
    ): ReplaceConfirmedTournamentRosterUseCase =
        ReplaceConfirmedTournamentRosterUseCase(repository, rosterValidator, authRepository)

    @Provides
    @Singleton
    fun provideRosterValidator(): RosterValidator = RosterValidator()

    @Provides
    @Singleton
    fun provideValidateTournamentRosterUseCase(
        observeTournamentSlots: ObserveTournamentSlotsUseCase,
        observeRosterPlayers: ObserveRosterPlayersUseCase,
        validator: RosterValidator,
    ): ValidateTournamentRosterUseCase = ValidateTournamentRosterUseCase(
        observeTournamentSlots,
        observeRosterPlayers,
        validator,
    )

    @Provides
    @Singleton
    fun provideConfirmTournamentRosterUseCase(
        repository: TournamentRepository,
        validateTournamentRoster: ValidateTournamentRosterUseCase,
        authRepository: AuthRepository,
    ): ConfirmTournamentRosterUseCase = ConfirmTournamentRosterUseCase(
        repository,
        validateTournamentRoster,
        authRepository,
    )

    @Provides
    @Singleton
    fun provideCreateMatchUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): CreateMatchUseCase = CreateMatchUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideCreateNextMatchUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): CreateNextMatchUseCase = CreateNextMatchUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideObserveMatchesUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): ObserveMatchesUseCase = ObserveMatchesUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideCumulativeTournamentStandingsEngine(): CumulativeTournamentStandingsEngine =
        CumulativeTournamentStandingsEngine()

    @Provides
    @Singleton
    fun provideTieBreakRules(): TieBreakRules = TieBreakRules()

    @Provides
    @Singleton
    fun provideSaveMatchPlacementsUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): SaveMatchPlacementsUseCase = SaveMatchPlacementsUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideSaveMatchKillsUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): SaveMatchKillsUseCase = SaveMatchKillsUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideObserveMatchDraftValuesUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase =
        com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideSaveMatchDraftValueUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueUseCase =
        com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideClearDraftMatchUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): com.hoggamers.rankforge.domain.tournament.ClearDraftMatchUseCase =
        com.hoggamers.rankforge.domain.tournament.ClearDraftMatchUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideValidateMatchResultUseCase(): ValidateMatchResultUseCase = ValidateMatchResultUseCase()

    @Provides
    @Singleton
    fun provideFinalizeMatchUseCase(
        repository: TournamentRepository,
        validateMatchResult: ValidateMatchResultUseCase,
        authRepository: AuthRepository,
    ): FinalizeMatchUseCase = FinalizeMatchUseCase(repository, validateMatchResult, authRepository)

    @Provides
    @Singleton
    fun provideFinalizeOcrCorrectionMatchUseCase(
        repository: TournamentRepository,
        finalizeMatch: FinalizeMatchUseCase,
        authRepository: AuthRepository,
    ): FinalizeOcrCorrectionMatchUseCase =
        FinalizeOcrCorrectionMatchUseCase(repository, finalizeMatch, authRepository)

    @Provides
    @Singleton
    fun provideStartMatchCorrectionUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): StartMatchCorrectionUseCase = StartMatchCorrectionUseCase(repository, authRepository)

    @Provides
    @Singleton
    fun provideSubmitMatchCorrectionUseCase(
        repository: TournamentRepository,
        validateMatchResult: ValidateMatchResultUseCase,
        authRepository: AuthRepository,
        protectedCorrection: ProtectedMatchCorrectionAction,
    ): SubmitMatchCorrectionUseCase = SubmitMatchCorrectionUseCase(
        repository,
        validateMatchResult,
        authRepository,
        protectedCorrection,
    )

    @Provides
    @Singleton
    fun provideClearMatchCorrectionDraftUseCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ): ClearMatchCorrectionDraftUseCase = ClearMatchCorrectionDraftUseCase(repository, authRepository)
}
