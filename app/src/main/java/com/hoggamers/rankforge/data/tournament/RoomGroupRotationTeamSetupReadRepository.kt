package com.hoggamers.rankforge.data.tournament

import androidx.room.withTransaction
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingTeamEntry
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupCandidate
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityNormalizer
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupRead
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupReadRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupReadResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupValidator
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.formatDerivedSlots
import javax.inject.Inject

class RoomGroupRotationTeamSetupReadRepository @Inject constructor(
    private val database: RankForgeDatabase,
) : GroupRotationTeamSetupReadRepository {
    private val validator = GroupRotationTeamSetupValidator()
    private val identityNormalizer = GroupRotationTeamIdentityNormalizer()
    override suspend fun readGroupRotationTeamSetup(
        tournamentId: String,
        ownerUserId: String,
    ): GroupRotationTeamSetupReadResult = database.withTransaction {
        val readDao = database.groupRotationTeamSetupReadDao()
        val tournamentEntity = readDao.readOwnedTournament(tournamentId, ownerUserId)
            ?: return@withTransaction GroupRotationTeamSetupReadResult.TournamentNotFound
        val tournament = runCatching {
            tournamentEntity.toDomain(
                readDao.readPairings(tournamentId).map { it.toDomain() },
            )
        }.getOrElse {
            return@withTransaction GroupRotationTeamSetupReadResult.InvalidStoredSetup
        }
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            return@withTransaction GroupRotationTeamSetupReadResult.InvalidStoredSetup
        }

        val teamSlots = readDao.readTeamSlots(tournamentId)
        val expectedTeamSlots = tournament.formatDerivedSlots()
            .associateBy { it.slotNumber }
        if (
            teamSlots.size != expectedTeamSlots.size ||
            teamSlots.any { storedSlot ->
                val expectedSlot = expectedTeamSlots[storedSlot.slotNumber]
                expectedSlot == null || storedSlot.group != expectedSlot.group?.name
            }
        ) {
            return@withTransaction GroupRotationTeamSetupReadResult.InvalidStoredSetup
        }
        val rows = readDao.readMappingRows(tournamentId)
        if (rows.isEmpty()) {
            return@withTransaction GroupRotationTeamSetupReadResult.NoSavedSetup(tournament)
        }
        val expectedPairingKeys = tournament.selectedGroupPairings.map { it.canonicalKey }.toSet()
        val storedPairingKeys = rows.map { it.pairingKey }.toSet()
        if (storedPairingKeys != expectedPairingKeys || rows.any { it.tournamentId != tournamentId }) {
            return@withTransaction GroupRotationTeamSetupReadResult.InvalidStoredSetup
        }

        val entries = runCatching {
            rows.map { row ->
                val pairing = tournament.selectedGroupPairings
                    .first { it.canonicalKey == row.pairingKey }
                require(row.resolvedTeamSlotNumber == row.teamSlotNumber)
                require(!row.teamName.isNullOrBlank())
                GroupRotationPairingTeamEntry(
                    pairing = pairing,
                    lobbySlotNumber = row.lobbySlotNumber,
                    teamName = row.teamName,
                )
            }
        }.getOrElse {
            return@withTransaction GroupRotationTeamSetupReadResult.InvalidStoredSetup
        }
        val candidate = GroupRotationTeamSetupCandidate(tournamentId, entries)
        val teamSlotNumbersByIdentity = rows
            .mapNotNull { row ->
                val teamName = row.teamName ?: return@mapNotNull null
                val normalizedIdentity = identityNormalizer.normalize(teamName)
                val teamSlotNumber = row.resolvedTeamSlotNumber ?: return@mapNotNull null
                normalizedIdentity
                    .takeIf(String::isNotBlank)
                    ?.let { it to teamSlotNumber }
            }
            .groupBy({ it.first }, { it.second })
        if (teamSlotNumbersByIdentity.values.any { it.toSet().size > 1 }) {
            return@withTransaction GroupRotationTeamSetupReadResult.InvalidStoredSetup
        }
        if (validator.validate(tournament, candidate) !is com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupValidationResult.Valid) {
            return@withTransaction GroupRotationTeamSetupReadResult.InvalidStoredSetup
        }
        GroupRotationTeamSetupReadResult.Loaded(
            GroupRotationTeamSetupRead(tournament, candidate),
        )
    }
}
