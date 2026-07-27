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
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.data.tournament.RoomTournamentRepository
import com.hoggamers.rankforge.domain.tournament.CreateTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterPlayersUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.CreateMatchUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsUseCase
import com.hoggamers.rankforge.domain.tournament.ConfirmTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.SaveRosterUseCase
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.RosterValidator
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.StartMatchCorrectionUseCase
import com.hoggamers.rankforge.domain.tournament.SubmitMatchCorrectionUseCase
import com.hoggamers.rankforge.domain.tournament.ClearMatchCorrectionDraftUseCase
import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStandingsEngine
import com.hoggamers.rankforge.domain.tournament.TieBreakRules

@Module
@InstallIn(SingletonComponent::class)
abstract class TournamentDataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindTournamentRepository(
        repository: RoomTournamentRepository,
    ): TournamentRepository
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
    ).build()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideCreateTournamentUseCase(
        repository: TournamentRepository,
        clock: Clock,
    ): CreateTournamentUseCase = CreateTournamentUseCase(repository, clock)

    @Provides
    @Singleton
    fun provideObserveTournamentsUseCase(
        repository: TournamentRepository,
    ): ObserveTournamentsUseCase = ObserveTournamentsUseCase(repository)

    @Provides
    @Singleton
    fun provideGetTournamentByIdUseCase(
        repository: TournamentRepository,
    ): GetTournamentByIdUseCase = GetTournamentByIdUseCase(repository)

    @Provides
    @Singleton
    fun provideObserveTournamentSlotsUseCase(
        repository: TournamentRepository,
    ): ObserveTournamentSlotsUseCase = ObserveTournamentSlotsUseCase(repository)

    @Provides
    @Singleton
    fun provideSaveTeamSlotNamesUseCase(
        repository: TournamentRepository,
    ): SaveTeamSlotNamesUseCase = SaveTeamSlotNamesUseCase(repository)

    @Provides
    @Singleton
    fun provideObserveRosterPlayersUseCase(
        repository: TournamentRepository,
    ): ObserveRosterPlayersUseCase = ObserveRosterPlayersUseCase(repository)

    @Provides
    @Singleton
    fun provideObserveRosterByTournamentUseCase(
        repository: TournamentRepository,
    ): ObserveRosterByTournamentUseCase = ObserveRosterByTournamentUseCase(repository)

    @Provides
    @Singleton
    fun provideSaveRosterUseCase(
        repository: TournamentRepository,
    ): SaveRosterUseCase = SaveRosterUseCase(repository)

    @Provides
    @Singleton
    fun provideRosterValidator(): RosterValidator = RosterValidator()

    @Provides
    @Singleton
    fun provideValidateTournamentRosterUseCase(
        repository: TournamentRepository,
        validator: RosterValidator,
    ): ValidateTournamentRosterUseCase = ValidateTournamentRosterUseCase(repository, validator)

    @Provides
    @Singleton
    fun provideConfirmTournamentRosterUseCase(
        repository: TournamentRepository,
        validateTournamentRoster: ValidateTournamentRosterUseCase,
    ): ConfirmTournamentRosterUseCase = ConfirmTournamentRosterUseCase(repository, validateTournamentRoster)

    @Provides
    @Singleton
    fun provideCreateMatchUseCase(
        repository: TournamentRepository,
    ): CreateMatchUseCase = CreateMatchUseCase(repository)

    @Provides
    @Singleton
    fun provideObserveMatchesUseCase(
        repository: TournamentRepository,
    ): ObserveMatchesUseCase = ObserveMatchesUseCase(repository)

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
    ): SaveMatchPlacementsUseCase = SaveMatchPlacementsUseCase(repository)

    @Provides
    @Singleton
    fun provideSaveMatchKillsUseCase(
        repository: TournamentRepository,
    ): SaveMatchKillsUseCase = SaveMatchKillsUseCase(repository)

    @Provides
    @Singleton
    fun provideObserveMatchDraftValuesUseCase(
        repository: TournamentRepository,
    ): com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase =
        com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase(repository)

    @Provides
    @Singleton
    fun provideSaveMatchDraftValueUseCase(
        repository: TournamentRepository,
    ): com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueUseCase =
        com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueUseCase(repository)

    @Provides
    @Singleton
    fun provideClearDraftMatchUseCase(
        repository: TournamentRepository,
    ): com.hoggamers.rankforge.domain.tournament.ClearDraftMatchUseCase =
        com.hoggamers.rankforge.domain.tournament.ClearDraftMatchUseCase(repository)

    @Provides
    @Singleton
    fun provideValidateMatchResultUseCase(): ValidateMatchResultUseCase = ValidateMatchResultUseCase()

    @Provides
    @Singleton
    fun provideFinalizeMatchUseCase(
        repository: TournamentRepository,
        validateMatchResult: ValidateMatchResultUseCase,
    ): FinalizeMatchUseCase = FinalizeMatchUseCase(repository, validateMatchResult)

    @Provides
    @Singleton
    fun provideStartMatchCorrectionUseCase(
        repository: TournamentRepository,
    ): StartMatchCorrectionUseCase = StartMatchCorrectionUseCase(repository)

    @Provides
    @Singleton
    fun provideSubmitMatchCorrectionUseCase(
        repository: TournamentRepository,
        validateMatchResult: ValidateMatchResultUseCase,
    ): SubmitMatchCorrectionUseCase = SubmitMatchCorrectionUseCase(repository, validateMatchResult)

    @Provides
    @Singleton
    fun provideClearMatchCorrectionDraftUseCase(
        repository: TournamentRepository,
    ): ClearMatchCorrectionDraftUseCase = ClearMatchCorrectionDraftUseCase(repository)
}
