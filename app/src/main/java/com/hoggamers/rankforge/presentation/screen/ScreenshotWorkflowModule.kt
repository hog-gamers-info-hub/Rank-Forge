package com.hoggamers.rankforge.presentation.screen

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScreenshotWorkflowModule {
    @Binds
    @Singleton
    abstract fun bindScreenshotOwnerProvider(
        provider: AuthStateScreenshotOwnerProvider,
    ): ScreenshotOwnerProvider
}
