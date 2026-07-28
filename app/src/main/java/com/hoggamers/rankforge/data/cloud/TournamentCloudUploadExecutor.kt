package com.hoggamers.rankforge.data.cloud

import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

enum class CloudUploadCompletedStage {
    TOURNAMENT,
    TEAM_SLOTS,
}

enum class CloudUploadFailureCategory {
    AUTHENTICATION,
    AUTHORIZATION,
    NETWORK,
    VALIDATION,
    CONFLICT,
    UNKNOWN,
}

sealed interface CloudUploadExecutionResult {
    data object Success : CloudUploadExecutionResult
    data class Failure(
        val completedStage: CloudUploadCompletedStage?,
        val category: CloudUploadFailureCategory,
        val conflict: com.hoggamers.rankforge.domain.sync.RevisionConflict? = null,
    ) : CloudUploadExecutionResult
}

class TournamentCloudUploadExecutor(
    private val upsertTournament: suspend (TournamentUploadPayload) -> Unit,
    private val upsertTeamSlots: suspend (List<TeamSlotUploadPayload>) -> Unit,
    private val upsertPlayers: suspend (List<PlayerUploadPayload>) -> Unit,
) {
    suspend fun execute(payloads: TournamentCloudUploadPayloads): CloudUploadExecutionResult {
        var completedStage: CloudUploadCompletedStage? = null
        return try {
            upsertTournament(payloads.tournament)
            completedStage = CloudUploadCompletedStage.TOURNAMENT
            upsertTeamSlots(payloads.teamSlots)
            completedStage = CloudUploadCompletedStage.TEAM_SLOTS
            upsertPlayers(payloads.players)
            CloudUploadExecutionResult.Success
        } catch (cancellation: CancellationException) {
            if (cancellation is TimeoutCancellationException) {
                CloudUploadExecutionResult.Failure(
                    completedStage = completedStage,
                    category = CloudUploadFailureCategory.NETWORK,
                )
            } else {
                throw cancellation
            }
        } catch (throwable: Throwable) {
            CloudUploadExecutionResult.Failure(
                completedStage = completedStage,
                category = throwable.toCloudUploadFailureCategory(),
            )
        }
    }
}

internal fun Throwable.toCloudUploadFailureCategory(): CloudUploadFailureCategory {
    val description = generateSequence(this) { it.cause }
        .joinToString(" ") { it.message.orEmpty() }
        .lowercase()
    return when {
        "42501" in description ||
            "row-level security" in description ||
            "permission denied" in description ||
            "forbidden" in description ||
            "403" in description -> CloudUploadFailureCategory.AUTHORIZATION
        "401" in description || "unauthorized" in description || "jwt" in description ->
            CloudUploadFailureCategory.AUTHENTICATION
        "violates" in description || "invalid" in description || "235" in description ||
            "bad request" in description -> CloudUploadFailureCategory.VALIDATION
        this is IOException ||
            this is ConnectException ||
            this is UnknownHostException ||
            "timeout" in description ||
            "network" in description -> CloudUploadFailureCategory.NETWORK
        else -> CloudUploadFailureCategory.UNKNOWN
    }
}
