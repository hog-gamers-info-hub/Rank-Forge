package com.hoggamers.rankforge.data.tournament

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.cloud.TournamentCloudRestorationMapper
import com.hoggamers.rankforge.data.cloud.TournamentCloudRestorationMappingResult
import com.hoggamers.rankforge.data.cloud.TournamentCloudRestorationPayloads
import com.hoggamers.rankforge.data.cloud.TournamentCloudUploadMapper
import com.hoggamers.rankforge.data.cloud.TournamentCloudUploadMappingResult
import com.hoggamers.rankforge.data.cloud.TournamentCloudUploadPayloads
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingTeamEntry
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupCandidate
import com.hoggamers.rankforge.domain.tournament.SaveGroupRotationTeamSetupResult
import com.hoggamers.rankforge.domain.tournament.SaveGroupRotationTeamSetupUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.defaultGroupPairings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupRotationCloudRoundTripTest {
    @Test
    fun realGroupRotationSetupUploadRestoreRoundTripPreservesCanonicalMappingAfterReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceDatabaseName = "group-rotation-cloud-roundtrip-source.db"
        val targetDatabaseName = "group-rotation-cloud-roundtrip-target.db"
        context.deleteDatabase(sourceDatabaseName)
        context.deleteDatabase(targetDatabaseName)

        var sourceDatabase: RankForgeDatabase? = null
        var targetDatabase: RankForgeDatabase? = null
        var reopenedTargetDatabase: RankForgeDatabase? = null
        try {
            sourceDatabase = openDatabase(context, sourceDatabaseName)
            val sourceRepository = RoomTournamentRepository(sourceDatabase)
            sourceRepository.create(
                Tournament(
                    id = TOURNAMENT_ID,
                    name = "Group Rotation Round Trip",
                    stageName = "Final",
                    organizerContactNumber = "1234567890",
                    status = TournamentStatus.DRAFT,
                    ownerUserId = OWNER_ID,
                    format = TournamentFormat.GROUP_ROTATION,
                    groupCount = 3,
                    selectedGroupPairings = defaultGroupPairings(3),
                ),
            )

            val saveSetup = SaveGroupRotationTeamSetupUseCase(
                repository = sourceRepository,
                authRepository = SignedInAuthRepository,
            )
            assertEquals(SaveGroupRotationTeamSetupResult.Saved, saveSetup(firstSetupCandidate()))

            val sourceSlotsAfterFirstSetup = sourceDatabase!!.teamSlotDao()
                .readByTournamentId(TOURNAMENT_ID)
            assertEquals((1..18).toList(), sourceSlotsAfterFirstSetup.map { it.slotNumber })
            assertEquals(
                (1..18).map { "Team $it" },
                sourceSlotsAfterFirstSetup.map { it.teamName },
            )

            assertEquals(
                SaveGroupRotationTeamSetupResult.Saved,
                saveSetup(authoritativeSecondSetupCandidate()),
            )

            val sourceMappings = sourceDatabase!!.groupRotationPairingLobbySlotDao()
                .readByTournamentId(TOURNAMENT_ID)
            assertEquals(36, sourceMappings.size)
            assertEquals(
                defaultGroupPairings(3)
                    .map { it.canonicalKey }
                    .sorted(),
                sourceMappings.groupBy { it.pairingKey }.toSortedMap().keys.toList(),
            )
            assertEquals(
                listOf(12, 12, 12),
                sourceMappings.groupBy { it.pairingKey }.values.map { it.size }.sorted(),
            )
            assertEquals(
                AUTHORITATIVE_AC_MAPPING,
                sourceMappings
                    .filter { it.pairingKey == AC_PAIRING_KEY }
                    .sortedBy { it.lobbySlotNumber }
                    .map { it.teamSlotNumber },
            )
            assertEquals(
                18,
                sourceDatabase!!.teamSlotDao().readByTournamentId(TOURNAMENT_ID)
                    .count { it.teamName.isNotBlank() },
            )

            val uploadSnapshot = checkNotNull(
                sourceRepository.readCloudUploadSnapshotByOwner(
                    tournamentId = TOURNAMENT_ID,
                    ownerUserId = OWNER_ID,
                ),
            )
            assertEquals(TournamentFormat.GROUP_ROTATION, uploadSnapshot.tournament.format)
            assertEquals(3, uploadSnapshot.tournament.groupCount)
            assertEquals(18, uploadSnapshot.slots.size)
            assertEquals(36, uploadSnapshot.pairingLobbySlots.size)
            assertEquals(
                AUTHORITATIVE_AC_MAPPING,
                uploadSnapshot.pairingLobbySlots
                    .filter { it.pairing.canonicalKey == AC_PAIRING_KEY }
                    .sortedBy { it.lobbySlotNumber }
                    .map { it.teamSlotNumber },
            )
            assertEquals(18, uploadSnapshot.slots.count { it.teamName.isNotBlank() })

            val uploadPayloads = (TournamentCloudUploadMapper.map(uploadSnapshot, OWNER_ID)
                as TournamentCloudUploadMappingResult.Success).payloads
            assertEquals("group_rotation", uploadPayloads.tournament.format)
            assertEquals(3, uploadPayloads.tournament.groupCount)
            assertEquals(18, uploadPayloads.teamSlots.size)
            assertEquals(3, uploadPayloads.tournament.selectedGroupPairings.size)
            assertEquals(36, uploadPayloads.pairingLobbySlots.size)
            assertEquals(
                defaultGroupPairings(3)
                    .map { it.canonicalKey }
                    .sorted(),
                uploadPayloads.tournament.selectedGroupPairings.map { it.pairingKey },
            )
            assertEquals(
                listOf(12, 12, 12),
                uploadPayloads.pairingLobbySlots
                    .groupBy { it.pairingKey }
                    .values
                    .map { it.size }
                    .sorted(),
            )
            assertEquals(
                AUTHORITATIVE_AC_MAPPING,
                uploadPayloads.pairingLobbySlots
                    .filter { it.pairingKey == AC_PAIRING_KEY }
                    .sortedBy { it.lobbySlotNumber }
                    .map { it.teamSlotNumber },
            )

            val restoredSnapshot = (
                TournamentCloudRestorationMapper.mapSnapshot(
                    TournamentCloudRestorationPayloads(
                        tournament = uploadPayloads.tournament.copy(revision = CLOUD_REVISION),
                        teamSlots = uploadPayloads.teamSlots,
                        players = uploadPayloads.players,
                        pairingLobbySlots = uploadPayloads.pairingLobbySlots,
                    ),
                ) as TournamentCloudRestorationMappingResult.Success
                ).value
            assertEquals(TournamentFormat.GROUP_ROTATION, restoredSnapshot.tournament.format)
            assertEquals(3, restoredSnapshot.tournament.groupCount)
            assertEquals((1..18).toList(), restoredSnapshot.slots.map { it.slotNumber })
            assertEquals(CLOUD_REVISION, restoredSnapshot.cloudRevision?.value)
            assertEquals(
                AUTHORITATIVE_AC_MAPPING,
                restoredSnapshot.pairingLobbySlots
                    .filter { it.pairing.canonicalKey == AC_PAIRING_KEY }
                    .sortedBy { it.lobbySlotNumber }
                    .map { it.teamSlotNumber },
            )

            targetDatabase = openDatabase(context, targetDatabaseName)
            val targetRepository = RoomTournamentRepository(targetDatabase!!)
            targetRepository.restoreByOwner(
                snapshot = restoredSnapshot,
                expectedOwnerUserId = OWNER_ID,
            )
            assertTargetState(targetDatabase!!, uploadPayloads)

            targetDatabase!!.close()
            targetDatabase = null
            reopenedTargetDatabase = openDatabase(context, targetDatabaseName)
            assertTargetState(reopenedTargetDatabase!!, uploadPayloads)
        } finally {
            sourceDatabase?.close()
            targetDatabase?.close()
            reopenedTargetDatabase?.close()
            context.deleteDatabase(sourceDatabaseName)
            context.deleteDatabase(targetDatabaseName)
        }
    }

    private suspend fun assertTargetState(
        database: RankForgeDatabase,
        uploadPayloads: TournamentCloudUploadPayloads,
    ) {
        val tournament = database.tournamentDao().observeById(TOURNAMENT_ID).first()
        assertEquals("GROUP_ROTATION", tournament?.format)
        assertEquals(3, tournament?.groupCount)
        assertEquals(OWNER_ID, tournament?.ownerUserId)
        assertEquals(
            uploadPayloads.tournament.selectedGroupPairings.map { it.pairingKey },
            database.groupRotationTeamSetupReadDao()
                .readPairings(TOURNAMENT_ID)
                .map { it.pairingKey },
        )
        val expectedMappings = uploadPayloads.pairingLobbySlots.map {
            Triple(it.pairingKey, it.lobbySlotNumber, it.teamSlotNumber)
        }
        val actualMappings = database.groupRotationPairingLobbySlotDao()
            .readByTournamentId(TOURNAMENT_ID)
            .map { Triple(it.pairingKey, it.lobbySlotNumber, it.teamSlotNumber) }
        assertEquals(expectedMappings, actualMappings)
        assertEquals(
            AUTHORITATIVE_AC_MAPPING,
            actualMappings
                .filter { it.first == AC_PAIRING_KEY }
                .sortedBy { it.second }
                .map { it.third },
        )
        assertEquals(18, database.teamSlotDao().readByTournamentId(TOURNAMENT_ID).size)
        assertEquals(
            (1..18).map { "Team $it" },
            database.teamSlotDao().readByTournamentId(TOURNAMENT_ID).map { it.teamName },
        )
        assertEquals(
            CLOUD_REVISION,
            database.syncRevisionDao().readByTournamentId(TOURNAMENT_ID)?.localRevision,
        )
        assertEquals(
            CLOUD_REVISION,
            database.syncRevisionDao().readByTournamentId(TOURNAMENT_ID)?.baseCloudRevision,
        )
    }

    private fun firstSetupCandidate(): GroupRotationTeamSetupCandidate =
        GroupRotationTeamSetupCandidate(
            tournamentId = TOURNAMENT_ID,
            entries = buildList {
                addAll((1..12).map { lobbySlot -> entry("A:B", lobbySlot, "Team $lobbySlot") })
                addAll((1..12).map { lobbySlot ->
                    val teamNumber = if (lobbySlot <= 6) lobbySlot else lobbySlot + 6
                    entry(AC_PAIRING_KEY, lobbySlot, "Team $teamNumber")
                })
                addAll((1..12).map { lobbySlot ->
                    entry("B:C", lobbySlot, "Team " + (lobbySlot + 6))
                })
            },
        )

    private fun authoritativeSecondSetupCandidate(): GroupRotationTeamSetupCandidate =
        GroupRotationTeamSetupCandidate(
            tournamentId = TOURNAMENT_ID,
            entries = buildList {
                addAll((1..12).map { lobbySlot -> entry("A:B", lobbySlot, "Team $lobbySlot") })
                addAll(AUTHORITATIVE_AC_MAPPING.mapIndexed { index, teamNumber ->
                    entry(AC_PAIRING_KEY, index + 1, "Team $teamNumber")
                })
                addAll((1..12).map { lobbySlot ->
                    entry("B:C", lobbySlot, "Team " + (lobbySlot + 6))
                })
            },
        )

    private fun entry(
        pairingKey: String,
        lobbySlotNumber: Int,
        teamName: String,
    ) = GroupRotationPairingTeamEntry(
        pairing = GroupPairing.fromCanonicalKey(pairingKey),
        lobbySlotNumber = lobbySlotNumber,
        teamName = teamName,
    )

    private fun openDatabase(context: Context, name: String): RankForgeDatabase =
        Room.databaseBuilder(context, RankForgeDatabase::class.java, name).build()

    private object SignedInAuthRepository : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(
            AuthState.SignedIn(AuthUser(OWNER_ID, "owner@example.test")),
        )

        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession

        override suspend fun signUp(email: String, password: String): AuthOperationResult = error("unused")

        override suspend fun login(email: String, password: String): AuthOperationResult = error("unused")

        override suspend fun logout(): AuthOperationResult = error("unused")
    }

    private companion object {
        const val OWNER_ID = "22222222-2222-2222-2222-222222222222"
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111114"
        const val AC_PAIRING_KEY = "A:C"
        const val CLOUD_REVISION = 41
        val AUTHORITATIVE_AC_MAPPING = listOf(
            14, 3, 18, 1, 16, 5,
            6, 15, 2, 17, 4, 13,
        )
    }
}
