package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.isSignedInAs
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.sync.RecordSyncQueueOutcome
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import com.hoggamers.rankforge.domain.sync.queueFailureCategory
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ReplaceTournamentRosterInCloudUseCase @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val localSnapshotRepository: TournamentCloudUploadLocalSnapshotRepository,
    private val authRepository: AuthRepository,
    private val cloudReplacementRepository: TournamentRosterCloudReplacementRepository,
    private val cloudRestorationRepository: TournamentCloudRestorationRepository,
    private val tournamentUploadRetryAction: TournamentCloudUploadRetryAction,
    private val queueRecorder: RecordSyncQueueOutcome,
    private val deletionIntentRepository: DeletionIntentRepository = NoOpDeletionIntentRepository,
) : TournamentRosterCloudReplacementAction, TournamentRosterCloudReplacementRetryAction {
    override suspend operator fun invoke(
        tournamentId: String,
    ): QueueAwareActionResult<TournamentRosterCloudReplacementResult> {
        val ownerUserId = currentOwnerUserId()
            ?: return QueueAwareActionResult(TournamentRosterCloudReplacementResult.AuthenticationRequired, QueueRecordingResult.NOT_REQUIRED)
        if (!hasOwnedTournament(tournamentId, ownerUserId)) {
            return QueueAwareActionResult(TournamentRosterCloudReplacementResult.ValidationFailure, QueueRecordingResult.NOT_REQUIRED)
        }
        val result = executeForRetry(tournamentId, ownerUserId)
        if (!authRepository.isSignedInAs(ownerUserId)) {
            return QueueAwareActionResult(result, QueueRecordingResult.NOT_REQUIRED)
        }
        return record(result, tournamentId, ownerUserId)
    }

    override suspend fun executeForRetry(
        tournamentId: String,
    ): TournamentRosterCloudReplacementResult = currentOwnerUserId()?.let { ownerUserId ->
        executeForRetry(tournamentId, ownerUserId)
    } ?: TournamentRosterCloudReplacementResult.AuthenticationRequired

    override suspend fun executeForRetry(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): TournamentRosterCloudReplacementResult {
        if (currentOwnerUserId() != expectedOwnerUserId) return TournamentRosterCloudReplacementResult.AuthorizationFailure
        if (deletionIntentRepository.isBlockingByTournamentIdAndOwner(tournamentId, expectedOwnerUserId)) {
            return TournamentRosterCloudReplacementResult.ValidationFailure
        }

        val snapshot = try {
            localSnapshotRepository.readCloudUploadSnapshotByOwner(
                tournamentId = tournamentId,
                ownerUserId = expectedOwnerUserId,
            ) ?: return TournamentRosterCloudReplacementResult.ValidationFailure
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return TournamentRosterCloudReplacementResult.ValidationFailure
        }

        val result = try {
            if (currentOwnerUserId() != expectedOwnerUserId) return TournamentRosterCloudReplacementResult.AuthorizationFailure
            if (snapshot.expectedCloudRevision == 0) {
                synchronizeFirstCloud(snapshot, expectedOwnerUserId)
            } else {
                replaceRosterIfOwner(snapshot.toRosterReplacement(), expectedOwnerUserId)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            TournamentRosterCloudReplacementResult.UnknownFailure
        }
        if (!authRepository.isSignedInAs(expectedOwnerUserId)) {
            return TournamentRosterCloudReplacementResult.AuthorizationFailure
        }
        if (result is TournamentRosterCloudReplacementResult.Success) {
            if (
                tournamentRepository.confirmCloudRevisionByOwner(
                    tournamentId,
                    expectedOwnerUserId,
                    result.newCloudRevision,
                ) != OwnerScopedTournamentMutationResult.Saved
            ) {
                return TournamentRosterCloudReplacementResult.AuthorizationFailure
            }
        }
        return result
    }

    private suspend fun synchronizeFirstCloud(
        snapshot: TournamentCloudUploadSnapshot,
        ownerId: String,
    ): TournamentRosterCloudReplacementResult = when (
        val cloud = cloudRestorationRepository.readOwnedTournament(snapshot.tournament.id)
    ) {
        is TournamentCloudRestorationRemoteResult.Success -> {
            if (
                cloud.value.tournament.id != snapshot.tournament.id ||
                cloud.value.tournament.ownerUserId != ownerId
            ) {
                return TournamentRosterCloudReplacementResult.AuthorizationFailure
            }
            val cloudRevision = cloud.value.cloudRevision?.value
                ?: return TournamentRosterCloudReplacementResult.Conflict(
                    com.hoggamers.rankforge.domain.sync.RevisionConflict.MissingRevision,
                )
            if (!authRepository.isSignedInAs(ownerId)) {
                return TournamentRosterCloudReplacementResult.AuthorizationFailure
            }
            if (
                tournamentRepository.establishCloudBaselineByOwner(
                    snapshot.tournament.id,
                    ownerId,
                    cloudRevision,
                ) != OwnerScopedTournamentMutationResult.Saved
            ) {
                return TournamentRosterCloudReplacementResult.AuthorizationFailure
            }
            if (currentOwnerUserId() != ownerId) {
                return TournamentRosterCloudReplacementResult.AuthorizationFailure
            }
            val freshSnapshot = localSnapshotRepository.readCloudUploadSnapshotByOwner(
                tournamentId = snapshot.tournament.id,
                ownerUserId = ownerId,
            ) ?: return TournamentRosterCloudReplacementResult.ValidationFailure
            if (currentOwnerUserId() != ownerId) {
                return TournamentRosterCloudReplacementResult.AuthorizationFailure
            }
            when {
                freshSnapshot.tournament.format == TournamentFormat.STANDARD ->
                    replaceRosterIfOwner(freshSnapshot.toRosterReplacement(), ownerId)
                freshSnapshot.pairingLobbySlots.isEmpty() ->
                    replaceRosterIfOwner(freshSnapshot.toRosterReplacement(), ownerId)
                else -> {
                    when (val upload = tournamentUploadRetryAction.executeForRetry(snapshot.tournament.id, ownerId)) {
                        is TournamentCloudUploadResult.Success -> {
                            val postUploadSnapshot = localSnapshotRepository.readCloudUploadSnapshotByOwner(
                                tournamentId = snapshot.tournament.id,
                                ownerUserId = ownerId,
                            ) ?: return TournamentRosterCloudReplacementResult.ValidationFailure
                            if (postUploadSnapshot.expectedCloudRevision != upload.confirmedCloudRevision) {
                                return TournamentRosterCloudReplacementResult.Conflict(
                                    com.hoggamers.rankforge.domain.sync.RevisionConflict.MissingRevision,
                                )
                            }
                            replaceRosterIfOwner(postUploadSnapshot.toRosterReplacement(), ownerId)
                        }
                        else -> upload.toRosterResult()
                    }
                }
            }
        }
        is TournamentCloudRestorationRemoteResult.Failure -> when (cloud.category) {
            TournamentCloudRestorationFailureCategory.NOT_FOUND ->
                tournamentUploadRetryAction.executeForRetry(snapshot.tournament.id, ownerId).toRosterResult()
            TournamentCloudRestorationFailureCategory.AUTHENTICATION ->
                TournamentRosterCloudReplacementResult.AuthenticationRequired
            TournamentCloudRestorationFailureCategory.AUTHORIZATION ->
                TournamentRosterCloudReplacementResult.AuthorizationFailure
            TournamentCloudRestorationFailureCategory.NETWORK ->
                TournamentRosterCloudReplacementResult.NetworkFailure
            TournamentCloudRestorationFailureCategory.VALIDATION ->
                TournamentRosterCloudReplacementResult.ValidationFailure
        }
    }

    private suspend fun record(
        result: TournamentRosterCloudReplacementResult,
        tournamentId: String,
        ownerUserId: String,
    ): QueueAwareActionResult<TournamentRosterCloudReplacementResult> = QueueAwareActionResult(
        primaryResult = result,
        queueRecordingResult = queueRecorder.record(
            ownerUserId = ownerUserId,
            operation = SyncQueueOperationType.ROSTER_REPLACEMENT,
            tournamentId = tournamentId,
            status = result.queueStatus(),
            failureCategory = result.queueFailureCategory() ?: result.queueStatus().name,
        ),
    )

    private suspend fun currentOwnerUserId(): String? = try {
        (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private suspend fun hasOwnedTournament(tournamentId: String, ownerUserId: String): Boolean =
        tournamentRepository.observeByIdAndOwner(tournamentId, ownerUserId).first() != null

    private suspend fun replaceRosterIfOwner(
        snapshot: TournamentRosterCloudReplacement,
        expectedOwnerUserId: String,
    ): TournamentRosterCloudReplacementResult =
        if (currentOwnerUserId() != expectedOwnerUserId) {
            TournamentRosterCloudReplacementResult.AuthorizationFailure
        } else {
            cloudReplacementRepository.replace(snapshot, expectedOwnerUserId)
        }
}

private fun TournamentCloudUploadSnapshot.toRosterReplacement(): TournamentRosterCloudReplacement =
    TournamentRosterCloudReplacement(
        tournament = tournament,
        slots = slots,
        rosters = rosters,
        expectedCloudRevision = expectedCloudRevision,
    )

private fun TournamentRosterCloudReplacementResult.queueStatus() = when (this) {
    is TournamentRosterCloudReplacementResult.Success -> SyncQueueStatus.COMPLETED
    TournamentRosterCloudReplacementResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION
    TournamentRosterCloudReplacementResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK
    TournamentRosterCloudReplacementResult.ValidationFailure,
    TournamentRosterCloudReplacementResult.BlockedByExistingMatches,
    -> SyncQueueStatus.FAILED_VALIDATION
    TournamentRosterCloudReplacementResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION
    is TournamentRosterCloudReplacementResult.Conflict -> SyncQueueStatus.FAILED_CONFLICT
    TournamentRosterCloudReplacementResult.UnknownFailure -> SyncQueueStatus.FAILED_UNKNOWN
}

private fun TournamentRosterCloudReplacementResult.queueFailureCategory(): String? = when (this) {
    TournamentRosterCloudReplacementResult.BlockedByExistingMatches -> "ROSTER_REPLACEMENT_BLOCKED_BY_MATCHES"
    is TournamentRosterCloudReplacementResult.Conflict -> conflict.queueFailureCategory()
    else -> null
}

private fun TournamentCloudUploadResult.toRosterResult(): TournamentRosterCloudReplacementResult = when (this) {
    is TournamentCloudUploadResult.Success ->
        TournamentRosterCloudReplacementResult.Success(confirmedCloudRevision)
    TournamentCloudUploadResult.AuthenticationRequired ->
        TournamentRosterCloudReplacementResult.AuthenticationRequired
    TournamentCloudUploadResult.ValidationFailure ->
        TournamentRosterCloudReplacementResult.ValidationFailure
    TournamentCloudUploadResult.AuthorizationFailure ->
        TournamentRosterCloudReplacementResult.AuthorizationFailure
    TournamentCloudUploadResult.NetworkFailure,
    TournamentCloudUploadResult.TournamentLimitReached,
    is TournamentCloudUploadResult.PartialFailure,
    -> TournamentRosterCloudReplacementResult.NetworkFailure
    is TournamentCloudUploadResult.Conflict ->
        TournamentRosterCloudReplacementResult.Conflict(conflict)
}
