package com.hoggamers.rankforge.data.di

import com.hoggamers.rankforge.data.ocr.matchlobby.AndroidMatchLobbyPlayersOcrRunner
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrRunner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MatchLobbyPlayersOcrModule {
    @Binds
    @Singleton
    abstract fun bindMatchLobbyPlayersOcrRunner(
        runner: AndroidMatchLobbyPlayersOcrRunner,
    ): MatchLobbyPlayersOcrRunner
}
