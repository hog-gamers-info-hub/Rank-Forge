package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionProbe
import com.hoggamers.rankforge.data.auth.allowsAuthenticatedState
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import javax.inject.Inject
import javax.inject.Singleton

interface TournamentCloudUploadRemoteDataSource {
    suspend fun upload(payloads: TournamentCloudUploadPayloads, expectedRevision: Int): CloudUploadExecutionResult
}

@Singleton
class SupabaseTournamentCloudUploadRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val sessionProbe: SupabaseAuthSessionProbe,
    private val rpcInvoker: TournamentSnapshotRpcInvoker,
) : TournamentCloudUploadRemoteDataSource {
    override suspend fun upload(payloads: TournamentCloudUploadPayloads, expectedRevision: Int): CloudUploadExecutionResult {
        if (!config.isConfigured) {
            return CloudUploadExecutionResult.Failure(
                completedStage = null,
                category = CloudUploadFailureCategory.VALIDATION,
            )
        }
        return try {
            sessionProbe.awaitInitialization()
            val readiness = sessionProbe.currentReadiness()
            if (!readiness.allowsAuthenticatedState()) {
                return CloudUploadExecutionResult.Failure(
                    completedStage = null,
                    category = CloudUploadFailureCategory.AUTHENTICATION,
                )
            }

            when (payloads.tournament.format) {
                "standard" -> invokeStandard(payloads, expectedRevision)
                "group_rotation" -> invokeGroupRotation(payloads, expectedRevision)
                else -> CloudUploadExecutionResult.Failure(
                    completedStage = null,
                    category = CloudUploadFailureCategory.VALIDATION,
                )
            }
        } catch (cancellation: java.util.concurrent.CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            CloudUploadExecutionResult.Failure(
                completedStage = null,
                category = throwable.toCloudUploadFailureCategory(),
            )
        }
    }

    private suspend fun invokeStandard(
        payloads: TournamentCloudUploadPayloads,
        expectedRevision: Int,
    ): CloudUploadExecutionResult {
        val response = rpcInvoker.invoke(
            TournamentSnapshotWriteParameters(
                tournament = payloads.tournament,
                teamSlots = payloads.teamSlots,
                players = payloads.players,
                expectedRevision = expectedRevision,
            ),
        )
        return if (response.outcome == "success") {
            CloudUploadExecutionResult.Success(response.revision)
        } else {
            CloudUploadExecutionResult.Failure(
                completedStage = null,
                category = CloudUploadFailureCategory.CONFLICT,
                conflict = response.toRevisionConflict(expectedRevision),
            )
        }
    }

    private suspend fun invokeGroupRotation(
        payloads: TournamentCloudUploadPayloads,
        expectedRevision: Int,
    ): CloudUploadExecutionResult {
        val response = rpcInvoker.invokeGroupRotation(
            TournamentSnapshotWriteV2Parameters(
                tournament = payloads.tournament,
                teamSlots = payloads.teamSlots,
                players = payloads.players,
                pairingLobbySlots = payloads.pairingLobbySlots,
                expectedRevision = expectedRevision,
            ),
        )
        return when (response.outcome) {
            "success" -> response.revision
                ?.takeIf { it > 0 }
                ?.let(CloudUploadExecutionResult::Success)
                ?: CloudUploadExecutionResult.Failure(
                    completedStage = null,
                    category = CloudUploadFailureCategory.VALIDATION,
                )
            "stale_write" -> CloudUploadExecutionResult.Failure(
                completedStage = null,
                category = CloudUploadFailureCategory.CONFLICT,
                conflict = response.toRevisionConflict(expectedRevision),
            )
            "missing_revision" -> CloudUploadExecutionResult.Failure(
                completedStage = null,
                category = CloudUploadFailureCategory.CONFLICT,
                conflict = RevisionConflict.MissingRevision,
            )
            "authentication_required" -> CloudUploadExecutionResult.Failure(
                completedStage = null,
                category = CloudUploadFailureCategory.AUTHENTICATION,
            )
            "unauthorized" -> CloudUploadExecutionResult.Failure(
                completedStage = null,
                category = CloudUploadFailureCategory.AUTHORIZATION,
            )
            "validation_failure" -> CloudUploadExecutionResult.Failure(
                completedStage = null,
                category = CloudUploadFailureCategory.VALIDATION,
            )
            else -> CloudUploadExecutionResult.Failure(
                completedStage = null,
                category = CloudUploadFailureCategory.UNKNOWN,
            )
        }
    }
}
