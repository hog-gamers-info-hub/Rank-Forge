package com.hoggamers.rankforge.data.tournament

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.data.local.MatchKillEntity
import com.hoggamers.rankforge.data.local.MatchPlacementEntity
import com.hoggamers.rankforge.data.local.DeletionIntentEntity
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.data.local.SyncQueueEntity
import com.hoggamers.rankforge.data.local.SyncRevisionEntity
import com.hoggamers.rankforge.data.local.TournamentEntity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import com.hoggamers.rankforge.domain.tournament.LocalDeletionResult
import com.hoggamers.rankforge.domain.tournament.DeletionIntent
import com.hoggamers.rankforge.domain.tournament.DeletionIntentPhase
import com.hoggamers.rankforge.domain.tournament.DeletionTargetType
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
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
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
            repository.createDraftMatch(match("foreign-match", foreignTournament.id))

            assertEquals(LocalDeletionResult.NotFound, repository.deleteTournamentLocallyByOwner("foreign", "owner-a"))
            assertEquals(LocalDeletionResult.NotFound, repository.deleteTournamentLocallyByOwner("null-owner", "owner-a"))
            assertEquals(LocalDeletionResult.NotFound, repository.deleteMatchLocallyByOwner("foreign-match", "owner-a"))
            assertTrue(database.tournamentDao().observeById("foreign").first() != null)
            assertTrue(database.tournamentDao().observeById("null-owner").first() != null)
            assertTrue(database.matchDao().observeById("foreign-match").first() != null)
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

}
