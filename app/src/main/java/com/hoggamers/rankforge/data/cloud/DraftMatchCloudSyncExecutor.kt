package com.hoggamers.rankforge.data.cloud

import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

enum class DraftMatchCloudSyncCompletedStage {
    MATCHES,
}

enum class DraftMatchCloudSyncFailureCategory {
    AUTHENTICATION,
    AUTHORIZATION,
    NETWORK,
    VALIDATION,
    CONFLICT,
    UNKNOWN,
}

sealed interface DraftMatchCloudSyncExecutionResult {
    data object Success : DraftMatchCloudSyncExecutionResult
    data class Failure(
        val completedStage: DraftMatchCloudSyncCompletedStage?,
        val category: DraftMatchCloudSyncFailureCategory,
        val conflict: com.hoggamers.rankforge.domain.sync.RevisionConflict? = null,
    ) : DraftMatchCloudSyncExecutionResult
}

class DraftMatchCloudSyncExecutor(
    private val upsertMatches: suspend (List<DraftMatchUploadPayload>) -> Unit,
    private val upsertMatchResults: suspend (List<DraftMatchResultUploadPayload>) -> Unit,
) {
    suspend fun execute(payloads: DraftMatchCloudSyncPayloads): DraftMatchCloudSyncExecutionResult {
        var completedStage: DraftMatchCloudSyncCompletedStage? = null
        return try {
            if (payloads.matches.isNotEmpty()) {
                upsertMatches(payloads.matches)
                completedStage = DraftMatchCloudSyncCompletedStage.MATCHES
            }
            if (payloads.matchResults.isNotEmpty()) {
                upsertMatchResults(payloads.matchResults)
            }
            DraftMatchCloudSyncExecutionResult.Success
        } catch (cancellation: CancellationException) {
            if (cancellation is TimeoutCancellationException) {
                DraftMatchCloudSyncExecutionResult.Failure(
                    completedStage = completedStage,
                    category = DraftMatchCloudSyncFailureCategory.NETWORK,
                )
            } else {
                throw cancellation
            }
        } catch (throwable: Throwable) {
            DraftMatchCloudSyncExecutionResult.Failure(
                completedStage = completedStage,
                category = throwable.toDraftMatchCloudSyncFailureCategory(),
            )
        }
    }
}

internal fun Throwable.toDraftMatchCloudSyncFailureCategory(): DraftMatchCloudSyncFailureCategory {
    val description = generateSequence(this) { it.cause }
        .joinToString(" ") { it.message.orEmpty() }
        .lowercase()
    return when {
        "42501" in description ||
            "row-level security" in description ||
            "permission denied" in description ||
            "forbidden" in description ||
            "403" in description -> DraftMatchCloudSyncFailureCategory.AUTHORIZATION
        "401" in description || "unauthorized" in description || "jwt" in description ->
            DraftMatchCloudSyncFailureCategory.AUTHENTICATION
        "violates" in description || "invalid" in description || "235" in description ||
            "bad request" in description -> DraftMatchCloudSyncFailureCategory.VALIDATION
        this is IOException ||
            this is ConnectException ||
            this is UnknownHostException ||
            "timeout" in description ||
            "network" in description -> DraftMatchCloudSyncFailureCategory.NETWORK
        else -> DraftMatchCloudSyncFailureCategory.UNKNOWN
    }
}
