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
    data class Success(
        val confirmedCloudRevision: Int,
    ) : FinalizedMatchCloudSyncExecutionResult {
        init {
            require(confirmedCloudRevision > 0) { "Confirmed cloud revisions must be positive." }
        }
    }

    data class Failure(
        val completedStage: FinalizedMatchCloudSyncCompletedStage?,
        val category: FinalizedMatchCloudSyncFailureCategory,
        val conflict: com.hoggamers.rankforge.domain.sync.RevisionConflict? = null,
        val confirmedCloudRevision: Int? = null,
    ) : FinalizedMatchCloudSyncExecutionResult
}

class FinalizedMatchCloudSyncExecutor(
    private val finalizeMatch: suspend (
        match: FinalizedMatchUploadPayload,
        matchResults: List<FinalizedMatchResultUploadPayload>,
        expectedRevision: Int,
    ) -> RevisionWriteResponse,
    private val writeDraftMatch: suspend (
        match: FinalizedMatchUploadPayload,
        matchResults: List<FinalizedMatchResultUploadPayload>,
        expectedRevision: Int,
    ) -> RevisionWriteResponse,
    private val syncOcrEvidence: suspend (
        evidence: List<FinalizedMatchOcrEvidenceUploadPayload>,
    ) -> FinalizedMatchCloudSyncExecutionResult.Failure? = { null },
) {
    suspend fun execute(
        payloads: FinalizedMatchCloudSyncPayloads,
        expectedRevision: Int,
    ): FinalizedMatchCloudSyncExecutionResult {
        var currentRevision = expectedRevision
        var lastConfirmedRevision: Int? = null
        return try {
            payloads.matches.sortedBy { it.matchNumber }.forEach { match ->
                val matchResults = payloads.matchResults.filter { it.matchId == match.id }
                var response = finalizeMatch(match, matchResults, currentRevision)
                if (response.outcome == "missing_data") {
                    val bootstrapResponse = writeDraftMatch(
                        match.copy(status = "draft"),
                        emptyList(),
                        currentRevision,
                    )
                    val bootstrapRevision = bootstrapResponse.positiveRevisionOrNull()
                    if (bootstrapResponse.outcome != "success" || bootstrapRevision == null) {
                        return failureFor(
                            response = bootstrapResponse,
                            expectedRevision = currentRevision,
                            lastConfirmedRevision = lastConfirmedRevision,
                        )
                    }
                    currentRevision = bootstrapRevision
                    lastConfirmedRevision = bootstrapRevision
                    response = finalizeMatch(match, matchResults, currentRevision)
                }

                when (response.outcome) {
                    "success" -> {
                        val revision = response.positiveRevisionOrNull()
                            ?: return validationFailure(lastConfirmedRevision)
                        currentRevision = revision
                        lastConfirmedRevision = revision
                    }

                    "already_finalized" -> {
                        val revision = response.positiveRevisionOrNull()
                            ?: return validationFailure(lastConfirmedRevision)
                        currentRevision = revision
                        lastConfirmedRevision = revision
                    }

                    else -> return failureFor(
                        response = response,
                        expectedRevision = currentRevision,
                        lastConfirmedRevision = lastConfirmedRevision,
                    )
                }
            }
            val confirmedRevision = lastConfirmedRevision
            if (confirmedRevision != null && confirmedRevision > 0) {
                val evidenceFailure = if (payloads.ocrEvidence.isEmpty()) {
                    null
                } else {
                    syncOcrEvidence(payloads.ocrEvidence)
                }
                evidenceFailure?.copy(
                    completedStage = FinalizedMatchCloudSyncCompletedStage.MATCHES,
                    confirmedCloudRevision = confirmedRevision,
                ) ?: FinalizedMatchCloudSyncExecutionResult.Success(confirmedRevision)
            } else {
                validationFailure(null)
            }
        } catch (cancellation: CancellationException) {
            if (cancellation is TimeoutCancellationException) {
                failure(
                    category = FinalizedMatchCloudSyncFailureCategory.NETWORK,
                    confirmedCloudRevision = lastConfirmedRevision,
                )
            } else {
                throw cancellation
            }
        } catch (throwable: Throwable) {
            failure(
                category = throwable.toFinalizedMatchCloudSyncFailureCategory(),
                confirmedCloudRevision = lastConfirmedRevision,
            )
        }
    }

    private fun failureFor(
        response: RevisionWriteResponse,
        expectedRevision: Int,
        lastConfirmedRevision: Int?,
    ): FinalizedMatchCloudSyncExecutionResult.Failure = when (response.outcome) {
        "stale_write", "missing_revision", "finalized_protected" -> failure(
            category = FinalizedMatchCloudSyncFailureCategory.CONFLICT,
            conflict = response.toRevisionConflict(expectedRevision),
            confirmedCloudRevision = lastConfirmedRevision,
        )

        "authentication_required" -> failure(
            category = FinalizedMatchCloudSyncFailureCategory.AUTHENTICATION,
            confirmedCloudRevision = lastConfirmedRevision,
        )

        "unauthorized" -> failure(
            category = FinalizedMatchCloudSyncFailureCategory.AUTHORIZATION,
            confirmedCloudRevision = lastConfirmedRevision,
        )

        else -> failure(
            category = FinalizedMatchCloudSyncFailureCategory.VALIDATION,
            confirmedCloudRevision = lastConfirmedRevision,
        )
    }

    private fun validationFailure(
        confirmedCloudRevision: Int?,
    ) = failure(
        category = FinalizedMatchCloudSyncFailureCategory.VALIDATION,
        confirmedCloudRevision = confirmedCloudRevision,
    )

    private fun failure(
        category: FinalizedMatchCloudSyncFailureCategory,
        conflict: com.hoggamers.rankforge.domain.sync.RevisionConflict? = null,
        confirmedCloudRevision: Int? = null,
    ) = FinalizedMatchCloudSyncExecutionResult.Failure(
        completedStage = confirmedCloudRevision?.let { FinalizedMatchCloudSyncCompletedStage.MATCHES },
        category = category,
        conflict = conflict,
        confirmedCloudRevision = confirmedCloudRevision,
    )
}

private fun RevisionWriteResponse.positiveRevisionOrNull(): Int? =
    revision?.takeIf { it > 0 }

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
