package com.hoggamers.rankforge.data.local

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface TournamentLobbyTemplateAssetRepository {
    fun observeByTournamentId(tournamentId: String): Flow<List<TournamentLobbyTemplateAssetEntity>>

    suspend fun getByTournamentId(tournamentId: String): List<TournamentLobbyTemplateAssetEntity>

    suspend fun replaceForTournament(
        tournamentId: String,
        assets: List<TournamentLobbyTemplateAssetEntity>,
    )
}

@Singleton
class RoomTournamentLobbyTemplateAssetRepository @Inject constructor(
    private val dao: TournamentLobbyTemplateAssetDao,
) : TournamentLobbyTemplateAssetRepository {
    override fun observeByTournamentId(tournamentId: String): Flow<List<TournamentLobbyTemplateAssetEntity>> =
        dao.observeByTournamentId(tournamentId)

    override suspend fun getByTournamentId(tournamentId: String): List<TournamentLobbyTemplateAssetEntity> =
        dao.readByTournamentId(tournamentId)

    override suspend fun replaceForTournament(
        tournamentId: String,
        assets: List<TournamentLobbyTemplateAssetEntity>,
    ) {
        dao.replaceForTournament(tournamentId, assets)
    }
}

class NoOpTournamentLobbyTemplateAssetRepository : TournamentLobbyTemplateAssetRepository {
    override fun observeByTournamentId(tournamentId: String): Flow<List<TournamentLobbyTemplateAssetEntity>> =
        flowOf(emptyList())

    override suspend fun getByTournamentId(tournamentId: String): List<TournamentLobbyTemplateAssetEntity> = emptyList()

    override suspend fun replaceForTournament(
        tournamentId: String,
        assets: List<TournamentLobbyTemplateAssetEntity>,
    ) = Unit
}
