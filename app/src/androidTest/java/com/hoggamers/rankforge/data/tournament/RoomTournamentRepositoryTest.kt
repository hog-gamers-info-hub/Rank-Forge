package com.hoggamers.rankforge.data.tournament

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchDraftFieldValues
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchRepositoryResult
import com.hoggamers.rankforge.domain.tournament.SubmitMatchCorrectionRepositoryResult
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTournamentRepositoryTest {
    @Test
    fun databaseReopenRestoresDraftState() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-reopen.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val database = openDatabase(context, databaseName, databases)
            val repository = RoomTournamentRepository(database)
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.saveDraftMatchPlacements("match-1", listOf(MatchPlacement(1, 7)))
            repository.saveDraftMatchKills("match-1", listOf(MatchKill(1, 3)))
            repository.saveDraftMatchValue("tournament-1", "match-1", 1, "7", "3")

            database.close()
            val reopenedRepository = RoomTournamentRepository(
                openDatabase(context, databaseName, databases),
            )

            assertEquals(
                listOf(MatchPlacement(1, 7)),
                reopenedRepository.observeMatchById("match-1").first { it != null }!!.placements,
            )
            assertEquals(
                listOf(MatchKill(1, 3)),
                reopenedRepository.observeMatchById("match-1").first { it != null }!!.kills,
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun rosterAndMatchRestoreAfterDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-roster-match.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.saveRoster(
                "tournament-1",
                1,
                listOf(RosterPlayer("tournament-1", 1, "Player One")),
            )

            databases.last().close()
            val reopenedRepository = RoomTournamentRepository(
                openDatabase(context, databaseName, databases),
            )

            assertEquals(
                "Player One",
                reopenedRepository.observeRosterByTournamentId("tournament-1")
                    .first { it.isNotEmpty() }[1]!!.single().displayName,
            )
            assertEquals(
                "match-1",
                reopenedRepository.observeMatchesByTournamentId("tournament-1")
                    .first { it.isNotEmpty() }.single().id,
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun rawPlacementAndKillInputsRestoreAfterDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-raw-inputs.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.saveDraftMatchValue(
                "tournament-1",
                "match-1",
                1,
                placementInput = "not-a-number",
            )
            repository.saveDraftMatchValue(
                "tournament-1",
                "match-1",
                1,
                killsInput = "-2",
            )

            databases.last().close()
            val reopenedRepository = RoomTournamentRepository(
                openDatabase(context, databaseName, databases),
            )

            assertEquals(
                MatchDraftFieldValues("not-a-number", "-2"),
                reopenedRepository.observeDraftMatchValues("tournament-1", "match-1")
                    .first { it.isNotEmpty() }[1],
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun newMatchDoesNotInheritAnotherMatchDraftValues() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-match-isolation.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.createDraftMatch(draftMatch("tournament-1", "match-2", 2))
            repository.saveDraftMatchValue("tournament-1", "match-1", 1, "7", "3")

            assertTrue(repository.observeDraftMatchValues("tournament-1", "match-2").first().isEmpty())
            assertEquals(
                MatchDraftFieldValues("7", "3"),
                repository.observeDraftMatchValues("tournament-1", "match-1").first()[1],
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun resetAffectsOnlyTheSelectedMatch() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-reset-isolation.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.createDraftMatch(draftMatch("tournament-1", "match-2", 2))
            repository.saveDraftMatchPlacements("match-1", listOf(MatchPlacement(1, 7)))
            repository.saveDraftMatchKills("match-1", listOf(MatchKill(1, 3)))
            repository.saveDraftMatchValue("tournament-1", "match-1", 1, "7", "3")
            repository.saveDraftMatchValue("tournament-1", "match-2", 1, "1", "9")

            repository.clearDraftMatch("tournament-1", "match-1")

            val resetMatch = repository.observeMatchById("match-1").first { it != null }!!
            assertTrue(resetMatch.placements.isEmpty())
            assertTrue(resetMatch.kills.isEmpty())
            assertTrue(repository.observeDraftMatchValues("tournament-1", "match-1").first().isEmpty())
            assertEquals(
                MatchDraftFieldValues("1", "9"),
                repository.observeDraftMatchValues("tournament-1", "match-2").first()[1],
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun finalizedMatchResultsAndStatusSurviveDatabaseReopenAndDraftCacheIsCleared() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-finalized-match.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.saveDraftMatchValue("tournament-1", "match-1", 1, "1", "4")
            val result = repository.finalizeDraftMatch(
                matchId = "match-1",
                placements = (1..12).map { MatchPlacement(it, it) },
                kills = (1..12).map { MatchKill(it, it - 1) },
            )
            assertTrue(result is FinalizeMatchRepositoryResult.Finalized)

            databases.last().close()
            val reopenedRepository = RoomTournamentRepository(
                openDatabase(context, databaseName, databases),
            )
            val reopenedMatch = reopenedRepository.observeMatchById("match-1").first { it != null }!!

            assertEquals(MatchStatus.FINALIZED, reopenedMatch.status)
            assertEquals((1..12).toList(), reopenedMatch.placements.map { it.position })
            assertEquals((0..11).toList(), reopenedMatch.kills.map { it.kills })
            assertTrue(
                reopenedRepository.observeDraftMatchValues("tournament-1", "match-1")
                    .first()
                    .isEmpty(),
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun correctionPersistsHistoryAndClearsCorrectionCacheAfterDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-correction-reopen.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.finalizeDraftMatch(
                "match-1",
                (1..12).map { MatchPlacement(it, it) },
                (1..12).map { MatchKill(it, it - 1) },
            )
            repository.saveDraftMatchValue("tournament-1", "match-1", 1, "2", "1")
            val result = repository.submitMatchCorrection(
                "match-1",
                (1..12).map { slot -> MatchPlacement(slot, if (slot == 1) 2 else if (slot == 2) 1 else slot) },
                (1..12).map { slot -> MatchKill(slot, if (slot == 1) 1 else slot - 1) },
            )
            assertTrue(result is SubmitMatchCorrectionRepositoryResult.Submitted)

            databases.last().close()
            val reopenedRepository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            val reopened = reopenedRepository.observeMatchById("match-1").first { it != null }!!

            assertEquals(MatchStatus.FINALIZED, reopened.status)
            assertEquals(2, reopened.placements.first { it.teamSlotNumber == 1 }.position)
            assertEquals(1, reopened.kills.first { it.teamSlotNumber == 1 }.kills)
            assertEquals(1, reopened.correctionHistory.size)
            assertEquals(1, reopened.correctionHistory.single().previousPlacements.first().position)
            assertEquals(2, reopened.correctionHistory.single().correctedPlacements.first().position)
            assertTrue(reopenedRepository.observeDraftMatchValues("tournament-1", "match-1").first().isEmpty())
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun discardingCorrectionKeepsOriginalFinalizedResultUnchanged() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-correction-discard.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.finalizeDraftMatch(
                "match-1",
                (1..12).map { MatchPlacement(it, it) },
                (1..12).map { MatchKill(it, it - 1) },
            )
            val before = repository.observeMatchById("match-1").first { it != null }!!
            repository.saveDraftMatchValue("tournament-1", "match-1", 1, "12", "88")

            repository.clearMatchCorrectionDraft("tournament-1", "match-1")

            assertEquals(before, repository.observeMatchById("match-1").first { it != null })
            assertTrue(repository.observeDraftMatchValues("tournament-1", "match-1").first().isEmpty())
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    private fun openDatabase(
        context: android.content.Context,
        databaseName: String,
        databases: MutableList<RankForgeDatabase>,
    ): RankForgeDatabase = Room.databaseBuilder(
        context,
        RankForgeDatabase::class.java,
        databaseName,
    ).build().also { databases += it }

    private suspend fun seedTournamentAndMatch(
        repository: RoomTournamentRepository,
        tournamentId: String,
        matchId: String,
    ) {
        repository.create(
            Tournament(
                id = tournamentId,
                name = "Summer Cup",
                date = LocalDate.of(2026, 7, 24),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        repository.createDraftMatch(draftMatch(tournamentId, matchId, 1))
    }

    private fun draftMatch(tournamentId: String, matchId: String, matchNumber: Int) = Match(
        id = matchId,
        tournamentId = tournamentId,
        matchNumber = matchNumber,
        date = LocalDate.of(2026, 7, 24),
        mapName = "Bermuda",
        status = MatchStatus.DRAFT,
    )
}
