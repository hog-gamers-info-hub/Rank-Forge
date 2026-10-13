package com.hoggamers.rankforge.domain.tournament

import android.util.Log
import com.hoggamers.rankforge.BuildConfig
import com.hoggamers.rankforge.data.cloud.GroupRotationUuidCloudRestorationMapper
import com.hoggamers.rankforge.data.cloud.GroupRotationUuidCloudRestorationMappingResult
import com.hoggamers.rankforge.data.cloud.GroupRotationUuidCloudRestorationRemoteDataSource
import com.hoggamers.rankforge.data.cloud.GroupRotationUuidCloudRestorationRemoteResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Internal, inactive Phone-B restoration entry point for UUID-backed Group
 * Rotation tournaments. Normal restoration navigation is intentionally not
 * wired to this use case yet.
 */
class RestoreGroupRotationUuidCloudUseCase @Inject constructor(
    private val remoteDataSource: GroupRotationUuidCloudRestorationRemoteDataSource,
    private val localRepository: GroupRotationUuidCloudRestorationLocalRepository,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        tournamentId: String,
    ): GroupRotationUuidCloudRestorationResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user
            ?.id
            ?.takeIf { it.isNotBlank() }
            ?: return GroupRotationUuidCloudRestorationResult.AuthenticationRequired.also {
                debugRestore("USE_CASE_AUTHENTICATION_REQUIRED")
            }

        val remote = remoteDataSource.readOwnedGroupRotation(tournamentId)
        debugRestore("USE_CASE_REMOTE_${remote.diagnosticCode()}")
        return when (remote) {
            is GroupRotationUuidCloudRestorationRemoteResult.Failure -> remote.category.toResult().also {
                debugRestore("USE_CASE_RESULT_${it.diagnosticCode()}")
            }
            is GroupRotationUuidCloudRestorationRemoteResult.Success -> when (
                val mapped = GroupRotationUuidCloudRestorationMapper.map(
                    payloads = remote.payloads,
                    expectedOwnerUserId = ownerUserId,
                ).also { debugRestore("USE_CASE_MAPPER_${it.diagnosticCode()}") }
            ) {
                GroupRotationUuidCloudRestorationMappingResult.NotUuidBacked ->
                    GroupRotationUuidCloudRestorationResult.NotUuidBacked.also {
                        debugRestore("USE_CASE_ROOM_NOT_REACHED")
                        debugRestore("USE_CASE_RESULT_${it.diagnosticCode()}")
                    }
                GroupRotationUuidCloudRestorationMappingResult.Invalid ->
                    GroupRotationUuidCloudRestorationResult.ValidationFailure.also {
                        debugRestore("USE_CASE_ROOM_NOT_REACHED")
                        debugRestore("USE_CASE_RESULT_${it.diagnosticCode()}")
                    }
                is GroupRotationUuidCloudRestorationMappingResult.Success -> {
                    debugRestore("USE_CASE_ROOM_PERSISTENCE_REACHED")
                    val localResult = try {
                        localRepository.restoreGroupRotationUuidSnapshot(
                            snapshot = mapped.snapshot,
                            expectedOwnerUserId = ownerUserId,
                        )
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        debugRestore("USE_CASE_ROOM_PERSISTENCE_EXCEPTION")
                        null
                    }
                    when (localResult) {
                        GroupRotationUuidLocalRestoreResult.Restored ->
                            GroupRotationUuidCloudRestorationResult.Restored(
                                mapped.snapshot.parent.tournament.name,
                            )
                        GroupRotationUuidLocalRestoreResult.Conflict ->
                            GroupRotationUuidCloudRestorationResult.Conflict
                        GroupRotationUuidLocalRestoreResult.DeletionBlocked ->
                            GroupRotationUuidCloudRestorationResult.DeletionBlocked
                        GroupRotationUuidLocalRestoreResult.Invalid ->
                            GroupRotationUuidCloudRestorationResult.ValidationFailure
                        null -> GroupRotationUuidCloudRestorationResult.LocalTransactionFailure
                    }.also { result ->
                        debugRestore("USE_CASE_ROOM_RESULT_${localResult.diagnosticCode()}")
                        debugRestore("USE_CASE_RESULT_${result.diagnosticCode()}")
                    }
                }
            }
        }
    }
}

private fun com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory.toResult() =
    when (this) {
        com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory.AUTHENTICATION ->
            GroupRotationUuidCloudRestorationResult.AuthenticationRequired
        com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory.AUTHORIZATION ->
            GroupRotationUuidCloudRestorationResult.AuthorizationFailure
        com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory.NOT_FOUND ->
            GroupRotationUuidCloudRestorationResult.NotFound
        com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory.NETWORK ->
            GroupRotationUuidCloudRestorationResult.NetworkFailure
        com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory.VALIDATION ->
            GroupRotationUuidCloudRestorationResult.ValidationFailure
    }

private const val RESTORE_LOG_TAG = "POINTIQ_V4_RESTORE"

private fun GroupRotationUuidCloudRestorationRemoteResult.diagnosticCode() = when (this) {
    is GroupRotationUuidCloudRestorationRemoteResult.Success -> "SUCCESS"
    is GroupRotationUuidCloudRestorationRemoteResult.Failure -> "FAILURE_${category.name}"
}

private fun GroupRotationUuidCloudRestorationMappingResult.diagnosticCode() = when (this) {
    is GroupRotationUuidCloudRestorationMappingResult.Success -> "SUCCESS"
    GroupRotationUuidCloudRestorationMappingResult.NotUuidBacked -> "NOT_UUID_BACKED"
    GroupRotationUuidCloudRestorationMappingResult.Invalid -> "INVALID"
}

private fun GroupRotationUuidLocalRestoreResult?.diagnosticCode() = when (this) {
    GroupRotationUuidLocalRestoreResult.Restored -> "RESTORED"
    GroupRotationUuidLocalRestoreResult.Conflict -> "CONFLICT"
    GroupRotationUuidLocalRestoreResult.DeletionBlocked -> "DELETION_BLOCKED"
    GroupRotationUuidLocalRestoreResult.Invalid -> "INVALID"
    null -> "EXCEPTION_OR_NO_RESULT"
}

private fun GroupRotationUuidCloudRestorationResult.diagnosticCode() = when (this) {
    is GroupRotationUuidCloudRestorationResult.Restored -> "RESTORED"
    GroupRotationUuidCloudRestorationResult.NotUuidBacked -> "NOT_UUID_BACKED"
    GroupRotationUuidCloudRestorationResult.AuthenticationRequired -> "AUTHENTICATION_REQUIRED"
    GroupRotationUuidCloudRestorationResult.AuthorizationFailure -> "AUTHORIZATION_FAILURE"
    GroupRotationUuidCloudRestorationResult.NotFound -> "NOT_FOUND"
    GroupRotationUuidCloudRestorationResult.ValidationFailure -> "VALIDATION_FAILURE"
    GroupRotationUuidCloudRestorationResult.NetworkFailure -> "NETWORK_FAILURE"
    GroupRotationUuidCloudRestorationResult.Conflict -> "CONFLICT"
    GroupRotationUuidCloudRestorationResult.DeletionBlocked -> "DELETION_BLOCKED"
    GroupRotationUuidCloudRestorationResult.LocalTransactionFailure -> "LOCAL_TRANSACTION_FAILURE"
}

private fun debugRestore(code: String) {
    if (BuildConfig.DEBUG) Log.d(RESTORE_LOG_TAG, code)
}
