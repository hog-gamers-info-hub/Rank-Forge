package com.hoggamers.rankforge.data.local

import androidx.room.withTransaction
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

interface MatchCalculatedEvidenceRepository {
    suspend fun save(
        ownerUserId: String,
        tournamentId: String,
        matchId: String,
        evidence: MatchCalculatedEvidence,
    ): Boolean

    suspend fun read(
        ownerUserId: String,
        tournamentId: String,
        matchId: String,
    ): MatchCalculatedEvidence?

    suspend fun delete(
        ownerUserId: String,
        tournamentId: String,
        matchId: String,
    ): Boolean
}

@Singleton
class RoomMatchCalculatedEvidenceRepository @Inject constructor(
    private val dao: MatchCalculatedEvidenceDao,
    private val codec: MatchCalculatedEvidenceCodec,
    private val clock: Clock,
    private val database: RankForgeDatabase,
) : MatchCalculatedEvidenceRepository {
    override suspend fun save(
        ownerUserId: String,
        tournamentId: String,
        matchId: String,
        evidence: MatchCalculatedEvidence,
    ): Boolean {
        if (ownerUserId.isBlank() || tournamentId.isBlank() || matchId.isBlank()) return false
        return database.withTransaction {
            if (!database.matchDao().existsByIdAndTournamentAndOwner(matchId, tournamentId, ownerUserId)) {
                return@withTransaction false
            }
            dao.upsert(
                MatchCalculatedEvidenceEntity(
                    matchId = matchId,
                    tournamentId = tournamentId,
                    ownerUserId = ownerUserId,
                    lobbyEvidenceJson = codec.encodeLobby(evidence.lobby),
                    resultEvidenceJson = codec.encodeResult(evidence.result),
                    savedAt = clock.millis(),
                ),
            )
            true
        }
    }

    override suspend fun read(
        ownerUserId: String,
        tournamentId: String,
        matchId: String,
    ): MatchCalculatedEvidence? {
        if (ownerUserId.isBlank() || tournamentId.isBlank() || matchId.isBlank()) return null
        val entity = dao.readByOwner(tournamentId, matchId, ownerUserId) ?: return null
        return codec.decode(entity.lobbyEvidenceJson, entity.resultEvidenceJson)
    }

    override suspend fun delete(
        ownerUserId: String,
        tournamentId: String,
        matchId: String,
    ): Boolean {
        if (ownerUserId.isBlank() || tournamentId.isBlank() || matchId.isBlank()) return false
        return database.withTransaction {
            if (!database.matchDao().existsByIdAndTournamentAndOwner(matchId, tournamentId, ownerUserId)) {
                return@withTransaction false
            }
            dao.deleteByOwner(tournamentId, matchId, ownerUserId) > 0
        }
    }
}
