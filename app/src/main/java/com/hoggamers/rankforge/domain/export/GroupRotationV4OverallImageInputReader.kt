package com.hoggamers.rankforge.domain.export

import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentityPersistenceRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceIssueCode
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityReadResult
import com.hoggamers.rankforge.domain.tournament.LegacyGroupRotationFinalizedRestorationValidator
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.finalizedParticipantResultsOrNull
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

sealed interface GroupRotationV4OverallImageReadResult {
    data class Available(
        val input: GroupRotationV4OverallImageInput.Valid,
    ) : GroupRotationV4OverallImageReadResult

    data object Legacy : GroupRotationV4OverallImageReadResult
    data object Invalid : GroupRotationV4OverallImageReadResult
}

/** Reads the authoritative UUID data used by the Group Rotation overall image only. */
interface GroupRotationV4OverallImageInputReader {
    suspend fun read(
        tournament: Tournament,
        matches: List<Match>,
        persistedTeamSlots: List<TeamSlot>,
    ): GroupRotationV4OverallImageReadResult

    object LegacyOnly : GroupRotationV4OverallImageInputReader {
        override suspend fun read(
            tournament: Tournament,
            matches: List<Match>,
            persistedTeamSlots: List<TeamSlot>,
        ): GroupRotationV4OverallImageReadResult = GroupRotationV4OverallImageReadResult.Legacy
    }
}

class DefaultGroupRotationV4OverallImageInputReader @Inject constructor(
    private val teamIdentityRepository: GroupRotationTeamIdentityLocalRepository,
    private val matchIdentityRepository: GroupRotationMatchIdentityPersistenceRepository,
) : GroupRotationV4OverallImageInputReader {
    override suspend fun read(
        tournament: Tournament,
        matches: List<Match>,
        persistedTeamSlots: List<TeamSlot>,
    ): GroupRotationV4OverallImageReadResult {
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            return GroupRotationV4OverallImageReadResult.Legacy
        }

        val finalizedMatches = matches.filter { it.status == MatchStatus.FINALIZED }
        if (finalizedMatches.isEmpty()) {
            return GroupRotationV4OverallImageReadResult.Legacy
        }

        val snapshots = linkedMapOf<String, com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceSnapshot>()
        var hasLegacyMatch = false
        for (match in finalizedMatches) {
            val result = try {
                matchIdentityRepository.read(tournament, match.id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            when (result) {
                is GroupRotationMatchPersistenceResult.Loaded -> snapshots[match.id] = result.snapshot
                is GroupRotationMatchPersistenceResult.Invalid -> {
                    if (
                        result.issues.size == 1 &&
                            result.issues.single().code ==
                            GroupRotationMatchPersistenceIssueCode.MATCH_IDENTITY_NOT_SAVED &&
                            match.finalizedParticipantResultsOrNull() != null &&
                            LegacyGroupRotationFinalizedRestorationValidator.isValid(
                                tournament = tournament,
                                persistedTeamSlots = persistedTeamSlots,
                                matches = listOf(match),
                            )
                    ) {
                        hasLegacyMatch = true
                    } else {
                        return GroupRotationV4OverallImageReadResult.Invalid
                    }
                }
                is GroupRotationMatchPersistenceResult.Saved,
                null,
                -> return GroupRotationV4OverallImageReadResult.Invalid
            }
        }

        if (snapshots.isEmpty() && hasLegacyMatch) {
            return GroupRotationV4OverallImageReadResult.Legacy
        }
        if (hasLegacyMatch || snapshots.size != finalizedMatches.size) {
            return GroupRotationV4OverallImageReadResult.Invalid
        }

        val teamReadResult = try {
            teamIdentityRepository.read(tournament)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        val teamSnapshot = when (val result = teamReadResult) {
            is GroupRotationTeamIdentityReadResult.Loaded -> result.snapshot
            else -> return GroupRotationV4OverallImageReadResult.Invalid
        }
        return GroupRotationV4OverallImageReadResult.Available(
            GroupRotationV4OverallImageInput.Valid(
                teamIdentitySnapshot = teamSnapshot,
                finalizedMatchSnapshots = snapshots,
            ),
        )
    }

}
