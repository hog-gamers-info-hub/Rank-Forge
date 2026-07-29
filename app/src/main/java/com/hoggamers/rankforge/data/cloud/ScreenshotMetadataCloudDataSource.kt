package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class ScreenshotMetadataCloudFailure {
    MISSING_AUTH_SESSION,
    NETWORK,
    AUTHORIZATION,
    WRITE_FAILED,
}

sealed interface ScreenshotMetadataCloudResult {
    data object Success : ScreenshotMetadataCloudResult
    data class Failed(val failure: ScreenshotMetadataCloudFailure) : ScreenshotMetadataCloudResult
}

@Serializable
data class ScreenshotMetadataCloudPayload(
    @SerialName("match_id") val matchId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("local_file_extension") val localFileExtension: String,
    @SerialName("mime_type") val mimeType: String,
    val width: Int,
    val height: Int,
    @SerialName("byte_size") val byteSize: Long,
    val sha256: String,
    @SerialName("storage_bucket") val storageBucket: String?,
    @SerialName("storage_object_path") val storageObjectPath: String?,
    @SerialName("local_status") val localStatus: String,
    @SerialName("upload_status") val uploadStatus: String,
    @SerialName("upload_failure_code") val uploadFailureCode: String?,
    @SerialName("preserved_at") val preservedAt: String,
    @SerialName("uploaded_at") val uploadedAt: String?,
    val revision: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

interface ScreenshotMetadataCloudDataSource {
    suspend fun upsert(payload: ScreenshotMetadataCloudPayload): ScreenshotMetadataCloudResult

    suspend fun deleteByMatchId(matchId: String): ScreenshotMetadataCloudResult
}

@Singleton
class SupabaseScreenshotMetadataCloudDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
) : ScreenshotMetadataCloudDataSource {
    override suspend fun upsert(
        payload: ScreenshotMetadataCloudPayload,
    ): ScreenshotMetadataCloudResult = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext ScreenshotMetadataCloudResult.Failed(
                ScreenshotMetadataCloudFailure.WRITE_FAILED,
            )
        }
        if (clientProvider.client.auth.currentSessionOrNull() == null) {
            return@withContext ScreenshotMetadataCloudResult.Failed(
                ScreenshotMetadataCloudFailure.MISSING_AUTH_SESSION,
            )
        }
        try {
            clientProvider.client.from(TABLE_NAME).upsert(payload)
            ScreenshotMetadataCloudResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ScreenshotMetadataCloudResult.Failed(throwable.toScreenshotMetadataCloudFailure())
        }
    }

    override suspend fun deleteByMatchId(matchId: String): ScreenshotMetadataCloudResult =
        withContext(Dispatchers.IO) {
            if (!config.isConfigured) {
                return@withContext ScreenshotMetadataCloudResult.Failed(
                    ScreenshotMetadataCloudFailure.WRITE_FAILED,
                )
            }
            if (clientProvider.client.auth.currentSessionOrNull() == null) {
                return@withContext ScreenshotMetadataCloudResult.Failed(
                    ScreenshotMetadataCloudFailure.MISSING_AUTH_SESSION,
                )
            }
            try {
                clientProvider.client.from(TABLE_NAME).delete {
                    filter {
                        eq("match_id", matchId)
                    }
                }
                ScreenshotMetadataCloudResult.Success
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                ScreenshotMetadataCloudResult.Failed(throwable.toScreenshotMetadataCloudFailure())
            }
        }

    private companion object {
        const val TABLE_NAME = "match_screenshot_metadata"
    }
}

class NoOpScreenshotMetadataCloudDataSource : ScreenshotMetadataCloudDataSource {
    override suspend fun upsert(payload: ScreenshotMetadataCloudPayload): ScreenshotMetadataCloudResult =
        ScreenshotMetadataCloudResult.Success

    override suspend fun deleteByMatchId(matchId: String): ScreenshotMetadataCloudResult =
        ScreenshotMetadataCloudResult.Success
}

fun Long.toCloudTimestamp(): String = Instant.ofEpochMilli(this).toString()

private fun Throwable.toScreenshotMetadataCloudFailure(): ScreenshotMetadataCloudFailure {
    val message = message.orEmpty().lowercase()
    return when {
        this is IOException ||
            message.contains("network") ||
            message.contains("timeout") ||
            message.contains("connection") -> ScreenshotMetadataCloudFailure.NETWORK
        message.contains("401") ||
            message.contains("session") ||
            message.contains("jwt") ||
            message.contains("unauthorized") -> ScreenshotMetadataCloudFailure.MISSING_AUTH_SESSION
        message.contains("403") ||
            message.contains("42501") ||
            message.contains("forbidden") ||
            message.contains("row-level security") -> ScreenshotMetadataCloudFailure.AUTHORIZATION
        else -> ScreenshotMetadataCloudFailure.WRITE_FAILED
    }
}
