package com.hoggamers.rankforge.data.di

import com.hoggamers.rankforge.data.activity.ActivityEventTracker
import com.hoggamers.rankforge.data.activity.SupabaseActivityEventTracker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ActivityDataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindActivityEventTracker(
        tracker: SupabaseActivityEventTracker,
    ): ActivityEventTracker
}
