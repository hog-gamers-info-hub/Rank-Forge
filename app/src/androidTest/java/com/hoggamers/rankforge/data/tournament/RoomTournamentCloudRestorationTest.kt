package com.hoggamers.rankforge.data.tournament

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.RestoredRosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTournamentCloudRestorationTest {
    @Test
    fun restorationReplacesOnlyTargetTournamentRosterAndPreservesUnrelatedData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-replacement.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder(
            context,
            RankForgeDatabase::class.java,
            databaseName,
        ).build()
        try {
            val repository = RoomTournamentRepository(database)
            repository.create(tournament(TARGET_ID, "Old Target", TournamentStatus.CONFIRMED))
            repository.create(tournament(OTHER_ID, "Other Local", TournamentStatus.DRAFT))
            repository.saveTeamNames(TARGET_ID, mapOf(1 to "Old Team"))
            repository.saveRoster(TARGET_ID, 1, listOf(com.hoggamers.rankforge.domain.tournament.RosterPlayer(
                TARGET_ID,
                1,
                "Old Player",
            )))
            repository.confirmTournament(TARGET_ID)
            repository.createDraftMatch(
                Match(
                    id = MATCH_ID,
                    tournamentId = TARGET_ID,
                    matchNumber = 1,
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                    status = MatchStatus.DRAFT,
                ),
            )

            repository.restore(
                TournamentCloudRestorationSnapshot(
                    tournament = tournament(TARGET_ID, "Restored Target", TournamentStatus.DRAFT),
                    slots = TeamSlot.fixedSlotsForTournament(TARGET_ID).map { slot ->
                        if (slot.slotNumber == 1) slot.copy(teamName = "Restored Team") else slot
                    },
                    players = listOf(
                        RestoredRosterPlayer(TARGET_ID, 1, 1, "Restored Player"),
                    ),
                ),
            )

            assertEquals("Restored Target", repository.observeById(TARGET_ID).first()!!.name)
            assertEquals(
                "Restored Team",
                repository.observeSlotsByTournamentId(TARGET_ID).first().first().teamName,
            )
            assertEquals(
                listOf("Restored Player"),
                repository.observeRosterByTournamentAndSlot(TARGET_ID, 1)
                    .first()
                    .map { it.displayName },
            )
            assertEquals("Other Local", repository.observeById(OTHER_ID).first()!!.name)
            assertTrue(repository.observeMatchById(MATCH_ID).first() != null)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun tournament(id: String, name: String, status: TournamentStatus) = Tournament(
        id = id,
        name = name,
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = status,
    )

    private companion object {
        const val TARGET_ID = "11111111-1111-1111-1111-111111111111"
        const val OTHER_ID = "22222222-2222-2222-2222-222222222222"
        const val MATCH_ID = "33333333-3333-3333-3333-333333333333"
    }
}
