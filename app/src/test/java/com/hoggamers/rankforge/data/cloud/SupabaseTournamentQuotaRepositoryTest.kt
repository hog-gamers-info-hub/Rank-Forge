package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRemoteResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentQuotaResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseTournamentQuotaRepositoryTest {
    @Test
    fun countsZeroThroughFourAsAllowed() = runBlocking {
        (0..4).forEach { count ->
            val result = repositoryWithCount(count).checkQuota()

            assertEquals(TournamentQuotaResult.Allowed(count), result)
        }
    }

    @Test
    fun countsFiveAndAboveAsLimitReached() = runBlocking {
        listOf(5, 6, 12).forEach { count ->
            val result = repositoryWithCount(count).checkQuota()

            assertEquals(TournamentQuotaResult.LimitReached(count), result)
        }
    }

    @Test
    fun mapsAuthenticationNetworkAndUnknownFailures() = runBlocking {
        assertEquals(
            TournamentQuotaResult.AuthenticationRequired,
            repositoryWithFailure(TournamentCloudRestorationFailureCategory.AUTHENTICATION).checkQuota(),
        )
        assertEquals(
            TournamentQuotaResult.NetworkFailure,
            repositoryWithFailure(TournamentCloudRestorationFailureCategory.NETWORK).checkQuota(),
        )
        assertEquals(
            TournamentQuotaResult.UnknownFailure,
            repositoryWithFailure(TournamentCloudRestorationFailureCategory.AUTHORIZATION).checkQuota(),
        )
    }

    private fun repositoryWithCount(count: Int): SupabaseTournamentQuotaRepository =
        SupabaseTournamentQuotaRepository(
            FakeTournamentCloudRestorationRemoteDataSource(
                result = TournamentCloudRestorationRemoteResult.Success(
                    (0 until count).map { index ->
                        TournamentCloudRestorePayload(
                            id = "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}",
                            ownerId = "owner",
                            name = "Tournament $index",
                            tournamentDate = "2026-07-24",
                            organizerName = "Organizer",
                            organizerContact = "",
                            status = "draft",
                            revision = 1,
                        )
                    },
                ),
            ),
        )

    private fun repositoryWithFailure(
        category: TournamentCloudRestorationFailureCategory,
    ): SupabaseTournamentQuotaRepository = SupabaseTournamentQuotaRepository(
        FakeTournamentCloudRestorationRemoteDataSource(
            result = TournamentCloudRestorationRemoteResult.Failure(category),
        ),
    )

    private class FakeTournamentCloudRestorationRemoteDataSource(
        private val result: TournamentCloudRestorationRemoteResult<List<TournamentCloudRestorePayload>>,
    ) : TournamentCloudRestorationRemoteDataSource {
        override suspend fun listOwnedTournaments(): TournamentCloudRestorationRemoteResult<
            List<TournamentCloudRestorePayload>
            > = result

        override suspend fun readOwnedTournament(
            tournamentId: String,
        ): TournamentCloudRestorationRemoteResult<TournamentCloudRestorationPayloads> =
            error("unused")
    }
}
