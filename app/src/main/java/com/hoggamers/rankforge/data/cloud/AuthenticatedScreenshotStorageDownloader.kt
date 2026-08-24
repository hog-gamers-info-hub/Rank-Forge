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

    suspend fun download(
        expectedOwnerUserId: String,
        bucket: String,
        objectPath: String,
    ): ByteArray = throw SecurityException("Expected screenshot owner is required.")
}

@Singleton
class SupabaseAuthenticatedScreenshotStorageDownloader @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
) : AuthenticatedScreenshotStorageDownloader {
    override suspend fun download(bucket: String, objectPath: String): ByteArray {
        return downloadInternal(null, bucket, objectPath)
    }

    override suspend fun download(
        expectedOwnerUserId: String,
        bucket: String,
        objectPath: String,
    ): ByteArray = downloadInternal(expectedOwnerUserId, bucket, objectPath)

    private suspend fun downloadInternal(
        expectedOwnerUserId: String?,
        bucket: String,
        objectPath: String,
    ): ByteArray {
        if (!config.isConfigured) throw IOException("Supabase is not configured")
        val userId = clientProvider.client.auth.currentSessionOrNull()?.user?.id?.takeIf { it.isNotBlank() }
        if (userId == null) {
            throw IOException("Authentication required")
        }
        if (expectedOwnerUserId != null && (expectedOwnerUserId.isBlank() || userId != expectedOwnerUserId)) {
            throw SecurityException("Screenshot owner changed during restoration.")
        }
        if (expectedOwnerUserId != null && objectPath.split('/').let { it.size >= 2 && (it[0] != "users" || it[1] != expectedOwnerUserId) }) {
            throw SecurityException("Screenshot storage path owner mismatch.")
        }
        return try {
            clientProvider.client.storage.from(bucket).downloadAuthenticated(objectPath)
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
    }
}
