package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.matching.ResultLobbySlotDecisionReason
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotDecisionStatus
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotMatcher
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotMatchInput
import com.hoggamers.rankforge.domain.matching.TeamCandidateScore
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat

/**
 * A match-local view of one already persisted Group Rotation team identity.
 *
 * [lobbySlotNumber] is deliberately the local 1..12 lobby position. It is not
 * a permanent TeamSlot number.
 */
internal data class GroupRotationMatchOcrTeam(
    val matchId: String,
    val lobbySlotNumber: Int,
    val teamUUID: String,
    val displayName: String,
    val playerNames: List<String?>,
)

internal data class GroupRotationMatchOcrIdentityProjection(
    val matchId: String,
    val tournamentId: String,
    val pairing: GroupPairing,
    val teams: List<GroupRotationMatchOcrTeam>,
)

internal enum class GroupRotationMatchOcrIdentityIssueCode {
    INVALID_MATCH_ID,
    NOT_GROUP_ROTATION,
    TOURNAMENT_ID_MISMATCH,
    UNSELECTED_PAIRING,
    INVALID_LOBBY_SLOT,
    DUPLICATE_LOBBY_SLOT,
    DUPLICATE_TEAM_UUID,
    INVALID_TEAM_UUID,
    INVALID_TEAM_NAME,
    INVALID_PLAYER_NUMBER,
    DUPLICATE_PLAYER_NUMBER,
    TOO_MANY_PLAYERS,
    INVALID_RESULT_POSITION,
    DUPLICATE_RESULT_POSITION,
    ACCEPTED_LOBBY_SLOT_NOT_FOUND,
    DUPLICATE_ACCEPTED_TEAM_UUID,
}

internal data class GroupRotationMatchOcrIdentityIssue(
    val code: GroupRotationMatchOcrIdentityIssueCode,
    val lobbySlotNumber: Int? = null,
    val teamUUID: String? = null,
    val resultPosition: Int? = null,
)

internal sealed interface GroupRotationMatchOcrIdentityProjectionResult {
    data class Loaded(
        val projection: GroupRotationMatchOcrIdentityProjection,
    ) : GroupRotationMatchOcrIdentityProjectionResult

    data class Invalid(
        val issues: List<GroupRotationMatchOcrIdentityIssue>,
    ) : GroupRotationMatchOcrIdentityProjectionResult
}

internal data class GroupRotationMatchOcrCandidate(
    val lobbySlotNumber: Int,
    val teamUUID: String,
    val displayName: String,
    val confidenceScore: Int,
    val detectedPlayerCount: Int,
    val validDetectedPlayerCount: Int,
    val rosterPlayerCount: Int,
    val contributingMatchCount: Int,
    val averageMatchedPlayerScore: Int,
    val coverageScore: Int,
)

internal data class GroupRotationMatchOcrRowSuggestion(
    val resultPosition: Int,
    val resultPlayerNames: List<String?>,
    val proposedLobbySlotNumber: Int?,
    val proposedTeamUUID: String?,
    val proposedTeamDisplayName: String?,
    val automaticLobbySlotNumber: Int?,
    val automaticTeamUUID: String?,
    val automaticTeamDisplayName: String?,
    val winningVotePercent: Int?,
    val decisionStatus: ResultLobbySlotDecisionStatus,
    val decisionReason: ResultLobbySlotDecisionReason,
    val rankedCandidates: List<GroupRotationMatchOcrCandidate>,
)

internal sealed interface GroupRotationMatchOcrSuggestionResult {
    data class Loaded(
        val suggestions: List<GroupRotationMatchOcrRowSuggestion>,
    ) : GroupRotationMatchOcrSuggestionResult

    data class Invalid(
        val issues: List<GroupRotationMatchOcrIdentityIssue>,
    ) : GroupRotationMatchOcrSuggestionResult
}

internal data class GroupRotationMatchOcrAcceptedAssignment(
    val matchId: String,
    val resultPosition: Int,
    val lobbySlotNumber: Int,
    val teamUUID: String,
    val displayName: String,
)

internal sealed interface GroupRotationMatchOcrAcceptedAssignmentResult {
    data class Loaded(
        val assignments: List<GroupRotationMatchOcrAcceptedAssignment>,
    ) : GroupRotationMatchOcrAcceptedAssignmentResult

    data class Invalid(
        val issues: List<GroupRotationMatchOcrIdentityIssue>,
    ) : GroupRotationMatchOcrAcceptedAssignmentResult
}

/**
 * Pure Group Rotation adapter. It consumes only the requested match's lobby
 * OCR result and the immutable UUID-backed match snapshot supplied by the
 * caller; it has no historical roster or cross-match state.
 */
internal object GroupRotationMatchOcrIdentityAdapter {
    fun project(
        tournament: Tournament,
        matchId: String,
        identitySnapshot: GroupRotationMatchIdentitySnapshot,
        lobbyOcrResult: MatchLobbyPlayersOcrResult,
    ): GroupRotationMatchOcrIdentityProjectionResult {
        val issues = validateSnapshot(tournament, matchId, identitySnapshot)
        issues += validateLobbyOcr(lobbyOcrResult)
        if (issues.isNotEmpty()) {
            return GroupRotationMatchOcrIdentityProjectionResult.Invalid(issues.distinct())
        }

        val playersByLobbySlot = lobbyOcrResult.slots.associate { slot ->
            slot.slotNumber to playerNamesByLogicalSlot(slot)
        }
        return GroupRotationMatchOcrIdentityProjectionResult.Loaded(
            GroupRotationMatchOcrIdentityProjection(
                matchId = matchId,
                tournamentId = identitySnapshot.tournamentId,
                pairing = identitySnapshot.pairing,
                teams = identitySnapshot.teams
                    .sortedBy { it.lobbySlotNumber }
                    .map { team ->
                        GroupRotationMatchOcrTeam(
                            matchId = matchId,
                            lobbySlotNumber = team.lobbySlotNumber,
                            teamUUID = team.teamUUID,
                            displayName = team.displayName,
                            playerNames = playersByLobbySlot[team.lobbySlotNumber]
                                ?: List(PLAYERS_PER_TEAM) { null },
                        )
                    },
            ),
        )
    }

    fun suggest(
        projection: GroupRotationMatchOcrIdentityProjection,
        resultRows: List<MatchResultOcrRow>,
    ): GroupRotationMatchOcrSuggestionResult {
        val issues = mutableListOf<GroupRotationMatchOcrIdentityIssue>()
        resultRows
            .groupingBy { it.position }
            .eachCount()
            .filterValues { it > 1 }
            .forEach { (resultPosition, _) ->
                issues += issue(
                    GroupRotationMatchOcrIdentityIssueCode.DUPLICATE_RESULT_POSITION,
                    resultPosition = resultPosition,
                )
            }
        resultRows.forEach { row ->
            if (row.position !in LOBBY_SLOT_NUMBERS) {
                issues += issue(
                    GroupRotationMatchOcrIdentityIssueCode.INVALID_RESULT_POSITION,
                    resultPosition = row.position,
                )
            }
        }
        if (issues.isNotEmpty()) {
            return GroupRotationMatchOcrSuggestionResult.Invalid(issues.distinct())
        }

        val teamsByLobbySlot = projection.teams.associateBy { it.lobbySlotNumber }
        val candidates = projection.teams
            .sortedBy { it.lobbySlotNumber }
            .map { team ->
                com.hoggamers.rankforge.domain.matching.LobbyTeamSlotMatchCandidate(
                    teamSlotNumber = team.lobbySlotNumber,
                    playerNames = team.playerNames,
                )
            }
        val usedAutomaticTeamUuids = mutableSetOf<String>()
        val suggestions = resultRows.sortedBy { it.position }.map { resultRow ->
            val match = ResultLobbySlotMatcher.rank(
                ResultLobbySlotMatchInput(
                    resultPosition = resultRow.position,
                    resultPlayerNames = resultPlayerNames(resultRow),
                    lobbyCandidates = candidates,
                ),
            )
            val proposedTeam = match.proposedTeamSlot?.let(teamsByLobbySlot::get)
            val automaticTeam = match.automaticAssignedTeamSlot?.let(teamsByLobbySlot::get)
            val duplicateAutomatic = automaticTeam != null &&
                !usedAutomaticTeamUuids.add(automaticTeam.teamUUID)
            val decisionStatus = if (duplicateAutomatic) {
                ResultLobbySlotDecisionStatus.MANUAL
            } else {
                match.decisionStatus
            }
            val decisionReason = if (duplicateAutomatic) {
                ResultLobbySlotDecisionReason.DUPLICATE_SLOT_ACROSS_RESULT_ROWS
            } else {
                match.decisionReason
            }
            GroupRotationMatchOcrRowSuggestion(
                resultPosition = resultRow.position,
                resultPlayerNames = match.resultPlayerNames,
                proposedLobbySlotNumber = proposedTeam?.lobbySlotNumber,
                proposedTeamUUID = proposedTeam?.teamUUID,
                proposedTeamDisplayName = proposedTeam?.displayName,
                automaticLobbySlotNumber = automaticTeam
                    ?.takeUnless { duplicateAutomatic }
                    ?.lobbySlotNumber,
                automaticTeamUUID = automaticTeam
                    ?.takeUnless { duplicateAutomatic }
                    ?.teamUUID,
                automaticTeamDisplayName = automaticTeam
                    ?.takeUnless { duplicateAutomatic }
                    ?.displayName,
                winningVotePercent = match.winningVotePercent,
                decisionStatus = decisionStatus,
                decisionReason = decisionReason,
                rankedCandidates = match.rankedCandidates.suggestions.mapNotNull { suggestion ->
                    val candidate = teamsByLobbySlot[suggestion.teamCandidateScore.candidateTeamSlot]
                        ?: return@mapNotNull null
                    suggestion.teamCandidateScore.toCandidate(candidate)
                },
            )
        }
        return GroupRotationMatchOcrSuggestionResult.Loaded(suggestions)
    }

    /** Resolves explicit manual/accepted local lobby positions to saved UUIDs. */
    fun accept(
        projection: GroupRotationMatchOcrIdentityProjection,
        acceptedLobbySlotsByResultPosition: Map<Int, Int>,
    ): GroupRotationMatchOcrAcceptedAssignmentResult {
        val issues = mutableListOf<GroupRotationMatchOcrIdentityIssue>()
        acceptedLobbySlotsByResultPosition.keys.forEach { resultPosition ->
            if (resultPosition !in LOBBY_SLOT_NUMBERS) {
                issues += issue(
                    GroupRotationMatchOcrIdentityIssueCode.INVALID_RESULT_POSITION,
                    resultPosition = resultPosition,
                )
            }
        }
        acceptedLobbySlotsByResultPosition.values.forEach { lobbySlotNumber ->
            if (lobbySlotNumber !in LOBBY_SLOT_NUMBERS ||
                projection.teams.none { it.lobbySlotNumber == lobbySlotNumber }
            ) {
                issues += issue(
                    GroupRotationMatchOcrIdentityIssueCode.ACCEPTED_LOBBY_SLOT_NOT_FOUND,
                    lobbySlotNumber = lobbySlotNumber,
                )
            }
        }
        acceptedLobbySlotsByResultPosition.values
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .forEach { (lobbySlotNumber, _) ->
                val team = projection.teams.firstOrNull { it.lobbySlotNumber == lobbySlotNumber }
                issues += issue(
                    GroupRotationMatchOcrIdentityIssueCode.DUPLICATE_ACCEPTED_TEAM_UUID,
                    lobbySlotNumber = lobbySlotNumber,
                    teamUUID = team?.teamUUID,
                )
            }
        if (issues.isNotEmpty()) {
            return GroupRotationMatchOcrAcceptedAssignmentResult.Invalid(issues.distinct())
        }

        return GroupRotationMatchOcrAcceptedAssignmentResult.Loaded(
            acceptedLobbySlotsByResultPosition
                .toSortedMap()
                .map { (resultPosition, lobbySlotNumber) ->
                    val team = projection.teams.first { it.lobbySlotNumber == lobbySlotNumber }
                    GroupRotationMatchOcrAcceptedAssignment(
                        matchId = projection.matchId,
                        resultPosition = resultPosition,
                        lobbySlotNumber = lobbySlotNumber,
                        teamUUID = team.teamUUID,
                        displayName = team.displayName,
                    )
                },
        )
    }

    private fun validateSnapshot(
        tournament: Tournament,
        matchId: String,
        snapshot: GroupRotationMatchIdentitySnapshot,
    ): MutableList<GroupRotationMatchOcrIdentityIssue> {
        val issues = mutableListOf<GroupRotationMatchOcrIdentityIssue>()
        if (matchId.isBlank()) {
            issues += issue(GroupRotationMatchOcrIdentityIssueCode.INVALID_MATCH_ID)
        }
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            issues += issue(GroupRotationMatchOcrIdentityIssueCode.NOT_GROUP_ROTATION)
        }
        if (snapshot.tournamentId != tournament.id) {
            issues += issue(GroupRotationMatchOcrIdentityIssueCode.TOURNAMENT_ID_MISMATCH)
        }
        if (snapshot.pairing !in tournament.selectedGroupPairings) {
            issues += issue(GroupRotationMatchOcrIdentityIssueCode.UNSELECTED_PAIRING)
        }
        snapshot.teams.forEach { team ->
            if (team.lobbySlotNumber !in LOBBY_SLOT_NUMBERS) {
                issues += issue(
                    GroupRotationMatchOcrIdentityIssueCode.INVALID_LOBBY_SLOT,
                    lobbySlotNumber = team.lobbySlotNumber,
                    teamUUID = team.teamUUID,
                )
            }
            if (team.teamUUID.isBlank()) {
                issues += issue(
                    GroupRotationMatchOcrIdentityIssueCode.INVALID_TEAM_UUID,
                    lobbySlotNumber = team.lobbySlotNumber,
                )
            }
            if (team.displayName.isBlank()) {
                issues += issue(
                    GroupRotationMatchOcrIdentityIssueCode.INVALID_TEAM_NAME,
                    lobbySlotNumber = team.lobbySlotNumber,
                    teamUUID = team.teamUUID,
                )
            }
        }
        snapshot.teams
            .groupBy { it.lobbySlotNumber }
            .filterValues { it.size > 1 }
            .forEach { (lobbySlotNumber, teams) ->
                issues += issue(
                    GroupRotationMatchOcrIdentityIssueCode.DUPLICATE_LOBBY_SLOT,
                    lobbySlotNumber = lobbySlotNumber,
                    teamUUID = teams.first().teamUUID,
                )
            }
        snapshot.teams
            .groupBy { it.teamUUID }
            .filterValues { it.size > 1 }
            .forEach { (teamUUID, teams) ->
                issues += issue(
                    GroupRotationMatchOcrIdentityIssueCode.DUPLICATE_TEAM_UUID,
                    lobbySlotNumber = teams.first().lobbySlotNumber,
                    teamUUID = teamUUID,
                )
            }
        return issues
    }

    private fun validateLobbyOcr(
        lobbyOcrResult: MatchLobbyPlayersOcrResult,
    ): List<GroupRotationMatchOcrIdentityIssue> {
        val issues = mutableListOf<GroupRotationMatchOcrIdentityIssue>()
        lobbyOcrResult.slots.forEach { slot ->
            if (slot.slotNumber !in LOBBY_SLOT_NUMBERS) {
                issues += issue(
                    GroupRotationMatchOcrIdentityIssueCode.INVALID_LOBBY_SLOT,
                    lobbySlotNumber = slot.slotNumber,
                )
            }
            if (slot.players.size > PLAYERS_PER_TEAM) {
                issues += issue(
                    GroupRotationMatchOcrIdentityIssueCode.TOO_MANY_PLAYERS,
                    lobbySlotNumber = slot.slotNumber,
                )
            }
            slot.players
                .groupingBy { it.playerNumber }
                .eachCount()
                .filterValues { it > 1 }
                .forEach { (playerNumber, _) ->
                    issues += issue(
                        GroupRotationMatchOcrIdentityIssueCode.DUPLICATE_PLAYER_NUMBER,
                        lobbySlotNumber = slot.slotNumber,
                        resultPosition = playerNumber,
                    )
                }
            slot.players.forEach { player ->
                if (player.playerNumber !in 1..PLAYERS_PER_TEAM) {
                    issues += issue(
                        GroupRotationMatchOcrIdentityIssueCode.INVALID_PLAYER_NUMBER,
                        lobbySlotNumber = slot.slotNumber,
                        resultPosition = player.playerNumber,
                    )
                }
            }
        }
        lobbyOcrResult.slots
            .groupBy { it.slotNumber }
            .filterValues { it.size > 1 }
            .forEach { (lobbySlotNumber, _) ->
                issues += issue(
                    GroupRotationMatchOcrIdentityIssueCode.DUPLICATE_LOBBY_SLOT,
                    lobbySlotNumber = lobbySlotNumber,
                )
            }
        return issues
    }

    private fun playerNamesByLogicalSlot(slot: MatchLobbyPlayersOcrSlot): List<String?> =
        (1..PLAYERS_PER_TEAM).map { playerNumber ->
            slot.players.firstOrNull { it.playerNumber == playerNumber }?.playerName
        }

    private fun resultPlayerNames(resultRow: MatchResultOcrRow): List<String?> =
        (1..PLAYERS_PER_TEAM).map { playerSlot ->
            resultRow.playerSlots
                .firstOrNull { it.slot == playerSlot }
                ?.player
                ?.let { player ->
                    player.resolvedText
                        .ifBlank { player.ocrText }
                        .takeIf { it.isNotBlank() }
                }
        }

    private fun TeamCandidateScore.toCandidate(
        team: GroupRotationMatchOcrTeam,
    ) = GroupRotationMatchOcrCandidate(
        lobbySlotNumber = team.lobbySlotNumber,
        teamUUID = team.teamUUID,
        displayName = team.displayName,
        confidenceScore = confidenceScore,
        detectedPlayerCount = detectedPlayerCount,
        validDetectedPlayerCount = validDetectedPlayerCount,
        rosterPlayerCount = rosterPlayerCount,
        contributingMatchCount = contributingMatchCount,
        averageMatchedPlayerScore = averageMatchedPlayerScore,
        coverageScore = coverageScore,
    )

    private fun issue(
        code: GroupRotationMatchOcrIdentityIssueCode,
        lobbySlotNumber: Int? = null,
        teamUUID: String? = null,
        resultPosition: Int? = null,
    ) = GroupRotationMatchOcrIdentityIssue(
        code = code,
        lobbySlotNumber = lobbySlotNumber,
        teamUUID = teamUUID,
        resultPosition = resultPosition,
    )

    private const val PLAYERS_PER_TEAM = 4
    private val LOBBY_SLOT_NUMBERS = 1..12
}
