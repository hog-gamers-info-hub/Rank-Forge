package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncRepository
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncSnapshot
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseFinalizedMatchCloudSyncRepository @Inject constructor(
    private val remoteDataSource: FinalizedMatchCloudSyncRemoteDataSource,
) : FinalizedMatchCloudSyncRepository {
    override suspend fun sync(snapshot: FinalizedMatchCloudSyncSnapshot): FinalizedMatchCloudSyncResult {
        return when (val mapping = FinalizedMatchCloudSyncMapper.map(snapshot)) {
            FinalizedMatchCloudSyncMappingResult.Invalid -> FinalizedMatchCloudSyncResult.ValidationFailure
            is FinalizedMatchCloudSyncMappingResult.Success ->
                remoteDataSource.sync(mapping.payloads).toDomainResult()
        }
    }
}

private fun FinalizedMatchCloudSyncExecutionResult.toDomainResult(): FinalizedMatchCloudSyncResult = when (this) {
    FinalizedMatchCloudSyncExecutionResult.Success -> FinalizedMatchCloudSyncResult.Success
    is FinalizedMatchCloudSyncExecutionResult.Failure -> {
        if (completedStage != null) {
            FinalizedMatchCloudSyncResult.PartialFailure(
                completedStage = FinalizedMatchCloudSyncStage.MATCHES,
            )
        } else {
            when (category) {
                FinalizedMatchCloudSyncFailureCategory.AUTHENTICATION ->
                    FinalizedMatchCloudSyncResult.AuthenticationRequired
                FinalizedMatchCloudSyncFailureCategory.AUTHORIZATION ->
                    FinalizedMatchCloudSyncResult.AuthorizationFailure
                FinalizedMatchCloudSyncFailureCategory.NETWORK -> FinalizedMatchCloudSyncResult.NetworkFailure
                FinalizedMatchCloudSyncFailureCategory.VALIDATION,
                FinalizedMatchCloudSyncFailureCategory.UNKNOWN,
                -> FinalizedMatchCloudSyncResult.ValidationFailure
            }
        }
    }
}
