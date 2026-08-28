package com.hoggamers.rankforge.data.cloud

import android.util.Log
import com.hoggamers.rankforge.BuildConfig
import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.identityOrNull
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
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

private const val LOBBY_METADATA_TIMING_TAG = "PointIQCalcTiming"

private fun logLobbyMetadataTiming(
    startedAtNanos: Long,
    identity: MatchLobbyScreenshotIdentity?,
    outcome: String,
) {
    if (!BuildConfig.DEBUG) return
    val durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000L
    val suffix = if (identity == null) {
        "outcome=$outcome"
    } else {
        "tournament_id=${identity.tournamentId} match_id=${identity.matchId} " +
            "lobby_index=${identity.lobbyScreenshotIndex} outcome=$outcome"
    }
    Log.d(
        LOBBY_METADATA_TIMING_TAG,
        "stage=LOBBY_METADATA_UPSERT duration_ms=$durationMs $suffix",
    )
}

enum class MatchLobbyScreenshotAssetCloudFailure {
    MISSING_AUTH_SESSION,
    INVALID_IDENTITY,
    CLOUD_MATCH_ID_UNAVAILABLE,
    NETWORK,
    AUTHORIZATION,
    READ_FAILED,
    WRITE_FAILED,
}

sealed interface MatchLobbyScreenshotAssetCloudReadResult {
    data class Success(val assets: List<MatchLobbyScreenshotAssetCloudPayload>) : MatchLobbyScreenshotAssetCloudReadResult
    data class Failed(val failure: MatchLobbyScreenshotAssetCloudFailure) : MatchLobbyScreenshotAssetCloudReadResult
}

sealed interface MatchLobbyScreenshotAssetCloudResult {
    data object Success : MatchLobbyScreenshotAssetCloudResult

    data class Failed(
        val failure: MatchLobbyScreenshotAssetCloudFailure,
    ) : MatchLobbyScreenshotAssetCloudResult
}

@Serializable
data class MatchLobbyScreenshotAssetCloudPayload(
    @SerialName("match_id") val matchId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("lobby_screenshot_index") val lobbyScreenshotIndex: Int,
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

interface MatchLobbyScreenshotAssetCloudDataSource {
    suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetCloudResult

    suspend fun upsert(
        asset: MatchLobbyScreenshotAssetEntity,
        expectedOwnerUserId: String,
    ): MatchLobbyScreenshotAssetCloudResult = throw SecurityException("Expected screenshot owner is required.")

    suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity): MatchLobbyScreenshotAssetCloudResult

    suspend fun readByTournamentAndMatchIds(
        tournamentId: String,
        matchIds: Set<String>,
    ): MatchLobbyScreenshotAssetCloudReadResult =
        MatchLobbyScreenshotAssetCloudReadResult.Success(emptyList())
}

@Singleton
class SupabaseMatchLobbyScreenshotAssetCloudDataSource internal constructor(
    private val isConfigured: () -> Boolean,
    private val currentUserId: suspend () -> String?,
    private val upsertPayload: suspend (MatchLobbyScreenshotAssetCloudPayload) -> Unit,
    private val deleteAsset: suspend (String, Int) -> Unit,
    private val readAssets: suspend (String, Set<String>) -> List<MatchLobbyScreenshotAssetCloudPayload> =
        { _, _ -> emptyList() },
) : MatchLobbyScreenshotAssetCloudDataSource {
    @Inject
    constructor(
        config: SupabaseAuthConfig,
        clientProvider: SupabaseClientProvider,
    ) : this(
        isConfigured = { config.isConfigured },
        currentUserId = {
            clientProvider.client.auth.currentSessionOrNull()?.user?.id?.takeIf { it.isNotBlank() }
        },
        upsertPayload = { payload -> clientProvider.client.from(TABLE_NAME).upsert(payload) },
        deleteAsset = { cloudMatchId, index ->
            clientProvider.client.from(TABLE_NAME).delete {
                filter {
                    eq("match_id", cloudMatchId)
                    eq("lobby_screenshot_index", index)
                }
            }
        },
        readAssets = { tournamentId, matchIds ->
            clientProvider.client.from(TABLE_NAME).select {
                filter { eq("tournament_id", tournamentId) }
            }.decodeList<MatchLobbyScreenshotAssetCloudPayload>()
                .filter { it.matchId in matchIds }
        },
    )

    override suspend fun readByTournamentAndMatchIds(
        tournamentId: String,
        matchIds: Set<String>,
    ): MatchLobbyScreenshotAssetCloudReadResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext MatchLobbyScreenshotAssetCloudReadResult.Failed(
            MatchLobbyScreenshotAssetCloudFailure.READ_FAILED,
        )
        if (currentUserId() == null) return@withContext MatchLobbyScreenshotAssetCloudReadResult.Failed(
            MatchLobbyScreenshotAssetCloudFailure.MISSING_AUTH_SESSION,
        )
        try {
            MatchLobbyScreenshotAssetCloudReadResult.Success(readAssets(tournamentId, matchIds))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            MatchLobbyScreenshotAssetCloudReadResult.Failed(throwable.toCloudFailure())
        }
    }

    override suspend fun upsert(
        asset: MatchLobbyScreenshotAssetEntity,
    ): MatchLobbyScreenshotAssetCloudResult = upsertInternal(asset, expectedOwnerUserId = null)

    override suspend fun upsert(
        asset: MatchLobbyScreenshotAssetEntity,
        expectedOwnerUserId: String,
    ): MatchLobbyScreenshotAssetCloudResult = upsertInternal(asset, expectedOwnerUserId)

    private suspend fun upsertInternal(
        asset: MatchLobbyScreenshotAssetEntity,
        expectedOwnerUserId: String?,
    ): MatchLobbyScreenshotAssetCloudResult {
        if (!isConfigured()) return failed(MatchLobbyScreenshotAssetCloudFailure.WRITE_FAILED)
        val ownerId = currentUserId()
            ?: return failed(MatchLobbyScreenshotAssetCloudFailure.MISSING_AUTH_SESSION)
        if (expectedOwnerUserId != null && (expectedOwnerUserId.isBlank() || ownerId != expectedOwnerUserId)) {
            return failed(MatchLobbyScreenshotAssetCloudFailure.AUTHORIZATION)
        }
        val timingIdentity = asset.identityOrNull()
        val payload = when (val result = asset.copy(ownerUserId = expectedOwnerUserId ?: asset.ownerUserId)
            .toMatchLobbyScreenshotAssetCloudPayload(expectedOwnerUserId ?: ownerId)) {
            is MatchLobbyScreenshotAssetCloudPayloadMappingResult.Success -> result.payload
            MatchLobbyScreenshotAssetCloudPayloadMappingResult.InvalidIdentity ->
                return failed(MatchLobbyScreenshotAssetCloudFailure.INVALID_IDENTITY)
            MatchLobbyScreenshotAssetCloudPayloadMappingResult.CloudMatchIdUnavailable ->
                return failed(MatchLobbyScreenshotAssetCloudFailure.CLOUD_MATCH_ID_UNAVAILABLE)
        }
        val upsertStartedAtNanos = System.nanoTime()
        var upsertOutcome = "THREW"
        return try {
            upsertPayload(payload)
            upsertOutcome = "SUCCESS"
            MatchLobbyScreenshotAssetCloudResult.Success
        } catch (cancellation: CancellationException) {
            upsertOutcome = "CANCELLED"
            throw cancellation
        } catch (throwable: Throwable) {
            val failure = throwable.toCloudFailure()
            upsertOutcome = "FAILED_$failure"
            failed(failure)
        } finally {
            logLobbyMetadataTiming(
                startedAtNanos = upsertStartedAtNanos,
                identity = timingIdentity,
                outcome = upsertOutcome,
            )
        }
    }

    override suspend fun deleteByIdentity(
        identity: MatchLobbyScreenshotIdentity,
    ): MatchLobbyScreenshotAssetCloudResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext failed(MatchLobbyScreenshotAssetCloudFailure.WRITE_FAILED)
        if (currentUserId() == null) return@withContext failed(MatchLobbyScreenshotAssetCloudFailure.MISSING_AUTH_SESSION)
        val cloudMatchId = MatchCloudIdentity.matchId(identity.tournamentId, identity.matchId)
            ?: return@withContext failed(MatchLobbyScreenshotAssetCloudFailure.CLOUD_MATCH_ID_UNAVAILABLE)
        try {
            deleteAsset(cloudMatchId, identity.lobbyScreenshotIndex)
            MatchLobbyScreenshotAssetCloudResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            failed(throwable.toCloudFailure())
        }
    }

    private fun failed(failure: MatchLobbyScreenshotAssetCloudFailure) =
        MatchLobbyScreenshotAssetCloudResult.Failed(failure)

    companion object {
        const val TABLE_NAME = "match_lobby_screenshot_assets"
    }
}

class NoOpMatchLobbyScreenshotAssetCloudDataSource : MatchLobbyScreenshotAssetCloudDataSource {
    override suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity) =
        MatchLobbyScreenshotAssetCloudResult.Success

    override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) =
        MatchLobbyScreenshotAssetCloudResult.Success
}

sealed interface MatchLobbyScreenshotAssetCloudPayloadMappingResult {
    data class Success(val payload: MatchLobbyScreenshotAssetCloudPayload) : MatchLobbyScreenshotAssetCloudPayloadMappingResult
    data object InvalidIdentity : MatchLobbyScreenshotAssetCloudPayloadMappingResult
    data object CloudMatchIdUnavailable : MatchLobbyScreenshotAssetCloudPayloadMappingResult
}

fun MatchLobbyScreenshotAssetEntity.toMatchLobbyScreenshotAssetCloudPayload(
    ownerId: String,
): MatchLobbyScreenshotAssetCloudPayloadMappingResult {
    val identity = identityOrNull() ?: return MatchLobbyScreenshotAssetCloudPayloadMappingResult.InvalidIdentity
    val cloudMatchId = MatchCloudIdentity.matchId(identity.tournamentId, identity.matchId)
        ?: return MatchLobbyScreenshotAssetCloudPayloadMappingResult.CloudMatchIdUnavailable
    if (identity.lobbyScreenshotIndex !in 1..3) return MatchLobbyScreenshotAssetCloudPayloadMappingResult.InvalidIdentity
    return MatchLobbyScreenshotAssetCloudPayloadMappingResult.Success(
        MatchLobbyScreenshotAssetCloudPayload(
            matchId = cloudMatchId,
            ownerId = ownerId,
            tournamentId = identity.tournamentId,
            lobbyScreenshotIndex = identity.lobbyScreenshotIndex,
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

private fun Throwable.toCloudFailure(): MatchLobbyScreenshotAssetCloudFailure {
    val message = message.orEmpty().lowercase()
    return when {
        this is IOException || message.contains("network") || message.contains("timeout") || message.contains("connection") ->
            MatchLobbyScreenshotAssetCloudFailure.NETWORK
        message.contains("401") || message.contains("session") || message.contains("jwt") || message.contains("unauthorized") ->
            MatchLobbyScreenshotAssetCloudFailure.MISSING_AUTH_SESSION
        message.contains("403") || message.contains("42501") || message.contains("forbidden") || message.contains("row-level security") ->
            MatchLobbyScreenshotAssetCloudFailure.AUTHORIZATION
        else -> MatchLobbyScreenshotAssetCloudFailure.WRITE_FAILED
    }
}
