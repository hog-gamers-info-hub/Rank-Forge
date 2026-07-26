package com.hoggamers.rankforge.data.tournament

import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.data.local.RankForgeStateEntity
import com.hoggamers.rankforge.domain.tournament.CreateMatchRepositoryResult
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchFailure
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchRepositoryResult
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchCreationFailure
import com.hoggamers.rankforge.domain.tournament.MatchDraftFieldValues
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MAX_MATCHES_PER_TOURNAMENT
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsFailure
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsRepositoryResult
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsFailure
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsRepositoryResult
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Singleton
class RoomTournamentRepository @Inject constructor(
    private val database: RankForgeDatabase,
) : TournamentRepository {
    private val state = MutableStateFlow(RepositoryState())
    private val writeMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        scope.launch {
            try {
                runCatching { database.stateDao().readPayload() }
                    .getOrNull()
                    ?.let { payload ->
                        runCatching { json.decodeFromString<PersistedState>(payload).toRepositoryState() }
                            .onSuccess { restored -> state.value = restored }
                    }
            } finally {
                ready.complete(Unit)
            }
        }
    }

    override fun observeAll(): Flow<List<Tournament>> = state.map { it.tournaments }

    override fun observeById(tournamentId: String): Flow<Tournament?> = state.map { current ->
        current.tournaments.firstOrNull { it.id == tournamentId }
    }

    override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> = state.map { current ->
        if (current.tournaments.none { it.id == tournamentId }) {
            emptyList()
        } else {
            current.slots[tournamentId]
                ?.takeIf { slots -> slots.map { it.slotNumber } == TeamSlot.SLOT_NUMBERS.toList() }
                ?: TeamSlot.fixedSlotsForTournament(tournamentId)
        }
    }

    override suspend fun create(tournament: Tournament) = updateState { current ->
        if (current.tournaments.any { it.id == tournament.id }) {
            current
        } else {
            current.copy(
                tournaments = current.tournaments + tournament,
                slots = current.slots + (tournament.id to TeamSlot.fixedSlotsForTournament(tournament.id)),
            )
        }
    }

    override suspend fun saveTeamNames(
        tournamentId: String,
        teamNamesBySlotNumber: Map<Int, String>,
    ) {
        teamNamesBySlotNumber.keys.forEach { require(it in TeamSlot.SLOT_NUMBERS) }
        updateState { current ->
            if (current.tournaments.none { it.id == tournamentId }) return@updateState current
            val slots = current.slots[tournamentId]
                ?.takeIf { it.map(TeamSlot::slotNumber) == TeamSlot.SLOT_NUMBERS.toList() }
                ?: TeamSlot.fixedSlotsForTournament(tournamentId)
            current.copy(
                tournaments = current.tournaments.map { tournament ->
                    if (tournament.id == tournamentId && tournament.status == TournamentStatus.CONFIRMED) {
                        tournament.copy(status = TournamentStatus.DRAFT)
                    } else tournament
                },
                slots = current.slots + (tournamentId to slots.map { slot ->
                    slot.copy(teamName = teamNamesBySlotNumber[slot.slotNumber] ?: slot.teamName)
                }),
            )
        }
    }

    override fun observeRosterByTournamentAndSlot(
        tournamentId: String,
        slotNumber: Int,
    ): Flow<List<RosterPlayer>> = observeRosterByTournamentId(tournamentId).map { it[slotNumber].orEmpty() }

    override fun observeRosterByTournamentId(
        tournamentId: String,
    ): Flow<Map<Int, List<RosterPlayer>>> = state.map { current ->
        if (current.tournaments.none { it.id == tournamentId }) emptyMap()
        else current.rosters.filterKeys { it.tournamentId == tournamentId }
            .mapKeys { (key, _) -> key.slotNumber }
    }

    override suspend fun saveRoster(
        tournamentId: String,
        slotNumber: Int,
        players: List<RosterPlayer>,
    ) {
        require(slotNumber in TeamSlot.SLOT_NUMBERS)
        require(players.size <= RosterPlayer.MAX_PLAYERS)
        require(players.all { it.tournamentId == tournamentId && it.slotNumber == slotNumber })
        updateState { current ->
            if (current.tournaments.none { it.id == tournamentId }) current
            else current.copy(
                tournaments = current.tournaments.map { tournament ->
                    if (tournament.id == tournamentId && tournament.status == TournamentStatus.CONFIRMED) {
                        tournament.copy(status = TournamentStatus.DRAFT)
                    } else tournament
                },
                rosters = current.rosters + (RosterKey(tournamentId, slotNumber) to players.toList()),
            )
        }
    }

    override suspend fun confirmTournament(tournamentId: String): Boolean {
        var confirmed = false
        updateState { current ->
            if (current.tournaments.none { it.id == tournamentId }) return@updateState current
            current.copy(tournaments = current.tournaments.map { tournament ->
                if (tournament.id == tournamentId && tournament.status == TournamentStatus.DRAFT) {
                    confirmed = true
                    tournament.copy(status = TournamentStatus.CONFIRMED)
                } else tournament
            })
        }
        return confirmed
    }

    override fun observeMatchesByTournamentId(tournamentId: String): Flow<List<Match>> = state.map { current ->
        if (current.tournaments.none { it.id == tournamentId }) emptyList()
        else current.matches[tournamentId].orEmpty()
    }

    override fun observeMatchById(matchId: String): Flow<Match?> = state.map { current ->
        current.matches.values.flatten().firstOrNull { it.id == matchId }
    }

    override suspend fun createDraftMatch(match: Match): CreateMatchRepositoryResult {
        val current = awaitState()
        val tournament = current.tournaments.firstOrNull { it.id == match.tournamentId }
            ?: return CreateMatchRepositoryResult.Rejected(MatchCreationFailure.TOURNAMENT_NOT_FOUND)
        if (tournament.status != TournamentStatus.CONFIRMED) {
            return CreateMatchRepositoryResult.Rejected(MatchCreationFailure.TOURNAMENT_NOT_CONFIRMED)
        }
        val matches = current.matches[match.tournamentId].orEmpty()
        if (matches.any { it.id == match.id }) return CreateMatchRepositoryResult.Rejected(MatchCreationFailure.DUPLICATE_ID)
        if (matches.any { it.matchNumber == match.matchNumber }) {
            return CreateMatchRepositoryResult.Rejected(MatchCreationFailure.DUPLICATE_MATCH_NUMBER)
        }
        if (matches.size >= MAX_MATCHES_PER_TOURNAMENT) {
            return CreateMatchRepositoryResult.Rejected(MatchCreationFailure.LIMIT_REACHED)
        }
        updateState { it.copy(matches = it.matches + (match.tournamentId to (matches + match))) }
        return CreateMatchRepositoryResult.Created
    }

    override suspend fun saveDraftMatchPlacements(
        matchId: String,
        placements: List<MatchPlacement>,
    ): SaveMatchPlacementsRepositoryResult {
        val match = awaitState().matches.values.flatten().firstOrNull { it.id == matchId }
            ?: return SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.MATCH_NOT_FOUND)
        if (match.status != MatchStatus.DRAFT) return SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.MATCH_NOT_DRAFT)
        if (placements.any { it.teamSlotNumber !in TeamSlot.SLOT_NUMBERS }) return SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.INVALID_TEAM_SLOT)
        if (placements.any { it.position !in TeamSlot.SLOT_NUMBERS }) return SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.INVALID_POSITION)
        if (placements.map { it.teamSlotNumber }.distinct().size != placements.size) return SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.DUPLICATE_TEAM_SLOT)
        if (placements.map { it.position }.distinct().size != placements.size) return SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.DUPLICATE_POSITION)
        updateState { current -> current.replaceMatch(match.id) { it.copy(placements = placements.toList()) } }
        return SaveMatchPlacementsRepositoryResult.Saved
    }

    override suspend fun saveDraftMatchKills(
        matchId: String,
        kills: List<MatchKill>,
    ): SaveMatchKillsRepositoryResult {
        val match = awaitState().matches.values.flatten().firstOrNull { it.id == matchId }
            ?: return SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.MATCH_NOT_FOUND)
        if (match.status != MatchStatus.DRAFT) return SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.MATCH_NOT_DRAFT)
        if (kills.any { it.teamSlotNumber !in TeamSlot.SLOT_NUMBERS }) return SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.INVALID_TEAM_SLOT)
        if (kills.any { it.kills < 0 }) return SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.INVALID_KILLS)
        if (kills.map { it.teamSlotNumber }.distinct().size != kills.size) return SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.DUPLICATE_TEAM_SLOT)
        updateState { current -> current.replaceMatch(match.id) { it.copy(kills = kills.toList()) } }
        return SaveMatchKillsRepositoryResult.Saved
    }

    override suspend fun finalizeDraftMatch(
        matchId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
    ): FinalizeMatchRepositoryResult {
        val match = awaitState().matches.values.flatten().firstOrNull { it.id == matchId }
            ?: return FinalizeMatchRepositoryResult.Rejected(FinalizeMatchFailure.MATCH_NOT_FOUND)
        if (match.status != MatchStatus.DRAFT) {
            return FinalizeMatchRepositoryResult.Rejected(FinalizeMatchFailure.MATCH_NOT_DRAFT)
        }
        if (
            placements.size != TeamSlot.MAX_SLOT_NUMBER ||
            kills.size != TeamSlot.MAX_SLOT_NUMBER ||
            placements.any { it.teamSlotNumber !in TeamSlot.SLOT_NUMBERS || it.position !in TeamSlot.SLOT_NUMBERS } ||
            kills.any { it.teamSlotNumber !in TeamSlot.SLOT_NUMBERS || it.kills < 0 } ||
            placements.map { it.teamSlotNumber }.distinct().size != placements.size ||
            kills.map { it.teamSlotNumber }.distinct().size != kills.size ||
            placements.map { it.position }.distinct().size != placements.size
        ) {
            return FinalizeMatchRepositoryResult.Rejected(FinalizeMatchFailure.INVALID_DATA)
        }
        val finalizedMatch = match.copy(
            status = MatchStatus.FINALIZED,
            placements = placements.toList(),
            kills = kills.toList(),
        )
        updateState { current ->
            current.copy(
                matches = current.matches.replaceMatch(match.tournamentId, matchId) { finalizedMatch },
                draftValues = current.draftValues - DraftKey(match.tournamentId, matchId),
            )
        }
        return FinalizeMatchRepositoryResult.Finalized(finalizedMatch)
    }

    override fun observeDraftMatchValues(
        tournamentId: String,
        matchId: String,
    ): Flow<Map<Int, MatchDraftFieldValues>> = state.map { current ->
        current.draftValues[DraftKey(tournamentId, matchId)].orEmpty()
    }

    override suspend fun saveDraftMatchValue(
        tournamentId: String,
        matchId: String,
        teamSlotNumber: Int,
        placementInput: String?,
        killsInput: String?,
    ) {
        require(teamSlotNumber in TeamSlot.SLOT_NUMBERS)
        if (awaitState().matches[tournamentId].orEmpty().none { it.id == matchId }) return
        updateState { current ->
            val key = DraftKey(tournamentId, matchId)
            val old = current.draftValues[key]?.get(teamSlotNumber) ?: MatchDraftFieldValues()
            current.copy(draftValues = current.draftValues + (key to (
                current.draftValues[key].orEmpty() + (teamSlotNumber to old.copy(
                    placementInput = placementInput ?: old.placementInput,
                    killsInput = killsInput ?: old.killsInput,
                ))
                )))
        }
    }

    override suspend fun clearDraftMatch(tournamentId: String, matchId: String) {
        updateState { current ->
            current.copy(
                matches = current.matches.replaceMatch(tournamentId, matchId) {
                    it.copy(placements = emptyList(), kills = emptyList())
                },
                draftValues = current.draftValues - DraftKey(tournamentId, matchId),
            )
        }
    }

    private suspend fun awaitState(): RepositoryState {
        ready.await()
        return state.value
    }

    private suspend fun updateState(transform: (RepositoryState) -> RepositoryState) {
        ready.await()
        writeMutex.withLock {
            val next = transform(state.value)
            database.stateDao().save(
                RankForgeStateEntity(payload = json.encodeToString(next.toPersistedState())),
            )
            state.value = next
        }
    }

    private fun RepositoryState.replaceMatch(matchId: String, transform: (Match) -> Match): RepositoryState = copy(
        matches = matches.mapValues { (_, matches) -> matches.map { if (it.id == matchId) transform(it) else it } },
    )

    private fun Map<String, List<Match>>.replaceMatch(
        tournamentId: String,
        matchId: String,
        transform: (Match) -> Match,
    ): Map<String, List<Match>> = mapValues { (key, matches) ->
        if (key == tournamentId) matches.map { if (it.id == matchId) transform(it) else it } else matches
    }
}

@Serializable
private data class PersistedState(
    val tournaments: List<PersistedTournament> = emptyList(),
    val slots: List<PersistedSlot> = emptyList(),
    val rosters: List<PersistedRoster> = emptyList(),
    val matches: List<PersistedMatch> = emptyList(),
    val draftValues: List<PersistedDraftMatch> = emptyList(),
)

@Serializable
private data class PersistedTournament(val id: String, val name: String, val date: String, val organizerName: String, val organizerContactNumber: String, val status: String)
@Serializable
private data class PersistedSlot(val tournamentId: String, val slotNumber: Int, val teamName: String)
@Serializable
private data class PersistedRoster(val tournamentId: String, val slotNumber: Int, val displayName: String)
@Serializable
private data class PersistedMatch(val id: String, val tournamentId: String, val matchNumber: Int, val date: String, val mapName: String, val status: String, val placements: List<PersistedPlacement> = emptyList(), val kills: List<PersistedKill> = emptyList())
@Serializable
private data class PersistedPlacement(val teamSlotNumber: Int, val position: Int)
@Serializable
private data class PersistedKill(val teamSlotNumber: Int, val kills: Int)
@Serializable
private data class PersistedDraftMatch(val tournamentId: String, val matchId: String, val values: List<PersistedDraftValue>)
@Serializable
private data class PersistedDraftValue(val teamSlotNumber: Int, val placementInput: String, val killsInput: String)

private fun RepositoryState.toPersistedState() = PersistedState(
    tournaments = tournaments.map { PersistedTournament(it.id, it.name, it.date.toString(), it.organizerName, it.organizerContactNumber, it.status.name) },
    slots = slots.values.flatten().map { PersistedSlot(it.tournamentId, it.slotNumber, it.teamName) },
    rosters = rosters.map { (key, players) -> players.map { PersistedRoster(key.tournamentId, key.slotNumber, it.displayName) } }.flatten(),
    matches = matches.values.flatten().map { match -> PersistedMatch(match.id, match.tournamentId, match.matchNumber, match.date.toString(), match.mapName, match.status.name, match.placements.map { PersistedPlacement(it.teamSlotNumber, it.position) }, match.kills.map { PersistedKill(it.teamSlotNumber, it.kills) }) },
    draftValues = draftValues.map { (key, values) -> PersistedDraftMatch(key.tournamentId, key.matchId, values.map { (slot, value) -> PersistedDraftValue(slot, value.placementInput, value.killsInput) }) },
)

private fun PersistedState.toRepositoryState() = RepositoryState(
    tournaments = tournaments.map { Tournament(it.id, it.name, LocalDate.parse(it.date), it.organizerName, it.organizerContactNumber, TournamentStatus.valueOf(it.status)) },
    slots = slots.groupBy { it.tournamentId }.mapValues { (_, values) -> values.map { TeamSlot(it.tournamentId, it.slotNumber, it.teamName) } },
    rosters = rosters.groupBy { RosterKey(it.tournamentId, it.slotNumber) }.mapValues { (_, values) -> values.map { RosterPlayer(it.tournamentId, it.slotNumber, it.displayName) } },
    matches = matches.groupBy { it.tournamentId }.mapValues { (_, values) -> values.map { match -> Match(match.id, match.tournamentId, match.matchNumber, LocalDate.parse(match.date), match.mapName, MatchStatus.valueOf(match.status), match.placements.map { MatchPlacement(it.teamSlotNumber, it.position) }, match.kills.map { MatchKill(it.teamSlotNumber, it.kills) }) } },
    draftValues = draftValues.associate { draft ->
        DraftKey(draft.tournamentId, draft.matchId) to draft.values.associate { value ->
            value.teamSlotNumber to MatchDraftFieldValues(value.placementInput, value.killsInput)
        }
    },
)

private data class RepositoryState(
    val tournaments: List<Tournament> = emptyList(),
    val slots: Map<String, List<TeamSlot>> = emptyMap(),
    val rosters: Map<RosterKey, List<RosterPlayer>> = emptyMap(),
    val matches: Map<String, List<Match>> = emptyMap(),
    val draftValues: Map<DraftKey, Map<Int, MatchDraftFieldValues>> = emptyMap(),
)

private data class RosterKey(val tournamentId: String, val slotNumber: Int)
private data class DraftKey(val tournamentId: String, val matchId: String)
