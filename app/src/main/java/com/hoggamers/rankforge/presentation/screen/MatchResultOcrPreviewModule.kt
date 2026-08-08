package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.ocr.matchresult.AndroidMatchResultOcrPreviewProcessor
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewLocalFileResolver
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRunner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MatchResultOcrPreviewModule {
    @Provides
    @Singleton
    fun provideMatchResultOcrPreviewRunner(
        assetRepository: MatchResultScreenshotAssetRepository,
        localImagePreserver: LocalImagePreserver,
    ): MatchResultOcrPreviewRunner = AndroidMatchResultOcrPreviewProcessor(
        assetRepository = assetRepository,
        localFileResolver = MatchResultOcrPreviewLocalFileResolver(
            localImagePreserver::resolveRelativePath,
        ),
    )
}
