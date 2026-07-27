package com.hoggamers.rankforge.data.di

import com.hoggamers.rankforge.data.auth.AuthRemoteDataSource
import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseAuthRemoteDataSource
import com.hoggamers.rankforge.data.auth.SupabaseAuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.LoginUseCase
import com.hoggamers.rankforge.domain.auth.LogoutUseCase
import com.hoggamers.rankforge.domain.auth.ObserveAuthStateUseCase
import com.hoggamers.rankforge.domain.auth.RestoreSessionUseCase
import com.hoggamers.rankforge.domain.auth.SignUpUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthDataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindAuthRemoteDataSource(
        dataSource: SupabaseAuthRemoteDataSource,
    ): AuthRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        repository: SupabaseAuthRepository,
    ): AuthRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AuthDataProvidersModule {
    @Provides
    @Singleton
    fun provideSupabaseAuthConfig(): SupabaseAuthConfig = SupabaseAuthConfig.fromBuildConfig()

    @Provides
    @Singleton
    fun provideObserveAuthStateUseCase(
        repository: AuthRepository,
    ): ObserveAuthStateUseCase = ObserveAuthStateUseCase(repository)

    @Provides
    @Singleton
    fun provideRestoreSessionUseCase(
        repository: AuthRepository,
    ): RestoreSessionUseCase = RestoreSessionUseCase(repository)

    @Provides
    @Singleton
    fun provideSignUpUseCase(
        repository: AuthRepository,
    ): SignUpUseCase = SignUpUseCase(repository)

    @Provides
    @Singleton
    fun provideLoginUseCase(
        repository: AuthRepository,
    ): LoginUseCase = LoginUseCase(repository)

    @Provides
    @Singleton
    fun provideLogoutUseCase(
        repository: AuthRepository,
    ): LogoutUseCase = LogoutUseCase(repository)
}
