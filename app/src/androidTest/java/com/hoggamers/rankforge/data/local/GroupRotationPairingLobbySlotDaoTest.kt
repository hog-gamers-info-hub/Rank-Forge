package com.hoggamers.rankforge.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupRotationPairingLobbySlotDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: RankForgeDatabase

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, RankForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.tournamentDao().upsert(
            TournamentEntity(
                id = TOURNAMENT_ID,
                name = "Rotation Cup",
                stageName = "Stage One",
                organizerContactNumber = "123",
                status = "DRAFT",
            ),
        )
        database.teamSlotDao().upsertAll(
            (1..24).map { slotNumber ->
                TeamSlotEntity(
                    tournamentId = TOURNAMENT_ID,
                    slotNumber = slotNumber,
                    teamName = "Team $slotNumber",
                    group = when (slotNumber) {
                        in 1..6 -> "A"
                        in 7..12 -> "B"
                        in 13..18 -> "C"
                        else -> "D"
                    },
                )
            },
        )
        database.tournamentGroupPairingDao().upsertAll(
            listOf(
                TournamentGroupPairingEntity(TOURNAMENT_ID, "A:B", "A", "B"),
                TournamentGroupPairingEntity(TOURNAMENT_ID, "A:C", "A", "C"),
                TournamentGroupPairingEntity(TOURNAMENT_ID, "B:C", "B", "C"),
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observesTournamentAndPairingAssignmentsInTheirRequiredOrder() = runBlocking {
        val dao = database.groupRotationPairingLobbySlotDao()
        dao.upsertAll(
            listOf(
                assignment("B:C", lobbySlot = 2, teamSlot = 14),
                assignment("A:C", lobbySlot = 7, teamSlot = 17),
                assignment("B:C", lobbySlot = 1, teamSlot = 13),
                assignment("A:C", lobbySlot = 1, teamSlot = 16),
            ),
        )

        assertEquals(
            listOf("A:C:1:16", "A:C:7:17", "B:C:1:13", "B:C:2:14"),
            dao.observeByTournamentId(TOURNAMENT_ID).first().map { it.asIdentity() },
        )
        assertEquals(
            listOf("A:C:1:16", "A:C:7:17", "B:C:1:13", "B:C:2:14"),
            dao.readByTournamentId(TOURNAMENT_ID).map { it.asIdentity() },
        )
        assertEquals(
            listOf("A:C:1:16", "A:C:7:17"),
            dao.observeByTournamentAndPairing(TOURNAMENT_ID, "A:C")
                .first()
                .map { it.asIdentity() },
        )
    }

    @Test
    fun allowsDistinctTeamsForDistinctLobbySlotsWithinOnePairing() = runBlocking {
        database.groupRotationPairingLobbySlotDao().upsertAll(
            listOf(
                assignment("A:C", lobbySlot = 1, teamSlot = 13),
                assignment("A:C", lobbySlot = 2, teamSlot = 14),
            ),
        )

        assertEquals(
            listOf(13, 14),
            database.groupRotationPairingLobbySlotDao()
                .observeByTournamentAndPairing(TOURNAMENT_ID, "A:C")
                .first()
                .map { it.teamSlotNumber },
        )
    }

    @Test
    fun rejectsTheSameTeamTwiceWithinOnePairing() {
        assertThrows(Exception::class.java) {
            runBlocking {
                database.groupRotationPairingLobbySlotDao().upsertAll(
                    listOf(
                        assignment("A:C", lobbySlot = 1, teamSlot = 13),
                        assignment("A:C", lobbySlot = 2, teamSlot = 13),
                    ),
                )
            }
        }
    }

    @Test
    fun allowsTheSamePermanentTeamInDifferentPairings() = runBlocking {
        val dao = database.groupRotationPairingLobbySlotDao()
        dao.upsertAll(
            listOf(
                assignment("A:C", lobbySlot = 1, teamSlot = 13),
                assignment("B:C", lobbySlot = 1, teamSlot = 13),
            ),
        )

        assertEquals(2, dao.observeByTournamentId(TOURNAMENT_ID).first().size)
    }

    @Test
    fun rejectsMissingPairingOrTeamParent() {
        assertThrows(Exception::class.java) {
            runBlocking {
                database.groupRotationPairingLobbySlotDao().upsertAll(
                    listOf(assignment("A:D", lobbySlot = 1, teamSlot = 13)),
                )
            }
        }
        assertThrows(Exception::class.java) {
            runBlocking {
                database.groupRotationPairingLobbySlotDao().upsertAll(
                    listOf(assignment("A:C", lobbySlot = 1, teamSlot = 25)),
                )
            }
        }
    }

    @Test
    fun replacingOnePairingDoesNotTouchAnotherPairing() = runBlocking {
        val dao = database.groupRotationPairingLobbySlotDao()
        dao.upsertAll(
            listOf(
                assignment("A:C", lobbySlot = 1, teamSlot = 13),
                assignment("B:C", lobbySlot = 1, teamSlot = 14),
            ),
        )

        dao.replaceForTournamentAndPairing(
            tournamentId = TOURNAMENT_ID,
            pairingKey = "A:C",
            assignments = listOf(assignment("A:C", lobbySlot = 5, teamSlot = 18)),
        )

        assertEquals(
            listOf("A:C:5:18", "B:C:1:14"),
            dao.observeByTournamentId(TOURNAMENT_ID).first().map { it.asIdentity() },
        )
    }

    @Test
    fun replacementRejectsAssignmentsFromAnotherPairingBeforeDeletingExistingMapping() {
        val dao = database.groupRotationPairingLobbySlotDao()
        runBlocking {
            dao.upsertAll(listOf(assignment("A:B", lobbySlot = 1, teamSlot = 1)))
        }

        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                dao.replaceForTournamentAndPairing(
                    tournamentId = TOURNAMENT_ID,
                    pairingKey = "A:B",
                    assignments = listOf(assignment("B:C", lobbySlot = 2, teamSlot = 7)),
                )
            }
        }

        assertEquals(
            "All assignments must belong to tournament '$TOURNAMENT_ID' and pairing 'A:B'.",
            exception.message,
        )
        assertEquals(
            listOf("A:B:1:1"),
            runBlocking {
                dao.observeByTournamentAndPairing(TOURNAMENT_ID, "A:B")
                    .first()
                    .map { it.asIdentity() }
            },
        )
    }

    @Test
    fun deletingTournamentCascadesPairingLobbyAssignments() = runBlocking {
        val dao = database.groupRotationPairingLobbySlotDao()
        dao.upsertAll(listOf(assignment("A:C", lobbySlot = 1, teamSlot = 13)))

        database.tournamentDao().deleteById(TOURNAMENT_ID)

        assertTrue(dao.observeByTournamentId(TOURNAMENT_ID).first().isEmpty())
    }

    @Test
    fun deletingAllAssignmentsForTournamentLeavesOtherTournamentsUntouched() = runBlocking {
        val dao = database.groupRotationPairingLobbySlotDao()
        database.tournamentDao().upsert(
            TournamentEntity(
                id = "other-tournament",
                name = "Other",
                stageName = "Stage",
                organizerContactNumber = "456",
                status = "DRAFT",
            ),
        )
        database.teamSlotDao().upsertAll(
            listOf(TeamSlotEntity("other-tournament", 1, "Other Team", "A")),
        )
        database.tournamentGroupPairingDao().upsertAll(
            listOf(TournamentGroupPairingEntity("other-tournament", "A:B", "A", "B")),
        )
        dao.upsertAll(listOf(assignment("A:C", lobbySlot = 1, teamSlot = 13)))
        dao.upsertAll(
            listOf(
                GroupRotationPairingLobbySlotEntity("other-tournament", "A:B", 1, 1),
            ),
        )

        dao.deleteByTournamentId(TOURNAMENT_ID)

        assertTrue(dao.observeByTournamentId(TOURNAMENT_ID).first().isEmpty())
        assertEquals(
            listOf("A:B:1:1"),
            dao.observeByTournamentId("other-tournament").first().map { it.asIdentity() },
        )
    }

    private fun assignment(pairingKey: String, lobbySlot: Int, teamSlot: Int) =
        GroupRotationPairingLobbySlotEntity(
            tournamentId = TOURNAMENT_ID,
            pairingKey = pairingKey,
            lobbySlotNumber = lobbySlot,
            teamSlotNumber = teamSlot,
        )

    private fun GroupRotationPairingLobbySlotEntity.asIdentity(): String =
        "$pairingKey:$lobbySlotNumber:$teamSlotNumber"

    private companion object {
        const val TOURNAMENT_ID = "tournament-rotation"
    }
}
