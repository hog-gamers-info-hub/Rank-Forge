package com.hoggamers.rankforge.data.di

import com.hoggamers.rankforge.data.ocr.DefaultMlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.MlKitOcrEngine
import com.hoggamers.rankforge.data.ocr.MlKitOcrEngineImpl
import com.hoggamers.rankforge.data.ocr.MlKitOcrTextRecognizer
import com.hoggamers.rankforge.data.ocr.MlKitTextRecognizerFactory
import com.hoggamers.rankforge.domain.ocr.OcrTextRecognizer
import dagger.Binds
import dagger.Module
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
}
