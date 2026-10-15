package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class SaveGroupRotationTeamIdentitySetupUseCase @Inject constructor(
    private val repository: GroupRotationTeamIdentityLocalRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: GroupRotationTeamIdentityLocalRepository) : this(
        repository,
        SetupMutationUnauthenticatedAuthRepository,
    )

    suspend operator fun invoke(
        tournament: Tournament,
        candidate: GroupRotationTeamSetupCandidate,
        pairing: GroupPairing,
    ): GroupRotationTeamIdentitySetupSaveResult {
        val signedInUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user
            ?.id
            ?.takeIf { it.isNotBlank() }
        if (signedInUserId == null) {
            return GroupRotationTeamIdentitySetupSaveResult.AuthenticationRequired
        }

        if (tournament.ownerUserId != null && tournament.ownerUserId != signedInUserId) {
            return GroupRotationTeamIdentitySetupSaveResult.OwnershipMismatch
        }
        if (candidate.entries.any { it.pairing != pairing }) {
            return GroupRotationTeamIdentitySetupSaveResult.InvalidSetup(
                listOf(
                    GroupRotationTeamSetupIssue(
                        code = GroupRotationTeamSetupIssueCode.EXTRA_PAIRING,
                        pairingKey = candidate.entries.first { it.pairing != pairing }.pairing.canonicalKey,
                    ),
                ),
            )
        }
        return when (
            val result = repository.savePairing(
                tournament = tournament,
                pairingKey = pairing,
                entries = candidate.entries.map {
                    GroupRotationPairingTeamIdentityEntry(
                        lobbySlotNumber = it.lobbySlotNumber,
                        displayName = it.teamName,
                    )
                },
            )
        ) {
            is GroupRotationTeamIdentityWriteResult.Saved ->
                GroupRotationTeamIdentitySetupSaveResult.Saved(result.snapshot)
            GroupRotationTeamIdentityWriteResult.ProtectedHistory ->
                GroupRotationTeamIdentitySetupSaveResult.ProtectedHistory
            is GroupRotationTeamIdentityWriteResult.Invalid ->
                GroupRotationTeamIdentitySetupSaveResult.InvalidSetup(result.issues.toTeamSetupIssues(pairing))
            GroupRotationTeamIdentityWriteResult.Unsupported ->
                GroupRotationTeamIdentitySetupSaveResult.Unsupported
        }
    }

    suspend fun savePairings(
        tournament: Tournament,
        candidatesByPairing: Map<GroupPairing, GroupRotationTeamSetupCandidate>,
    ): GroupRotationTeamIdentitySetupSaveResult {
        val signedInUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user
            ?.id
            ?.takeIf { it.isNotBlank() }
        if (signedInUserId == null) {
            return GroupRotationTeamIdentitySetupSaveResult.AuthenticationRequired
        }
        if (tournament.ownerUserId != null && tournament.ownerUserId != signedInUserId) {
            return GroupRotationTeamIdentitySetupSaveResult.OwnershipMismatch
        }
        val invalidCandidate = candidatesByPairing.entries.firstNotNullOfOrNull { (pairing, candidate) ->
            candidate.entries.firstOrNull { it.pairing != pairing }?.let { entry ->
                GroupRotationTeamSetupIssue(
                    code = GroupRotationTeamSetupIssueCode.EXTRA_PAIRING,
                    pairingKey = entry.pairing.canonicalKey,
                )
            }
        }
        if (invalidCandidate != null) {
            return GroupRotationTeamIdentitySetupSaveResult.InvalidSetup(listOf(invalidCandidate))
        }
        val result = repository.savePairings(
            tournament = tournament,
            entriesByPairing = candidatesByPairing.mapValues { (_, candidate) ->
                candidate.entries.map {
                    GroupRotationPairingTeamIdentityEntry(
                        lobbySlotNumber = it.lobbySlotNumber,
                        displayName = it.teamName,
                    )
                }
            },
        )
        val fallbackPairing = candidatesByPairing.keys.firstOrNull()
            ?: tournament.selectedGroupPairings.firstOrNull()
            ?: return GroupRotationTeamIdentitySetupSaveResult.Unsupported
        return when (result) {
            is GroupRotationTeamIdentityWriteResult.Saved ->
                GroupRotationTeamIdentitySetupSaveResult.Saved(result.snapshot)
            GroupRotationTeamIdentityWriteResult.ProtectedHistory ->
                GroupRotationTeamIdentitySetupSaveResult.ProtectedHistory
            is GroupRotationTeamIdentityWriteResult.Invalid ->
                GroupRotationTeamIdentitySetupSaveResult.InvalidSetup(
                    result.issues.toTeamSetupIssues(fallbackPairing),
                )
            GroupRotationTeamIdentityWriteResult.Unsupported ->
                GroupRotationTeamIdentitySetupSaveResult.Unsupported
        }
    }
}

sealed interface GroupRotationTeamIdentitySetupSaveResult {
    data class Saved(val snapshot: GroupRotationTeamIdentityLocalSnapshot) : GroupRotationTeamIdentitySetupSaveResult
    data object AuthenticationRequired : GroupRotationTeamIdentitySetupSaveResult
    data object OwnershipMismatch : GroupRotationTeamIdentitySetupSaveResult
    data object ProtectedHistory : GroupRotationTeamIdentitySetupSaveResult
    data class InvalidSetup(val issues: List<GroupRotationTeamSetupIssue>) : GroupRotationTeamIdentitySetupSaveResult
    data object Unsupported : GroupRotationTeamIdentitySetupSaveResult
}

private fun List<GroupRotationTeamIdentityIssue>.toTeamSetupIssues(
    pairing: GroupPairing,
): List<GroupRotationTeamSetupIssue> = if (isEmpty()) {
    listOf(GroupRotationTeamSetupIssue(GroupRotationTeamSetupIssueCode.AMBIGUOUS_EXISTING_IDENTITY))
} else {
    map { issue ->
        GroupRotationTeamSetupIssue(
            code = when (issue.code) {
                GroupRotationTeamIdentityIssueCode.BLANK_TEAM_NAME -> GroupRotationTeamSetupIssueCode.BLANK_TEAM_NAME
                GroupRotationTeamIdentityIssueCode.DUPLICATE_NORMALIZED_NAME,
                GroupRotationTeamIdentityIssueCode.DUPLICATE_TEAM_IN_PAIRING,
                -> GroupRotationTeamSetupIssueCode.DUPLICATE_TEAM_IDENTITY_WITHIN_PAIRING
                GroupRotationTeamIdentityIssueCode.TEAM_CAPACITY_EXCEEDED -> GroupRotationTeamSetupIssueCode.TOO_MANY_UNIQUE_TEAMS
                GroupRotationTeamIdentityIssueCode.INVALID_LOBBY_SLOT -> GroupRotationTeamSetupIssueCode.INVALID_LOBBY_SLOT
                else -> GroupRotationTeamSetupIssueCode.AMBIGUOUS_EXISTING_IDENTITY
            },
            pairingKey = issue.pairingKey ?: pairing.canonicalKey,
            lobbySlotNumber = issue.lobbySlotNumber,
            normalizedIdentity = issue.normalizedName,
        )
    }.distinct()
}
