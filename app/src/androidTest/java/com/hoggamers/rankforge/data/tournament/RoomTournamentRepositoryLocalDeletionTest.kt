package com.hoggamers.rankforge.data.tournament

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.data.local.MatchKillEntity
import com.hoggamers.rankforge.data.local.MatchPlacementEntity
import com.hoggamers.rankforge.data.local.DeletionIntentEntity
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.data.local.ScreenshotMetadataEntity
import com.hoggamers.rankforge.data.local.SyncQueueEntity
import com.hoggamers.rankforge.data.local.SyncRevisionEntity
import com.hoggamers.rankforge.data.local.TournamentEntity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.auth.AccountDeletionLocalCleanupResult
import com.hoggamers.rankforge.domain.auth.AccountDeletionPhase
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import com.hoggamers.rankforge.domain.tournament.LocalDeletionResult
import com.hoggamers.rankforge.domain.tournament.DeletionIntent
import com.hoggamers.rankforge.domain.tournament.DeletionIntentPhase
import com.hoggamers.rankforge.domain.tournament.DeletionTargetType
import com.hoggamers.rankforge.domain.tournament.CreateMatchRepositoryResult
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.OwnerScopedTournamentConfirmationResult
import com.hoggamers.rankforge.domain.tournament.OwnerScopedTournamentMutationResult
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.presentation.screen.ImageSourceMimeTypeReader
import com.hoggamers.rankforge.presentation.screen.ImageSourceStreamOpener
import com.hoggamers.rankforge.presentation.screen.LocalImagePreserver
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTournamentRepositoryLocalDeletionTest {
    @Test
    fun accountCleanupRemovesOnlyOwnerDataAndPreservesUnownedAndForeignData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "account-owner-cleanup-${UUID.randomUUID()}.db"
        val root = File(context.cacheDir, "account-owner-cleanup-${UUID.randomUUID()}")
        val downloads = File(root.parentFile, "Downloads/PointIQ")
        val database = database(context, databaseName)
        val ownerA = "a0000000-0000-0000-0000-000000000001"
        val ownerB = "b0000000-0000-0000-0000-000000000001"
        val designA = "a1000000-0000-0000-0000-000000000001"
        try {
            val preserver = preserver(root)
            val repository = repository(database, preserver)
            val owned = tournament("account-owned").copy(ownerUserId = ownerA)
            val foreign = tournament("account-foreign").copy(ownerUserId = ownerB)
            val ownerless = tournament("account-ownerless").copy(ownerUserId = null)
            repository.create(owned)
            repository.create(foreign)
            repository.create(ownerless)
            val ownedMatch = Match(
                id = "account-owned-match",
                tournamentId = owned.id,
                matchNumber = 1,
                date = LocalDate.of(2026, 8, 21),
                mapName = "Alpine",
                status = MatchStatus.DRAFT,
            )
            assertEquals(CreateMatchRepositoryResult.Created, repository.createDraftMatch(ownedMatch))
            database.screenshotMetadataDao().upsert(
                ScreenshotMetadataEntity(
                    matchId = ownedMatch.id,
                    tournamentId = owned.id,
                    ownerUserId = ownerA,
                    localRelativePath = preserver.relativePath(owned.id, ownedMatch.id, "png"),
                    fileExtension = "png",
                    mimeType = "image/png",
                    width = 100,
                    height = 100,
                    byteSize = 4,
                    sha256 = "a".repeat(64),
                    storageBucket = null,
                    storageObjectPath = null,
                    localStatus = "PRESERVED",
                    uploadStatus = "PENDING",
                    uploadFailureCode = null,
                    createdAt = 1,
                    updatedAt = 1,
                    preservedAt = 1,
                    uploadedAt = null,
                    revision = 1,
                ),
            )
            database.syncQueueDao().insert(queueEntry("account-owner-queue", owned.id).copy(ownerUserId = ownerA))
            database.syncQueueDao().insert(queueEntry("account-foreign-queue", foreign.id).copy(ownerUserId = ownerB))
            database.syncQueueDao().insert(
                queueEntry("account-ownerless-queue", "ownerless").copy(
                    tournamentId = null,
                    ownerUserId = null,
                ),
            )
            database.deletionIntentDao().insertIfAbsent(
                DeletionIntentEntity(
                    targetType = DeletionTargetType.TOURNAMENT.name,
                    targetId = owned.id,
                    tournamentId = owned.id,
                    ownerUserId = ownerA,
                    phase = DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING.name,
                    updatedAtEpochMillis = 1,
                ),
            )

            val ownedFiles = matchFiles(preserver, owned.id, ownedMatch.id)
            ownedFiles.forEach(::writeFile)
            val customDesignFile = preserver.customDesignPreservedFile(ownerA, designA, "png")
            writeFile(customDesignFile)
            val missingCustomDesignFile = preserver.customDesignPreservedFile(
                ownerA,
                "a1000000-0000-0000-0000-000000000002",
                "png",
            )
            missingCustomDesignFile.parentFile?.mkdirs()
            val foreignCustomDesignFile = preserver.customDesignPreservedFile(ownerB, designA, "png")
            writeFile(foreignCustomDesignFile)
            val downloadsFile = File(downloads, "keep.txt")
            writeFile(downloadsFile)

            repository.markRemoteConfirmed(ownerA)
            assertEquals(AccountDeletionLocalCleanupResult.Completed, repository.purgeLocalDataForOwner(ownerA))

            assertTrue(database.tournamentDao().observeById(owned.id).first() == null)
            assertTrue(database.matchDao().observeById(ownedMatch.id).first() == null)
            assertTrue(database.screenshotMetadataDao().readByMatchId(ownedMatch.id) == null)
            assertTrue(database.syncQueueDao().observeAll().first().none { it.ownerUserId == ownerA })
            assertTrue(database.deletionIntentDao().findByTargetAndOwner(
                DeletionTargetType.TOURNAMENT.name,
                owned.id,
                ownerA,
            ) == null)
            assertTrue(ownedFiles.none { it.exists() })
            assertFalse(customDesignFile.exists())
            assertFalse(missingCustomDesignFile.parentFile?.exists() == true)
            assertTrue(database.tournamentDao().observeById(foreign.id).first() != null)
            assertTrue(database.tournamentDao().observeById(ownerless.id).first() != null)
            assertTrue(database.syncQueueDao().observeAll().first().any { it.id == "account-foreign-queue" })
            assertTrue(database.syncQueueDao().observeAll().first().any { it.id == "account-ownerless-queue" })
            assertTrue(foreignCustomDesignFile.exists())
            assertTrue(downloadsFile.exists())
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(databaseName)
            root.deleteRecursively()
            downloads.deleteRecursively()
        }
    }

    @Test
    fun remoteConfirmedMarkerSurvivesRecreationAndRepeatedCleanupIsIdempotent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "account-recovery-${UUID.randomUUID()}.db"
        val root = File(context.cacheDir, "account-recovery-${UUID.randomUUID()}")
        val ownerA = "a0000000-0000-0000-0000-000000000001"
        val database = database(context, databaseName)
        try {
            val preserver = preserver(root)
            val repository = repository(database, preserver)
            val owned = tournament("account-recovery-owned").copy(ownerUserId = ownerA)
            repository.create(owned)
            writeFile(preserver.customDesignPreservedFile(
                ownerA,
                "a1000000-0000-0000-0000-000000000001",
                "jpg",
            ))
            repository.markRemoteConfirmed(ownerA)
            database.close()

            val reopenedDatabase = database(context, databaseName)
            try {
                val recoveredRepository = repository(reopenedDatabase, preserver)
                assertEquals(AccountDeletionPhase.REMOTE_CONFIRMED, recoveredRepository.readMarker()?.phase)
                assertEquals(AccountDeletionLocalCleanupResult.Completed, recoveredRepository.purgeLocalDataForOwner(ownerA))
                assertEquals(AccountDeletionLocalCleanupResult.Completed, recoveredRepository.purgeLocalDataForOwner(ownerA))
                assertTrue(reopenedDatabase.tournamentDao().observeById(owned.id).first() == null)
            } finally {
                reopenedDatabase.close()
            }
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    @Test
    fun failedAccountCleanupRetainsOwnerDataForRetry() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "account-cleanup-failure-${UUID.randomUUID()}.db"
        val root = File(context.cacheDir, "account-cleanup-failure-${UUID.randomUUID()}")
        val database = database(context, databaseName)
        val ownerA = "a0000000-0000-0000-0000-000000000001"
        try {
            val preserver = preserver(root)
            val repository = repository(database, preserver)
            val owned = tournament("account-cleanup-failure").copy(ownerUserId = ownerA)
            repository.create(owned)
            val unknownFile = File(
                root,
                "custom-designs/users/$ownerA/a1000000-0000-0000-0000-000000000001/keep.txt",
            )
            writeFile(unknownFile)
            repository.markRemoteConfirmed(ownerA)

            assertEquals(AccountDeletionLocalCleanupResult.Failed, repository.purgeLocalDataForOwner(ownerA))
            assertEquals(AccountDeletionPhase.REMOTE_CONFIRMED, repository.readMarker()?.phase)
            assertTrue(database.tournamentDao().observeById(owned.id).first() != null)
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    @Test
    fun deletingMatchWithoutLocalAssetsDoesNotBlockRoomDeletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "local-empty-match-delete-${UUID.randomUUID()}.db"
        val root = File(context.cacheDir, "local-empty-match-delete-${UUID.randomUUID()}")
        val database = database(context, databaseName)
        try {
            val repository = repository(database, preserver(root))
            val tournament = tournament("tournament-empty-match-delete")
            repository.create(tournament)
            repository.saveTeamNames(tournament.id, mapOf(1 to "Team One", 2 to "Team Two"))
            repository.createDraftMatch(
                Match(
                    id = "match-without-assets",
                    tournamentId = tournament.id,
                    matchNumber = 1,
                    date = LocalDate.of(2026, 8, 21),
                    mapName = "Alpine",
                    status = MatchStatus.DRAFT,
                ),
            )
            claim(database, DeletionTargetType.MATCH, "match-without-assets", tournament.id)

            assertEquals(
                LocalDeletionResult.Deleted,
                repository.deleteMatchLocallyByOwner("match-without-assets", "owner-a"),
            )
            assertTrue(database.matchDao().observeByTournamentId(tournament.id).first().isEmpty())
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    @Test
    fun deletingOneMatchPreservesOtherMatchesAndPurgesItsTournamentQueue() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "local-match-delete-${UUID.randomUUID()}.db"
        val root = File(context.cacheDir, "local-match-delete-${UUID.randomUUID()}")
        val database = database(context, databaseName)
        try {
            val preserver = preserver(root)
            val repository = repository(database, preserver)
            val tournament = tournament("tournament-match-delete")
            repository.create(tournament)
            repository.saveTeamNames(tournament.id, mapOf(1 to "Team One", 2 to "Team Two"))
            val matches = (1..3).map { number ->
                Match(
                    id = "match-$number",
                    tournamentId = tournament.id,
                    matchNumber = number,
                    date = LocalDate.of(2026, 8, number),
                    mapName = "Bermuda",
                    status = MatchStatus.DRAFT,
                ).also { match -> repository.createDraftMatch(match) }
            }
            database.matchPlacementDao().upsertAll(listOf(MatchPlacementEntity("match-2", 1, 1)))
            database.matchKillDao().upsertAll(listOf(MatchKillEntity("match-2", 1, 4)))
            database.syncQueueDao().insert(queueEntry("queue-match", tournament.id))
            database.syncQueueDao().insert(queueEntry("queue-other", "other-tournament"))
            claim(database, DeletionTargetType.MATCH, "match-2", tournament.id)

            val selectedFiles = matchFiles(preserver, tournament.id, matches[1].id)
            selectedFiles.forEach(::writeFile)
            val survivorFile = preserver.preservedFile(tournament.id, matches[0].id, "png")
            writeFile(survivorFile)

            assertEquals(LocalDeletionResult.Deleted, repository.deleteMatchLocallyByOwner("match-2", "owner-a"))
            assertEquals(listOf(1, 3), database.matchDao().observeByTournamentId(tournament.id).first().map { it.matchNumber })
            assertTrue(database.matchPlacementDao().observeByMatchId("match-2").first().isEmpty())
            assertTrue(database.matchKillDao().observeByMatchId("match-2").first().isEmpty())
            assertTrue(selectedFiles.none { it.exists() })
            assertTrue(survivorFile.exists())
            assertEquals("Team One", database.teamSlotDao().observeByTournamentId(tournament.id).first().first { it.slotNumber == 1 }.teamName)
            assertTrue(database.syncQueueDao().observeAll().first().none { it.tournamentId == tournament.id })
            assertTrue(database.syncQueueDao().observeAll().first().any { it.id == "queue-other" })
            assertFalse(database.stateDao().readPayload().orEmpty().contains("match-2"))

            database.close()
            val restartedDatabase = database(context, databaseName)
            try {
                val restartedRepository = repository(restartedDatabase, preserver)
                assertEquals(listOf("match-1", "match-3"), restartedRepository.observeMatchesByTournamentId(tournament.id).first().map { it.id })
                assertNull(restartedRepository.observeMatchById("match-2").first())
            } finally {
                restartedDatabase.close()
            }
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    @Test
    fun deletingTournamentRemovesRoomRevisionQueueFilesAndLegacyState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "local-tournament-delete-${UUID.randomUUID()}.db"
        val root = File(context.cacheDir, "local-tournament-delete-${UUID.randomUUID()}")
        val database = database(context, databaseName)
        try {
            val preserver = preserver(root)
            val repository = repository(database, preserver)
            val tournament = tournament("tournament-delete")
            repository.create(tournament)
            repository.saveTeamNames(tournament.id, mapOf(1 to "Team One"))
            repository.saveRoster(tournament.id, 1, listOf(RosterPlayer(tournament.id, 1, "Player One")))
            repository.createDraftMatch(
                Match(
                    id = "match-delete",
                    tournamentId = tournament.id,
                    matchNumber = 1,
                    date = LocalDate.of(2026, 8, 21),
                    mapName = "Alpine",
                    status = MatchStatus.DRAFT,
                ),
            )
            database.syncRevisionDao().upsert(SyncRevisionEntity(tournament.id, 7, 6))
            database.syncQueueDao().insert(queueEntry("queue-tournament", tournament.id))
            val files = buildList {
                add(preserver.preservedFile(tournament.id, "match-delete", "png"))
                add(preserver.matchResultPreservedFile(tournament.id, "match-delete", MatchResultScreenshotRole.MATCH_RESULT_UPPER, "png"))
                add(preserver.matchResultPreservedFile(tournament.id, "match-delete", MatchResultScreenshotRole.MATCH_RESULT_LOWER, "jpg"))
                (1..3).forEach { index -> add(preserver.lobbyPreservedFile(tournament.id, "match-delete", index, "png")) }
                (1..3).forEach { index -> add(preserver.resolveRelativePath(preserver.rosterRelativePath(tournament.id, index, "png"))!!) }
            }
            files.forEach(::writeFile)
            claim(database, DeletionTargetType.TOURNAMENT, tournament.id, tournament.id)

            assertEquals(LocalDeletionResult.Deleted, repository.deleteTournamentLocallyByOwner(tournament.id, "owner-a"))
            assertNull(database.tournamentDao().observeById(tournament.id).first())
            assertTrue(database.teamSlotDao().observeByTournamentId(tournament.id).first().isEmpty())
            assertTrue(database.rosterPlayerDao().observeByTournamentId(tournament.id).first().isEmpty())
            assertTrue(database.matchDao().observeByTournamentId(tournament.id).first().isEmpty())
            assertNull(database.syncRevisionDao().readByTournamentId(tournament.id))
            assertTrue(database.syncQueueDao().observeAll().first().none { it.tournamentId == tournament.id })
            assertTrue(files.none { it.exists() })
            assertFalse(database.stateDao().readPayload().orEmpty().contains(tournament.id))

            database.close()
            val restartedDatabase = database(context, databaseName)
            try {
                val restartedRepository = repository(restartedDatabase, preserver)
                assertTrue(restartedRepository.observeAll().first().none { it.id == tournament.id })
            } finally {
                restartedDatabase.close()
            }
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    @Test
    fun foreignAndNullOwnerTargetsLeaveTournamentAndMatchDataUntouched() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "local-owner-delete-${UUID.randomUUID()}.db"
        val root = File(context.cacheDir, "local-owner-delete-${UUID.randomUUID()}")
        val database = database(context, databaseName)
        try {
            val repository = repository(database, preserver(root))
            val foreignTournament = tournament("foreign").copy(ownerUserId = "owner-b")
            val nullOwnerTournament = tournament("null-owner").copy(ownerUserId = null)
            repository.create(foreignTournament)
            repository.create(nullOwnerTournament)
            repository.saveTeamNames(foreignTournament.id, mapOf(1 to "Foreign Team"))
            repository.saveTeamNames(nullOwnerTournament.id, mapOf(1 to "Legacy Team"))
            assertEquals(
                CreateMatchRepositoryResult.Created,
                repository.createDraftMatch(match("foreign-match", foreignTournament.id)),
            )
            assertEquals(
                CreateMatchRepositoryResult.Created,
                repository.createDraftMatch(match("null-owner-match", nullOwnerTournament.id)),
            )
            assertTrue(database.tournamentDao().observeById("foreign").first() != null)
            assertTrue(database.tournamentDao().observeById("null-owner").first() != null)
            assertTrue(database.matchDao().observeById("foreign-match").first() != null)
            assertTrue(database.matchDao().observeById("null-owner-match").first() != null)

            assertEquals(LocalDeletionResult.NotFound, repository.deleteTournamentLocallyByOwner("foreign", "owner-a"))
            assertEquals(LocalDeletionResult.NotFound, repository.deleteTournamentLocallyByOwner("null-owner", "owner-a"))
            assertEquals(LocalDeletionResult.NotFound, repository.deleteMatchLocallyByOwner("foreign-match", "owner-a"))
            assertEquals(LocalDeletionResult.NotFound, repository.deleteMatchLocallyByOwner("null-owner-match", "owner-a"))
            assertTrue(database.tournamentDao().observeById("foreign").first() != null)
            assertTrue(database.tournamentDao().observeById("null-owner").first() != null)
            assertTrue(database.matchDao().observeById("foreign-match").first() != null)
            assertTrue(database.matchDao().observeById("null-owner-match").first() != null)
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    @Test
    fun missingOrWrongPhaseClaimIsReportedWithoutChangingLocalState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "local-claim-loss-${UUID.randomUUID()}.db"
        val root = File(context.cacheDir, "local-claim-loss-${UUID.randomUUID()}")
        val database = database(context, databaseName)
        try {
            val repository = repository(database, preserver(root))
            val tournament = tournament("claim-loss-tournament")
            repository.create(tournament)
            repository.saveTeamNames(tournament.id, mapOf(1 to "Before"))
            repository.saveRoster(tournament.id, 1, listOf(RosterPlayer(tournament.id, 1, "Before Player")))
            repository.createDraftMatch(match("claim-loss-match", tournament.id))
            database.syncRevisionDao().upsert(SyncRevisionEntity(tournament.id, 4, 3))
            database.syncQueueDao().insert(queueEntry("claim-loss-queue", tournament.id))
            val beforeTournament = database.tournamentDao().observeById(tournament.id).first()
            val beforeTeam = database.teamSlotDao().observeByTournamentId(tournament.id).first()
            val beforeRoster = database.rosterPlayerDao().observeByTournamentId(tournament.id).first()
            val beforeMatches = database.matchDao().observeByTournamentId(tournament.id).first()
            val beforeRevision = database.syncRevisionDao().readByTournamentId(tournament.id)

            assertEquals(
                LocalDeletionResult.CleanupClaimLost,
                repository.deleteTournamentLocallyByOwner(tournament.id, "owner-a"),
            )
            assertEquals(
                LocalDeletionResult.CleanupClaimLost,
                repository.deleteMatchLocallyByOwner("claim-loss-match", "owner-a"),
            )
            claim(
                database,
                DeletionTargetType.MATCH,
                "claim-loss-match",
                tournament.id,
                ownerUserId = "owner-b",
            )
            assertEquals(
                LocalDeletionResult.CleanupClaimLost,
                repository.deleteMatchLocallyByOwner("claim-loss-match", "owner-a"),
            )
            database.deletionIntentDao().insertIfAbsent(
                DeletionIntentEntity(
                    targetType = DeletionTargetType.TOURNAMENT.name,
                    targetId = tournament.id,
                    tournamentId = tournament.id,
                    ownerUserId = "owner-a",
                    phase = DeletionIntentPhase.DELETE_STARTED.name,
                    updatedAtEpochMillis = 2,
                ),
            )
            assertEquals(
                LocalDeletionResult.CleanupClaimLost,
                repository.deleteTournamentLocallyByOwner(tournament.id, "owner-a"),
            )

            assertEquals(beforeTournament, database.tournamentDao().observeById(tournament.id).first())
            assertEquals(beforeTeam, database.teamSlotDao().observeByTournamentId(tournament.id).first())
            assertEquals(beforeRoster, database.rosterPlayerDao().observeByTournamentId(tournament.id).first())
            assertEquals(beforeMatches, database.matchDao().observeByTournamentId(tournament.id).first())
            assertEquals(beforeRevision, database.syncRevisionDao().readByTournamentId(tournament.id))
            assertTrue(database.syncQueueDao().observeAll().first().any { it.id == "claim-loss-queue" })
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    @Test
    fun tournamentClaimRemovedDuringCleanupDoesNotFinalizeDbDeletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "local-tournament-claim-race-${UUID.randomUUID()}.db"
        val root = File(context.cacheDir, "local-tournament-claim-race-${UUID.randomUUID()}")
        val database = database(context, databaseName)
        try {
            val operations = PausingFileOperations()
            val preserver = preserver(root, operations)
            val repository = repository(database, preserver)
            val tournament = tournament("tournament-claim-race")
            repository.create(tournament)
            database.syncRevisionDao().upsert(SyncRevisionEntity(tournament.id, 5, 4))
            database.syncQueueDao().insert(queueEntry("claim-race-queue", tournament.id))
            val file = preserver.resolveRelativePath(preserver.rosterRelativePath(tournament.id, 1, "png"))!!
            writeFile(file)
            claim(database, DeletionTargetType.TOURNAMENT, tournament.id, tournament.id)
            val beforeTournament = database.tournamentDao().observeById(tournament.id).first()
            val beforeRevision = database.syncRevisionDao().readByTournamentId(tournament.id)
            val beforeQueue = database.syncQueueDao().observeAll().first()

            val deletion = async(Dispatchers.IO) {
                repository.deleteTournamentLocallyByOwner(tournament.id, "owner-a")
            }
            assertTrue(operations.started.await(5, TimeUnit.SECONDS))
            database.deletionIntentDao().deleteByTargetAndOwner(
                DeletionTargetType.TOURNAMENT.name,
                tournament.id,
                "owner-a",
            )
            operations.release.countDown()

            assertEquals(LocalDeletionResult.CleanupClaimLost, deletion.await())
            assertEquals(beforeTournament, database.tournamentDao().observeById(tournament.id).first())
            assertEquals(beforeRevision, database.syncRevisionDao().readByTournamentId(tournament.id))
            assertEquals(beforeQueue, database.syncQueueDao().observeAll().first())
            assertFalse(file.exists())
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    @Test
    fun matchClaimRemovedDuringCleanupDoesNotFinalizeDbDeletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "local-match-claim-race-${UUID.randomUUID()}.db"
        val root = File(context.cacheDir, "local-match-claim-race-${UUID.randomUUID()}")
        val database = database(context, databaseName)
        try {
            val operations = PausingFileOperations()
            val preserver = preserver(root, operations)
            val repository = repository(database, preserver)
            val tournament = tournament("match-claim-race-tournament")
            repository.create(tournament)
            repository.saveTeamNames(tournament.id, mapOf(1 to "Team One"))
            val match = match("match-claim-race", tournament.id)
            assertEquals(CreateMatchRepositoryResult.Created, repository.createDraftMatch(match))
            database.syncRevisionDao().upsert(SyncRevisionEntity(tournament.id, 6, 5))
            database.syncQueueDao().insert(queueEntry("match-claim-race-queue", tournament.id))
            val file = preserver.preservedFile(tournament.id, match.id, "png")
            writeFile(file)
            claim(database, DeletionTargetType.MATCH, match.id, tournament.id)
            val beforeMatch = database.matchDao().observeById(match.id).first()
            val beforeTournament = database.tournamentDao().observeById(tournament.id).first()
            val beforeRevision = database.syncRevisionDao().readByTournamentId(tournament.id)
            val beforeQueue = database.syncQueueDao().observeAll().first()

            val deletion = async(Dispatchers.IO) {
                repository.deleteMatchLocallyByOwner(match.id, "owner-a")
            }
            assertTrue(operations.started.await(5, TimeUnit.SECONDS))
            database.deletionIntentDao().deleteByTargetAndOwner(
                DeletionTargetType.MATCH.name,
                match.id,
                "owner-a",
            )
            operations.release.countDown()

            assertEquals(LocalDeletionResult.CleanupClaimLost, deletion.await())
            assertEquals(beforeMatch, database.matchDao().observeById(match.id).first())
            assertEquals(beforeTournament, database.tournamentDao().observeById(tournament.id).first())
            assertEquals(beforeRevision, database.syncRevisionDao().readByTournamentId(tournament.id))
            assertEquals(beforeQueue, database.syncQueueDao().observeAll().first())
            assertFalse(file.exists())
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    @Test
    fun ownedTournamentClaimBlocksOwnerMutationsWithoutChangingRoomState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "local-mutation-block-${UUID.randomUUID()}.db"
        val database = database(context, databaseName)
        try {
            val repository = repository(database, preserver(File(context.cacheDir, "local-mutation-block-${UUID.randomUUID()}")))
            val tournament = tournament("blocked-tournament")
            repository.create(tournament)
            repository.saveTeamNames(tournament.id, mapOf(1 to "Before"))
            repository.saveRoster(tournament.id, 1, listOf(RosterPlayer(tournament.id, 1, "Before Player")))
            val beforeTeam = database.teamSlotDao().observeByTournamentId(tournament.id).first()
            val beforeRoster = database.rosterPlayerDao().observeByTournamentId(tournament.id).first()
            val beforeTournament = database.tournamentDao().observeById(tournament.id).first()
            val beforeRevision = database.syncRevisionDao().readByTournamentId(tournament.id)
            claim(database, DeletionTargetType.TOURNAMENT, tournament.id, tournament.id)

            assertEquals(
                OwnerScopedTournamentMutationResult.TournamentNotFound,
                repository.saveTeamNamesByOwner(tournament.id, "owner-a", mapOf(1 to "After")),
            )
            assertEquals(
                OwnerScopedTournamentMutationResult.TournamentNotFound,
                repository.saveRosterByOwner(
                    tournament.id,
                    "owner-a",
                    1,
                    listOf(RosterPlayer(tournament.id, 1, "After Player")),
                ),
            )
            assertEquals(
                OwnerScopedTournamentConfirmationResult.TournamentNotFound,
                repository.confirmTournamentByOwner(tournament.id, "owner-a"),
            )
            assertEquals(beforeTeam, database.teamSlotDao().observeByTournamentId(tournament.id).first())
            assertEquals(beforeRoster, database.rosterPlayerDao().observeByTournamentId(tournament.id).first())
            assertEquals(beforeTournament, database.tournamentDao().observeById(tournament.id).first())
            assertEquals(beforeRevision, database.syncRevisionDao().readByTournamentId(tournament.id))
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun database(context: Context, name: String): RankForgeDatabase =
        Room.databaseBuilder(context, RankForgeDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()

    private fun preserver(root: File): LocalImagePreserver = LocalImagePreserver(
        appPrivateRoot = root,
        sourceStreamOpener = ImageSourceStreamOpener { null },
        mimeTypeReader = ImageSourceMimeTypeReader { null },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun preserver(
        root: File,
        fileOperations: com.hoggamers.rankforge.presentation.screen.LocalImageFileOperations,
    ): LocalImagePreserver = LocalImagePreserver(
        appPrivateRoot = root,
        sourceStreamOpener = ImageSourceStreamOpener { null },
        mimeTypeReader = ImageSourceMimeTypeReader { null },
        fileOperations = fileOperations,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun repository(
        database: RankForgeDatabase,
        preserver: LocalImagePreserver,
    ): RoomTournamentRepository =
        RoomTournamentRepository(
            database = database,
            localImagePreserver = preserver,
        )

    private fun tournament(id: String) = Tournament(
        id = id,
        name = "Test Tournament",
        date = LocalDate.of(2026, 8, 21),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        ownerUserId = "owner-a",
    )

    private fun match(id: String, tournamentId: String) = Match(
        id = id,
        tournamentId = tournamentId,
        matchNumber = 1,
        date = LocalDate.of(2026, 8, 21),
        mapName = "Alpine",
        status = MatchStatus.DRAFT,
    )

    private fun queueEntry(id: String, tournamentId: String) = SyncQueueEntity(
        id = id,
        operationType = SyncQueueOperationType.TOURNAMENT_UPLOAD.name,
        tournamentId = tournamentId,
        createdAtEpochMillis = 1,
        status = SyncQueueStatus.PENDING.name,
        failureCategory = null,
        attemptCount = 0,
        ownerUserId = "owner-a",
    )

    private suspend fun claim(
        database: RankForgeDatabase,
        targetType: DeletionTargetType,
        targetId: String,
        tournamentId: String,
        ownerUserId: String = "owner-a",
    ) {
        database.deletionIntentDao().insertIfAbsent(
            DeletionIntentEntity(
                targetType = targetType.name,
                targetId = targetId,
                tournamentId = tournamentId,
                ownerUserId = ownerUserId,
                phase = DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING.name,
                updatedAtEpochMillis = 1,
            ),
        )
    }

    private fun matchFiles(
        preserver: LocalImagePreserver,
        tournamentId: String,
        matchId: String,
    ): List<File> = buildList {
        add(preserver.preservedFile(tournamentId, matchId, "png"))
        add(preserver.matchResultPreservedFile(tournamentId, matchId, MatchResultScreenshotRole.MATCH_RESULT_UPPER, "png"))
        add(preserver.matchResultPreservedFile(tournamentId, matchId, MatchResultScreenshotRole.MATCH_RESULT_LOWER, "jpg"))
        (1..3).forEach { index -> add(preserver.lobbyPreservedFile(tournamentId, matchId, index, "png")) }
    }

    private fun writeFile(file: File) {
        file.parentFile?.mkdirs()
        file.writeText("test")
    }

    private class PausingFileOperations : com.hoggamers.rankforge.presentation.screen.LocalImageFileOperations {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun ensureDirectory(directory: File): Boolean =
            directory.isDirectory || (directory.mkdirs() && directory.isDirectory)

        override fun createTempFile(directory: File): File =
            File.createTempFile("original-", ".tmp", directory)

        override fun openOutput(file: File): OutputStream = FileOutputStream(file)

        override fun atomicMove(source: File, target: File): Boolean {
            if (target.exists()) target.delete()
            return source.renameTo(target)
        }

        override fun listFiles(directory: File): List<File>? =
            if (!directory.exists()) emptyList() else directory.listFiles()?.toList()

        override fun delete(file: File): Boolean {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "timed out waiting to release cleanup" }
            return !file.exists() || file.delete()
        }
    }

}
