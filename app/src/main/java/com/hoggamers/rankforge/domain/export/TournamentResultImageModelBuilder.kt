package com.hoggamers.rankforge.domain.export

import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStanding
import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStandingsEngine
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityNormalizer
import com.hoggamers.rankforge.domain.tournament.KillPointsEngine
import com.hoggamers.rankforge.domain.tournament.MatchTotalEngine
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.PositionPointsEngine
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TieBreakRules
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.formatDerivedSlots
import com.hoggamers.rankforge.domain.tournament.finalizedParticipantResultsOrNull

enum class TournamentResultImageModelFailure {
    NO_FINALIZED_MATCHES,
    TOURNAMENT_IDENTITY_MISMATCH,
    DUPLICATE_MATCH_IDENTITY,
    INVALID_TOURNAMENT_CONFIGURATION,
    INVALID_TEAM_SLOT,
    DUPLICATE_TEAM_SLOT,
    MISSING_TEAM_SLOT,
    MISSING_TEAM_IDENTITY,
    INVALID_FINALIZED_MATCH,
    INVALID_UUID_RESULT_DATA,
    STANDINGS_GENERATION_FAILURE,
}

sealed interface TournamentResultImageModelBuildResult {
    data class Success(
        val model: TournamentResultExportModel,
    ) : TournamentResultImageModelBuildResult

    data class Failure(
        val failures: Set<TournamentResultImageModelFailure>,
    ) : TournamentResultImageModelBuildResult
}

/** Builds the existing PointIQ image model without widening the CSV/PDF export contract. */
class TournamentResultImageModelBuilder(
    private val standingsEngine: CumulativeTournamentStandingsEngine =
        CumulativeTournamentStandingsEngine(),
    private val tieBreakRules: TieBreakRules = TieBreakRules(),
) {
    fun build(input: TournamentCsvExportInput): TournamentResultImageModelBuildResult {
        when (val v4Input = input.groupRotationV4OverallImageInput) {
            is GroupRotationV4OverallImageInput.Valid -> return buildGroupRotationV4(input, v4Input)
            GroupRotationV4OverallImageInput.Invalid -> return TournamentResultImageModelBuildResult.Failure(
                setOf(TournamentResultImageModelFailure.INVALID_UUID_RESULT_DATA),
            )
            null -> Unit
        }
        val failures = linkedSetOf<TournamentResultImageModelFailure>()
        val expectedSlots = expectedSlotsOrNull(input.tournament, failures)
        validateTournamentIdentity(input, failures)
        validateTeamSlots(input, expectedSlots, failures)

        val finalizedMatches = input.matches.filter { match ->
            match.status == MatchStatus.FINALIZED
        }
        if (finalizedMatches.isEmpty()) {
            failures += TournamentResultImageModelFailure.NO_FINALIZED_MATCHES
        }
        if (input.matches.map { match -> match.id }.duplicates().isNotEmpty()) {
            failures += TournamentResultImageModelFailure.DUPLICATE_MATCH_IDENTITY
        }
        finalizedMatches.forEach { match ->
            validateFinalizedMatch(
                tournament = input.tournament,
                match = match,
                expectedSlots = expectedSlots,
                failures = failures,
            )
        }

        if (failures.isNotEmpty() || expectedSlots == null) {
            return TournamentResultImageModelBuildResult.Failure(failures)
        }

        val standings = runCatching {
            tieBreakRules(
                standingsEngine(
                    matches = finalizedMatches,
                    expectedTeamSlots = input.teamSlots,
                ),
            )
        }.getOrElse {
            return TournamentResultImageModelBuildResult.Failure(
                setOf(TournamentResultImageModelFailure.STANDINGS_GENERATION_FAILURE),
            )
        }

        if (standings.map { it.standing.teamSlotNumber }.toSet() != expectedSlots.map { it.slotNumber }.toSet()) {
            return TournamentResultImageModelBuildResult.Failure(
                setOf(TournamentResultImageModelFailure.STANDINGS_GENERATION_FAILURE),
            )
        }

        val teamNamesBySlot = input.teamSlots.associate { slot ->
            slot.slotNumber to slot.teamName.trim()
        }
        return TournamentResultImageModelBuildResult.Success(
            TournamentResultExportModel(
                tournamentName = input.tournament.name,
                stageName = input.tournament.stageName,
                finalizedMatchCount = finalizedMatches.size,
                rows = standings.mapIndexed { index, tieBreakStanding ->
                    tieBreakStanding.toResultExportRow(
                        rank = index + 1,
                        teamName = teamNamesBySlot.getValue(tieBreakStanding.standing.teamSlotNumber),
                    )
                },
            ),
        )
    }

    private fun buildGroupRotationV4(
        input: TournamentCsvExportInput,
        v4Input: GroupRotationV4OverallImageInput.Valid,
    ): TournamentResultImageModelBuildResult {
        val failures = linkedSetOf<TournamentResultImageModelFailure>()
        if (input.tournament.format != TournamentFormat.GROUP_ROTATION) {
            failures += TournamentResultImageModelFailure.INVALID_UUID_RESULT_DATA
        }
        validateTournamentIdentity(input, failures)
        val capacity = when (input.tournament.groupCount) {
            3 -> 18
            4 -> 24
            else -> {
                failures += TournamentResultImageModelFailure.INVALID_TOURNAMENT_CONFIGURATION
                0
            }
        }
        val pairingScope = input.pairingScope
        if (
            pairingScope != null &&
            (
                pairingScope !in input.tournament.selectedGroupPairings ||
                    input.matches.any { match -> match.groupPairing != pairingScope }
                )
        ) {
            failures += TournamentResultImageModelFailure.INVALID_UUID_RESULT_DATA
        }
        val identities = v4Input.teamIdentitySnapshot.teams
        val identitiesByUuid = identities.associateBy { it.teamUUID }
        val activeAssignedTeamUUIDs = v4Input.teamIdentitySnapshot.assignments
            .filterNot { it.nameCleared }
            .map { it.teamUUID }
            .toSet()
        val fullyClearedAssignedTeamUUIDs = v4Input.teamIdentitySnapshot.assignments
            .groupBy { it.teamUUID }
            .filterValues { assignments -> assignments.all { it.nameCleared } }
            .keys
        if (
            identities.size != capacity ||
            identitiesByUuid.size != identities.size ||
            identities.any { identity ->
                identity.tournamentId != input.tournament.id ||
                    identity.teamUUID.isBlank() ||
                    (identity.displayName.trim().isBlank() && identity.teamUUID in activeAssignedTeamUUIDs) ||
                    (identity.displayName.trim().isNotBlank() &&
                        GroupRotationTeamIdentityNormalizer().normalize(identity.displayName) !=
                        identity.normalizedName)
            } ||
            identities.map { it.normalizedName }.filter { it.isNotBlank() }.let { names ->
                names.size != names.toSet().size
            }
        ) {
            failures += TournamentResultImageModelFailure.INVALID_UUID_RESULT_DATA
        }

        validatePairingAssignments(input, v4Input, identitiesByUuid, failures)
        val finalizedMatches = input.matches
            .filter { match -> match.status == MatchStatus.FINALIZED }
            .distinctBy { match -> match.id }
            .sortedWith(compareBy<Match> { it.matchNumber }.thenBy { it.id })
        if (finalizedMatches.isEmpty()) {
            failures += TournamentResultImageModelFailure.NO_FINALIZED_MATCHES
        }
        if (input.matches.map { match -> match.id }.duplicates().isNotEmpty()) {
            failures += TournamentResultImageModelFailure.DUPLICATE_MATCH_IDENTITY
        }
        if (v4Input.finalizedMatchSnapshots.keys != finalizedMatches.map { it.id }.toSet()) {
            failures += TournamentResultImageModelFailure.INVALID_UUID_RESULT_DATA
        }

        val participatingTeamUUIDs = linkedSetOf<String>()
        val totals = identities.associate { identity ->
            identity.teamUUID to MutableUuidStanding()
        }.toMutableMap()
        finalizedMatches.forEach { match ->
            val snapshot = v4Input.finalizedMatchSnapshots[match.id]
            if (snapshot == null) {
                failures += TournamentResultImageModelFailure.INVALID_UUID_RESULT_DATA
            } else {
                validateUuidMatchSnapshot(
                    input = input,
                    match = match,
                    snapshot = snapshot,
                    identitiesByUuid = identitiesByUuid,
                    pairingScope = pairingScope,
                    failures = failures,
                )
                snapshot.participantResults.forEach { result ->
                    if (result.participationStatus == MatchParticipationStatus.PARTICIPATED) {
                        participatingTeamUUIDs += result.teamUUID
                        val placement = result.placement
                        if (placement != null) {
                            val standing = totals[result.teamUUID]
                            if (standing != null) {
                                standing.totalPositionPoints += PositionPointsEngine()(placement)
                                standing.totalKillPoints += KillPointsEngine()(result.kills)
                                standing.totalPoints +=
                                    MatchTotalEngine()(placement, result.kills) + result.pointAdjustment
                                if (placement == 1) standing.firstPlaceFinishes++
                                standing.latestMatchPlacement = placement
                                standing.matchesIncluded++
                            }
                        }
                    }
                }
            }
        }

        val outputIdentities = if (pairingScope == null) {
            identities
        } else {
            if (participatingTeamUUIDs.size !in 1..GroupRotationPairingLobbySlot.MAX_LOBBY_SLOT_NUMBER) {
                failures += TournamentResultImageModelFailure.STANDINGS_GENERATION_FAILURE
            }
            identities.filter { identity -> identity.teamUUID in participatingTeamUUIDs }
        }
        if (failures.isNotEmpty() || identities.size != capacity) {
            return TournamentResultImageModelBuildResult.Failure(failures)
        }

        val orderedIdentities = outputIdentities.sortedBy { identity -> identity.teamUUID }
        val standingsByOrdinal = orderedIdentities.mapIndexed { index, identity ->
            val standing = totals.getValue(identity.teamUUID)
            CumulativeTournamentStanding(
                teamSlotNumber = index + 1,
                totalPositionPoints = standing.totalPositionPoints,
                totalKillPoints = standing.totalKillPoints,
                totalPoints = standing.totalPoints,
                firstPlaceFinishes = standing.firstPlaceFinishes,
                latestMatchPlacement = standing.latestMatchPlacement,
                matchesIncluded = standing.matchesIncluded,
            )
        }
        val orderedStandings = tieBreakRules(standingsByOrdinal)
        val identityByOrdinal = orderedIdentities.withIndex().associate { (index, identity) ->
            index + 1 to identity
        }
        val blankDisplayNameTeamUUIDs = if (pairingScope == null) {
            fullyClearedAssignedTeamUUIDs
        } else {
            v4Input.teamIdentitySnapshot.assignments
                .filter { assignment ->
                    assignment.pairingKey == pairingScope && assignment.nameCleared
                }
                .map { assignment -> assignment.teamUUID }
                .toSet()
        }
        return TournamentResultImageModelBuildResult.Success(
            TournamentResultExportModel(
                tournamentName = input.tournament.name,
                stageName = input.tournament.stageName,
                finalizedMatchCount = finalizedMatches.size,
                pairingScope = pairingScope,
                rows = orderedStandings.mapIndexed { index, tieBreakStanding ->
                    val identity = identityByOrdinal.getValue(tieBreakStanding.standing.teamSlotNumber)
                    tieBreakStanding.toResultExportRow(
                        rank = index + 1,
                        teamName = if (identity.teamUUID in blankDisplayNameTeamUUIDs) {
                            ""
                        } else {
                            identity.displayName.trim()
                        },
                    )
                },
            ),
        )
    }

    private fun validatePairingAssignments(
        input: TournamentCsvExportInput,
        v4Input: GroupRotationV4OverallImageInput.Valid,
        identitiesByUuid: Map<String, GroupRotationTeamIdentity>,
        failures: MutableSet<TournamentResultImageModelFailure>,
    ) {
        val assignments = v4Input.teamIdentitySnapshot.assignments
        if (assignments.any { assignment ->
                assignment.tournamentId != input.tournament.id ||
                    assignment.pairingKey !in input.tournament.selectedGroupPairings ||
                    assignment.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS ||
                    assignment.teamUUID !in identitiesByUuid
            }
        ) {
            failures += TournamentResultImageModelFailure.INVALID_UUID_RESULT_DATA
        }
        if (assignments.groupBy { it.pairingKey to it.lobbySlotNumber }.values.any { it.size > 1 }) {
            failures += TournamentResultImageModelFailure.INVALID_UUID_RESULT_DATA
        }
        if (assignments.groupBy { it.pairingKey }.values.any { pairingAssignments ->
                pairingAssignments.map { it.teamUUID }.toSet().size != pairingAssignments.size
            }
        ) {
            failures += TournamentResultImageModelFailure.INVALID_UUID_RESULT_DATA
        }
    }

    private fun validateUuidMatchSnapshot(
        input: TournamentCsvExportInput,
        match: Match,
        snapshot: GroupRotationMatchPersistenceSnapshot,
        identitiesByUuid: Map<String, GroupRotationTeamIdentity>,
        pairingScope: GroupPairing?,
        failures: MutableSet<TournamentResultImageModelFailure>,
    ) {
        val identity = snapshot.identity
        val teams = identity.teams
        val assignedUuids = teams.map { it.teamUUID }
        val results = snapshot.participantResults
        if (
            identity.tournamentId != input.tournament.id ||
            identity.pairing !in input.tournament.selectedGroupPairings ||
            (pairingScope != null && identity.pairing != pairingScope) ||
            match.groupPairing != identity.pairing ||
            teams.isEmpty() ||
            teams.size > GroupRotationPairingLobbySlot.MAX_LOBBY_SLOT_NUMBER ||
            assignedUuids.toSet().size != assignedUuids.size ||
            teams.any { team ->
                team.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS ||
                    team.teamUUID !in identitiesByUuid
            } ||
            teams.map { it.lobbySlotNumber }.toSet().size != teams.size ||
            results.map { it.teamUUID }.toSet() != assignedUuids.toSet() ||
            results.groupBy { it.teamUUID }.values.any { it.size > 1 } ||
            results.groupBy { it.placement }.filterKeys { it != null }.values.any { it.size > 1 } ||
            results.any { result ->
                result.teamUUID !in assignedUuids ||
                    result.kills < 0 ||
                    when (result.participationStatus) {
                        MatchParticipationStatus.PARTICIPATED ->
                            result.placement == null || result.placement !in 1..12
                        MatchParticipationStatus.NO_SHOW ->
                            result.placement != null || result.kills != 0
                    }
            }
        ) {
            failures += TournamentResultImageModelFailure.INVALID_UUID_RESULT_DATA
        }
    }

    private class MutableUuidStanding {
        var totalPositionPoints: Int = 0
        var totalKillPoints: Int = 0
        var totalPoints: Int = 0
        var firstPlaceFinishes: Int = 0
        var latestMatchPlacement: Int? = null
        var matchesIncluded: Int = 0
    }

    private fun expectedSlotsOrNull(
        tournament: Tournament,
        failures: MutableSet<TournamentResultImageModelFailure>,
    ): List<TeamSlot>? {
        val expected = runCatching { tournament.expectedPermanentSlots() }.getOrElse {
            failures += TournamentResultImageModelFailure.INVALID_TOURNAMENT_CONFIGURATION
            return null
        }
        return expected
    }

    private fun validateTournamentIdentity(
        input: TournamentCsvExportInput,
        failures: MutableSet<TournamentResultImageModelFailure>,
    ) {
        if (
            input.matches.any { match -> match.tournamentId != input.tournament.id } ||
            input.teamSlots.any { slot -> slot.tournamentId != input.tournament.id } ||
            input.rosterPlayers.any { player -> player.tournamentId != input.tournament.id }
        ) {
            failures += TournamentResultImageModelFailure.TOURNAMENT_IDENTITY_MISMATCH
        }
    }

    private fun validateTeamSlots(
        input: TournamentCsvExportInput,
        expectedSlots: List<TeamSlot>?,
        failures: MutableSet<TournamentResultImageModelFailure>,
    ) {
        if (expectedSlots == null) return
        val actualSlotNumbers = input.teamSlots.map { slot -> slot.slotNumber }
        if (actualSlotNumbers.any { slotNumber -> slotNumber !in TeamSlot.TOURNAMENT_SLOT_NUMBERS }) {
            failures += TournamentResultImageModelFailure.INVALID_TEAM_SLOT
        }
        if (actualSlotNumbers.duplicates().isNotEmpty()) {
            failures += TournamentResultImageModelFailure.DUPLICATE_TEAM_SLOT
        }
        val expectedByNumber = expectedSlots.associateBy { slot -> slot.slotNumber }
        if (actualSlotNumbers.toSet() != expectedByNumber.keys) {
            failures += TournamentResultImageModelFailure.MISSING_TEAM_SLOT
        }
        input.teamSlots.forEach { slot ->
            if (slot.teamName.trim().isBlank()) {
                failures += TournamentResultImageModelFailure.MISSING_TEAM_IDENTITY
            }
            if (
                input.tournament.format == TournamentFormat.STANDARD &&
                    slot.group != expectedByNumber[slot.slotNumber]?.group
            ) {
                failures += TournamentResultImageModelFailure.INVALID_TOURNAMENT_CONFIGURATION
            }
        }
    }

    private fun validateFinalizedMatch(
        tournament: Tournament,
        match: Match,
        expectedSlots: List<TeamSlot>?,
        failures: MutableSet<TournamentResultImageModelFailure>,
    ) {
        val participantResults = match.finalizedParticipantResultsOrNull()
        if (participantResults == null || expectedSlots == null) {
            failures += TournamentResultImageModelFailure.INVALID_FINALIZED_MATCH
            return
        }
        val expectedSlotNumbers = expectedSlots.map { slot -> slot.slotNumber }.toSet()
        if (
            participantResults.size !in 1..TeamSlot.MAX_SLOT_NUMBER ||
            participantResults.any { result -> result.teamSlotNumber !in expectedSlotNumbers }
        ) {
            failures += TournamentResultImageModelFailure.INVALID_FINALIZED_MATCH
        }

        when (tournament.format) {
            TournamentFormat.STANDARD -> {
                if (match.groupPairing != null) {
                    failures += TournamentResultImageModelFailure.INVALID_FINALIZED_MATCH
                }
            }
            TournamentFormat.GROUP_ROTATION -> {
                val pairing = match.groupPairing
                val validPairing = pairing != null && pairing in tournament.selectedGroupPairings
                if (!validPairing) {
                    failures += TournamentResultImageModelFailure.INVALID_FINALIZED_MATCH
                    return
                }
            }
        }
    }

    private fun Tournament.expectedPermanentSlots(): List<TeamSlot> = when (format) {
        TournamentFormat.STANDARD -> TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            TeamSlot.create(id, slotNumber)
        }
        TournamentFormat.GROUP_ROTATION -> {
            val count = groupCount ?: error("Group Rotation requires a group count.")
            require(count == 3 || count == 4)
            formatDerivedSlots()
        }
    }

    private fun com.hoggamers.rankforge.domain.tournament.TieBreakStanding.toResultExportRow(
        rank: Int,
        teamName: String,
    ): ResultExportRow {
        val standing: CumulativeTournamentStanding = standing
        return ResultExportRow(
            rank = rank,
            teamName = teamName,
            win = standing.firstPlaceFinishes,
            totalKills = standing.totalKillPoints,
            positionPoints = standing.totalPositionPoints,
            totalPoints = standing.totalPoints,
            matchesPlayed = standing.matchesPlayed,
        )
    }

    private fun <T> List<T>.duplicates(): Set<T> =
        groupingBy { value -> value }.eachCount().filterValues { count -> count > 1 }.keys
}
