package com.hoggamers.rankforge.data.tournament

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentRepository

@Singleton
class InMemoryTournamentRepository @Inject constructor() : TournamentRepository {
    private val tournaments = MutableStateFlow<List<Tournament>>(emptyList())
    private val slotsByTournamentId = MutableStateFlow<Map<String, List<TeamSlot>>>(emptyMap())
    private val rostersByTournamentAndSlot = MutableStateFlow<Map<RosterKey, List<RosterPlayer>>>(emptyMap())

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

        rostersByTournamentAndSlot.update { current ->
            current + (RosterKey(tournamentId, slotNumber) to players.toList())
        }
    }

    private data class RosterKey(
        val tournamentId: String,
        val slotNumber: Int,
    )
}
