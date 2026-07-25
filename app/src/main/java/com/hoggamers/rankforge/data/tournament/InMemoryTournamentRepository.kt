package com.hoggamers.rankforge.data.tournament

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentRepository

@Singleton
class InMemoryTournamentRepository @Inject constructor() : TournamentRepository {
    private val tournaments = MutableStateFlow<List<Tournament>>(emptyList())
    private val slotsByTournamentId = MutableStateFlow<Map<String, List<TeamSlot>>>(emptyMap())

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
}
