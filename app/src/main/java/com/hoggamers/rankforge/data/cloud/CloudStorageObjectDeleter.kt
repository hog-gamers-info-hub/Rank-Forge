package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.tournament.CloudDeletionFailureCategory
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

data class CloudStorageObject(
    val bucket: String,
    val path: String,
)

sealed interface CloudStorageObjectDeletionResult {
    data object Success : CloudStorageObjectDeletionResult
    data class Failed(val category: CloudDeletionFailureCategory) : CloudStorageObjectDeletionResult
}

interface CloudStorageObjectDeleter {
    suspend fun delete(objects: Collection<CloudStorageObject>): CloudStorageObjectDeletionResult
}

@Singleton
class SupabaseCloudStorageObjectDeleter @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
) : CloudStorageObjectDeleter {
    override suspend fun delete(objects: Collection<CloudStorageObject>): CloudStorageObjectDeletionResult {
        if (objects.isEmpty()) return CloudStorageObjectDeletionResult.Success
        if (!config.isConfigured) {
            return CloudStorageObjectDeletionResult.Failed(CloudDeletionFailureCategory.VALIDATION)
        }
        if (clientProvider.client.auth.currentSessionOrNull() == null) {
            return CloudStorageObjectDeletionResult.Failed(CloudDeletionFailureCategory.AUTHENTICATION)
        }
        return try {
            objects
                .asSequence()
                .filter { it.bucket.isNotBlank() && it.path.isNotBlank() }
                .groupBy { it.bucket }
                .forEach { (bucket, bucketObjects) ->
                    clientProvider.client.storage.from(bucket).delete(
                        bucketObjects.map { it.path }.toSet(),
                    )
                }
            CloudStorageObjectDeletionResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            if (throwable.isMissingStorageObject()) {
                CloudStorageObjectDeletionResult.Success
            } else {
                CloudStorageObjectDeletionResult.Failed(throwable.toCloudDeletionFailureCategory())
            }
        }
    }
}

private fun Throwable.isMissingStorageObject(): Boolean {
    val message = message.orEmpty().lowercase()
    return message.contains("404") ||
        message.contains("not found") ||
        message.contains("object does not exist")
}

internal fun Throwable.toCloudDeletionFailureCategory(): CloudDeletionFailureCategory {
    val message = message.orEmpty().lowercase()
    return when {
        message.contains("401") ||
            message.contains("session") ||
            message.contains("jwt") ||
            message.contains("unauthorized") -> CloudDeletionFailureCategory.AUTHENTICATION
        message.contains("403") ||
            message.contains("42501") ||
            message.contains("forbidden") ||
            message.contains("row-level security") -> CloudDeletionFailureCategory.AUTHORIZATION
        this is IOException ||
            message.contains("network") ||
            message.contains("timeout") ||
            message.contains("connection") -> CloudDeletionFailureCategory.NETWORK
        else -> CloudDeletionFailureCategory.STORAGE
    }
}

internal fun Throwable.toCloudRemoteDeletionFailureCategory(): CloudDeletionFailureCategory {
    val message = message.orEmpty().lowercase()
    return when {
        message.contains("401") ||
            message.contains("session") ||
            message.contains("jwt") ||
            message.contains("unauthorized") -> CloudDeletionFailureCategory.AUTHENTICATION
        message.contains("403") ||
            message.contains("42501") ||
            message.contains("forbidden") ||
            message.contains("row-level security") -> CloudDeletionFailureCategory.AUTHORIZATION
        this is IOException ||
            message.contains("network") ||
            message.contains("timeout") ||
            message.contains("connection") -> CloudDeletionFailureCategory.NETWORK
        else -> CloudDeletionFailureCategory.REMOTE
    }
}
