package com.hoggamers.rankforge.data.local

import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

interface TournamentLobbyTemplateAssetRepository {
    fun observeByTournamentId(tournamentId: String): Flow<List<TournamentLobbyTemplateAssetEntity>>

    suspend fun getByTournamentId(tournamentId: String): List<TournamentLobbyTemplateAssetEntity>

    suspend fun replaceForTournament(
        tournamentId: String,
        assets: List<TournamentLobbyTemplateAssetEntity>,
    )

    suspend fun deleteByTournamentId(tournamentId: String)

    fun observeByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Flow<List<TournamentLobbyTemplateAssetEntity>> = emptyFlow()

    suspend fun getByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): List<TournamentLobbyTemplateAssetEntity> = emptyList()

    suspend fun replaceForTournamentByOwner(
        tournamentId: String,
        ownerUserId: String,
        assets: List<TournamentLobbyTemplateAssetEntity>,
    ): Boolean = false

    suspend fun deleteByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Boolean = false
}

@Singleton
class RoomTournamentLobbyTemplateAssetRepository @Inject constructor(
    private val dao: TournamentLobbyTemplateAssetDao,
    private val database: RankForgeDatabase?,
) : TournamentLobbyTemplateAssetRepository {
    constructor(dao: TournamentLobbyTemplateAssetDao) : this(
        dao = dao,
        database = null,
    )
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

    override suspend fun deleteByTournamentId(tournamentId: String) {
        dao.deleteByTournamentId(tournamentId)
    }

    override fun observeByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Flow<List<TournamentLobbyTemplateAssetEntity>> =
        if (ownerUserId.isBlank()) emptyFlow() else dao.observeByTournamentIdAndOwner(tournamentId, ownerUserId)

    override suspend fun getByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): List<TournamentLobbyTemplateAssetEntity> =
        if (ownerUserId.isBlank()) emptyList() else dao.readByTournamentIdAndOwner(tournamentId, ownerUserId)

    override suspend fun replaceForTournamentByOwner(
        tournamentId: String,
        ownerUserId: String,
        assets: List<TournamentLobbyTemplateAssetEntity>,
    ): Boolean =
        if (ownerUserId.isBlank()) {
            false
        } else {
            val db = database ?: return false
            db.withTransaction {
                if (!dao.existsTournamentByOwner(tournamentId, ownerUserId) ||
                    db.deletionIntentDao().isLocalMutationBlocked(tournamentId, null, ownerUserId)
                ) {
                    false
                } else {
                    dao.deleteByTournamentId(tournamentId)
                    dao.upsertAll(assets)
                    true
                }
            }
        }

    override suspend fun deleteByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Boolean =
        if (ownerUserId.isBlank() || !dao.existsTournamentByOwner(tournamentId, ownerUserId)) {
            false
        } else {
            dao.deleteByTournamentIdAndOwner(tournamentId, ownerUserId)
            true
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

    override suspend fun deleteByTournamentId(tournamentId: String) = Unit
}
