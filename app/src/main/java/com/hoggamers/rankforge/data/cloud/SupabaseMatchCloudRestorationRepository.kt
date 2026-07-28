package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationFailureCategory
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationRemoteResult
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class SupabaseMatchCloudRestorationRepository @Inject constructor(private val remote: MatchCloudRestorationRemoteDataSource) : MatchCloudRestorationRepository {
    override suspend fun readOwnedMatches(tournamentId: String) = when (val result = remote.readOwnedMatches(tournamentId)) {
        is MatchCloudRestorationRemoteResult.Failure -> result
        is MatchCloudRestorationRemoteResult.Success -> when (val mapped = MatchCloudRestorationMapper.map(result.value)) {
            is MatchCloudRestorationMappingResult.Success -> MatchCloudRestorationRemoteResult.Success(mapped.value)
            MatchCloudRestorationMappingResult.Invalid -> MatchCloudRestorationRemoteResult.Failure(MatchCloudRestorationFailureCategory.VALIDATION)
        }
    }
}
