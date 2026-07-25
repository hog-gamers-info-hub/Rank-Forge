package com.hoggamers.rankforge.data.tournament

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus

class InMemoryTournamentRepositoryTest {
    @Test
    fun createdTournamentIsStoredAndObservable() = runTest {
        val repository = InMemoryTournamentRepository()
        val tournament = Tournament(
            id = "stable-id",
            name = "Spring Cup",
            date = LocalDate.of(2026, 7, 24),
            organizerName = "Organizer",
            organizerContactNumber = "123",
            status = TournamentStatus.DRAFT,
        )

        repository.create(tournament)

        assertEquals(listOf(tournament), repository.observeAll().first())
    }

    @Test
    fun sameStableIdIsNotStoredTwice() = runTest {
        val repository = InMemoryTournamentRepository()
        val tournament = Tournament(
            id = "stable-id",
            name = "Spring Cup",
            date = LocalDate.of(2026, 7, 24),
            organizerName = "Organizer",
            organizerContactNumber = "123",
            status = TournamentStatus.DRAFT,
        )

        repository.create(tournament)
        repository.create(tournament)

        assertEquals(listOf(tournament), repository.observeAll().first())
    }

    @Test
    fun getByIdReturnsCreatedTournament() = runTest {
        val repository = InMemoryTournamentRepository()
        val tournament = Tournament(
            id = "stable-id",
            name = "Spring Cup",
            date = LocalDate.of(2026, 7, 24),
            organizerName = "Organizer",
            organizerContactNumber = "123",
            status = TournamentStatus.DRAFT,
        )

        repository.create(tournament)

        assertEquals(tournament, repository.observeById("stable-id").first())
    }

    @Test
    fun getByIdReturnsNullForUnknownTournament() = runTest {
        val repository = InMemoryTournamentRepository()

        assertEquals(null, repository.observeById("missing").first())
    }

    @Test
    fun createGeneratesExactlyTwelveSlotsForTournament() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament(id = "stable-id"))

        val slots = repository.observeSlotsByTournamentId("stable-id").first()

        assertEquals(12, slots.size)
        assertEquals((1..12).toList(), slots.map { it.slotNumber })
        assertEquals(List(12) { "stable-id" }, slots.map { it.tournamentId })
    }

    @Test
    fun duplicateCreateDoesNotDuplicateSlots() = runTest {
        val repository = InMemoryTournamentRepository()
        val tournament = tournament(id = "stable-id")

        repository.create(tournament)
        repository.create(tournament)

        assertEquals(12, repository.observeSlotsByTournamentId("stable-id").first().size)
    }

    @Test
    fun unknownTournamentReturnsNoSlots() = runTest {
        val repository = InMemoryTournamentRepository()

        assertEquals(emptyList<TeamSlot>(), repository.observeSlotsByTournamentId("missing").first())
    }

    @Test
    fun savingTeamNameUpdatesOnlyTheRequestedSlot() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament(id = "stable-id"))

        repository.saveTeamNames(
            tournamentId = "stable-id",
            teamNamesBySlotNumber = mapOf(2 to "Bravo"),
        )

        val slots = repository.observeSlotsByTournamentId("stable-id").first()
        assertEquals("", slots.first { it.slotNumber == 1 }.teamName)
        assertEquals("Bravo", slots.first { it.slotNumber == 2 }.teamName)
        assertEquals("", slots.first { it.slotNumber == 3 }.teamName)
    }

    @Test
    fun savingAllTeamNamesPreservesSlotIdentityAndOrder() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament(id = "stable-id"))

        repository.saveTeamNames(
            tournamentId = "stable-id",
            teamNamesBySlotNumber = (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )

        val slots = repository.observeSlotsByTournamentId("stable-id").first()
        assertEquals((1..12).toList(), slots.map { it.slotNumber })
        assertEquals((1..12).map { slotNumber -> "Team $slotNumber" }, slots.map { it.teamName })
    }

    @Test
    fun savingEmptyTeamNameRemainsAllowedDraftValue() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament(id = "stable-id"))

        repository.saveTeamNames(
            tournamentId = "stable-id",
            teamNamesBySlotNumber = mapOf(1 to ""),
        )

        assertEquals("", repository.observeSlotsByTournamentId("stable-id").first().first().teamName)
    }

    @Test
    fun savingTeamNameTrimsWhitespace() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament(id = "stable-id"))

        repository.saveTeamNames(
            tournamentId = "stable-id",
            teamNamesBySlotNumber = mapOf(1 to "  Alpha  "),
        )

        assertEquals("Alpha", repository.observeSlotsByTournamentId("stable-id").first().first().teamName)
    }

    @Test
    fun rosterSupportsZeroThroughSixPlayers() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament(id = "stable-id"))

        repository.saveRoster(
            tournamentId = "stable-id",
            slotNumber = 1,
            players = (1..6).map { playerNumber ->
                RosterPlayer.create("stable-id", 1, "Player $playerNumber")
            },
        )
        assertEquals(6, repository.observeRosterByTournamentAndSlot("stable-id", 1).first().size)

        repository.saveRoster("stable-id", 1, emptyList())
        assertEquals(emptyList<RosterPlayer>(), repository.observeRosterByTournamentAndSlot("stable-id", 1).first())
    }

    @Test
    fun rosterRejectsSeventhPlayerWithoutSavingIt() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament(id = "stable-id"))
        val players = (1..7).map { playerNumber ->
            RosterPlayer.create("stable-id", 1, "Player $playerNumber")
        }

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.saveRoster("stable-id", 1, players)
            }
        }
        assertEquals(emptyList<RosterPlayer>(), repository.observeRosterByTournamentAndSlot("stable-id", 1).first())
    }

    @Test
    fun rostersAreIsolatedByTournamentAndSlot() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament(id = "first"))
        repository.create(tournament(id = "second"))

        repository.saveRoster(
            tournamentId = "first",
            slotNumber = 2,
            players = listOf(RosterPlayer.create("first", 2, "Alpha")),
        )

        assertEquals(
            listOf("Alpha"),
            repository.observeRosterByTournamentAndSlot("first", 2).first().map { it.displayName },
        )
        assertEquals(
            emptyList<String>(),
            repository.observeRosterByTournamentAndSlot("first", 1).first().map { it.displayName },
        )
        assertEquals(
            emptyList<String>(),
            repository.observeRosterByTournamentAndSlot("second", 2).first().map { it.displayName },
        )
    }

    @Test
    fun rosterRejectsPlayersFromAnotherSlot() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament(id = "stable-id"))

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.saveRoster(
                    tournamentId = "stable-id",
                    slotNumber = 1,
                    players = listOf(RosterPlayer.create("stable-id", 2, "Wrong slot")),
                )
            }
        }
    }

    private fun tournament(id: String) = Tournament(
        id = id,
        name = "Spring Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
    )
}
