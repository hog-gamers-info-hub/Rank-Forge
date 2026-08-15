package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.identityOrNull
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class MatchResultScreenshotAssetCloudFailure {
    MISSING_AUTH_SESSION,
    INVALID_IDENTITY,
    CLOUD_MATCH_ID_UNAVAILABLE,
    NETWORK,
    AUTHORIZATION,
    READ_FAILED,
    WRITE_FAILED,
}

sealed interface MatchResultScreenshotAssetCloudReadResult {
    data class Success(val assets: List<MatchResultScreenshotAssetCloudPayload>) : MatchResultScreenshotAssetCloudReadResult
    data class Failed(val failure: MatchResultScreenshotAssetCloudFailure) : MatchResultScreenshotAssetCloudReadResult
}

sealed interface MatchResultScreenshotAssetCloudResult {
    data object Success : MatchResultScreenshotAssetCloudResult

    data class Failed(
        val failure: MatchResultScreenshotAssetCloudFailure,
    ) : MatchResultScreenshotAssetCloudResult
}

@Serializable
data class MatchResultScreenshotAssetCloudPayload(
    @SerialName("match_id") val matchId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("screenshot_kind") val screenshotKind: String,
    @SerialName("screenshot_role") val screenshotRole: String,
    @SerialName("local_file_extension") val localFileExtension: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("original_width") val originalWidth: Int,
    @SerialName("original_height") val originalHeight: Int,
    @SerialName("byte_size") val byteSize: Long,
    val sha256: String,
    @SerialName("storage_bucket") val storageBucket: String?,
    @SerialName("storage_object_path") val storageObjectPath: String?,
    @SerialName("local_status") val localStatus: String,
    @SerialName("upload_status") val uploadStatus: String,
    @SerialName("upload_failure_code") val uploadFailureCode: String?,
    @SerialName("crop_profile_id") val cropProfileId: String?,
    @SerialName("crop_left") val cropLeft: Double?,
    @SerialName("crop_top") val cropTop: Double?,
    @SerialName("crop_right") val cropRight: Double?,
    @SerialName("crop_bottom") val cropBottom: Double?,
    @SerialName("preserved_at") val preservedAt: String,
    @SerialName("uploaded_at") val uploadedAt: String?,
    val revision: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

interface MatchResultScreenshotAssetCloudDataSource {
    suspend fun upsert(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetCloudResult

    suspend fun deleteByIdentity(
        identity: MatchResultScreenshotIdentity,
    ): MatchResultScreenshotAssetCloudResult

    suspend fun readByTournamentAndMatchIds(
        tournamentId: String,
        matchIds: Set<String>,
    ): MatchResultScreenshotAssetCloudReadResult =
        MatchResultScreenshotAssetCloudReadResult.Success(emptyList())
}

@Singleton
class SupabaseMatchResultScreenshotAssetCloudDataSource internal constructor(
    private val isConfigured: () -> Boolean,
    private val currentUserId: suspend () -> String?,
    private val upsertPayload: suspend (MatchResultScreenshotAssetCloudPayload) -> Unit,
    private val deleteRole: suspend (String, String) -> Unit,
    private val readAssets: suspend (String, Set<String>) -> List<MatchResultScreenshotAssetCloudPayload> =
        { _, _ -> emptyList() },
) : MatchResultScreenshotAssetCloudDataSource {
    @Inject
    constructor(
        config: SupabaseAuthConfig,
        clientProvider: SupabaseClientProvider,
    ) : this(
        isConfigured = { config.isConfigured },
        currentUserId = {
            clientProvider.client.auth.currentSessionOrNull()?.user?.id?.takeIf { it.isNotBlank() }
        },
        upsertPayload = { payload ->
            clientProvider.client.from(TABLE_NAME).upsert(payload)
        },
        deleteRole = { cloudMatchId, screenshotRole ->
            clientProvider.client.from(TABLE_NAME).delete {
                filter {
                    eq("match_id", cloudMatchId)
                    eq("screenshot_role", screenshotRole)
                }
            }
        },
        readAssets = { tournamentId, matchIds ->
            clientProvider.client.from(TABLE_NAME).select {
                filter { eq("tournament_id", tournamentId) }
            }.decodeList<MatchResultScreenshotAssetCloudPayload>()
                .filter { it.matchId in matchIds }
        },
    )

    override suspend fun readByTournamentAndMatchIds(
        tournamentId: String,
        matchIds: Set<String>,
    ): MatchResultScreenshotAssetCloudReadResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext MatchResultScreenshotAssetCloudReadResult.Failed(
            MatchResultScreenshotAssetCloudFailure.READ_FAILED,
        )
        if (currentUserId() == null) return@withContext MatchResultScreenshotAssetCloudReadResult.Failed(
            MatchResultScreenshotAssetCloudFailure.MISSING_AUTH_SESSION,
        )
        try {
            MatchResultScreenshotAssetCloudReadResult.Success(readAssets(tournamentId, matchIds))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            MatchResultScreenshotAssetCloudReadResult.Failed(throwable.toMatchResultScreenshotAssetCloudFailure())
        }
    }

    override suspend fun upsert(
        asset: MatchResultScreenshotAssetEntity,
    ): MatchResultScreenshotAssetCloudResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext MatchResultScreenshotAssetCloudResult.Failed(
                MatchResultScreenshotAssetCloudFailure.WRITE_FAILED,
            )
        }
        val ownerId = currentUserId()
            ?: return@withContext MatchResultScreenshotAssetCloudResult.Failed(
                MatchResultScreenshotAssetCloudFailure.MISSING_AUTH_SESSION,
            )
        val payload = when (val result = asset.toMatchResultScreenshotAssetCloudPayload(ownerId)) {
            is MatchResultScreenshotAssetCloudPayloadMappingResult.Success -> result.payload
            MatchResultScreenshotAssetCloudPayloadMappingResult.InvalidIdentity ->
                return@withContext MatchResultScreenshotAssetCloudResult.Failed(
                    MatchResultScreenshotAssetCloudFailure.INVALID_IDENTITY,
                )

            MatchResultScreenshotAssetCloudPayloadMappingResult.CloudMatchIdUnavailable ->
                return@withContext MatchResultScreenshotAssetCloudResult.Failed(
                    MatchResultScreenshotAssetCloudFailure.CLOUD_MATCH_ID_UNAVAILABLE,
                )
        }

        try {
            upsertPayload(payload)
            MatchResultScreenshotAssetCloudResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            MatchResultScreenshotAssetCloudResult.Failed(throwable.toMatchResultScreenshotAssetCloudFailure())
        }
    }

    override suspend fun deleteByIdentity(
        identity: MatchResultScreenshotIdentity,
    ): MatchResultScreenshotAssetCloudResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext MatchResultScreenshotAssetCloudResult.Failed(
                MatchResultScreenshotAssetCloudFailure.WRITE_FAILED,
            )
        }
        if (currentUserId() == null) {
            return@withContext MatchResultScreenshotAssetCloudResult.Failed(
                MatchResultScreenshotAssetCloudFailure.MISSING_AUTH_SESSION,
            )
        }
        val cloudMatchId = MatchCloudIdentity.matchId(
            tournamentId = identity.tournamentId,
            localMatchId = identity.matchId,
        ) ?: return@withContext MatchResultScreenshotAssetCloudResult.Failed(
            MatchResultScreenshotAssetCloudFailure.CLOUD_MATCH_ID_UNAVAILABLE,
        )
        try {
            deleteRole(cloudMatchId, identity.role.name)
            MatchResultScreenshotAssetCloudResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            MatchResultScreenshotAssetCloudResult.Failed(throwable.toMatchResultScreenshotAssetCloudFailure())
        }
    }

    companion object {
        const val TABLE_NAME = "match_result_screenshot_assets"
    }
}

class NoOpMatchResultScreenshotAssetCloudDataSource : MatchResultScreenshotAssetCloudDataSource {
    override suspend fun upsert(
        asset: MatchResultScreenshotAssetEntity,
    ): MatchResultScreenshotAssetCloudResult = MatchResultScreenshotAssetCloudResult.Success

    override suspend fun deleteByIdentity(
        identity: MatchResultScreenshotIdentity,
    ): MatchResultScreenshotAssetCloudResult = MatchResultScreenshotAssetCloudResult.Success
}

sealed interface MatchResultScreenshotAssetCloudPayloadMappingResult {
    data class Success(
        val payload: MatchResultScreenshotAssetCloudPayload,
    ) : MatchResultScreenshotAssetCloudPayloadMappingResult

    data object InvalidIdentity : MatchResultScreenshotAssetCloudPayloadMappingResult
    data object CloudMatchIdUnavailable : MatchResultScreenshotAssetCloudPayloadMappingResult
}

fun MatchResultScreenshotAssetEntity.toMatchResultScreenshotAssetCloudPayload(
    ownerId: String,
): MatchResultScreenshotAssetCloudPayloadMappingResult {
    val identity = identityOrNull()
        ?: return MatchResultScreenshotAssetCloudPayloadMappingResult.InvalidIdentity
    val cloudMatchId = MatchCloudIdentity.matchId(
        tournamentId = identity.tournamentId,
        localMatchId = identity.matchId,
    ) ?: return MatchResultScreenshotAssetCloudPayloadMappingResult.CloudMatchIdUnavailable
    return MatchResultScreenshotAssetCloudPayloadMappingResult.Success(
        MatchResultScreenshotAssetCloudPayload(
            matchId = cloudMatchId,
            ownerId = ownerId,
            tournamentId = identity.tournamentId,
            screenshotKind = screenshotKind,
            screenshotRole = screenshotRole,
            localFileExtension = fileExtension,
            mimeType = mimeType,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            byteSize = byteSize,
            sha256 = sha256,
            storageBucket = storageBucket,
            storageObjectPath = storageObjectPath,
            localStatus = localStatus,
            uploadStatus = uploadStatus,
            uploadFailureCode = uploadFailureCode,
            cropProfileId = cropProfileId,
            cropLeft = cropLeft,
            cropTop = cropTop,
            cropRight = cropRight,
            cropBottom = cropBottom,
            preservedAt = preservedAt.toCloudTimestamp(),
            uploadedAt = uploadedAt?.toCloudTimestamp(),
            revision = revision,
            createdAt = createdAt.toCloudTimestamp(),
            updatedAt = updatedAt.toCloudTimestamp(),
        ),
    )
}

private fun Throwable.toMatchResultScreenshotAssetCloudFailure(): MatchResultScreenshotAssetCloudFailure {
    val message = message.orEmpty().lowercase()
    return when {
        this is IOException ||
            message.contains("network") ||
            message.contains("timeout") ||
            message.contains("connection") -> MatchResultScreenshotAssetCloudFailure.NETWORK
        message.contains("401") ||
            message.contains("session") ||
            message.contains("jwt") ||
            message.contains("unauthorized") -> MatchResultScreenshotAssetCloudFailure.MISSING_AUTH_SESSION
        message.contains("403") ||
            message.contains("42501") ||
            message.contains("forbidden") ||
            message.contains("row-level security") -> MatchResultScreenshotAssetCloudFailure.AUTHORIZATION
        else -> MatchResultScreenshotAssetCloudFailure.WRITE_FAILED
    }
}
