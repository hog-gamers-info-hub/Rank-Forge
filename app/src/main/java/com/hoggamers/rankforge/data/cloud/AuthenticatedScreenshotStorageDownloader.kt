package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

interface AuthenticatedScreenshotStorageDownloader {
    suspend fun download(bucket: String, objectPath: String): ByteArray
}

@Singleton
class SupabaseAuthenticatedScreenshotStorageDownloader @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
) : AuthenticatedScreenshotStorageDownloader {
    override suspend fun download(bucket: String, objectPath: String): ByteArray {
        if (!config.isConfigured) throw IOException("Supabase is not configured")
        if (clientProvider.client.auth.currentSessionOrNull() == null) {
            throw IOException("Authentication required")
        }
        return try {
            clientProvider.client.storage.from(bucket).downloadAuthenticated(objectPath)
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
    }
}
