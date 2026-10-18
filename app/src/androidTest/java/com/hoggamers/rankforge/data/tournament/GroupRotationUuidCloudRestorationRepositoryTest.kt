package com.hoggamers.rankforge.data.tournament

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.local.GroupRotationPairingTeamIdentityAssignmentEntity
import com.hoggamers.rankforge.data.local.GroupRotationMatchIdentityHeaderEntity
import com.hoggamers.rankforge.data.local.GroupRotationMatchParticipantResultEntity
import com.hoggamers.rankforge.data.local.GroupRotationMatchTeamAssignmentEntity
import com.hoggamers.rankforge.data.local.GroupRotationTeamIdentityEntity
import com.hoggamers.rankforge.data.local.MatchCalculatedEvidenceEntity
import com.hoggamers.rankforge.data.local.MatchEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchOcrCorrectionSnapshotEntity
import com.hoggamers.rankforge.data.local.MatchOcrEvidenceEntity
import com.hoggamers.rankforge.data.local.MatchOcrRowEvidenceEntity
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.data.local.ScreenshotMetadataEntity
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.data.local.SyncRevisionEntity
import com.hoggamers.rankforge.data.local.TournamentEntity
import com.hoggamers.rankforge.data.local.TournamentGroupPairingEntity
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingAssignment
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationUuidMatchRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityReadResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationUuidCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationUuidLocalRestoreResult
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import java.time.LocalDate
import java.io.File
import com.hoggamers.rankforge.domain.tournament.toSetupCandidate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupRotationUuidCloudRestorationRepositoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var databaseFile: File
    private lateinit var database: RankForgeDatabase
    private lateinit var repository: RoomTournamentRepository

    @Before
    fun setUp() {
        databaseFile = File(context.cacheDir, "group-rotation-uuid-restore-${System.nanoTime()}.db")
        database = Room.databaseBuilder(
            context,
            RankForgeDatabase::class.java,
            databaseFile.path,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
        databaseFile.delete()
        File(databaseFile.path + "-shm").delete()
        File(databaseFile.path + "-wal").delete()
    }

    @Test
    fun restoresExactUuidPoolAndSparsePairingAssignments() = runBlocking {
        repository = RoomTournamentRepository(database)
        repository.observeAll().first()
        val result = repository.restoreGroupRotationUuidSnapshot(snapshot(), OWNER)

        assertEquals(GroupRotationUuidLocalRestoreResult.Restored, result)
        assertEquals(
            listOf(GroupRotationTeamIdentityEntity(T, U1, "Phoenix", "phoenix")),
            database.groupRotationTeamIdentityDao().readByTournamentId(T),
        )
        assertEquals(
            listOf(GroupRotationPairingTeamIdentityAssignmentEntity(T, "A:B", 5, U1)),
            database.groupRotationPairingTeamIdentityAssignmentDao().readByTournamentId(T),
        )
        assertEquals(18, database.teamSlotDao().readByTournamentId(T).size)
    }

    @Test
    fun restoresPairingSpecificClearedNameWithoutClearingGlobalIdentity() = runBlocking {
        repository = RoomTournamentRepository(database)
        repository.observeAll().first()

        val result = repository.restoreGroupRotationUuidSnapshot(
            snapshot(
                assignments = listOf(
                    GroupRotationPairingAssignment(
                        tournamentId = T,
                        pairingKey = pairing("A:B"),
                        lobbySlotNumber = 5,
                        teamUUID = U1,
                        nameCleared = true,
                    ),
                ),
            ),
            OWNER,
        )

        assertEquals(GroupRotationUuidLocalRestoreResult.Restored, result)
        assertEquals(
            listOf(GroupRotationTeamIdentityEntity(T, U1, "Phoenix", "phoenix")),
            database.groupRotationTeamIdentityDao().readByTournamentId(T),
        )
        assertEquals(
            listOf(GroupRotationPairingTeamIdentityAssignmentEntity(T, "A:B", 5, U1, true)),
            database.groupRotationPairingTeamIdentityAssignmentDao().readByTournamentId(T),
        )

        val identityRead = RoomGroupRotationTeamIdentityRepository(database).read(tournament())
        require(identityRead is GroupRotationTeamIdentityReadResult.Loaded)
        assertEquals("", identityRead.snapshot.toSetupCandidate(tournament()).entries.single().teamName)
    }

    @Test
    fun restoresCloudClearedStateOverCachedValueWithoutClearingDatabase() = runBlocking {
        seedExistingTournament()
        repository = RoomTournamentRepository(database)
        repository.observeAll().first()
        assertEquals(
            false,
            database.groupRotationPairingTeamIdentityAssignmentDao()
                .readByTournamentId(T)
                .single()
                .nameCleared,
        )

        val result = repository.restoreGroupRotationUuidSnapshot(
            snapshot(
                assignments = listOf(
                    GroupRotationPairingAssignment(
                        tournamentId = T,
                        pairingKey = pairing("A:B"),
                        lobbySlotNumber = 5,
                        teamUUID = U1,
                        nameCleared = true,
                    ),
                ),
            ),
            OWNER,
        )

        assertEquals(GroupRotationUuidLocalRestoreResult.Restored, result)
        assertEquals(
            true,
            database.groupRotationPairingTeamIdentityAssignmentDao()
                .readByTournamentId(T)
                .single()
                .nameCleared,
        )
        assertEquals(
            GroupRotationTeamIdentityEntity(T, U1, "Phoenix", "phoenix"),
            database.groupRotationTeamIdentityDao().readByTournamentId(T).single(),
        )
    }

    @Test
    fun retainedDraftUuidMatchPreservesEvidenceAndReplacesUuidChildren() = runBlocking {
        seedExistingTournament()
        repository = RoomTournamentRepository(database)
        repository.observeAll().first()
        val existingMatch = Match(
            id = MATCH,
            tournamentId = T,
            matchNumber = 1,
            date = LocalDate.of(2026, 10, 12),
            mapName = "Local Map",
            status = MatchStatus.DRAFT,
            groupPairing = pairing("A:B"),
        )
        val retainedScreenshot = MatchLobbyScreenshotAssetEntity(
            tournamentId = T,
            matchId = MATCH,
            lobbyScreenshotIndex = 1,
            ownerUserId = OWNER,
            localRelativePath = "screenshots/$MATCH/lobby/1/original.png",
            fileExtension = "png",
            mimeType = "image/png",
            originalWidth = 100,
            originalHeight = 100,
            byteSize = 3,
            sha256 = "a".repeat(64),
            localStatus = ScreenshotLocalStatus.PRESERVED.name,
            uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
            uploadFailureCode = null,
            storageBucket = "bucket",
            storageObjectPath = "users/$OWNER/$MATCH/lobby/1/original.png",
            cropProfileId = "lobby",
            cropLeft = 0.1,
            cropTop = 0.1,
            cropRight = 0.9,
            cropBottom = 0.9,
            createdAt = 1,
            updatedAt = 2,
            preservedAt = 2,
            uploadedAt = 2,
            revision = 1,
        )
        val retainedEvidence = MatchOcrEvidenceEntity(MATCH, T, "retained", 3, "preserved")
        val retainedRow = MatchOcrRowEvidenceEntity(
            MATCH,
            T,
            0,
            "raw",
            1,
            2,
            1,
            "confidence",
            "safe",
            false,
        )
        val retainedCorrection = MatchOcrCorrectionSnapshotEntity(
            MATCH,
            T,
            0,
            1,
            2,
            1,
            true,
            false,
            false,
            3,
            "manual",
        )
        val retainedCalculated = MatchCalculatedEvidenceEntity(
            MATCH,
            T,
            OWNER,
            "lobby-evidence",
            "result-evidence",
            3,
        )
        database.withTransaction {
            database.matchDao().upsert(existingMatch.toEntity())
            database.screenshotMetadataDao().upsert(
                ScreenshotMetadataEntity(
                    matchId = MATCH,
                    tournamentId = T,
                    ownerUserId = OWNER,
                    localRelativePath = "screenshots/$MATCH/original.png",
                    fileExtension = "png",
                    mimeType = "image/png",
                    width = 100,
                    height = 100,
                    byteSize = 3,
                    sha256 = "b".repeat(64),
                    storageBucket = "bucket",
                    storageObjectPath = "users/$OWNER/$MATCH/original.png",
                    localStatus = ScreenshotLocalStatus.PRESERVED.name,
                    uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
                    uploadFailureCode = null,
                    createdAt = 1,
                    updatedAt = 2,
                    preservedAt = 2,
                    uploadedAt = 2,
                    revision = 1,
                ),
            )
            database.matchLobbyScreenshotAssetDao().upsert(retainedScreenshot)
            database.matchOcrEvidenceDao().insertSnapshot(
                retainedEvidence,
                listOf(retainedRow),
                listOf(retainedCorrection),
            )
            database.matchCalculatedEvidenceDao().upsert(retainedCalculated)
            database.groupRotationMatchIdentityHeaderDao().insert(
                GroupRotationMatchIdentityHeaderEntity(MATCH, T, "A:B"),
            )
            database.groupRotationMatchTeamAssignmentDao().insertAll(
                listOf(GroupRotationMatchTeamAssignmentEntity(MATCH, T, 5, U1)),
            )
            database.groupRotationMatchParticipantResultDao().insertAll(
                listOf(
                    GroupRotationMatchParticipantResultEntity(
                        MATCH,
                        T,
                        U1,
                        MatchParticipationStatus.PARTICIPATED.name,
                        1,
                        2,
                        0,
                    ),
                ),
            )
        }

        val cloudMatch = existingMatch.copy(mapName = "Cloud Map")
        val restored = repository.restoreGroupRotationUuidSnapshot(
            retainedDraftUuidSnapshot(cloudMatch),
            OWNER,
        )

        assertEquals(GroupRotationUuidLocalRestoreResult.Restored, restored)
        assertEquals(cloudMatch.toEntity(), database.matchDao().readById(MATCH))
        assertEquals(retainedScreenshot, database.matchLobbyScreenshotAssetDao().readByMatchAndIndex(MATCH, 1))
        assertEquals(MATCH, database.screenshotMetadataDao().readByMatchId(MATCH)?.matchId)
        assertEquals(retainedEvidence, database.matchOcrEvidenceDao().readMatchEvidence(MATCH))
        assertEquals(listOf(retainedRow), database.matchOcrEvidenceDao().readRowEvidence(MATCH))
        assertEquals(listOf(retainedCorrection), database.matchOcrEvidenceDao().readCorrectionSnapshots(MATCH))
        assertEquals(retainedCalculated, database.matchCalculatedEvidenceDao().readByOwner(T, MATCH, OWNER))
        assertEquals(
            listOf(GroupRotationMatchTeamAssignmentEntity(MATCH, T, 5, U2)),
            database.groupRotationMatchTeamAssignmentDao().readByMatchId(MATCH),
        )
        assertEquals(
            listOf(
                GroupRotationMatchParticipantResultEntity(
                    MATCH,
                    T,
                    U2,
                    MatchParticipationStatus.PARTICIPATED.name,
                    1,
                    9,
                    0,
                ),
            ),
            database.groupRotationMatchParticipantResultDao().readByMatchId(MATCH),
        )
        database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertTrue(!cursor.moveToFirst())
        }
    }

    @Test
    fun restoresTwoFinalizedUuidMatchesWithExactResultsIntoEmptyDatabase() = runBlocking {
        repository = RoomTournamentRepository(database)
        repository.observeAll().first()

        val restored = repository.restoreGroupRotationUuidSnapshot(twoFinalizedMatchSnapshot(), OWNER)

        assertEquals(GroupRotationUuidLocalRestoreResult.Restored, restored)
        assertEquals(19, database.syncRevisionDao().readByTournamentId(T)?.localRevision)
        assertEquals(18, database.teamSlotDao().readByTournamentId(T).size)
        assertEquals(12, database.groupRotationTeamIdentityDao().readByTournamentId(T).size)
        assertEquals(12, database.groupRotationPairingTeamIdentityAssignmentDao()
            .readByTournamentId(T).size)

        val matches = database.matchDao().readForTournamentId(T)
        assertEquals(listOf(MATCH_1, MATCH_2), matches.map { it.id })
        assertEquals(listOf(MatchStatus.FINALIZED.name, MatchStatus.FINALIZED.name), matches.map { it.status })

        val expectedAssignments = (1..12).map { lobbySlot ->
            GroupRotationMatchTeamAssignmentEntity(
                matchId = MATCH_1,
                tournamentId = T,
                lobbySlotNumber = lobbySlot,
                teamUUID = teamUuid(lobbySlot),
            )
        }
        assertEquals(
            expectedAssignments,
            database.groupRotationMatchTeamAssignmentDao().readByMatchId(MATCH_1),
        )
        assertEquals(
            expectedAssignments.map { it.copy(matchId = MATCH_2) },
            database.groupRotationMatchTeamAssignmentDao().readByMatchId(MATCH_2),
        )

        val expectedMatch1Results = expectedUuidResults(MATCH_1, 79)
        val expectedMatch2Results = expectedUuidResults(MATCH_2, 107)
        assertEquals(
            expectedMatch1Results,
            database.groupRotationMatchParticipantResultDao().readByMatchId(MATCH_1),
        )
        assertEquals(
            expectedMatch2Results,
            database.groupRotationMatchParticipantResultDao().readByMatchId(MATCH_2),
        )
        assertEquals(
            pairing("A:B"),
            database.groupRotationMatchIdentityHeaderDao().readByMatchId(MATCH_1)?.pairingKey
                ?.let(::pairing),
        )
        assertEquals(
            pairing("A:B"),
            database.groupRotationMatchIdentityHeaderDao().readByMatchId(MATCH_2)?.pairingKey
                ?.let(::pairing),
        )
    }

    @Test
    fun finalizedLocalMatchBlocksUuidReplacementWithoutMutation() = runBlocking {
        seedExistingTournament()
        repository = RoomTournamentRepository(database)
        repository.observeAll().first()
        database.matchDao().upsert(
            MatchEntity(
                id = MATCH,
                tournamentId = T,
                matchNumber = 1,
                date = "2026-10-12",
                mapName = "Erangel",
                status = "FINALIZED",
                groupPairingKey = "A:B",
            ),
        )
        val before = database.groupRotationTeamIdentityDao().readByTournamentId(T)

        val result = repository.restoreGroupRotationUuidSnapshot(snapshot(), OWNER)

        assertEquals(GroupRotationUuidLocalRestoreResult.Conflict, result)
        assertEquals(before, database.groupRotationTeamIdentityDao().readByTournamentId(T))
        assertEquals("FINALIZED", database.matchDao().readById(MATCH)?.status)
        assertEquals(7, database.syncRevisionDao().readByTournamentId(T)?.localRevision)
    }

    @Test
    fun invalidIdentityReplacementLeavesAllLocalWritesUnchanged() = runBlocking {
        seedExistingTournament()
        repository = RoomTournamentRepository(database)
        repository.observeAll().first()
        val beforeAssignments = database.groupRotationPairingTeamIdentityAssignmentDao()
            .readByTournamentId(T)

        val invalid = snapshot(
            identities = listOf(
                GroupRotationTeamIdentity(T, U1, "Phoenix", "same"),
                GroupRotationTeamIdentity(T, U2, "Other", "same"),
            ),
        )
        val result = repository.restoreGroupRotationUuidSnapshot(invalid, OWNER)

        assertEquals(GroupRotationUuidLocalRestoreResult.Invalid, result)
        assertEquals(
            listOf(GroupRotationTeamIdentityEntity(T, U1, "Original", "original")),
            database.groupRotationTeamIdentityDao().readByTournamentId(T),
        )
        assertEquals(beforeAssignments, database.groupRotationPairingTeamIdentityAssignmentDao()
            .readByTournamentId(T))
        assertEquals(7, database.syncRevisionDao().readByTournamentId(T)?.localRevision)
    }

    private suspend fun seedExistingTournament() {
        database.withTransaction {
            database.tournamentDao().upsert(tournamentEntity())
            database.tournamentGroupPairingDao().upsertAll(
                listOf(TournamentGroupPairingEntity(T, "A:B", "A", "B")),
            )
            database.groupRotationTeamIdentityDao().upsertAll(
                listOf(GroupRotationTeamIdentityEntity(T, U1, "Original", "original")),
            )
            database.groupRotationPairingTeamIdentityAssignmentDao().upsertAll(
                listOf(GroupRotationPairingTeamIdentityAssignmentEntity(T, "A:B", 5, U1)),
            )
            database.syncRevisionDao().upsert(SyncRevisionEntity(T, 7, 7))
        }
    }

    private fun twoFinalizedMatchSnapshot() = GroupRotationUuidCloudRestorationSnapshot(
        parent = TournamentCloudRestorationSnapshot(
            tournament = tournament().copy(selectedGroupPairings = listOf(pairing("A:B"))),
            slots = (1..18).map { slot -> TeamSlot.create(T, slot) },
            players = emptyList(),
            cloudRevision = CloudRevision(19),
            pairingLobbySlots = emptyList(),
        ),
        identities = (1..12).map { slot ->
            GroupRotationTeamIdentity(
                tournamentId = T,
                teamUUID = teamUuid(slot),
                displayName = "Team $slot",
                normalizedName = "team $slot",
            )
        },
        assignments = (1..12).map { lobbySlot ->
            GroupRotationPairingAssignment(T, pairing("A:B"), lobbySlot, teamUuid(lobbySlot))
        },
        matches = listOf(
            finalizedMatch(MATCH_1),
            finalizedMatch(MATCH_2),
        ),
        uuidMatches = listOf(
            finalizedUuidMatch(MATCH_1, 79),
            finalizedUuidMatch(MATCH_2, 107),
        ),
        cloudRevision = CloudRevision(19),
    )

    private fun finalizedMatch(id: String) = Match(
        id = id,
        tournamentId = T,
        matchNumber = if (id == MATCH_1) 1 else 2,
        date = LocalDate.of(2026, 10, 12),
        mapName = "Erangel",
        status = MatchStatus.FINALIZED,
        groupPairing = pairing("A:B"),
    )

    private fun finalizedUuidMatch(
        matchId: String,
        totalKills: Int,
    ) = com.hoggamers.rankforge.domain.tournament.GroupRotationUuidMatchRestorationSnapshot(
        match = finalizedMatch(matchId),
        identity = GroupRotationMatchIdentitySnapshot(
            tournamentId = T,
            pairing = pairing("A:B"),
            teams = (1..12).map { lobbySlot ->
                GroupRotationMatchTeamIdentity(
                    lobbySlotNumber = lobbySlot,
                    teamUUID = teamUuid(lobbySlot),
                    displayName = "Team $lobbySlot",
                )
            },
        ),
        participantResults = expectedDomainResults(matchId, totalKills),
    )

    private fun expectedDomainResults(matchId: String, totalKills: Int) =
        (1..12).map { placement ->
            GroupRotationMatchParticipantResult(
                teamUUID = teamUuid(placement),
                participationStatus = MatchParticipationStatus.PARTICIPATED,
                placement = placement,
                kills = if (totalKills == 79) {
                    if (placement == 12) 13 else placement
                } else {
                    if (placement == 12) 30 else placement + 1
                },
                pointAdjustment = 0,
            )
        }

    private fun expectedUuidResults(matchId: String, totalKills: Int) =
        expectedDomainResults(matchId, totalKills).map { result ->
            GroupRotationMatchParticipantResultEntity(
                matchId = matchId,
                tournamentId = T,
                teamUUID = result.teamUUID,
                participationStatus = result.participationStatus.name,
                placement = result.placement,
                kills = result.kills,
                pointAdjustment = result.pointAdjustment,
            )
        }

    private fun snapshot(
        identities: List<GroupRotationTeamIdentity> =
            listOf(GroupRotationTeamIdentity(T, U1, "Phoenix", "phoenix")),
        assignments: List<GroupRotationPairingAssignment> =
            listOf(GroupRotationPairingAssignment(T, pairing("A:B"), 5, U1)),
    ) = GroupRotationUuidCloudRestorationSnapshot(
        parent = TournamentCloudRestorationSnapshot(
            tournament = tournament(),
            slots = (1..18).map { slot -> TeamSlot.create(T, slot) },
            players = emptyList(),
            cloudRevision = CloudRevision(7),
            pairingLobbySlots = emptyList(),
        ),
        identities = identities,
        assignments = assignments,
        matches = emptyList(),
        uuidMatches = emptyList(),
        cloudRevision = CloudRevision(7),
    )

    private fun retainedDraftUuidSnapshot(match: Match) = GroupRotationUuidCloudRestorationSnapshot(
        parent = TournamentCloudRestorationSnapshot(
            tournament = tournament(),
            slots = (1..18).map { slot -> TeamSlot.create(T, slot) },
            players = emptyList(),
            cloudRevision = CloudRevision(7),
            pairingLobbySlots = emptyList(),
        ),
        identities = listOf(
            GroupRotationTeamIdentity(T, U2, "Cloud Team", "cloud team"),
        ),
        assignments = listOf(
            GroupRotationPairingAssignment(T, pairing("A:B"), 5, U2),
        ),
        matches = listOf(match),
        uuidMatches = listOf(
            GroupRotationUuidMatchRestorationSnapshot(
                match = match,
                identity = GroupRotationMatchIdentitySnapshot(
                    tournamentId = T,
                    pairing = pairing("A:B"),
                    teams = listOf(GroupRotationMatchTeamIdentity(5, U2, "Cloud Team")),
                ),
                participantResults = listOf(
                    GroupRotationMatchParticipantResult(
                        teamUUID = U2,
                        participationStatus = MatchParticipationStatus.PARTICIPATED,
                        placement = 1,
                        kills = 9,
                        pointAdjustment = 0,
                    ),
                ),
            ),
        ),
        cloudRevision = CloudRevision(7),
    )

    private fun tournament() = Tournament(
        id = T,
        name = "Rotation",
        stageName = "Stage",
        organizerContactNumber = "",
        status = TournamentStatus.DRAFT,
        ownerUserId = OWNER,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = 3,
        selectedGroupPairings = listOf(pairing("A:B"), pairing("A:C")),
    )

    private fun tournamentEntity() = TournamentEntity(
        id = T,
        name = "Rotation",
        stageName = "Stage",
        organizerContactNumber = "",
        status = TournamentStatus.DRAFT.name,
        ownerUserId = OWNER,
        format = TournamentFormat.GROUP_ROTATION.name,
        groupCount = 3,
    )

    private fun pairing(key: String): GroupPairing = GroupPairing.fromCanonicalKey(key)

    private fun teamUuid(slot: Int): String =
        "00000000-0000-0000-0000-${slot.toString().padStart(12, '0')}"

    private companion object {
        const val T = "00000000-0000-0000-0000-000000000001"
        const val OWNER = "00000000-0000-0000-0000-000000000099"
        const val MATCH = "00000000-0000-0000-0000-000000000101"
        const val MATCH_1 = "018b05a8-5fea-46e9-a581-e77aa603bdd4"
        const val MATCH_2 = "cfd65c4b-a79d-485a-8ea0-02f0e09f018f"
        const val U1 = "00000000-0000-0000-0000-0000000000a1"
        const val U2 = "00000000-0000-0000-0000-0000000000b2"
    }
}
