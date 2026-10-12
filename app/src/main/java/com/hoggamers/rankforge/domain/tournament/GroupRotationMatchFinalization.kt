package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.first

data class FinalizeGroupRotationMatchInput(
    val tournamentId: String,
    val matchId: String,
    val participantResults: List<GroupRotationMatchParticipantResult>,
    val ocrEvidence: PreservedMatchOcrEvidence? = null,
)

enum class GroupRotationMatchFinalizationFailure {
    AUTHENTICATION_REQUIRED,
    NOT_GROUP_ROTATION,
    TOURNAMENT_NOT_FOUND,
    MATCH_NOT_FOUND,
    MATCH_NOT_DRAFT,
    INVALID_DATA,
}

sealed interface GroupRotationMatchFinalizationResult {
    data class Finalized(
        val match: Match,
        val snapshot: GroupRotationMatchPersistenceSnapshot,
    ) : GroupRotationMatchFinalizationResult

    data class Rejected(
        val reason: GroupRotationMatchFinalizationFailure,
    ) : GroupRotationMatchFinalizationResult
}

/** Separate repository boundary for UUID-backed Group Rotation finalization. */
interface GroupRotationMatchFinalizationRepository {
    suspend fun finalizeGroupRotationMatchByOwner(
        input: FinalizeGroupRotationMatchInput,
        ownerUserId: String,
    ): GroupRotationMatchFinalizationResult
}

/** Authentication boundary for the inactive UUID-backed finalization route. */
class FinalizeGroupRotationMatchUseCase(
    private val repository: GroupRotationMatchFinalizationRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: GroupRotationMatchFinalizationRepository) : this(
        repository,
        SetupMutationUnauthenticatedAuthRepository,
    )

    suspend operator fun invoke(
        input: FinalizeGroupRotationMatchInput,
    ): GroupRotationMatchFinalizationResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id
            ?.takeIf { it.isNotBlank() }
            ?: return GroupRotationMatchFinalizationResult.Rejected(
                GroupRotationMatchFinalizationFailure.AUTHENTICATION_REQUIRED,
            )
        return repository.finalizeGroupRotationMatchByOwner(input, ownerUserId)
    }
}

enum class GroupRotationMatchFinalizationIssueCode {
    EMPTY_IDENTITY_SNAPSHOT,
    INVALID_IDENTITY_SNAPSHOT,
    MISSING_RESULT,
    DUPLICATE_RESULT,
    RESULT_TEAM_NOT_IN_MATCH,
    INVALID_PLACEMENT,
    INVALID_KILLS,
    INVALID_NO_SHOW_RESULT,
    DUPLICATE_PLACEMENT,
    PLACEMENTS_NOT_GAP_FREE,
    NO_PARTICIPATED_TEAM,
    INVALID_OCR_EVIDENCE,
}

data class GroupRotationMatchFinalizationIssue(
    val code: GroupRotationMatchFinalizationIssueCode,
    val teamUUID: String? = null,
)

/** Final-only validation. Draft persistence intentionally remains partial. */
class GroupRotationMatchFinalizationValidator {
    fun validate(
        snapshot: GroupRotationMatchIdentitySnapshot,
        participantResults: List<GroupRotationMatchParticipantResult>,
    ): List<GroupRotationMatchFinalizationIssue> {
        val issues = mutableListOf<GroupRotationMatchFinalizationIssue>()
        val teams = snapshot.teams
        if (teams.isEmpty()) {
            issues += GroupRotationMatchFinalizationIssueCode.EMPTY_IDENTITY_SNAPSHOT.issue()
        }
        if (
            teams.any {
                it.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS ||
                    it.teamUUID.isBlank() ||
                    it.displayName.isBlank()
            } ||
            teams.groupBy { it.lobbySlotNumber }.any { it.value.size > 1 } ||
            teams.groupBy { it.teamUUID }.any { it.value.size > 1 }
        ) {
            issues += GroupRotationMatchFinalizationIssueCode.INVALID_IDENTITY_SNAPSHOT.issue()
        }

        val expectedTeamUuids = teams.map { it.teamUUID }.toSet()
        participantResults
            .groupBy { it.teamUUID }
            .filterValues { it.size > 1 }
            .keys
            .forEach { teamUUID ->
                issues += GroupRotationMatchFinalizationIssue(
                    GroupRotationMatchFinalizationIssueCode.DUPLICATE_RESULT,
                    teamUUID,
                )
            }
        participantResults
            .filter { it.teamUUID !in expectedTeamUuids }
            .forEach { result ->
                issues += GroupRotationMatchFinalizationIssue(
                    GroupRotationMatchFinalizationIssueCode.RESULT_TEAM_NOT_IN_MATCH,
                    result.teamUUID,
                )
            }
        expectedTeamUuids
            .filterNot { teamUUID -> participantResults.any { it.teamUUID == teamUUID } }
            .forEach { teamUUID ->
                issues += GroupRotationMatchFinalizationIssue(
                    GroupRotationMatchFinalizationIssueCode.MISSING_RESULT,
                    teamUUID,
                )
            }

        val participated = participantResults.filter {
            it.teamUUID in expectedTeamUuids &&
                it.participationStatus == MatchParticipationStatus.PARTICIPATED
        }
        if (participated.isEmpty() && expectedTeamUuids.isNotEmpty()) {
            issues += GroupRotationMatchFinalizationIssueCode.NO_PARTICIPATED_TEAM.issue()
        }
        val expectedPlacements = 1..participated.size
        participated.forEach { result ->
            if (result.placement == null || result.placement !in expectedPlacements) {
                issues += GroupRotationMatchFinalizationIssue(
                    GroupRotationMatchFinalizationIssueCode.INVALID_PLACEMENT,
                    result.teamUUID,
                )
            }
            if (result.kills < 0) {
                issues += GroupRotationMatchFinalizationIssue(
                    GroupRotationMatchFinalizationIssueCode.INVALID_KILLS,
                    result.teamUUID,
                )
            }
        }
        participantResults
            .filter {
                it.teamUUID in expectedTeamUuids &&
                    it.participationStatus == MatchParticipationStatus.NO_SHOW
            }
            .forEach { result ->
                if (result.placement != null || result.kills != 0) {
                    issues += GroupRotationMatchFinalizationIssue(
                        GroupRotationMatchFinalizationIssueCode.INVALID_NO_SHOW_RESULT,
                        result.teamUUID,
                    )
                }
            }
        participated
            .groupBy { it.placement }
            .filterValues { it.size > 1 }
            .keys
            .forEach { placement ->
                issues += GroupRotationMatchFinalizationIssue(
                    GroupRotationMatchFinalizationIssueCode.DUPLICATE_PLACEMENT,
                )
            }
        val actualPlacements = participated.mapNotNull { it.placement }.toSet()
        if (participated.isNotEmpty() && actualPlacements != expectedPlacements.toSet()) {
            issues += GroupRotationMatchFinalizationIssueCode.PLACEMENTS_NOT_GAP_FREE.issue()
        }
        return issues.distinct()
    }

    fun isValidOcrEvidence(
        evidence: PreservedMatchOcrEvidence,
        snapshot: GroupRotationMatchIdentitySnapshot,
    ): Boolean {
        val lobbySlots = snapshot.teams.map { it.lobbySlotNumber }.toSet()
        return evidence.tournamentId == snapshot.tournamentId &&
            evidence.matchId.isNotBlank() &&
            evidence.provenance.isNotBlank() &&
            evidence.rows.map { it.rowIndex }.toSet() == (0 until TeamSlot.MAX_SLOT_NUMBER).toSet() &&
            evidence.correctionSnapshots.map { it.rowIndex }.distinct().size ==
                evidence.correctionSnapshots.size &&
            evidence.correctionSnapshots.all { correction ->
                correction.correctedTeamSlot in lobbySlots &&
                    correction.correctedPlacement in TeamSlot.SLOT_NUMBERS &&
                    correction.correctedKills >= 0
            }
    }

    private fun GroupRotationMatchFinalizationIssueCode.issue() =
        GroupRotationMatchFinalizationIssue(this)
}
