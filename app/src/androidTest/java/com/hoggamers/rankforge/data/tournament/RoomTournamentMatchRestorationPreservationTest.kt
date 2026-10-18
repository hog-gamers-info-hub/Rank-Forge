package com.hoggamers.rankforge.data.tournament

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.local.*
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.MatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchRestorationConflictException
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTournamentMatchRestorationPreservationTest {
    @Test
    fun retainedMatchPreservesEvidenceAndReconcilesChildrenWhileObsoleteMatchCascades() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-match-preservation.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder(context, RankForgeDatabase::class.java, databaseName).build()
        try {
            val tournamentId = "restoration-preservation-tournament"
            val retainedId = "retained-match"
            val obsoleteId = "obsolete-match"
            val owner = "owner-a"
            val repository = RoomTournamentRepository(database)
            repository.create(
                Tournament(
                    id = tournamentId,
                    name = "Restoration",
                    stageName = "Stage",
                    organizerContactNumber = "",
                    status = TournamentStatus.DRAFT,
                    ownerUserId = owner,
                ),
            )
            repository.saveTeamNames(tournamentId, mapOf(1 to "Team 1"))
            repository.createDraftMatch(match(tournamentId, retainedId, 1))
            repository.createDraftMatch(match(tournamentId, obsoleteId, 2))
            repository.saveDraftMatchValue(tournamentId, retainedId, 1, "7", "11", 3)
            repository.saveDraftMatchValue(tournamentId, obsoleteId, 1, "8", "12", 4)

            val retainedAsset = lobbyAsset(tournamentId, retainedId)
            val retainedResultAsset = resultAsset(tournamentId, retainedId)
            database.withTransaction {
                database.matchPlacementDao().upsertAll(
                    listOf(
                        MatchPlacementEntity(retainedId, 1, 2),
                        MatchPlacementEntity(retainedId, 2, 3),
                        MatchPlacementEntity(obsoleteId, 1, 1),
                    ),
                )
                database.matchKillDao().upsertAll(
                    listOf(
                        MatchKillEntity(retainedId, 1, 4),
                        MatchKillEntity(retainedId, 2, 5),
                        MatchKillEntity(obsoleteId, 1, 1),
                    ),
                )
                database.matchParticipantResultDao().upsertAll(
                    listOf(
                        MatchParticipantResultEntity(
                            retainedId,
                            1,
                            MatchParticipationStatus.PARTICIPATED.name,
                            2,
                            4,
                        ),
                        MatchParticipantResultEntity(
                            retainedId,
                            2,
                            MatchParticipationStatus.PARTICIPATED.name,
                            3,
                            5,
                        ),
                        MatchParticipantResultEntity(
                            obsoleteId,
                            1,
                            MatchParticipationStatus.PARTICIPATED.name,
                            1,
                            1,
                        ),
                    ),
                )
                database.screenshotMetadataDao().upsert(
                    ScreenshotMetadataEntity(
                        matchId = retainedId,
                        tournamentId = tournamentId,
                        ownerUserId = owner,
                        localRelativePath = "screenshots/retained/original.png",
                        fileExtension = "png",
                        mimeType = "image/png",
                        width = 100,
                        height = 100,
                        byteSize = 3,
                        sha256 = "a".repeat(64),
                        storageBucket = "bucket",
                        storageObjectPath = "users/$owner/retained.png",
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
                database.matchLobbyScreenshotAssetDao().upsert(retainedAsset)
                database.matchResultScreenshotAssetDao().upsert(retainedResultAsset)
                database.matchLobbyOcrCacheDao().upsert(
                    MatchLobbyOcrCacheEntity(
                        tournamentId,
                        retainedId,
                        1,
                        retainedAsset.sha256,
                        100,
                        100,
                        "lobby",
                        0.1,
                        0.1,
                        0.9,
                        0.9,
                        1,
                        "lobby-cache",
                        3,
                    ),
                )
                database.matchResultOcrCacheDao().upsert(
                    MatchResultOcrCacheEntity(
                        tournamentId,
                        retainedId,
                        "MATCH_RESULT_UPPER",
                        retainedResultAsset.sha256,
                        100,
                        100,
                        "match-result",
                        0.1,
                        0.1,
                        0.9,
                        0.9,
                        1,
                        "result-cache",
                        3,
                    ),
                )
                database.matchOcrEvidenceDao().insertSnapshot(
                    MatchOcrEvidenceEntity(retainedId, tournamentId, "retained-source", 4, "preserved"),
                    listOf(
                        MatchOcrRowEvidenceEntity(
                            retainedId,
                            tournamentId,
                            0,
                            "raw",
                            1,
                            2,
                            1,
                            "confidence",
                            "safe",
                            false,
                        ),
                    ),
                    listOf(
                        MatchOcrCorrectionSnapshotEntity(
                            retainedId,
                            tournamentId,
                            0,
                            1,
                            2,
                            1,
                            true,
                            false,
                            false,
                            4,
                            "manual",
                        ),
                    ),
                )
                database.matchCorrectionDao().upsertAll(
                    listOf(MatchCorrectionEntity(retainedId, 0, "[]", "[]", "[]", "[]")),
                )
                database.matchCalculatedEvidenceDao().upsert(
                    MatchCalculatedEvidenceEntity(
                        retainedId,
                        tournamentId,
                        owner,
                        "calculated-lobby",
                        "calculated-result",
                        4,
                    ),
                )
                database.groupRotationTeamIdentityDao().upsertAll(
                    listOf(GroupRotationTeamIdentityEntity(tournamentId, "team-uuid", "Team 1", "team 1")),
                )
                database.groupRotationMatchIdentityHeaderDao().insert(
                    GroupRotationMatchIdentityHeaderEntity(obsoleteId, tournamentId, "A:B"),
                )
                database.groupRotationMatchTeamAssignmentDao().insertAll(
                    listOf(GroupRotationMatchTeamAssignmentEntity(obsoleteId, tournamentId, 1, "team-uuid")),
                )
                database.groupRotationMatchParticipantResultDao().insertAll(
                    listOf(
                        GroupRotationMatchParticipantResultEntity(
                            obsoleteId,
                            tournamentId,
                            "team-uuid",
                            MatchParticipationStatus.PARTICIPATED.name,
                            1,
                            1,
                        ),
                    ),
                )
            }

            val incoming = match(tournamentId, retainedId, 1).copy(
                mapName = "Authoritative Map",
                placements = listOf(MatchPlacement(1, 1)),
                kills = listOf(com.hoggamers.rankforge.domain.tournament.MatchKill(1, 9)),
                participantResults = listOf(
                    MatchParticipantResult(
                        1,
                        MatchParticipationStatus.PARTICIPATED,
                        1,
                        9,
                    ),
                ),
            )
            repository.replaceMatchesByOwner(
                tournamentId = tournamentId,
                expectedOwnerUserId = owner,
                snapshot = MatchCloudRestorationSnapshot(tournamentId, listOf(incoming)),
            )

            assertEquals(incoming.id, database.matchDao().readById(retainedId)?.id)
            assertEquals(
                listOf(MatchPlacementEntity(retainedId, 1, 1)),
                database.matchPlacementDao().observeByMatchId(retainedId).first(),
            )
            assertEquals(
                listOf(MatchKillEntity(retainedId, 1, 9)),
                database.matchKillDao().observeByMatchId(retainedId).first(),
            )
            assertEquals(1, database.matchParticipantResultDao().observeByMatchId(retainedId).first().size)
            assertEquals(retainedAsset, database.matchLobbyScreenshotAssetDao()
                .readByMatchAndIndex(retainedId, 1))
            assertEquals(retainedResultAsset, database.matchResultScreenshotAssetDao()
                .readByMatchAndRole(retainedId, "MATCH_RESULT_UPPER"))
            assertEquals(retainedId, database.screenshotMetadataDao().readByMatchId(retainedId)?.matchId)
            assertEquals(retainedId, database.matchLobbyOcrCacheDao().readByMatchAndIndex(retainedId, 1)?.matchId)
            assertEquals(retainedId, database.matchResultOcrCacheDao().readByMatchAndRole(retainedId, "MATCH_RESULT_UPPER")?.matchId)
            assertEquals(retainedId, database.matchOcrEvidenceDao().readMatchEvidence(retainedId)?.matchId)
            assertEquals(1, database.matchOcrEvidenceDao().readRowEvidence(retainedId).size)
            assertEquals(1, database.matchOcrEvidenceDao().readCorrectionSnapshots(retainedId).size)
            assertEquals(1, database.matchCorrectionDao().observeByMatchId(retainedId).first().size)
            assertEquals(retainedId, database.matchCalculatedEvidenceDao().readByOwner(tournamentId, retainedId, owner)?.matchId)
            assertEquals(1, database.matchDraftValueDao().observeByMatchId(retainedId).first().size)

            assertTrue(database.matchDao().readById(obsoleteId) == null)
            assertTrue(database.matchDraftValueDao().observeByMatchId(obsoleteId).first().isEmpty())
            assertTrue(database.screenshotMetadataDao().readByMatchId(obsoleteId) == null)
            assertTrue(database.groupRotationMatchIdentityHeaderDao().readByMatchId(obsoleteId) == null)
            assertTrue(database.groupRotationMatchTeamAssignmentDao().readByMatchId(obsoleteId).isEmpty())
            assertTrue(database.groupRotationMatchParticipantResultDao().readByMatchId(obsoleteId).isEmpty())
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun conflictingFinalizedRestorationRejectsWithoutMutation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-finalized-conflict.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder(context, RankForgeDatabase::class.java, databaseName).build()
        try {
            val tournamentId = "finalized-conflict-tournament"
            val matchId = "finalized-conflict-match"
            val owner = "owner-a"
            RoomTournamentRepository(database).create(
                Tournament(
                    id = tournamentId,
                    name = "Finalized conflict",
                    stageName = "Stage",
                    organizerContactNumber = "",
                    status = TournamentStatus.DRAFT,
                    ownerUserId = owner,
                ),
            )
            val existing = match(tournamentId, matchId, 1).copy(
                mapName = "Local authoritative map",
                status = MatchStatus.FINALIZED,
                placements = listOf(MatchPlacement(1, 1)),
                kills = listOf(com.hoggamers.rankforge.domain.tournament.MatchKill(1, 3)),
                participantResults = listOf(
                    MatchParticipantResult(
                        1,
                        MatchParticipationStatus.PARTICIPATED,
                        1,
                        3,
                    ),
                ),
            )
            database.withTransaction {
                database.matchDao().upsert(existing.toEntity())
                database.matchPlacementDao().upsertAll(existing.placements.map { it.toEntity(matchId) })
                database.matchKillDao().upsertAll(existing.kills.map { it.toEntity(matchId) })
                database.matchParticipantResultDao().upsertAll(
                    existing.participantResults.map { it.toEntity(matchId) },
                )
            }

            val repository = RoomTournamentRepository(database)
            val conflicting = existing.copy(mapName = "Conflicting cloud map")
            var conflictThrown = false
            try {
                repository.replaceMatchesByOwner(
                    tournamentId = tournamentId,
                    expectedOwnerUserId = owner,
                    snapshot = MatchCloudRestorationSnapshot(tournamentId, listOf(conflicting)),
                )
            } catch (_: FinalizedMatchRestorationConflictException) {
                conflictThrown = true
            }

            assertTrue(conflictThrown)
            assertEquals(existing.toEntity(), database.matchDao().readById(matchId))
            assertEquals(
                existing.placements.map { it.toEntity(matchId) },
                database.matchPlacementDao().observeByMatchId(matchId).first(),
            )
            assertEquals(
                existing.kills.map { it.toEntity(matchId) },
                database.matchKillDao().observeByMatchId(matchId).first(),
            )
            assertEquals(1, database.matchParticipantResultDao().observeByMatchId(matchId).first().size)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun restorationRollsBackParentAndChildrenAfterConstraintFailure() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-rollback.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder(context, RankForgeDatabase::class.java, databaseName).build()
        try {
            val tournamentId = "restoration-rollback-tournament"
            val matchId = "restoration-rollback-match"
            val owner = "owner-a"
            val repository = RoomTournamentRepository(database)
            repository.create(
                Tournament(
                    id = tournamentId,
                    name = "Rollback",
                    stageName = "Stage",
                    organizerContactNumber = "",
                    status = TournamentStatus.DRAFT,
                    ownerUserId = owner,
                ),
            )
            repository.saveTeamNames(tournamentId, mapOf(1 to "Team 1", 2 to "Team 2"))
            val existing = match(tournamentId, matchId, 1).copy(
                mapName = "Local Map",
                placements = listOf(MatchPlacement(1, 1)),
                kills = listOf(com.hoggamers.rankforge.domain.tournament.MatchKill(1, 3)),
                participantResults = listOf(
                    MatchParticipantResult(
                        1,
                        MatchParticipationStatus.PARTICIPATED,
                        1,
                        3,
                    ),
                ),
            )
            repository.createDraftMatch(existing)

            val screenshot = ScreenshotMetadataEntity(
                matchId = matchId,
                tournamentId = tournamentId,
                ownerUserId = owner,
                localRelativePath = "screenshots/rollback/original.png",
                fileExtension = "png",
                mimeType = "image/png",
                width = 100,
                height = 100,
                byteSize = 3,
                sha256 = "d".repeat(64),
                storageBucket = "bucket",
                storageObjectPath = "users/$owner/rollback.png",
                localStatus = ScreenshotLocalStatus.PRESERVED.name,
                uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
                uploadFailureCode = null,
                createdAt = 1,
                updatedAt = 2,
                preservedAt = 2,
                uploadedAt = 2,
                revision = 1,
            )
            val evidence = MatchOcrEvidenceEntity(
                matchId,
                tournamentId,
                "rollback-source",
                4,
                "preserved",
            )
            val rowEvidence = MatchOcrRowEvidenceEntity(
                matchId,
                tournamentId,
                0,
                "raw",
                1,
                2,
                1,
                "confidence",
                "safe",
                false,
            )
            val correction = MatchOcrCorrectionSnapshotEntity(
                matchId,
                tournamentId,
                0,
                1,
                2,
                1,
                true,
                false,
                false,
                4,
                "manual",
            )
            database.withTransaction {
                database.screenshotMetadataDao().upsert(screenshot)
                database.matchOcrEvidenceDao().insertSnapshot(
                    evidence,
                    listOf(rowEvidence),
                    listOf(correction),
                )
                database.groupRotationTeamIdentityDao().upsertAll(
                    listOf(GroupRotationTeamIdentityEntity(tournamentId, "uuid-1", "Team 1", "team 1")),
                )
                database.groupRotationMatchIdentityHeaderDao().insert(
                    GroupRotationMatchIdentityHeaderEntity(matchId, tournamentId, "A:B"),
                )
                database.groupRotationMatchTeamAssignmentDao().insertAll(
                    listOf(GroupRotationMatchTeamAssignmentEntity(matchId, tournamentId, 1, "uuid-1")),
                )
                database.groupRotationMatchParticipantResultDao().insertAll(
                    listOf(
                        GroupRotationMatchParticipantResultEntity(
                            matchId,
                            tournamentId,
                            "uuid-1",
                            MatchParticipationStatus.PARTICIPATED.name,
                            1,
                            1,
                        ),
                    ),
                )
                database.syncRevisionDao().upsert(SyncRevisionEntity(tournamentId, 1, 1))
            }

            database.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER force_restoration_rollback
                BEFORE INSERT ON match_participant_results
                WHEN NEW.match_id = 'restoration-rollback-match'
                     AND NEW.team_slot_number = 2
                BEGIN
                    SELECT RAISE(ABORT, 'forced restoration rollback');
                END
                """.trimIndent(),
            )
            val beforeMatch = database.matchDao().readById(matchId)
            val beforePlacements = database.matchPlacementDao().observeByMatchId(matchId).first()
            val beforeKills = database.matchKillDao().observeByMatchId(matchId).first()
            val beforeParticipantResults = database.matchParticipantResultDao()
                .observeByMatchId(matchId)
                .first()
            val beforeScreenshot = database.screenshotMetadataDao().readByMatchId(matchId)
            val beforeEvidence = database.matchOcrEvidenceDao().readMatchEvidence(matchId)
            val beforeHeader = database.groupRotationMatchIdentityHeaderDao().readByMatchId(matchId)
            val beforeRevision = database.syncRevisionDao().readByTournamentId(tournamentId)
            val beforeRepositoryMatch = repository.observeMatchById(matchId).first()
            val incoming = existing.copy(
                mapName = "Cloud Map",
                placements = listOf(
                    MatchPlacement(1, 1),
                    MatchPlacement(2, 2),
                ),
                kills = listOf(
                    com.hoggamers.rankforge.domain.tournament.MatchKill(1, 9),
                    com.hoggamers.rankforge.domain.tournament.MatchKill(2, 8),
                ),
                participantResults = listOf(
                    MatchParticipantResult(
                        1,
                        MatchParticipationStatus.PARTICIPATED,
                        1,
                        9,
                    ),
                    MatchParticipantResult(
                        2,
                        MatchParticipationStatus.PARTICIPATED,
                        2,
                        8,
                    ),
                ),
            )

            var failure: Throwable? = null
            try {
                repository.replaceMatchesByOwner(
                    tournamentId = tournamentId,
                    expectedOwnerUserId = owner,
                    snapshot = MatchCloudRestorationSnapshot(
                        tournamentId,
                        listOf(incoming),
                    ),
                )
            } catch (error: Throwable) {
                failure = error
            }

            assertTrue(failure != null)
            assertTrue(
                generateSequence(failure) { it.cause }
                    .any { it.message?.contains("forced restoration rollback") == true },
            )
            assertEquals(beforeMatch, database.matchDao().readById(matchId))
            assertEquals(beforePlacements, database.matchPlacementDao().observeByMatchId(matchId).first())
            assertEquals(beforeKills, database.matchKillDao().observeByMatchId(matchId).first())
            assertEquals(
                beforeParticipantResults,
                database.matchParticipantResultDao().observeByMatchId(matchId).first(),
            )
            assertEquals(beforeScreenshot, database.screenshotMetadataDao().readByMatchId(matchId))
            assertEquals(beforeEvidence, database.matchOcrEvidenceDao().readMatchEvidence(matchId))
            assertEquals(beforeHeader, database.groupRotationMatchIdentityHeaderDao().readByMatchId(matchId))
            assertEquals(beforeRevision, database.syncRevisionDao().readByTournamentId(tournamentId))
            assertEquals(beforeRepositoryMatch, repository.observeMatchById(matchId).first())
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun draftRestorationPreservesFinalizedHistoryAndRetainedDraftChildren() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-draft-preservation.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder(context, RankForgeDatabase::class.java, databaseName).build()
        try {
            val tournamentId = "draft-restoration-tournament"
            val finalizedId = "finalized-match"
            val retainedId = "retained-draft"
            val obsoleteId = "obsolete-draft"
            val owner = "owner-a"
            RoomTournamentRepository(database).create(
                Tournament(
                    id = tournamentId,
                    name = "Draft restoration",
                    stageName = "Stage",
                    organizerContactNumber = "",
                    status = TournamentStatus.DRAFT,
                    ownerUserId = owner,
                ),
            )
            val finalized = match(tournamentId, finalizedId, 1).copy(
                mapName = "Finalized Map",
                status = MatchStatus.FINALIZED,
                placements = listOf(MatchPlacement(1, 1)),
                kills = listOf(com.hoggamers.rankforge.domain.tournament.MatchKill(1, 3)),
                participantResults = listOf(
                    MatchParticipantResult(
                        1,
                        MatchParticipationStatus.PARTICIPATED,
                        1,
                        3,
                    ),
                ),
            )
            val retained = match(tournamentId, retainedId, 2)
            val obsolete = match(tournamentId, obsoleteId, 3)
            database.withTransaction {
                database.matchDao().upsert(finalized.toEntity())
                database.matchDao().upsert(retained.toEntity())
                database.matchDao().upsert(obsolete.toEntity())
                database.matchPlacementDao().upsertAll(finalized.placements.map { it.toEntity(finalizedId) })
                database.matchKillDao().upsertAll(finalized.kills.map { it.toEntity(finalizedId) })
                database.matchParticipantResultDao().upsertAll(
                    finalized.participantResults.map { it.toEntity(finalizedId) },
                )
                database.matchCorrectionDao().upsertAll(
                    listOf(MatchCorrectionEntity(finalizedId, 0, "[]", "[]", "[]", "[]")),
                )
                database.matchDraftValueDao().upsert(
                    MatchDraftValueEntity(retainedId, 1, "retained-placement", "retained-kills", 5),
                )
                database.matchDraftValueDao().upsert(
                    MatchDraftValueEntity(obsoleteId, 1, "obsolete-placement", "obsolete-kills", 6),
                )
                database.screenshotMetadataDao().upsert(
                    ScreenshotMetadataEntity(
                        matchId = retainedId,
                        tournamentId = tournamentId,
                        ownerUserId = owner,
                        localRelativePath = "screenshots/draft/retained.png",
                        fileExtension = "png",
                        mimeType = "image/png",
                        width = 100,
                        height = 100,
                        byteSize = 3,
                        sha256 = "e".repeat(64),
                        storageBucket = "bucket",
                        storageObjectPath = "users/$owner/retained-draft.png",
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
                database.screenshotMetadataDao().upsert(
                    ScreenshotMetadataEntity(
                        matchId = obsoleteId,
                        tournamentId = tournamentId,
                        ownerUserId = owner,
                        localRelativePath = "screenshots/draft/obsolete.png",
                        fileExtension = "png",
                        mimeType = "image/png",
                        width = 100,
                        height = 100,
                        byteSize = 3,
                        sha256 = "f".repeat(64),
                        storageBucket = "bucket",
                        storageObjectPath = "users/$owner/obsolete-draft.png",
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
            }

            val repository = RoomTournamentRepository(database)
            val restoredDraft = retained.copy(mapName = "Cloud Draft")
            repository.replaceDraftMatchesByOwner(
                tournamentId = tournamentId,
                expectedOwnerUserId = owner,
                snapshot = MatchCloudRestorationSnapshot(
                    tournamentId,
                    listOf(restoredDraft),
                    CloudRevision(2),
                ),
            )

            assertEquals(finalized.toEntity(), database.matchDao().readById(finalizedId))
            assertEquals(1, database.matchParticipantResultDao().observeByMatchId(finalizedId).first().size)
            assertEquals(1, database.matchCorrectionDao().observeByMatchId(finalizedId).first().size)
            assertEquals("Cloud Draft", database.matchDao().readById(retainedId)?.mapName)
            assertEquals(1, database.matchDraftValueDao().observeByMatchId(retainedId).first().size)
            assertEquals(retainedId, database.screenshotMetadataDao().readByMatchId(retainedId)?.matchId)
            assertTrue(database.matchDao().readById(obsoleteId) == null)
            assertTrue(database.matchDraftValueDao().observeByMatchId(obsoleteId).first().isEmpty())
            assertTrue(database.screenshotMetadataDao().readByMatchId(obsoleteId) == null)

            val restoredMatches = repository.observeMatchesByTournamentId(tournamentId).first()
            assertEquals(
                listOf(finalizedId, retainedId),
                restoredMatches.map { it.id },
            )
            assertEquals(
                listOf(MatchStatus.FINALIZED, MatchStatus.DRAFT),
                restoredMatches.map { it.status },
            )
            assertEquals(
                restoredMatches.map { it.id }.toSet(),
                database.matchDao().readForTournamentId(tournamentId).map { it.id }.toSet(),
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun match(tournamentId: String, id: String, number: Int) = Match(
        id = id,
        tournamentId = tournamentId,
        matchNumber = number,
        date = LocalDate.of(2026, 10, 18),
        mapName = "Original Map",
        status = MatchStatus.DRAFT,
    )

    private fun lobbyAsset(tournamentId: String, matchId: String) = MatchLobbyScreenshotAssetEntity(
        tournamentId = tournamentId,
        matchId = matchId,
        lobbyScreenshotIndex = 1,
        ownerUserId = "owner-a",
        localRelativePath = "screenshots/retained/lobby/original.png",
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 100,
        originalHeight = 100,
        byteSize = 3,
        sha256 = "b".repeat(64),
        localStatus = ScreenshotLocalStatus.PRESERVED.name,
        uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
        uploadFailureCode = null,
        storageBucket = "bucket",
        storageObjectPath = "users/owner-a/lobby.png",
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

    private fun resultAsset(tournamentId: String, matchId: String) = MatchResultScreenshotAssetEntity(
        tournamentId = tournamentId,
        matchId = matchId,
        screenshotKind = "MATCH_RESULT",
        screenshotRole = "MATCH_RESULT_UPPER",
        ownerUserId = "owner-a",
        localRelativePath = "screenshots/retained/result/upper/original.png",
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 100,
        originalHeight = 100,
        byteSize = 3,
        sha256 = "c".repeat(64),
        localStatus = ScreenshotLocalStatus.PRESERVED.name,
        uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
        uploadFailureCode = null,
        storageBucket = "bucket",
        storageObjectPath = "users/owner-a/result.png",
        cropProfileId = "match-result",
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
}
