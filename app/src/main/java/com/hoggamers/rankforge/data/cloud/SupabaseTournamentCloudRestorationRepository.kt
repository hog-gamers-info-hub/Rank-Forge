package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRemoteResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseTournamentCloudRestorationRepository @Inject constructor(
    private val remoteDataSource: TournamentCloudRestorationRemoteDataSource,
) : TournamentCloudRestorationRepository {
    override suspend fun listOwnedTournaments() = when (
        val result = remoteDataSource.listOwnedTournaments()
    ) {
        is TournamentCloudRestorationRemoteResult.Success -> when (
            val mapping = TournamentCloudRestorationMapper.mapSummaries(result.value.map { it.toUploadPayload() })
        ) {
            is TournamentCloudRestorationMappingResult.Success ->
                TournamentCloudRestorationRemoteResult.Success(mapping.value)
            TournamentCloudRestorationMappingResult.Invalid ->
                TournamentCloudRestorationRemoteResult.Failure(
                    com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory.VALIDATION,
                )
        }
        is TournamentCloudRestorationRemoteResult.Failure -> result
    }

    override suspend fun readOwnedTournament(
        tournamentId: String,
    ): TournamentCloudRestorationRemoteResult<TournamentCloudRestorationSnapshot> = when (
        val result = remoteDataSource.readOwnedTournament(tournamentId)
    ) {
        is TournamentCloudRestorationRemoteResult.Success -> when (
            val mapping = TournamentCloudRestorationMapper.mapSnapshot(result.value)
        ) {
            is TournamentCloudRestorationMappingResult.Success ->
                TournamentCloudRestorationRemoteResult.Success(mapping.value)
            TournamentCloudRestorationMappingResult.Invalid ->
                TournamentCloudRestorationRemoteResult.Failure(
                    com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory.VALIDATION,
                )
        }
        is TournamentCloudRestorationRemoteResult.Failure -> result
    }
}

private fun TournamentCloudRestorePayload.toUploadPayload() = TournamentUploadPayload(
    id = id,
    ownerId = ownerId,
    name = name,
    tournamentDate = tournamentDate,
    organizerName = organizerName,
    organizerContact = organizerContact,
    status = status,
)
