package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncRepository
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncSnapshot
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncStage
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseDraftMatchCloudSyncRepository @Inject constructor(
    private val remoteDataSource: DraftMatchCloudSyncRemoteDataSource,
) : DraftMatchCloudSyncRepository {
    override suspend fun sync(snapshot: DraftMatchCloudSyncSnapshot): DraftMatchCloudSyncResult {
        val expectedRevision = snapshot.expectedCloudRevision
            ?: return DraftMatchCloudSyncResult.Conflict(RevisionConflict.MissingRevision)
        return when (val mapping = DraftMatchCloudSyncMapper.map(snapshot)) {
            DraftMatchCloudSyncMappingResult.Invalid -> DraftMatchCloudSyncResult.ValidationFailure
            is DraftMatchCloudSyncMappingResult.Success ->
                remoteDataSource.sync(mapping.payloads, expectedRevision).toDomainResult()
        }
    }
}

private fun DraftMatchCloudSyncExecutionResult.toDomainResult(): DraftMatchCloudSyncResult = when (this) {
    DraftMatchCloudSyncExecutionResult.Success -> DraftMatchCloudSyncResult.Success
    is DraftMatchCloudSyncExecutionResult.Failure -> {
        if (completedStage != null) {
            DraftMatchCloudSyncResult.PartialFailure(
                completedStage = DraftMatchCloudSyncStage.MATCHES,
            )
        } else {
            when (category) {
                DraftMatchCloudSyncFailureCategory.AUTHENTICATION ->
                    DraftMatchCloudSyncResult.AuthenticationRequired
                DraftMatchCloudSyncFailureCategory.AUTHORIZATION ->
                    DraftMatchCloudSyncResult.AuthorizationFailure
                DraftMatchCloudSyncFailureCategory.NETWORK -> DraftMatchCloudSyncResult.NetworkFailure
                DraftMatchCloudSyncFailureCategory.VALIDATION,
                DraftMatchCloudSyncFailureCategory.UNKNOWN,
                -> DraftMatchCloudSyncResult.ValidationFailure
                DraftMatchCloudSyncFailureCategory.CONFLICT -> DraftMatchCloudSyncResult.Conflict(
                    conflict ?: RevisionConflict.MissingRevision,
                )
            }
        }
    }
}
