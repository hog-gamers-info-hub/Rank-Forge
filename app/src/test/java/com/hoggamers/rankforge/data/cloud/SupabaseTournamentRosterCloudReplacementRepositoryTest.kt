package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacement
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementResult
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseTournamentRosterCloudReplacementRepositoryTest {
    @Test
    fun mapsSuccessfulRemoteRevisionWithoutUsingAFallbackRevision() = runTest {
        val remote = FakeRemote(
            TournamentRosterCloudReplacementRemoteResult.Success(7),
        )
        val result = repository(remote).replace(snapshot(), OWNER_ID)

        assertEquals(TournamentRosterCloudReplacementResult.Success(7), result)
        assertEquals(2, remote.expectedRevision)
        assertEquals(12, remote.payloads?.teamSlots?.size)
    }

    @Test
    fun preservesStaleRevisionConflict() = runTest {
        val conflict = RevisionConflict.StaleWrite(CloudRevision(2), CloudRevision(3))
        val result = repository(FakeRemote(TournamentRosterCloudReplacementRemoteResult.Conflict(conflict)))
            .replace(snapshot(), OWNER_ID)

        assertEquals(TournamentRosterCloudReplacementResult.Conflict(conflict), result)
    }

    @Test
    fun rejectsMissingRevisionBeforeMappingOrRemoteCall() = runTest {
        val remote = FakeRemote(TournamentRosterCloudReplacementRemoteResult.Success(4))
        val result = repository(remote).replace(snapshot().copy(expectedCloudRevision = null), OWNER_ID)

        assertEquals(
            TournamentRosterCloudReplacementResult.Conflict(RevisionConflict.MissingRevision),
            result,
        )
        assertTrue(remote.payloads == null)
    }

    private fun repository(remote: FakeRemote) =
        SupabaseTournamentRosterCloudReplacementRepository(remote)

    private fun snapshot() = TournamentRosterCloudReplacement(
        tournament = Tournament(
            TOURNAMENT_ID,
            "Roster Cup",
            LocalDate.of(2026, 8, 3),
            "Organizer",
            "123",
            TournamentStatus.CONFIRMED,
        ),
        slots = TeamSlot.SLOT_NUMBERS.map { TeamSlot.create(TOURNAMENT_ID, it, "Team $it") },
        rosters = mapOf(1 to listOf(RosterPlayer(TOURNAMENT_ID, 1, "Player One"))),
        expectedCloudRevision = 2,
    )

    private class FakeRemote(
        private val result: TournamentRosterCloudReplacementRemoteResult,
    ) : TournamentRosterCloudReplacementRemoteDataSource {
        var payloads: TournamentRosterCloudReplacementPayloads? = null
        var expectedRevision: Int? = null

        override suspend fun replace(
            payloads: TournamentRosterCloudReplacementPayloads,
            expectedRevision: Int,
        ): TournamentRosterCloudReplacementRemoteResult {
            this.payloads = payloads
            this.expectedRevision = expectedRevision
            return result
        }
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
        const val OWNER_ID = "22222222-2222-2222-2222-222222222222"
    }
}
