package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacement
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementRepository
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseTournamentRosterCloudReplacementRepository @Inject constructor(
    private val remoteDataSource: TournamentRosterCloudReplacementRemoteDataSource,
) : TournamentRosterCloudReplacementRepository {
    override suspend fun replace(
        snapshot: TournamentRosterCloudReplacement,
        ownerId: String,
    ): TournamentRosterCloudReplacementResult {
        val expectedRevision = snapshot.expectedCloudRevision
            ?.takeIf { it > 0 }
            ?: return TournamentRosterCloudReplacementResult.Conflict(RevisionConflict.MissingRevision)

        return when (val mapping = TournamentRosterCloudReplacementMapper.map(snapshot, ownerId)) {
            TournamentRosterCloudReplacementMappingResult.Invalid ->
                TournamentRosterCloudReplacementResult.ValidationFailure
            is TournamentRosterCloudReplacementMappingResult.Success ->
                remoteDataSource.replace(mapping.payloads, expectedRevision).toDomainResult()
        }
    }
}

private fun TournamentRosterCloudReplacementRemoteResult.toDomainResult(): TournamentRosterCloudReplacementResult = when (this) {
    is TournamentRosterCloudReplacementRemoteResult.Success ->
        TournamentRosterCloudReplacementResult.Success(newCloudRevision)
    TournamentRosterCloudReplacementRemoteResult.BlockedByExistingMatches ->
        TournamentRosterCloudReplacementResult.BlockedByExistingMatches
    is TournamentRosterCloudReplacementRemoteResult.Conflict ->
        TournamentRosterCloudReplacementResult.Conflict(conflict)
    is TournamentRosterCloudReplacementRemoteResult.Failure -> when (category) {
        CloudUploadFailureCategory.AUTHENTICATION -> TournamentRosterCloudReplacementResult.AuthenticationRequired
        CloudUploadFailureCategory.AUTHORIZATION -> TournamentRosterCloudReplacementResult.AuthorizationFailure
        CloudUploadFailureCategory.NETWORK -> TournamentRosterCloudReplacementResult.NetworkFailure
        CloudUploadFailureCategory.TOURNAMENT_LIMIT_REACHED ->
            TournamentRosterCloudReplacementResult.ValidationFailure
        CloudUploadFailureCategory.VALIDATION -> TournamentRosterCloudReplacementResult.ValidationFailure
        CloudUploadFailureCategory.CONFLICT -> TournamentRosterCloudReplacementResult.Conflict(
            RevisionConflict.MissingRevision,
        )
        CloudUploadFailureCategory.UNKNOWN -> TournamentRosterCloudReplacementResult.UnknownFailure
    }
}
