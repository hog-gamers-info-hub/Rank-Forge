package com.hoggamers.rankforge.data.di

import com.hoggamers.rankforge.data.ocr.DefaultMlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.MlKitOcrEngine
import com.hoggamers.rankforge.data.ocr.MlKitOcrEngineImpl
import com.hoggamers.rankforge.data.ocr.MlKitOcrTextRecognizer
import com.hoggamers.rankforge.data.ocr.MlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.extraction.MlKitRawOcrEngine
import com.hoggamers.rankforge.data.ocr.extraction.MlKitRawOcrEngineImpl
import com.hoggamers.rankforge.data.ocr.extraction.MlKitRawOcrTextExtractor
import com.hoggamers.rankforge.data.ocr.extraction.MlKitRosterRawOcrExtractor
import com.hoggamers.rankforge.data.ocr.preprocessing.AndroidBitmapOcrImagePreprocessor
import com.hoggamers.rankforge.data.ocr.preprocessing.AndroidRosterOcrPanelPreparer
import com.hoggamers.rankforge.data.ocr.preprocessing.RoomMatchOcrSourceProvider
import com.hoggamers.rankforge.data.ocr.preprocessing.RoomRosterOcrSourceProvider
import com.hoggamers.rankforge.domain.ocr.OcrTextRecognizer
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrTextExtractor
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractor
import com.hoggamers.rankforge.domain.ocr.parsing.DefaultRosterOcrValidator
import com.hoggamers.rankforge.domain.ocr.parsing.FixedLayoutKillParser
import com.hoggamers.rankforge.domain.ocr.parsing.FixedLayoutPlacementParser
import com.hoggamers.rankforge.domain.ocr.parsing.FixedLayoutPlayerNameParser
import com.hoggamers.rankforge.domain.ocr.parsing.FixedLayoutRosterCandidateParser
import com.hoggamers.rankforge.domain.ocr.parsing.FixedRosterSlotAssociator
import com.hoggamers.rankforge.domain.ocr.parsing.KillParser
import com.hoggamers.rankforge.domain.ocr.parsing.PlacementParser
import com.hoggamers.rankforge.domain.ocr.parsing.PlayerNameParser
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParser
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidator
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociator
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrImagePreprocessor
import com.hoggamers.rankforge.domain.ocr.review.FixedLayoutOcrFailureAnalyzer
import com.hoggamers.rankforge.domain.ocr.review.MatchOcrProcessor
import com.hoggamers.rankforge.domain.ocr.review.MatchOcrSourceProvider
import com.hoggamers.rankforge.domain.ocr.review.OcrFailureAnalyzer
import com.hoggamers.rankforge.domain.ocr.review.ProcessMatchOcrUseCase
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
    abstract fun bindMlKitRawOcrEngine(
        engine: MlKitRawOcrEngineImpl,
    ): MlKitRawOcrEngine

    @Binds
    @Singleton
    abstract fun bindRawOcrTextExtractor(
        extractor: MlKitRawOcrTextExtractor,
    ): RawOcrTextExtractor

    @Binds
    @Singleton
    abstract fun bindMatchOcrSourceProvider(
        provider: RoomMatchOcrSourceProvider,
    ): MatchOcrSourceProvider

    @Binds
    abstract fun bindMatchOcrProcessor(
        processor: ProcessMatchOcrUseCase,
    ): MatchOcrProcessor

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
    fun provideOcrImagePreprocessor(): OcrImagePreprocessor =
        AndroidBitmapOcrImagePreprocessor()

    @Provides
    @Singleton
    fun providePlacementParser(): PlacementParser =
        FixedLayoutPlacementParser()

    @Provides
    @Singleton
    fun providePlayerNameParser(): PlayerNameParser =
        FixedLayoutPlayerNameParser()

    @Provides
    @Singleton
    fun provideKillParser(): KillParser =
        FixedLayoutKillParser()

    @Provides
    @Singleton
    fun provideOcrFailureAnalyzer(): OcrFailureAnalyzer =
        FixedLayoutOcrFailureAnalyzer()

    @Provides
    @Singleton
    fun provideRosterCandidateParser(): RosterCandidateParser =
        FixedLayoutRosterCandidateParser()

    @Provides
    @Singleton
    fun provideRosterSlotAssociator(): RosterSlotAssociator =
        FixedRosterSlotAssociator()

    @Provides
    @Singleton
    fun provideRosterOcrValidator(): RosterOcrValidator =
        DefaultRosterOcrValidator()
}