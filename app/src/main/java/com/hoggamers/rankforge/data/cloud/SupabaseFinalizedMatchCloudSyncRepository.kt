package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncRepository
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncSnapshot
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncStage
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseFinalizedMatchCloudSyncRepository @Inject constructor(
    private val remoteDataSource: FinalizedMatchCloudSyncRemoteDataSource,
) : FinalizedMatchCloudSyncRepository {
    override suspend fun sync(snapshot: FinalizedMatchCloudSyncSnapshot): FinalizedMatchCloudSyncResult {
        val expectedRevision = snapshot.expectedCloudRevision
            ?: return FinalizedMatchCloudSyncResult.Conflict(RevisionConflict.MissingRevision)
        return when (val mapping = FinalizedMatchCloudSyncMapper.map(snapshot)) {
            FinalizedMatchCloudSyncMappingResult.Invalid -> FinalizedMatchCloudSyncResult.ValidationFailure
            is FinalizedMatchCloudSyncMappingResult.Success ->
                remoteDataSource.sync(mapping.payloads, expectedRevision).toDomainResult()
        }
    }
}

private fun FinalizedMatchCloudSyncExecutionResult.toDomainResult(): FinalizedMatchCloudSyncResult = when (this) {
    is FinalizedMatchCloudSyncExecutionResult.Success ->
        FinalizedMatchCloudSyncResult.Success(confirmedCloudRevision)
    is FinalizedMatchCloudSyncExecutionResult.Failure -> {
        when {
            category == FinalizedMatchCloudSyncFailureCategory.CONFLICT ->
                FinalizedMatchCloudSyncResult.Conflict(
                    conflict = conflict ?: RevisionConflict.MissingRevision,
                    confirmedCloudRevision = confirmedCloudRevision,
                )
            completedStage != null -> FinalizedMatchCloudSyncResult.PartialFailure(
                completedStage = FinalizedMatchCloudSyncStage.MATCHES,
                confirmedCloudRevision = confirmedCloudRevision,
            )
            category == FinalizedMatchCloudSyncFailureCategory.AUTHENTICATION ->
                FinalizedMatchCloudSyncResult.AuthenticationRequired
            category == FinalizedMatchCloudSyncFailureCategory.AUTHORIZATION ->
                FinalizedMatchCloudSyncResult.AuthorizationFailure
            category == FinalizedMatchCloudSyncFailureCategory.NETWORK -> FinalizedMatchCloudSyncResult.NetworkFailure
            else -> FinalizedMatchCloudSyncResult.ValidationFailure
        }
    }
}
