package com.hoggamers.rankforge.data.local

import androidx.room.withTransaction
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingTeamEntry
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupCandidate
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupDraftRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupDraftSaveResult
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.validateGroupRotationTeamSetupDraft
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomGroupRotationTeamSetupDraftRepository @Inject constructor(
    private val database: RankForgeDatabase,
) : GroupRotationTeamSetupDraftRepository {
    override fun observeDraft(tournamentId: String): Flow<List<GroupRotationPairingTeamEntry>> =
        database.groupRotationPairingTeamEntryDraftDao()
            .observeByTournamentId(tournamentId)
            .map { entries -> entries.map { it.toDomain() } }

    override suspend fun readDraft(tournamentId: String): List<GroupRotationPairingTeamEntry> =
        database.groupRotationPairingTeamEntryDraftDao()
            .readByTournamentId(tournamentId)
            .map { it.toDomain() }

    override suspend fun replaceDraft(
        tournament: Tournament,
        candidate: GroupRotationTeamSetupCandidate,
    ): GroupRotationTeamSetupDraftSaveResult {
        val issues = validateGroupRotationTeamSetupDraft(tournament, candidate)
        if (issues.isNotEmpty()) {
            return GroupRotationTeamSetupDraftSaveResult.InvalidDraft(issues)
        }
        database.withTransaction {
            val dao = database.groupRotationPairingTeamEntryDraftDao()
            dao.deleteByTournamentId(tournament.id)
            dao.upsertAll(candidate.entries.map { it.toEntity(tournament.id) })
        }
        return GroupRotationTeamSetupDraftSaveResult.Saved
    }

    override suspend fun clearDraft(tournamentId: String) {
        database.withTransaction {
            database.groupRotationPairingTeamEntryDraftDao().deleteByTournamentId(tournamentId)
        }
    }
}

private fun GroupRotationPairingTeamEntryDraftEntity.toDomain(): GroupRotationPairingTeamEntry =
    GroupRotationPairingTeamEntry(
        pairing = GroupPairing.fromCanonicalKey(pairingKey),
        lobbySlotNumber = lobbySlotNumber,
        teamName = rawTeamName,
    )

private fun GroupRotationPairingTeamEntry.toEntity(
    tournamentId: String,
): GroupRotationPairingTeamEntryDraftEntity = GroupRotationPairingTeamEntryDraftEntity(
    tournamentId = tournamentId,
    pairingKey = pairing.canonicalKey,
    lobbySlotNumber = lobbySlotNumber,
    rawTeamName = teamName,
)

