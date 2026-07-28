package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadStage
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseTournamentCloudUploadRepository @Inject constructor(
    private val remoteDataSource: TournamentCloudUploadRemoteDataSource,
) : TournamentCloudUploadRepository {
    override suspend fun upload(
        snapshot: TournamentCloudUploadSnapshot,
        ownerId: String,
    ): TournamentCloudUploadResult {
        val expectedRevision = snapshot.expectedCloudRevision
            ?: return TournamentCloudUploadResult.Conflict(RevisionConflict.MissingRevision)
        return when (val mapping = TournamentCloudUploadMapper.map(snapshot, ownerId)) {
            TournamentCloudUploadMappingResult.Invalid -> TournamentCloudUploadResult.ValidationFailure
            is TournamentCloudUploadMappingResult.Success ->
                remoteDataSource.upload(mapping.payloads, expectedRevision).toDomainResult()
        }
    }
}

private fun CloudUploadExecutionResult.toDomainResult(): TournamentCloudUploadResult = when (this) {
    CloudUploadExecutionResult.Success -> TournamentCloudUploadResult.Success
    is CloudUploadExecutionResult.Failure -> {
        if (completedStage != null) {
            TournamentCloudUploadResult.PartialFailure(
                completedStage = when (completedStage) {
                    CloudUploadCompletedStage.TOURNAMENT -> TournamentCloudUploadStage.TOURNAMENT
                    CloudUploadCompletedStage.TEAM_SLOTS -> TournamentCloudUploadStage.TEAM_SLOTS
                },
            )
        } else {
            when (category) {
                CloudUploadFailureCategory.AUTHENTICATION ->
                    TournamentCloudUploadResult.AuthenticationRequired
                CloudUploadFailureCategory.AUTHORIZATION ->
                    TournamentCloudUploadResult.AuthorizationFailure
                CloudUploadFailureCategory.NETWORK ->
                    TournamentCloudUploadResult.NetworkFailure
                CloudUploadFailureCategory.VALIDATION,
                CloudUploadFailureCategory.UNKNOWN,
                -> TournamentCloudUploadResult.ValidationFailure
                CloudUploadFailureCategory.CONFLICT -> TournamentCloudUploadResult.Conflict(
                    conflict ?: RevisionConflict.MissingRevision,
                )
            }
        }
    }
}
