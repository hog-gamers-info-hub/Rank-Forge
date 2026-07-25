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
}
