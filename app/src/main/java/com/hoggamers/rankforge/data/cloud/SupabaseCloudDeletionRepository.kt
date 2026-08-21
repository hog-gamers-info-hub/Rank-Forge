package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.tournament.CloudDeletionFailureCategory
import com.hoggamers.rankforge.domain.tournament.CloudDeletionRepository
import com.hoggamers.rankforge.domain.tournament.CloudDeletionStageResult
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
private data class DeletionRpcResponse(val outcome: String)

@Singleton
class SupabaseCloudDeletionRepository @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
    private val screenshotMetadata: ScreenshotMetadataCloudDataSource,
    private val resultScreenshotAssets: MatchResultScreenshotAssetCloudDataSource,
    private val lobbyScreenshotAssets: MatchLobbyScreenshotAssetCloudDataSource,
    private val storageObjectDeleter: CloudStorageObjectDeleter,
) : CloudDeletionRepository {
    override suspend fun deleteMatchStorage(
        tournamentId: String,
        matchId: String,
    ): CloudDeletionStageResult = deleteStorageForMatches(tournamentId, setOf(matchId))

    override suspend fun deleteTournamentStorage(
        tournamentId: String,
        matchIds: Set<String>,
    ): CloudDeletionStageResult = deleteStorageForMatches(tournamentId, matchIds)

    override suspend fun deleteMatchRemote(
        tournamentId: String,
        matchId: String,
    ): CloudDeletionStageResult {
        val cloudMatchId = MatchCloudIdentity.matchId(tournamentId, matchId)
            ?: return failed(CloudDeletionFailureCategory.VALIDATION)
        return runRemoteOperation {
            clientProvider.client.postgrest.rpc(
                "delete_match_idempotent",
                mapOf("p_match_id" to cloudMatchId),
            ).decodeSingle<DeletionRpcResponse>().toStageResult()
        }
    }

    override suspend fun deleteTournamentRemote(tournamentId: String): CloudDeletionStageResult {
        if (tournamentId.toUuidOrNull() == null) {
            return failed(CloudDeletionFailureCategory.VALIDATION)
        }
        return runRemoteOperation {
            clientProvider.client.postgrest.rpc(
                "delete_tournament_idempotent",
                mapOf("p_tournament_id" to tournamentId),
            ).decodeSingle<DeletionRpcResponse>().toStageResult()
        }
    }

    private suspend fun deleteStorageForMatches(
        tournamentId: String,
        matchIds: Set<String>,
    ): CloudDeletionStageResult {
        if (tournamentId.toUuidOrNull() == null || matchIds.any { it.isBlank() }) {
            return failed(CloudDeletionFailureCategory.VALIDATION)
        }
        val cloudMatchIds = matchIds.map { matchId ->
            MatchCloudIdentity.matchId(tournamentId, matchId)
                ?: return failed(CloudDeletionFailureCategory.VALIDATION)
        }.toSet()
        val objects = mutableListOf<CloudStorageObject>()
        when (val result = screenshotMetadata.readByTournamentAndMatchIds(tournamentId, cloudMatchIds)) {
            is ScreenshotMetadataCloudReadResult.Failed -> return failed(result.failure.toDeletionCategory())
            is ScreenshotMetadataCloudReadResult.Success -> objects += result.assets.toScreenshotStorageObjects()
        }
        when (val result = resultScreenshotAssets.readByTournamentAndMatchIds(tournamentId, cloudMatchIds)) {
            is MatchResultScreenshotAssetCloudReadResult.Failed -> return failed(result.failure.toDeletionCategory())
            is MatchResultScreenshotAssetCloudReadResult.Success -> objects += result.assets.toResultStorageObjects()
        }
        when (val result = lobbyScreenshotAssets.readByTournamentAndMatchIds(tournamentId, cloudMatchIds)) {
            is MatchLobbyScreenshotAssetCloudReadResult.Failed -> return failed(result.failure.toDeletionCategory())
            is MatchLobbyScreenshotAssetCloudReadResult.Success -> objects += result.assets.toLobbyStorageObjects()
        }
        return when (val result = storageObjectDeleter.delete(objects)) {
            CloudStorageObjectDeletionResult.Success -> CloudDeletionStageResult.Success
            is CloudStorageObjectDeletionResult.Failed -> failed(result.category)
        }
    }

    private suspend fun runRemoteOperation(
        operation: suspend () -> CloudDeletionStageResult,
    ): CloudDeletionStageResult {
        if (!config.isConfigured) return failed(CloudDeletionFailureCategory.VALIDATION)
        if (clientProvider.client.auth.currentSessionOrNull() == null) {
            return failed(CloudDeletionFailureCategory.AUTHENTICATION)
        }
        return try {
            operation()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            failed(throwable.toCloudRemoteDeletionFailureCategory())
        }
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

    private companion object {
        fun failed(category: CloudDeletionFailureCategory) =
            CloudDeletionStageResult.Failed(category)
    }
}

private fun DeletionRpcResponse.toStageResult(): CloudDeletionStageResult =
    deletionRpcOutcomeToStageResult(outcome)

internal fun deletionRpcOutcomeToStageResult(outcome: String): CloudDeletionStageResult = when (outcome) {
    "DELETED",
    "ALREADY_DELETED" -> CloudDeletionStageResult.Success
    "NOT_FOUND_OR_NOT_OWNER" ->
        CloudDeletionStageResult.Failed(CloudDeletionFailureCategory.AUTHORIZATION)
    else -> CloudDeletionStageResult.Failed(CloudDeletionFailureCategory.REMOTE)
}

private fun List<ScreenshotMetadataCloudPayload>.toScreenshotStorageObjects(): List<CloudStorageObject> =
    mapNotNull { asset ->
        asset.storageBucket?.let { bucket ->
            asset.storageObjectPath?.let { path -> CloudStorageObject(bucket, path) }
        }
    }

private fun List<MatchResultScreenshotAssetCloudPayload>.toResultStorageObjects(): List<CloudStorageObject> =
    mapNotNull { asset ->
        asset.storageBucket?.let { bucket ->
            asset.storageObjectPath?.let { path -> CloudStorageObject(bucket, path) }
        }
    }

private fun List<MatchLobbyScreenshotAssetCloudPayload>.toLobbyStorageObjects(): List<CloudStorageObject> =
    mapNotNull { asset ->
        asset.storageBucket?.let { bucket ->
            asset.storageObjectPath?.let { path -> CloudStorageObject(bucket, path) }
        }
    }

private fun ScreenshotMetadataCloudFailure.toDeletionCategory() = when (this) {
    ScreenshotMetadataCloudFailure.MISSING_AUTH_SESSION -> CloudDeletionFailureCategory.AUTHENTICATION
    ScreenshotMetadataCloudFailure.NETWORK -> CloudDeletionFailureCategory.NETWORK
    ScreenshotMetadataCloudFailure.AUTHORIZATION -> CloudDeletionFailureCategory.AUTHORIZATION
    ScreenshotMetadataCloudFailure.READ_FAILED,
    ScreenshotMetadataCloudFailure.WRITE_FAILED -> CloudDeletionFailureCategory.STORAGE
}

private fun MatchResultScreenshotAssetCloudFailure.toDeletionCategory() = when (this) {
    MatchResultScreenshotAssetCloudFailure.MISSING_AUTH_SESSION -> CloudDeletionFailureCategory.AUTHENTICATION
    MatchResultScreenshotAssetCloudFailure.NETWORK -> CloudDeletionFailureCategory.NETWORK
    MatchResultScreenshotAssetCloudFailure.AUTHORIZATION -> CloudDeletionFailureCategory.AUTHORIZATION
    MatchResultScreenshotAssetCloudFailure.INVALID_IDENTITY,
    MatchResultScreenshotAssetCloudFailure.CLOUD_MATCH_ID_UNAVAILABLE -> CloudDeletionFailureCategory.VALIDATION
    MatchResultScreenshotAssetCloudFailure.READ_FAILED,
    MatchResultScreenshotAssetCloudFailure.WRITE_FAILED -> CloudDeletionFailureCategory.STORAGE
}

private fun MatchLobbyScreenshotAssetCloudFailure.toDeletionCategory() = when (this) {
    MatchLobbyScreenshotAssetCloudFailure.MISSING_AUTH_SESSION -> CloudDeletionFailureCategory.AUTHENTICATION
    MatchLobbyScreenshotAssetCloudFailure.NETWORK -> CloudDeletionFailureCategory.NETWORK
    MatchLobbyScreenshotAssetCloudFailure.AUTHORIZATION -> CloudDeletionFailureCategory.AUTHORIZATION
    MatchLobbyScreenshotAssetCloudFailure.INVALID_IDENTITY,
    MatchLobbyScreenshotAssetCloudFailure.CLOUD_MATCH_ID_UNAVAILABLE -> CloudDeletionFailureCategory.VALIDATION
    MatchLobbyScreenshotAssetCloudFailure.READ_FAILED,
    MatchLobbyScreenshotAssetCloudFailure.WRITE_FAILED -> CloudDeletionFailureCategory.STORAGE
}
