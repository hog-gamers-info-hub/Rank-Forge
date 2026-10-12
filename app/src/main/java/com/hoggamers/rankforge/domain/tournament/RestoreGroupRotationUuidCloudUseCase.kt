package com.hoggamers.rankforge.domain.tournament

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
            ?: return GroupRotationUuidCloudRestorationResult.AuthenticationRequired

        return when (val remote = remoteDataSource.readOwnedGroupRotation(tournamentId)) {
            is GroupRotationUuidCloudRestorationRemoteResult.Failure -> remote.category.toResult()
            is GroupRotationUuidCloudRestorationRemoteResult.Success -> when (
                val mapped = GroupRotationUuidCloudRestorationMapper.map(
                    payloads = remote.payloads,
                    expectedOwnerUserId = ownerUserId,
                )
            ) {
                GroupRotationUuidCloudRestorationMappingResult.NotUuidBacked ->
                    GroupRotationUuidCloudRestorationResult.NotUuidBacked
                GroupRotationUuidCloudRestorationMappingResult.Invalid ->
                    GroupRotationUuidCloudRestorationResult.ValidationFailure
                is GroupRotationUuidCloudRestorationMappingResult.Success -> {
                    val localResult = try {
                        localRepository.restoreGroupRotationUuidSnapshot(
                            snapshot = mapped.snapshot,
                            expectedOwnerUserId = ownerUserId,
                        )
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
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
