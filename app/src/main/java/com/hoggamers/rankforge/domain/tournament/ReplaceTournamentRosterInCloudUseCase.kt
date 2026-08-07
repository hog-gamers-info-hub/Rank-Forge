package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.RecordSyncQueueOutcome
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import com.hoggamers.rankforge.domain.sync.expectedRevisionForWrite
import com.hoggamers.rankforge.domain.sync.queueFailureCategory
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ReplaceTournamentRosterInCloudUseCase @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val authRepository: AuthRepository,
    private val cloudReplacementRepository: TournamentRosterCloudReplacementRepository,
    private val cloudUploadRepository: TournamentCloudUploadRepository,
    private val cloudRestorationRepository: TournamentCloudRestorationRepository,
    private val queueRecorder: RecordSyncQueueOutcome,
) : TournamentRosterCloudReplacementAction, TournamentRosterCloudReplacementRetryAction {
    override suspend operator fun invoke(
        tournamentId: String,
    ): QueueAwareActionResult<TournamentRosterCloudReplacementResult> = record(
        result = executeForRetry(tournamentId),
        tournamentId = tournamentId,
    )

    override suspend fun executeForRetry(
        tournamentId: String,
    ): TournamentRosterCloudReplacementResult {
        val authState = try {
            authRepository.observeAuthState().first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return TournamentRosterCloudReplacementResult.AuthenticationRequired
        }

        val ownerId = (authState as? AuthState.SignedIn)?.user?.id
            ?.takeIf { it.isNotBlank() }
            ?: return TournamentRosterCloudReplacementResult.AuthenticationRequired

        val snapshot = try {
            val tournament = tournamentRepository.observeById(tournamentId).first()
                ?: return TournamentRosterCloudReplacementResult.ValidationFailure
            TournamentRosterCloudReplacement(
                tournament = tournament,
                slots = tournamentRepository.observeSlotsByTournamentId(tournamentId).first(),
                rosters = tournamentRepository.observeRosterByTournamentId(tournamentId).first(),
                expectedCloudRevision = tournamentRepository
                    .readLocalRevisionState(tournamentId)
                    .expectedRevisionForWrite()
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return TournamentRosterCloudReplacementResult.ValidationFailure
        }

        val result = try {
            if (snapshot.expectedCloudRevision == 0) {
                synchronizeFirstCloud(snapshot, ownerId)
            } else {
                cloudReplacementRepository.replace(snapshot, ownerId)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            TournamentRosterCloudReplacementResult.UnknownFailure
        }
        if (result is TournamentRosterCloudReplacementResult.Success) {
            tournamentRepository.confirmCloudRevision(tournamentId, result.newCloudRevision)
        }
        return result
    }

    private suspend fun synchronizeFirstCloud(
        snapshot: TournamentRosterCloudReplacement,
        ownerId: String,
    ): TournamentRosterCloudReplacementResult = when (
        val cloud = cloudRestorationRepository.readOwnedTournament(snapshot.tournament.id)
    ) {
        is TournamentCloudRestorationRemoteResult.Success -> {
            val cloudRevision = cloud.value.cloudRevision?.value
                ?: return TournamentRosterCloudReplacementResult.Conflict(
                    com.hoggamers.rankforge.domain.sync.RevisionConflict.MissingRevision,
                )
            tournamentRepository.establishCloudBaseline(snapshot.tournament.id, cloudRevision)
            cloudReplacementRepository.replace(
                snapshot.copy(expectedCloudRevision = cloudRevision),
                ownerId,
            )
        }
        is TournamentCloudRestorationRemoteResult.Failure -> when (cloud.category) {
            TournamentCloudRestorationFailureCategory.NOT_FOUND ->
                cloudUploadRepository.upload(
                    TournamentCloudUploadSnapshot(
                        tournament = snapshot.tournament,
                        slots = snapshot.slots,
                        rosters = snapshot.rosters,
                        expectedCloudRevision = 0,
                    ),
                    ownerId,
                ).toRosterResult()
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
    ): QueueAwareActionResult<TournamentRosterCloudReplacementResult> = QueueAwareActionResult(
        primaryResult = result,
        queueRecordingResult = queueRecorder.record(
            operation = SyncQueueOperationType.ROSTER_REPLACEMENT,
            tournamentId = tournamentId,
            status = result.queueStatus(),
            failureCategory = result.queueFailureCategory() ?: result.queueStatus().name,
        ),
    )
}

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
    is TournamentCloudUploadResult.PartialFailure,
    -> TournamentRosterCloudReplacementResult.NetworkFailure
    is TournamentCloudUploadResult.Conflict ->
        TournamentRosterCloudReplacementResult.Conflict(conflict)
}
