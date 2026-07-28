package com.hoggamers.rankforge.data.di

import com.hoggamers.rankforge.data.sync.RoomPersistentSyncQueueRepository
import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncQueueDataModule {
    @Binds @Singleton abstract fun bindPersistentSyncQueueRepository(repository: RoomPersistentSyncQueueRepository): PersistentSyncQueueRepository
}
