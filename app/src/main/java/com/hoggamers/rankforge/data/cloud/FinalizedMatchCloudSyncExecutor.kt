package com.hoggamers.rankforge.data.cloud

import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

enum class FinalizedMatchCloudSyncCompletedStage {
    MATCHES,
}

enum class FinalizedMatchCloudSyncFailureCategory {
    AUTHENTICATION,
    AUTHORIZATION,
    NETWORK,
    VALIDATION,
    CONFLICT,
    UNKNOWN,
}

sealed interface FinalizedMatchCloudSyncExecutionResult {
    data object Success : FinalizedMatchCloudSyncExecutionResult
    data class Failure(
        val completedStage: FinalizedMatchCloudSyncCompletedStage?,
        val category: FinalizedMatchCloudSyncFailureCategory,
        val conflict: com.hoggamers.rankforge.domain.sync.RevisionConflict? = null,
    ) : FinalizedMatchCloudSyncExecutionResult
}

class FinalizedMatchCloudSyncExecutor(
    private val upsertMatches: suspend (List<FinalizedMatchUploadPayload>) -> Unit,
    private val upsertMatchResults: suspend (List<FinalizedMatchResultUploadPayload>) -> Unit,
) {
    suspend fun execute(payloads: FinalizedMatchCloudSyncPayloads): FinalizedMatchCloudSyncExecutionResult {
        var completedStage: FinalizedMatchCloudSyncCompletedStage? = null
        return try {
            if (payloads.matches.isNotEmpty()) {
                upsertMatches(payloads.matches)
                completedStage = FinalizedMatchCloudSyncCompletedStage.MATCHES
            }
            if (payloads.matchResults.isNotEmpty()) {
                upsertMatchResults(payloads.matchResults)
            }
            FinalizedMatchCloudSyncExecutionResult.Success
        } catch (cancellation: CancellationException) {
            if (cancellation is TimeoutCancellationException) {
                FinalizedMatchCloudSyncExecutionResult.Failure(
                    completedStage = completedStage,
                    category = FinalizedMatchCloudSyncFailureCategory.NETWORK,
                )
            } else {
                throw cancellation
            }
        } catch (throwable: Throwable) {
            FinalizedMatchCloudSyncExecutionResult.Failure(
                completedStage = completedStage,
                category = throwable.toFinalizedMatchCloudSyncFailureCategory(),
            )
        }
    }
}

internal fun Throwable.toFinalizedMatchCloudSyncFailureCategory(): FinalizedMatchCloudSyncFailureCategory {
    val description = generateSequence(this) { it.cause }
        .joinToString(" ") { it.message.orEmpty() }
        .lowercase()
    return when {
        "42501" in description ||
            "row-level security" in description ||
            "permission denied" in description ||
            "forbidden" in description ||
            "403" in description -> FinalizedMatchCloudSyncFailureCategory.AUTHORIZATION
        "401" in description || "unauthorized" in description || "jwt" in description ->
            FinalizedMatchCloudSyncFailureCategory.AUTHENTICATION
        "violates" in description || "invalid" in description || "235" in description ||
            "bad request" in description -> FinalizedMatchCloudSyncFailureCategory.VALIDATION
        this is IOException ||
            this is ConnectException ||
            this is UnknownHostException ||
            "timeout" in description ||
            "network" in description -> FinalizedMatchCloudSyncFailureCategory.NETWORK
        else -> FinalizedMatchCloudSyncFailureCategory.UNKNOWN
    }
}
