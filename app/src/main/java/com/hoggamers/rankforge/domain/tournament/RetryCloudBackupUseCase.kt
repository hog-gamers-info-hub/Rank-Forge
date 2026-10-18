package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface RetryCloudBackupResult {
    data object Success : RetryCloudBackupResult
    data object AuthenticationRequired : RetryCloudBackupResult
    data object AuthorizationFailure : RetryCloudBackupResult
    data object DeletionBlocked : RetryCloudBackupResult
    data object LocalDataIncomplete : RetryCloudBackupResult
    data object RevisionConflict : RetryCloudBackupResult
    data object NetworkFailure : RetryCloudBackupResult
    data object ValidationFailure : RetryCloudBackupResult
    data object QueuePersistenceFailure : RetryCloudBackupResult
    data object PartialFailure : RetryCloudBackupResult
    data object Failure : RetryCloudBackupResult
}

fun interface RetryCloudBackupAction {
    suspend operator fun invoke(tournamentId: String): RetryCloudBackupResult
}

class RetryCloudBackupUseCase @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val authRepository: AuthRepository,
    private val uploadTournament: TournamentCloudUploadAction,
    private val syncFinalizedMatches: FinalizedMatchCloudSyncAction,
    private val deletionIntentRepository: DeletionIntentRepository = NoOpDeletionIntentRepository,
) : RetryCloudBackupAction {
    private val operationMutex = Mutex()

    override suspend operator fun invoke(tournamentId: String): RetryCloudBackupResult =
        operationMutex.withLock {
            execute(tournamentId)
        }

    private suspend fun execute(tournamentId: String): RetryCloudBackupResult {
        val ownerUserId = currentOwnerUserId() ?: return RetryCloudBackupResult.AuthenticationRequired
        val tournament = try {
            tournamentRepository.observeById(tournamentId).first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            return RetryCloudBackupResult.AuthorizationFailure
        } catch (_: Throwable) {
            return RetryCloudBackupResult.LocalDataIncomplete
        } ?: return RetryCloudBackupResult.LocalDataIncomplete

        if (tournament.ownerUserId != ownerUserId) {
            return RetryCloudBackupResult.AuthorizationFailure
        }
        if (tournament.format != TournamentFormat.STANDARD) {
            return RetryCloudBackupResult.LocalDataIncomplete
        }
        if (deletionIntentRepository.isBlockingByTournamentIdAndOwner(tournamentId, ownerUserId)) {
            return RetryCloudBackupResult.DeletionBlocked
        }

        val slots = try {
            tournamentRepository
                .observeSlotsByTournamentIdAndOwner(tournamentId, ownerUserId)
                .first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            return RetryCloudBackupResult.AuthorizationFailure
        } catch (_: Throwable) {
            return RetryCloudBackupResult.LocalDataIncomplete
        }
        val expectedSlots = tournament.formatDerivedSlots()
        if (
            slots.map { it.slotNumber } != expectedSlots.map { it.slotNumber } ||
            slots.any { it.tournamentId != tournamentId }
        ) {
            return RetryCloudBackupResult.LocalDataIncomplete
        }

        val matches = try {
            tournamentRepository
                .observeMatchesByTournamentIdAndOwner(tournamentId, ownerUserId)
                .first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            return RetryCloudBackupResult.AuthorizationFailure
        } catch (_: Throwable) {
            return RetryCloudBackupResult.LocalDataIncomplete
        }
        val finalizedMatches = matches.filter { it.status == MatchStatus.FINALIZED }
        if (
            finalizedMatches.isEmpty() ||
            finalizedMatches.any { !it.hasCompleteStandardFinalizedAggregate() }
        ) {
            return RetryCloudBackupResult.LocalDataIncomplete
        }
        val namedSlotNumbers = slots
            .filter { it.teamName.trim().isNotEmpty() }
            .map { it.slotNumber }
            .toSet()
        if (
            finalizedMatches
                .flatMap { it.finalizedParticipantResultsOrNull().orEmpty() }
                .any { it.teamSlotNumber !in namedSlotNumbers }
        ) {
            return RetryCloudBackupResult.LocalDataIncomplete
        }

        val revision = try {
            tournamentRepository.readLocalRevisionState(tournamentId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            return RetryCloudBackupResult.AuthorizationFailure
        } catch (_: Throwable) {
            return RetryCloudBackupResult.RevisionConflict
        }
        if (
            revision.localRevision == null ||
            revision.baseCloudRevision == null ||
            revision.baseCloudRevision.value <= 0 ||
            revision.localRevision < revision.baseCloudRevision.value
        ) {
            return RetryCloudBackupResult.RevisionConflict
        }
        if (currentOwnerUserId() != ownerUserId) {
            return RetryCloudBackupResult.AuthorizationFailure
        }

        val uploadResult = try {
            uploadTournament(tournamentId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            return RetryCloudBackupResult.AuthorizationFailure
        } catch (_: Throwable) {
            return RetryCloudBackupResult.Failure
        }
        when (val failure = uploadResult.toTournamentRecoveryFailure()) {
            null -> Unit
            else -> return failure
        }

        if (currentOwnerUserId() != ownerUserId) {
            return RetryCloudBackupResult.AuthorizationFailure
        }
        val finalizedResult = try {
            syncFinalizedMatches(tournamentId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            return RetryCloudBackupResult.AuthorizationFailure
        } catch (_: Throwable) {
            return RetryCloudBackupResult.Failure
        }
        return finalizedResult.toFinalizedRecoveryFailure() ?: RetryCloudBackupResult.Success
    }

    private suspend fun currentOwnerUserId(): String? =
        (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user
            ?.id
            ?.takeIf { it.isNotBlank() }

    private fun Match.hasCompleteStandardFinalizedAggregate(): Boolean {
        val participantResults = finalizedParticipantResultsOrNull() ?: return false
        return participantResults.isNotEmpty()
    }
}

private fun QueueAwareActionResult<TournamentCloudUploadResult>.toTournamentRecoveryFailure(): RetryCloudBackupResult? {
    if (primaryResult is TournamentCloudUploadResult.Success) return null
    if (queueRecordingResult == QueueRecordingResult.PERSISTENCE_FAILED) {
        return RetryCloudBackupResult.QueuePersistenceFailure
    }
    return when (primaryResult) {
        TournamentCloudUploadResult.AuthenticationRequired -> RetryCloudBackupResult.AuthenticationRequired
        TournamentCloudUploadResult.AuthorizationFailure -> RetryCloudBackupResult.AuthorizationFailure
        TournamentCloudUploadResult.NetworkFailure -> RetryCloudBackupResult.NetworkFailure
        TournamentCloudUploadResult.ValidationFailure,
        TournamentCloudUploadResult.TournamentLimitReached,
        -> RetryCloudBackupResult.ValidationFailure
        is TournamentCloudUploadResult.Conflict -> RetryCloudBackupResult.RevisionConflict
        is TournamentCloudUploadResult.PartialFailure -> RetryCloudBackupResult.PartialFailure
        is TournamentCloudUploadResult.Success -> null
    }
}

private fun QueueAwareActionResult<FinalizedMatchCloudSyncResult>.toFinalizedRecoveryFailure(): RetryCloudBackupResult? {
    if (primaryResult is FinalizedMatchCloudSyncResult.Success) return null
    if (queueRecordingResult == QueueRecordingResult.PERSISTENCE_FAILED) {
        return RetryCloudBackupResult.QueuePersistenceFailure
    }
    return when (primaryResult) {
        FinalizedMatchCloudSyncResult.AuthenticationRequired -> RetryCloudBackupResult.AuthenticationRequired
        FinalizedMatchCloudSyncResult.AuthorizationFailure -> RetryCloudBackupResult.AuthorizationFailure
        FinalizedMatchCloudSyncResult.NetworkFailure -> RetryCloudBackupResult.NetworkFailure
        FinalizedMatchCloudSyncResult.ValidationFailure -> RetryCloudBackupResult.ValidationFailure
        is FinalizedMatchCloudSyncResult.Conflict -> RetryCloudBackupResult.RevisionConflict
        is FinalizedMatchCloudSyncResult.PartialFailure -> RetryCloudBackupResult.PartialFailure
        is FinalizedMatchCloudSyncResult.Success -> null
    }
}
