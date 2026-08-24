package com.hoggamers.rankforge.data.tournament

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import com.hoggamers.rankforge.domain.tournament.CreateMatchRepositoryResult
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchCreationFailure
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MAX_MATCHES_PER_TOURNAMENT
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.analyzeTeamSlotParticipation
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsFailure
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsRepositoryResult
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsFailure
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsRepositoryResult
import com.hoggamers.rankforge.domain.tournament.MatchDraftFieldValues
import com.hoggamers.rankforge.domain.tournament.OwnerScopedMatchMutationResult
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchFailure
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchRepositoryResult
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionFailure
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionRecord
import com.hoggamers.rankforge.domain.tournament.PreservedMatchOcrEvidence
import com.hoggamers.rankforge.domain.tournament.SubmitMatchCorrectionRepositoryResult
import com.hoggamers.rankforge.domain.tournament.finalizedParticipantResultsOrNull
import com.hoggamers.rankforge.domain.tournament.isValidCorrectionSnapshot
import com.hoggamers.rankforge.domain.tournament.correctedMatchPlacements
import com.hoggamers.rankforge.domain.tournament.correctedMatchKills
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.LocalRevisionState

@Singleton
class InMemoryTournamentRepository @Inject constructor() : TournamentRepository {
    private val tournaments = MutableStateFlow<List<Tournament>>(emptyList())
    private val slotsByTournamentId = MutableStateFlow<Map<String, List<TeamSlot>>>(emptyMap())
    private val rostersByTournamentAndSlot = MutableStateFlow<Map<RosterKey, List<RosterPlayer>>>(emptyMap())
    private val matchesByTournamentId = MutableStateFlow<Map<String, List<Match>>>(emptyMap())
    private val draftValuesByMatch = MutableStateFlow<Map<DraftKey, Map<Int, MatchDraftFieldValues>>>(emptyMap())
    private val preservedOcrEvidenceByMatch = MutableStateFlow<Map<String, PreservedMatchOcrEvidence>>(emptyMap())
    private val cloudRevisions = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val baseCloudRevisions = MutableStateFlow<Map<String, Int?>>(emptyMap())

    override suspend fun create(tournament: Tournament) {
        tournaments.update { current ->
            if (current.any { it.id == tournament.id }) current else current + tournament
        }
        slotsByTournamentId.update { current ->
            if (current.containsKey(tournament.id)) {
                current
            } else {
                current + (tournament.id to TeamSlot.fixedSlotsForTournament(tournament.id))
            }
        }
        cloudRevisions.update { current -> current + (tournament.id to (current[tournament.id] ?: 1)) }
        baseCloudRevisions.update { current -> current + (tournament.id to (current[tournament.id] ?: 1)) }
}

private fun buildLegacyParticipantResults(
    registeredTeamSlots: Set<Int>,
    placements: List<MatchPlacement>,
    kills: List<MatchKill>,
): List<MatchParticipantResult> {
    val placementsBySlot = placements.associateBy { it.teamSlotNumber }
    val killsBySlot = kills.associateBy { it.teamSlotNumber }
    return registeredTeamSlots.sorted().map { teamSlotNumber ->
        val placement = placementsBySlot[teamSlotNumber]
        val kill = killsBySlot[teamSlotNumber]
        if (placement != null && kill != null) {
            MatchParticipantResult(
                teamSlotNumber,
                MatchParticipationStatus.PARTICIPATED,
                placement.position,
                kill.kills,
            )
        } else {
            MatchParticipantResult(
                teamSlotNumber,
                MatchParticipationStatus.NO_SHOW,
                null,
                0,
            )
        }
    }
}

private fun List<MatchParticipantResult>.isValidSnapshotFor(
    registeredTeamSlots: Set<Int>,
    positionedTeamSlots: Set<Int>,
): Boolean {
    val resultsBySlot = associateBy { it.teamSlotNumber }
    val participated = filter { it.participationStatus == MatchParticipationStatus.PARTICIPATED }
    val noShows = filter { it.participationStatus == MatchParticipationStatus.NO_SHOW }
    return size == registeredTeamSlots.size &&
        resultsBySlot.size == size &&
        resultsBySlot.keys == registeredTeamSlots &&
        participated.map { it.teamSlotNumber }.toSet() == positionedTeamSlots &&
        noShows.map { it.teamSlotNumber }.toSet() == registeredTeamSlots - positionedTeamSlots &&
        participated.mapNotNull { it.placement }.toSet() == (1..positionedTeamSlots.size).toSet() &&
        participated.mapNotNull { it.placement }.distinct().size == participated.size
}

    override suspend fun readLocalRevisionState(tournamentId: String): LocalRevisionState =
        cloudRevisions.value[tournamentId]?.let { revision ->
            LocalRevisionState(
                localRevision = revision,
                baseCloudRevision = baseCloudRevisions.value[tournamentId]?.let(::CloudRevision),
            )
        } ?: LocalRevisionState.Missing

    override suspend fun confirmCloudRevision(tournamentId: String, cloudRevision: Int) {
        cloudRevisions.update { it + (tournamentId to cloudRevision) }
        baseCloudRevisions.update { it + (tournamentId to cloudRevision) }
    }

    override suspend fun confirmCloudRevisionByOwner(
        tournamentId: String,
        ownerUserId: String,
        cloudRevision: Int,
    ): com.hoggamers.rankforge.domain.tournament.OwnerScopedTournamentMutationResult = if (
        tournaments.value.any { it.id == tournamentId && it.ownerUserId == ownerUserId }
    ) {
        confirmCloudRevision(tournamentId, cloudRevision)
        com.hoggamers.rankforge.domain.tournament.OwnerScopedTournamentMutationResult.Saved
    } else {
        com.hoggamers.rankforge.domain.tournament.OwnerScopedTournamentMutationResult.TournamentNotFound
    }

    override suspend fun establishCloudBaselineByOwner(
        tournamentId: String,
        ownerUserId: String,
        cloudRevision: Int,
    ): com.hoggamers.rankforge.domain.tournament.OwnerScopedTournamentMutationResult = if (
        tournaments.value.any { it.id == tournamentId && it.ownerUserId == ownerUserId }
    ) {
        establishCloudBaseline(tournamentId, cloudRevision)
        com.hoggamers.rankforge.domain.tournament.OwnerScopedTournamentMutationResult.Saved
    } else {
        com.hoggamers.rankforge.domain.tournament.OwnerScopedTournamentMutationResult.TournamentNotFound
    }

    override suspend fun rebaseCloudRevisionForConflictResolutionByOwner(
        tournamentId: String,
        ownerUserId: String,
        cloudRevision: Int,
    ): com.hoggamers.rankforge.domain.tournament.OwnerScopedTournamentMutationResult = if (
        tournaments.value.any { it.id == tournamentId && it.ownerUserId == ownerUserId }
    ) {
        require(cloudRevision > 0)
        val localRevision = cloudRevisions.value[tournamentId] ?: 1
        cloudRevisions.update { it + (tournamentId to localRevision) }
        baseCloudRevisions.update { it + (tournamentId to cloudRevision) }
        com.hoggamers.rankforge.domain.tournament.OwnerScopedTournamentMutationResult.Saved
    } else {
        com.hoggamers.rankforge.domain.tournament.OwnerScopedTournamentMutationResult.TournamentNotFound
    }

    override suspend fun establishCloudBaseline(tournamentId: String, cloudRevision: Int) {
        require(cloudRevision > 0)
        if (cloudRevisions.value[tournamentId] == null) {
            cloudRevisions.update { it + (tournamentId to 1) }
        }
        baseCloudRevisions.update { it + (tournamentId to cloudRevision) }
    }

    override fun observeAll(): Flow<List<Tournament>> = tournaments

    override fun observeById(tournamentId: String): Flow<Tournament?> =
        tournaments.map { current -> current.firstOrNull { it.id == tournamentId } }

    override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> =
        combine(tournaments, slotsByTournamentId) { currentTournaments, currentSlots ->
            if (currentTournaments.none { it.id == tournamentId }) {
                emptyList()
            } else {
                currentSlots[tournamentId]
                    ?.takeIf { slots -> slots.map { it.slotNumber } == TeamSlot.SLOT_NUMBERS.toList() }
                    ?: TeamSlot.fixedSlotsForTournament(tournamentId)
            }
        }

    override suspend fun saveTeamNames(
        tournamentId: String,
        teamNamesBySlotNumber: Map<Int, String>,
    ) {
        teamNamesBySlotNumber.keys.forEach { slotNumber ->
            require(slotNumber in TeamSlot.SLOT_NUMBERS) { "Team slot number must be between 1 and 12." }
        }
        if (tournaments.value.none { it.id == tournamentId }) return

        invalidateConfirmation(tournamentId)

        slotsByTournamentId.update { current ->
            val currentSlots = current[tournamentId]
                ?.takeIf { slots -> slots.map { it.slotNumber } == TeamSlot.SLOT_NUMBERS.toList() }
                ?: TeamSlot.fixedSlotsForTournament(tournamentId)
            current + (
                tournamentId to currentSlots.map { slot ->
                    if (teamNamesBySlotNumber.containsKey(slot.slotNumber)) {
                        slot.copy(teamName = teamNamesBySlotNumber.getValue(slot.slotNumber).trim())
                    } else {
                        slot
                    }
                }
                )
        }
    }

    override fun observeRosterByTournamentAndSlot(
        tournamentId: String,
        slotNumber: Int,
    ): Flow<List<RosterPlayer>> {
        require(slotNumber in TeamSlot.SLOT_NUMBERS) {
            "Team slot number must be between 1 and 12."
        }
        return combine(tournaments, rostersByTournamentAndSlot) { currentTournaments, currentRosters ->
            if (currentTournaments.none { it.id == tournamentId }) {
                emptyList()
            } else {
                currentRosters[RosterKey(tournamentId, slotNumber)].orEmpty()
            }
        }
    }

    override fun observeRosterByTournamentId(
        tournamentId: String,
    ): Flow<Map<Int, List<RosterPlayer>>> = combine(
        tournaments,
        rostersByTournamentAndSlot,
    ) { currentTournaments, currentRosters ->
        if (currentTournaments.none { it.id == tournamentId }) {
            emptyMap()
        } else {
            currentRosters
                .filterKeys { it.tournamentId == tournamentId }
                .mapKeys { (key, _) -> key.slotNumber }
        }
    }

    override suspend fun saveRoster(
        tournamentId: String,
        slotNumber: Int,
        players: List<RosterPlayer>,
    ) {
        require(slotNumber in TeamSlot.SLOT_NUMBERS) {
            "Team slot number must be between 1 and 12."
        }
        require(players.size <= RosterPlayer.MAX_PLAYERS) {
            "A team roster cannot contain more than six players."
        }
        require(players.all { player ->
            player.tournamentId == tournamentId && player.slotNumber == slotNumber
        }) {
            "Roster players must belong to the requested tournament and team slot."
        }
        if (tournaments.value.none { it.id == tournamentId }) return

        invalidateConfirmation(tournamentId)

        rostersByTournamentAndSlot.update { current ->
            current + (RosterKey(tournamentId, slotNumber) to players.toList())
        }
    }

    override suspend fun confirmTournament(tournamentId: String): Boolean {
        if (tournaments.value.none { it.id == tournamentId }) return false

        var didConfirm = false
        tournaments.update { current ->
            current.map { tournament ->
                if (tournament.id == tournamentId && tournament.status == TournamentStatus.DRAFT) {
                    didConfirm = true
                    tournament.copy(status = TournamentStatus.CONFIRMED)
                } else {
                    tournament
                }
            }
        }
        return didConfirm
    }

    override fun observeMatchesByTournamentId(tournamentId: String): Flow<List<Match>> =
        combine(tournaments, matchesByTournamentId) { currentTournaments, currentMatches ->
            if (currentTournaments.none { it.id == tournamentId }) {
                emptyList()
            } else {
                currentMatches[tournamentId].orEmpty()
            }
        }

    override fun observeMatchById(matchId: String): Flow<Match?> =
        matchesByTournamentId.map { currentMatches ->
            currentMatches.values.asSequence().flatten().firstOrNull { it.id == matchId }
        }

    override fun observeMatchByIdAndOwner(matchId: String, ownerUserId: String): Flow<Match?> =
        combine(tournaments, observeMatchById(matchId)) { currentTournaments, match ->
            match?.takeIf { candidate ->
                currentTournaments.any { it.id == candidate.tournamentId && it.ownerUserId == ownerUserId }
            }
        }

    override suspend fun createDraftMatch(match: Match): CreateMatchRepositoryResult {
        val tournament = tournaments.value.firstOrNull { it.id == match.tournamentId }
            ?: return CreateMatchRepositoryResult.Rejected(MatchCreationFailure.TOURNAMENT_NOT_FOUND)
        val participation = slotsByTournamentId.value[match.tournamentId]
            .orEmpty()
            .analyzeTeamSlotParticipation()
        if (participation.activeCount == 0) {
            return CreateMatchRepositoryResult.Rejected(MatchCreationFailure.NO_PARTICIPATING_TEAMS)
        }
        val currentMatches = matchesByTournamentId.value[match.tournamentId].orEmpty()
        if (currentMatches.any { it.id == match.id }) {
            return CreateMatchRepositoryResult.Rejected(MatchCreationFailure.DUPLICATE_ID)
        }
        if (currentMatches.any { it.matchNumber == match.matchNumber }) {
            return CreateMatchRepositoryResult.Rejected(MatchCreationFailure.DUPLICATE_MATCH_NUMBER)
        }
        if (currentMatches.size >= MAX_MATCHES_PER_TOURNAMENT) {
            return CreateMatchRepositoryResult.Rejected(MatchCreationFailure.LIMIT_REACHED)
        }

        matchesByTournamentId.update { current ->
            current + (match.tournamentId to (current[match.tournamentId].orEmpty() + match))
        }
        return CreateMatchRepositoryResult.Created
    }

    override suspend fun createDraftMatchByOwner(
        match: Match,
        ownerUserId: String,
    ): CreateMatchRepositoryResult = if (
        tournaments.value.none { it.id == match.tournamentId && it.ownerUserId == ownerUserId }
    ) {
        CreateMatchRepositoryResult.Rejected(MatchCreationFailure.TOURNAMENT_NOT_FOUND)
    } else {
        createDraftMatch(match)
    }

    override suspend fun saveDraftMatchPlacements(
        matchId: String,
        placements: List<MatchPlacement>,
    ): SaveMatchPlacementsRepositoryResult {
        val match = matchesByTournamentId.value.values
            .flatten()
            .firstOrNull { it.id == matchId }
            ?: return SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.MATCH_NOT_FOUND)
        if (match.status != MatchStatus.DRAFT) {
            return SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.MATCH_NOT_DRAFT)
        }
        if (placements.any { it.teamSlotNumber !in TeamSlot.SLOT_NUMBERS }) {
            return SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.INVALID_TEAM_SLOT)
        }
        if (placements.any { it.position !in TeamSlot.SLOT_NUMBERS }) {
            return SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.INVALID_POSITION)
        }
        if (placements.map { it.teamSlotNumber }.distinct().size != placements.size) {
            return SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.DUPLICATE_TEAM_SLOT)
        }
        if (placements.map { it.position }.distinct().size != placements.size) {
            return SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.DUPLICATE_POSITION)
        }

        matchesByTournamentId.update { current ->
            current.mapValues { (_, matches) ->
                matches.map { existing ->
                    if (existing.id == matchId) existing.copy(placements = placements.toList()) else existing
                }
            }
        }
        return SaveMatchPlacementsRepositoryResult.Saved
    }

    override suspend fun saveDraftMatchPlacementsByOwner(
        matchId: String,
        ownerUserId: String,
        placements: List<MatchPlacement>,
    ): SaveMatchPlacementsRepositoryResult = if (!isOwnedMatch(matchId, ownerUserId)) {
        SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.MATCH_NOT_FOUND)
    } else {
        saveDraftMatchPlacements(matchId, placements)
    }

    override suspend fun saveDraftMatchKills(
        matchId: String,
        kills: List<MatchKill>,
    ): SaveMatchKillsRepositoryResult {
        val match = matchesByTournamentId.value.values
            .flatten()
            .firstOrNull { it.id == matchId }
            ?: return SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.MATCH_NOT_FOUND)
        if (match.status != MatchStatus.DRAFT) {
            return SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.MATCH_NOT_DRAFT)
        }
        if (kills.any { it.teamSlotNumber !in TeamSlot.SLOT_NUMBERS }) {
            return SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.INVALID_TEAM_SLOT)
        }
        if (kills.any { it.kills < 0 }) {
            return SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.INVALID_KILLS)
        }
        if (kills.map { it.teamSlotNumber }.distinct().size != kills.size) {
            return SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.DUPLICATE_TEAM_SLOT)
        }

        matchesByTournamentId.update { current ->
            current.mapValues { (_, matches) ->
                matches.map { existing ->
                    if (existing.id == matchId) existing.copy(kills = kills.toList()) else existing
                }
            }
        }
        return SaveMatchKillsRepositoryResult.Saved
    }

    override suspend fun saveDraftMatchKillsByOwner(
        matchId: String,
        ownerUserId: String,
        kills: List<MatchKill>,
    ): SaveMatchKillsRepositoryResult = if (!isOwnedMatch(matchId, ownerUserId)) {
        SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.MATCH_NOT_FOUND)
    } else {
        saveDraftMatchKills(matchId, kills)
    }

    override suspend fun finalizeDraftMatch(
        matchId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>?,
    ): FinalizeMatchRepositoryResult {
        val match = matchesByTournamentId.value.values
            .flatten()
            .firstOrNull { it.id == matchId }
            ?: return FinalizeMatchRepositoryResult.Rejected(FinalizeMatchFailure.MATCH_NOT_FOUND)
        if (match.status != MatchStatus.DRAFT) {
            return FinalizeMatchRepositoryResult.Rejected(FinalizeMatchFailure.MATCH_NOT_DRAFT)
        }
        val participation = slotsByTournamentId.value[match.tournamentId]
            .orEmpty()
            .analyzeTeamSlotParticipation()
        val expectedTeamSlots = participation.activeSlotNumbers.toSet()
        val positionedTeamSlots = placements.map { it.teamSlotNumber }.toSet()
        val expectedPlacements = 1..placements.size
        val finalizedParticipantResults = participantResults
            ?: buildLegacyParticipantResults(expectedTeamSlots, placements, kills)
        val hasValidParticipantAwareResult =
            participation.isReadyForMatchCreation &&
                placements.isNotEmpty() &&
                placements.all {
                    it.teamSlotNumber in expectedTeamSlots && it.position in expectedPlacements
                } &&
                kills.all {
                    it.teamSlotNumber in positionedTeamSlots && it.kills >= 0
                } &&
                kills.map { it.teamSlotNumber }.toSet() == positionedTeamSlots &&
                placements.map { it.position }.toSet() == expectedPlacements.toSet() &&
                placements.map { it.teamSlotNumber }.distinct().size == placements.size &&
                kills.map { it.teamSlotNumber }.distinct().size == kills.size &&
                placements.map { it.position }.distinct().size == placements.size &&
                finalizedParticipantResults.isValidSnapshotFor(expectedTeamSlots, positionedTeamSlots)
        if (!hasValidParticipantAwareResult) {
            return FinalizeMatchRepositoryResult.Rejected(FinalizeMatchFailure.INVALID_DATA)
        }

        val finalizedMatch = match.copy(
            status = MatchStatus.FINALIZED,
            placements = placements.toList(),
            kills = kills.toList(),
            participantResults = finalizedParticipantResults.sortedBy { it.teamSlotNumber },
        )
        matchesByTournamentId.update { current ->
            current.mapValues { (_, matches) ->
                matches.map { existing -> if (existing.id == matchId) finalizedMatch else existing }
            }
        }
        draftValuesByMatch.update { current -> current - DraftKey(match.tournamentId, matchId) }
        return FinalizeMatchRepositoryResult.Finalized(finalizedMatch)
    }

    override suspend fun finalizeDraftMatchByOwner(
        matchId: String,
        ownerUserId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>?,
    ): FinalizeMatchRepositoryResult = if (!isOwnedMatch(matchId, ownerUserId)) {
        FinalizeMatchRepositoryResult.Rejected(FinalizeMatchFailure.MATCH_NOT_FOUND)
    } else {
        finalizeDraftMatch(matchId, placements, kills, participantResults)
    }

    override suspend fun finalizeDraftMatchWithOcrEvidence(
        matchId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>?,
        evidence: PreservedMatchOcrEvidence,
    ): FinalizeMatchRepositoryResult {
        val match = matchesByTournamentId.value.values
            .flatten()
            .firstOrNull { it.id == matchId }
            ?: return FinalizeMatchRepositoryResult.Rejected(FinalizeMatchFailure.MATCH_NOT_FOUND)
        val existingEvidence = preservedOcrEvidenceByMatch.value[matchId]
        if (
            evidence.matchId != matchId ||
            evidence.tournamentId != match.tournamentId ||
            existingEvidence?.let { it != evidence } == true
        ) {
            return FinalizeMatchRepositoryResult.Rejected(FinalizeMatchFailure.INVALID_DATA)
        }

        val result = finalizeDraftMatch(matchId, placements, kills, participantResults)
        if (result is FinalizeMatchRepositoryResult.Finalized && existingEvidence == null) {
            preservedOcrEvidenceByMatch.update { current -> current + (matchId to evidence) }
        }
        return result
    }

    override suspend fun finalizeDraftMatchWithOcrEvidenceByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>?,
        evidence: PreservedMatchOcrEvidence,
    ): FinalizeMatchRepositoryResult = if (!isOwnedMatch(matchId, tournamentId, ownerUserId)) {
        FinalizeMatchRepositoryResult.Rejected(FinalizeMatchFailure.MATCH_NOT_FOUND)
    } else {
        finalizeDraftMatchWithOcrEvidence(matchId, placements, kills, participantResults, evidence)
    }

    override suspend fun readPreservedMatchOcrEvidence(
        tournamentId: String,
        matchId: String,
    ): PreservedMatchOcrEvidence? = preservedOcrEvidenceByMatch.value[matchId]
        ?.takeIf { it.tournamentId == tournamentId }

    override suspend fun readPreservedMatchOcrEvidenceByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
    ): PreservedMatchOcrEvidence? = readPreservedMatchOcrEvidence(tournamentId, matchId)
        ?.takeIf { isOwnedMatch(matchId, tournamentId, ownerUserId) }

    fun readPreservedOcrEvidence(matchId: String): PreservedMatchOcrEvidence? =
        preservedOcrEvidenceByMatch.value[matchId]

    override suspend fun submitMatchCorrection(
        matchId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>?,
    ): SubmitMatchCorrectionRepositoryResult {
        val match = matchesByTournamentId.value.values
            .flatten()
            .firstOrNull { it.id == matchId }
            ?: return SubmitMatchCorrectionRepositoryResult.Rejected(MatchCorrectionFailure.MATCH_NOT_FOUND)
        if (match.status != MatchStatus.FINALIZED) {
            return SubmitMatchCorrectionRepositoryResult.Rejected(MatchCorrectionFailure.MATCH_NOT_FINALIZED)
        }
            val previousParticipantResults = match.finalizedParticipantResultsOrNull()
            val correctedParticipantResults = participantResults ?: placements.map { placement ->
                val kill = kills.singleOrNull { it.teamSlotNumber == placement.teamSlotNumber }
                    ?: return SubmitMatchCorrectionRepositoryResult.Rejected(MatchCorrectionFailure.INVALID_DATA)
                MatchParticipantResult(placement.teamSlotNumber, MatchParticipationStatus.PARTICIPATED, placement.position, kill.kills)
            }
            if (!isValidCorrectionSnapshot(previousParticipantResults, correctedParticipantResults)) {
            return SubmitMatchCorrectionRepositoryResult.Rejected(MatchCorrectionFailure.INVALID_DATA)
        }

        val correctedMatch = match.copy(
            placements = correctedParticipantResults.mapNotNull { it.placement?.let { p -> MatchPlacement(it.teamSlotNumber, p) } },
            kills = correctedMatchKills(correctedParticipantResults),
            participantResults = correctedParticipantResults.sortedBy { it.teamSlotNumber },
            correctionHistory = match.correctionHistory + MatchCorrectionRecord(
                previousPlacements = match.placements.toList(),
                previousKills = match.kills.toList(),
                correctedPlacements = correctedMatchPlacements(correctedParticipantResults),
                correctedKills = correctedMatchKills(correctedParticipantResults),
                previousParticipantResults = previousParticipantResults.orEmpty(),
                correctedParticipantResults = correctedParticipantResults,
            ),
        )
        matchesByTournamentId.update { current ->
            current.mapValues { (_, matches) ->
                matches.map { existing -> if (existing.id == matchId) correctedMatch else existing }
            }
        }
        draftValuesByMatch.update { current -> current - DraftKey(match.tournamentId, matchId) }
        return SubmitMatchCorrectionRepositoryResult.Submitted(correctedMatch)
    }

    override suspend fun submitMatchCorrectionByOwner(
        matchId: String,
        ownerUserId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>?,
    ): SubmitMatchCorrectionRepositoryResult = if (!isOwnedMatch(matchId, ownerUserId)) {
        SubmitMatchCorrectionRepositoryResult.Rejected(MatchCorrectionFailure.MATCH_NOT_FOUND)
    } else {
        submitMatchCorrection(matchId, placements, kills, participantResults)
    }

    override fun observeDraftMatchValues(
        tournamentId: String,
        matchId: String,
    ): Flow<Map<Int, MatchDraftFieldValues>> = draftValuesByMatch.map { current ->
        current[DraftKey(tournamentId, matchId)].orEmpty()
    }

    override suspend fun saveDraftMatchValue(
        tournamentId: String,
        matchId: String,
        teamSlotNumber: Int,
        placementInput: String?,
        killsInput: String?,
    ) {
        require(teamSlotNumber in TeamSlot.SLOT_NUMBERS) {
            "Team slot number must be between 1 and 12."
        }
        if (matchesByTournamentId.value[tournamentId].orEmpty().none { it.id == matchId }) return
        draftValuesByMatch.update { current ->
            val key = DraftKey(tournamentId, matchId)
            val existing = current[key]?.get(teamSlotNumber) ?: MatchDraftFieldValues()
            current + (key to (current[key].orEmpty() + (
                teamSlotNumber to existing.copy(
                    placementInput = placementInput ?: existing.placementInput,
                    killsInput = killsInput ?: existing.killsInput,
                )
                )))
        }
    }

    override suspend fun saveDraftMatchValueByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
        teamSlotNumber: Int,
        placementInput: String?,
        killsInput: String?,
    ): OwnerScopedMatchMutationResult = if (!isOwnedMatch(matchId, tournamentId, ownerUserId)) {
        OwnerScopedMatchMutationResult.MatchNotFound
    } else {
        saveDraftMatchValue(tournamentId, matchId, teamSlotNumber, placementInput, killsInput)
        OwnerScopedMatchMutationResult.Saved
    }

    override suspend fun clearDraftMatch(
        tournamentId: String,
        matchId: String,
    ) {
        matchesByTournamentId.update { current ->
            current.mapValues { (_, matches) ->
                matches.map { existing ->
                    if (existing.id == matchId && existing.tournamentId == tournamentId) {
                        existing.copy(placements = emptyList(), kills = emptyList())
                    } else {
                        existing
                    }
                }
            }
        }
        draftValuesByMatch.update { current ->
            current - DraftKey(tournamentId, matchId)
        }
    }

    override suspend fun clearDraftMatchByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
    ): OwnerScopedMatchMutationResult = if (!isOwnedMatch(matchId, tournamentId, ownerUserId)) {
        OwnerScopedMatchMutationResult.MatchNotFound
    } else {
        clearDraftMatch(tournamentId, matchId)
        OwnerScopedMatchMutationResult.Saved
    }

    override suspend fun clearMatchCorrectionDraft(tournamentId: String, matchId: String) {
        draftValuesByMatch.update { current -> current - DraftKey(tournamentId, matchId) }
    }

    override suspend fun clearMatchCorrectionDraftByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
    ): OwnerScopedMatchMutationResult = if (!isOwnedMatch(matchId, tournamentId, ownerUserId)) {
        OwnerScopedMatchMutationResult.MatchNotFound
    } else {
        clearMatchCorrectionDraft(tournamentId, matchId)
        OwnerScopedMatchMutationResult.Saved
    }

    private fun isOwnedMatch(matchId: String, ownerUserId: String): Boolean = matchesByTournamentId.value
        .values
        .asSequence()
        .flatten()
        .firstOrNull { it.id == matchId }
        ?.let { match -> tournaments.value.any { it.id == match.tournamentId && it.ownerUserId == ownerUserId } }
        ?: false

    private fun isOwnedMatch(matchId: String, tournamentId: String, ownerUserId: String): Boolean =
        matchesByTournamentId.value[tournamentId]
            .orEmpty()
            .any { it.id == matchId } &&
            tournaments.value.any { it.id == tournamentId && it.ownerUserId == ownerUserId }

    private fun invalidateConfirmation(tournamentId: String) {
        tournaments.update { current ->
            current.map { tournament ->
                if (tournament.id == tournamentId && tournament.status == TournamentStatus.CONFIRMED) {
                    tournament.copy(status = TournamentStatus.DRAFT)
                } else {
                    tournament
                }
            }
        }
    }

    private data class RosterKey(
        val tournamentId: String,
        val slotNumber: Int,
    )

    private data class DraftKey(
        val tournamentId: String,
        val matchId: String,
    )

}
