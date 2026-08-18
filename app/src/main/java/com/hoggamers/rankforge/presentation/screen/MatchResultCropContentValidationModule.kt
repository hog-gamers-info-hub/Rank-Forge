package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.ocr.PreparedCropMlKitRecognizer
import com.hoggamers.rankforge.data.ocr.PreparedCropMlKitTextRecognizer
import com.hoggamers.rankforge.data.ocr.matchresult.AndroidMatchResultCropContentValidator
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultCropContentValidator
import com.hoggamers.rankforge.domain.ocr.validation.MatchResultCropContentClassifier
import com.hoggamers.rankforge.domain.ocr.validation.MatchResultCropContentEvidenceEvaluator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MatchResultCropContentValidationBindingsModule {
    @Binds
    @Singleton
    abstract fun bindPreparedCropMlKitRecognizer(
        recognizer: PreparedCropMlKitTextRecognizer,
    ): PreparedCropMlKitRecognizer

    @Binds
    @Singleton
    abstract fun bindMatchResultCropContentValidator(
        validator: AndroidMatchResultCropContentValidator,
    ): MatchResultCropContentValidator
}

@Module
@InstallIn(SingletonComponent::class)
object MatchResultCropContentValidationProvidersModule {
    @Provides
    @Singleton
    fun provideMatchResultCropContentEvidenceEvaluator(): MatchResultCropContentEvidenceEvaluator =
        MatchResultCropContentEvidenceEvaluator()

    @Provides
    @Singleton
    fun provideMatchResultCropContentClassifier(): MatchResultCropContentClassifier =
        MatchResultCropContentClassifier()
}
