package com.hoggamers.rankforge.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.tournament.CreateTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterPlayersUseCase
import com.hoggamers.rankforge.domain.tournament.ConfirmTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.SaveRosterUseCase
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.RosterValidator
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.TournamentRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class TournamentDataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindTournamentRepository(
        repository: InMemoryTournamentRepository,
    ): TournamentRepository
}

@Module
@InstallIn(SingletonComponent::class)
object TournamentDataProvidersModule {
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
}
