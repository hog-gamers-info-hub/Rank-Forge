package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.MAX_TOURNAMENTS_PER_ACCOUNT
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRemoteResult
import com.hoggamers.rankforge.domain.tournament.TournamentQuotaRepository
import com.hoggamers.rankforge.domain.tournament.TournamentQuotaResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseTournamentQuotaRepository @Inject constructor(
    private val restorationRemoteDataSource: TournamentCloudRestorationRemoteDataSource,
) : TournamentQuotaRepository {
    override suspend fun checkQuota(): TournamentQuotaResult =
        when (val result = restorationRemoteDataSource.listOwnedTournaments()) {
            is TournamentCloudRestorationRemoteResult.Success -> {
                val currentCount = result.value.size
                if (currentCount >= MAX_TOURNAMENTS_PER_ACCOUNT) {
                    TournamentQuotaResult.LimitReached(currentCount)
                } else {
                    TournamentQuotaResult.Allowed(currentCount)
                }
            }
            is TournamentCloudRestorationRemoteResult.Failure -> when (result.category) {
                TournamentCloudRestorationFailureCategory.AUTHENTICATION ->
                    TournamentQuotaResult.AuthenticationRequired
                TournamentCloudRestorationFailureCategory.NETWORK ->
                    TournamentQuotaResult.NetworkFailure
                TournamentCloudRestorationFailureCategory.AUTHORIZATION,
                TournamentCloudRestorationFailureCategory.NOT_FOUND,
                TournamentCloudRestorationFailureCategory.VALIDATION,
                -> TournamentQuotaResult.UnknownFailure
            }
        }
}
