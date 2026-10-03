package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionProbe
import com.hoggamers.rankforge.data.auth.allowsAuthenticatedState
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

            val response = rpcInvoker.invoke(
                TournamentSnapshotWriteParameters(
                    tournament = payloads.tournament,
                    teamSlots = payloads.teamSlots,
                    players = payloads.players,
                    expectedRevision = expectedRevision,
                ),
            )
            if (response.outcome == "success") {
                CloudUploadExecutionResult.Success(response.revision)
            } else {
                CloudUploadExecutionResult.Failure(
                    completedStage = null,
                    category = CloudUploadFailureCategory.CONFLICT,
                    conflict = response.toRevisionConflict(expectedRevision),
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
}
