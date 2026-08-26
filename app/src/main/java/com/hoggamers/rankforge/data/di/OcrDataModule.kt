package com.hoggamers.rankforge.data.di

import com.hoggamers.rankforge.data.ocr.DefaultMlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.MlKitOcrEngine
import com.hoggamers.rankforge.data.ocr.MlKitOcrEngineImpl
import com.hoggamers.rankforge.data.ocr.MlKitOcrTextRecognizer
import com.hoggamers.rankforge.data.ocr.MlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.extraction.MlKitRosterRawOcrExtractor
import com.hoggamers.rankforge.data.ocr.matchlobby.AndroidMatchLobbyAutoCropProposer
import com.hoggamers.rankforge.data.ocr.matchlobby.AndroidLobbyPlayerDualOcrRunner
import com.hoggamers.rankforge.data.ocr.matchlobby.AndroidLobbyPlayerPpOcrRuntime
import com.hoggamers.rankforge.data.ocr.matchlobby.AndroidLobbyPlayerRowCropPipeline
import com.hoggamers.rankforge.data.ocr.matchlobby.LobbyPlayerDualOcrRunner
import com.hoggamers.rankforge.data.ocr.matchlobby.LobbyPlayerPpOcrRuntime
import com.hoggamers.rankforge.data.ocr.matchresult.AndroidMatchResultAutoCropProposer
import com.hoggamers.rankforge.presentation.screen.AndroidMatchResultPositionCropPreviewGenerator
import com.hoggamers.rankforge.presentation.screen.MatchResultPositionCropPreviewGenerator
import com.hoggamers.rankforge.presentation.screen.AndroidMatchResultPositionRowCropPreviewGenerator
import com.hoggamers.rankforge.presentation.screen.MatchResultPositionRowCropPreviewGenerator
import com.hoggamers.rankforge.data.ocr.preprocessing.AndroidRosterOcrPanelPreparer
import com.hoggamers.rankforge.data.ocr.preprocessing.RoomRosterOcrSourceProvider
import com.hoggamers.rankforge.domain.ocr.OcrTextRecognizer
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractor
import com.hoggamers.rankforge.domain.ocr.matchlobby.MatchLobbyAutoCropProposer
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropProposer
import com.hoggamers.rankforge.domain.ocr.parsing.DefaultRosterOcrValidator
import com.hoggamers.rankforge.domain.ocr.parsing.FixedLayoutRosterCandidateParser
import com.hoggamers.rankforge.domain.ocr.parsing.FixedRosterSlotAssociator
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParser
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidator
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociator
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparer
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrSourceProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrDataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindOcrTextRecognizer(
        recognizer: MlKitOcrTextRecognizer,
    ): OcrTextRecognizer

    @Binds
    @Singleton
    abstract fun bindMlKitOcrEngine(
        engine: MlKitOcrEngineImpl,
    ): MlKitOcrEngine

    @Binds
    @Singleton
    abstract fun bindMlKitTextRecognizerFactory(
        factory: DefaultMlKitTextRecognizerFactory,
    ): MlKitTextRecognizerFactory

    @Binds
    @Singleton
    abstract fun bindMatchResultAutoCropProposer(
        proposer: AndroidMatchResultAutoCropProposer,
    ): MatchResultAutoCropProposer

    @Binds
    @Singleton
    abstract fun bindMatchResultPositionCropPreviewGenerator(
        generator: AndroidMatchResultPositionCropPreviewGenerator,
    ): MatchResultPositionCropPreviewGenerator

    @Binds
    @Singleton
    abstract fun bindMatchResultPositionRowCropPreviewGenerator(
        generator: AndroidMatchResultPositionRowCropPreviewGenerator,
    ): MatchResultPositionRowCropPreviewGenerator

    @Binds
    @Singleton
    abstract fun bindMatchLobbyAutoCropProposer(
        proposer: AndroidMatchLobbyAutoCropProposer,
    ): MatchLobbyAutoCropProposer

    @Binds
    @Singleton
    abstract fun bindLobbyPlayerRowCropPipeline(
        pipeline: AndroidLobbyPlayerRowCropPipeline,
    ): com.hoggamers.rankforge.data.ocr.matchlobby.LobbyPlayerRowCropPipeline

    @Binds
    @Singleton
    abstract fun bindLobbyPlayerDualOcrRunner(
        runner: AndroidLobbyPlayerDualOcrRunner,
    ): LobbyPlayerDualOcrRunner

    @Binds
    @Singleton
    abstract fun bindLobbyPlayerPpOcrRuntime(
        runtime: AndroidLobbyPlayerPpOcrRuntime,
    ): LobbyPlayerPpOcrRuntime

    @Binds
    @Singleton
    abstract fun bindRosterRawOcrExtractor(
        extractor: MlKitRosterRawOcrExtractor,
    ): RosterRawOcrExtractor

    @Binds
    @Singleton
    abstract fun bindRosterOcrSourceProvider(
        provider: RoomRosterOcrSourceProvider,
    ): RosterOcrSourceProvider

    @Binds
    @Singleton
    abstract fun bindRosterOcrPanelPreparer(
        preparer: AndroidRosterOcrPanelPreparer,
    ): RosterOcrPanelPreparer
}

@Module
@InstallIn(SingletonComponent::class)
object OcrDataProvidersModule {
    @Provides
    @Singleton
    fun provideRosterCandidateParser(): RosterCandidateParser = FixedLayoutRosterCandidateParser()

    @Provides
    @Singleton
    fun provideRosterSlotAssociator(): RosterSlotAssociator = FixedRosterSlotAssociator()

    @Provides
    @Singleton
    fun provideRosterOcrValidator(): RosterOcrValidator = DefaultRosterOcrValidator()
}
