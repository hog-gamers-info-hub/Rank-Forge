package com.hoggamers.rankforge.data.di

import com.hoggamers.rankforge.data.ocr.matchlobby.AndroidMatchLobbySlotNumberOcrRunner
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrRunner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MatchLobbySlotNumberOcrModule {
    @Binds
    @Singleton
    abstract fun bindMatchLobbySlotNumberOcrRunner(
        runner: AndroidMatchLobbySlotNumberOcrRunner,
    ): MatchLobbySlotNumberOcrRunner
}
