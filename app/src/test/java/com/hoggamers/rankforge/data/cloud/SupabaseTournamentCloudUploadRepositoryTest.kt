package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseTournamentCloudUploadRepositoryTest {
    @Test
    fun mapsTournamentLimitDatabaseFailureToTypedResult() = runBlocking {
        val result = repository(
            CloudUploadExecutionResult.Failure(
                completedStage = null,
                category = CloudUploadFailureCategory.TOURNAMENT_LIMIT_REACHED,
            ),
        ).upload(snapshot(), OWNER_ID)

        assertEquals(TournamentCloudUploadResult.TournamentLimitReached, result)
    }

    @Test
    fun preservesExistingSuccessOutcome() = runBlocking {
        val result = repository(
            CloudUploadExecutionResult.Success(confirmedCloudRevision = 8),
        ).upload(snapshot(), OWNER_ID)

        assertEquals(TournamentCloudUploadResult.Success(8), result)
    }

    private fun repository(result: CloudUploadExecutionResult): TournamentCloudUploadRepository =
        SupabaseTournamentCloudUploadRepository(
            object : TournamentCloudUploadRemoteDataSource {
                override suspend fun upload(
                    payloads: TournamentCloudUploadPayloads,
                    expectedRevision: Int,
                ): CloudUploadExecutionResult = result
            },
        )

    private fun snapshot() = TournamentCloudUploadSnapshot(
        tournament = Tournament(
            id = TOURNAMENT_ID,
            name = "Summer Cup",
            date = LocalDate.of(2026, 7, 24),
            organizerName = "Organizer",
            organizerContactNumber = "123",
            status = TournamentStatus.DRAFT,
        ),
        slots = listOf(TeamSlot.create(TOURNAMENT_ID, 1)),
        rosters = mapOf(1 to listOf(RosterPlayer.create(TOURNAMENT_ID, 1, "Player One"))),
        expectedCloudRevision = 0,
    )

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
        const val OWNER_ID = "22222222-2222-2222-2222-222222222222"
    }
}
