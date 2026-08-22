package com.hoggamers.rankforge.data.tournament

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.domain.tournament.ConfirmedRosterReplacementCandidate
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TournamentSummaryRepositoryTest {
    @Test
    fun summaryCountsTrimmedTeamsAndAllLocalMatches() = runBlocking {
        withRepository { repository, database, _ ->
            repository.saveTeamNames(
                TOURNAMENT_ID,
                mapOf(1 to "  Alpha  ", 2 to "   ", 3 to "Beta"),
            )
            repository.createDraftMatch(match("match-1", 1))
            repository.createDraftMatch(match("match-2", 2))
            repository.finalizeDraftMatch("match-1", listOf(MatchPlacement(1, 1)), listOf(MatchKill(1, 0)))

            val summary = repository.observeSummaries().first { it.single().totalMatches == 2 }.single()

            assertEquals(2, summary.totalTeams)
            assertEquals(2, summary.totalMatches)
            assertEquals(database.tournamentDao().observeById(TOURNAMENT_ID).first()!!.lastUpdatedEpochMillis, summary.lastUpdatedEpochMillis)
        }
    }

    @Test
    fun effectiveAndRejectedMutationsRespectLastUpdatedContract() = runBlocking {
        withRepository { repository, database, clock ->
            val createdAt = database.tournamentDao().observeById(TOURNAMENT_ID).first()!!.lastUpdatedEpochMillis
            assertEquals(1_000L, createdAt)

            clock.setMillis(2_000L)
            repository.saveTeamNames(TOURNAMENT_ID, mapOf(1 to "Alpha"))
            val afterTeamName = lastUpdated(database)
            assertEquals(2_000L, afterTeamName)

            repository.saveTeamNames(TOURNAMENT_ID, mapOf(1 to "Alpha"))
            assertEquals(afterTeamName, lastUpdated(database))

            clock.setMillis(2_500L)
            repository.createDraftMatch(match("match-1", 1))
            assertEquals(2_500L, lastUpdated(database))

            clock.setMillis(3_000L)
            repository.saveDraftMatchPlacements("match-1", listOf(MatchPlacement(1, 1)))
            assertEquals(3_000L, lastUpdated(database))
            repository.saveDraftMatchPlacements("match-1", listOf(MatchPlacement(1, 1)))
            assertEquals(3_000L, lastUpdated(database))

            clock.setMillis(4_000L)
            repository.saveDraftMatchKills("match-1", listOf(MatchKill(1, 0)))
            assertEquals(4_000L, lastUpdated(database))

            clock.setMillis(5_000L)
            repository.finalizeDraftMatch("match-1", listOf(MatchPlacement(1, 1)), listOf(MatchKill(1, 0)))
            assertEquals(5_000L, lastUpdated(database))

            clock.setMillis(6_000L)
            repository.submitMatchCorrection(
                matchId = "match-1",
                placements = listOf(MatchPlacement(1, 1)),
                kills = listOf(MatchKill(1, 0)),
            )
            assertEquals(6_000L, lastUpdated(database))

            clock.setMillis(7_000L)
            repository.deleteMatchLocally("match-1")
            assertEquals(7_000L, lastUpdated(database))
            assertEquals(0, repository.observeSummaries().first().single().totalMatches)
        }
    }

    @Test
    fun rosterOnlyAndRejectedMutationsDoNotAdvanceLastUpdated() = runBlocking {
        withRepository { repository, database, clock ->
            repository.saveTeamNames(TOURNAMENT_ID, mapOf(1 to "Alpha"))
            val beforeRoster = lastUpdated(database)
            repository.saveRoster(
                TOURNAMENT_ID,
                1,
                listOf(com.hoggamers.rankforge.domain.tournament.RosterPlayer(TOURNAMENT_ID, 1, "Player")),
            )
            assertEquals(beforeRoster, lastUpdated(database))

            clock.setMillis(9_000L)
            repository.createDraftMatch(match("match-1", 1))
            val beforeRejected = lastUpdated(database)
            repository.saveDraftMatchPlacements("match-1", listOf(MatchPlacement(1, 0)))
            assertEquals(beforeRejected, lastUpdated(database))
        }
    }

    @Test
    fun existingTimestampIsNeverErasedByRosterOrStatusPersistence() = runBlocking {
        withRepository { repository, database, clock ->
            clock.setMillis(2_000L)
            repository.saveTeamNames(TOURNAMENT_ID, mapOf(1 to "Alpha"))
            clock.setMillis(3_000L)
            assertEquals(true, repository.confirmTournament(TOURNAMENT_ID))
            assertEquals(3_000L, lastUpdated(database))

            clock.setMillis(4_000L)
            repository.saveRoster(
                TOURNAMENT_ID,
                1,
                listOf(com.hoggamers.rankforge.domain.tournament.RosterPlayer(TOURNAMENT_ID, 1, "Player")),
            )
            assertEquals(TournamentStatus.DRAFT, repository.observeById(TOURNAMENT_ID).first()!!.status)
            assertEquals(4_000L, lastUpdated(database))
        }
    }

    @Test
    fun draftIdenticalSaveTeamNamesIsARealNoOp() = runBlocking {
        withRepository { repository, database, clock ->
            clock.setMillis(2_000L)
            repository.saveTeamNames(TOURNAMENT_ID, mapOf(1 to ""))
            assertEquals(TournamentStatus.DRAFT, repository.observeById(TOURNAMENT_ID).first()!!.status)
            assertEquals(1_000L, lastUpdated(database))
        }
    }

    @Test
    fun confirmedIdenticalSaveTeamNamesPreservesStatusTransition() = runBlocking {
        withRepository { repository, database, clock ->
            val confirmedId = "confirmed-identical"
            repository.create(tournament(id = confirmedId, status = TournamentStatus.CONFIRMED))
            clock.setMillis(2_000L)

            repository.saveTeamNames(confirmedId, mapOf(1 to ""))

            assertEquals(TournamentStatus.DRAFT, repository.observeById(confirmedId).first()!!.status)
            assertEquals(2_000L, database.tournamentDao().observeById(confirmedId).first()!!.lastUpdatedEpochMillis)
        }
    }

    @Test
    fun confirmedRosterReplacementAdvancesTimestampWhenTeamNamesChange() = runBlocking {
        withRepository { repository, database, clock ->
            clock.setMillis(2_000L)
            val result = repository.replaceConfirmedTournamentRoster(replacementCandidate("Changed"))

            assertEquals(com.hoggamers.rankforge.domain.tournament.ReplaceConfirmedTournamentRosterRepositoryResult.Replaced, result)
            assertEquals(TournamentStatus.CONFIRMED, repository.observeById(TOURNAMENT_ID).first()!!.status)
            assertEquals(2_000L, lastUpdated(database))
        }
    }

    @Test
    fun confirmedRosterReplacementWithOnlyRosterChangesDoesNotAdvanceTimestamp() = runBlocking {
        withRepository { repository, database, clock ->
            val names = TeamSlot.SLOT_NUMBERS.associateWith { slotNumber -> "Team $slotNumber" }
            clock.setMillis(2_000L)
            repository.saveTeamNames(TOURNAMENT_ID, names)
            clock.setMillis(3_000L)
            assertEquals(true, repository.confirmTournament(TOURNAMENT_ID))
            val beforeReplacement = lastUpdated(database)

            clock.setMillis(4_000L)
            val result = repository.replaceConfirmedTournamentRoster(
                replacementCandidate("Team", names = names),
            )

            assertEquals(com.hoggamers.rankforge.domain.tournament.ReplaceConfirmedTournamentRosterRepositoryResult.Replaced, result)
            assertEquals(TournamentStatus.CONFIRMED, repository.observeById(TOURNAMENT_ID).first()!!.status)
            assertEquals(beforeReplacement, lastUpdated(database))
        }
    }

    @Test
    fun cloudRestoreOfExistingTournamentPreservesLocalTimestamp() = runBlocking {
        withRepository { repository, database, clock ->
            clock.setMillis(2_000L)
            repository.saveTeamNames(TOURNAMENT_ID, mapOf(1 to "Local"))
            val beforeRestore = lastUpdated(database)
            clock.setMillis(9_000L)

            repository.restore(cloudSnapshot(TOURNAMENT_ID, "Cloud version"))

            assertEquals(beforeRestore, lastUpdated(database))
        }
    }

    @Test
    fun cloudRestoreOfNewTournamentDoesNotFabricateLocalTimestamp() = runBlocking {
        withRepository { repository, database, _ ->
            val cloudId = "cloud-only"

            repository.restore(cloudSnapshot(cloudId, "Cloud only"))

            assertNull(database.tournamentDao().observeById(cloudId).first()!!.lastUpdatedEpochMillis)
        }
    }

    @Test
    fun summariesObservedBeforeRoomMutationEmitUpdatedCountsAndTimestamp() = runBlocking {
        withRepository { repository, _, clock ->
            val emissions = Channel<com.hoggamers.rankforge.domain.tournament.TournamentSummary>(Channel.UNLIMITED)
            val observation = launch {
                repository.observeSummaries().collect { summaries -> emissions.send(summaries.single()) }
            }
            try {
                val initial = receiveSummary(emissions) { it.totalTeams == 0 && it.totalMatches == 0 }
                assertEquals(1_000L, initial.lastUpdatedEpochMillis)

                clock.setMillis(2_000L)
                repository.saveTeamNames(TOURNAMENT_ID, mapOf(1 to "Alpha"))
                val afterTeamNames = receiveSummary(emissions) { it.totalTeams == 1 && it.lastUpdatedEpochMillis == 2_000L }
                assertEquals(1, afterTeamNames.totalTeams)

                clock.setMillis(3_000L)
                repository.createDraftMatch(match("observed-match", 1))
                val afterMatch = receiveSummary(emissions) { it.totalMatches == 1 && it.lastUpdatedEpochMillis == 3_000L }
                assertEquals(1, afterMatch.totalMatches)
            } finally {
                observation.cancel()
                emissions.close()
            }
        }
    }

    private suspend fun withRepository(
        block: suspend (RoomTournamentRepository, RankForgeDatabase, TestClock) -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "tournament-summary-${System.nanoTime()}.db"
        val clock = TestClock(1_000L)
        val database = Room.databaseBuilder(context, RankForgeDatabase::class.java, databaseName).build()
        try {
            val repository = RoomTournamentRepository(database, clock)
            repository.create(tournament())
            block(repository, database, clock)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private suspend fun lastUpdated(database: RankForgeDatabase): Long? =
        database.tournamentDao().observeById(TOURNAMENT_ID).first()!!.lastUpdatedEpochMillis

    private fun tournament(
        id: String = TOURNAMENT_ID,
        status: TournamentStatus = TournamentStatus.DRAFT,
    ) = Tournament(
        id = id,
        name = "Summary Cup",
        date = LocalDate.of(2026, 8, 22),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = status,
    )

    private fun replacementCandidate(
        prefix: String,
        names: Map<Int, String> = TeamSlot.SLOT_NUMBERS.associateWith { slotNumber -> "$prefix Team $slotNumber" },
    ) = ConfirmedRosterReplacementCandidate(
        tournamentId = TOURNAMENT_ID,
        teamNamesBySlotNumber = names,
        rosterPlayersBySlotNumber = TeamSlot.SLOT_NUMBERS.associateWith { emptyList() },
    )

    private fun cloudSnapshot(id: String, name: String) = TournamentCloudRestorationSnapshot(
        tournament = tournament(id = id),
        slots = TeamSlot.fixedSlotsForTournament(id).mapIndexed { index, slot ->
            slot.copy(teamName = if (index == 0) name else "")
        },
        players = emptyList(),
    )

    private suspend fun receiveSummary(
        emissions: Channel<com.hoggamers.rankforge.domain.tournament.TournamentSummary>,
        predicate: (com.hoggamers.rankforge.domain.tournament.TournamentSummary) -> Boolean,
    ): com.hoggamers.rankforge.domain.tournament.TournamentSummary {
        while (true) {
            val summary = withTimeout(5_000L) { emissions.receive() }
            if (predicate(summary)) return summary
        }
    }

    private fun match(id: String, number: Int) = Match(
        id = id,
        tournamentId = TOURNAMENT_ID,
        matchNumber = number,
        date = LocalDate.of(2026, 8, 22),
        mapName = "Bermuda",
        status = MatchStatus.DRAFT,
    )

    private class TestClock(private var millis: Long) : Clock() {
        override fun instant(): Instant = Instant.ofEpochMilli(millis)
        override fun withZone(zone: ZoneId): Clock = this
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        fun setMillis(value: Long) { millis = value }
    }

    private companion object {
        const val TOURNAMENT_ID = "summary-tournament"
    }
}
