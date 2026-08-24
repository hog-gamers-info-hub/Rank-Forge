package com.hoggamers.rankforge.data.local

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val ROSTER_SCREENSHOT_MINIMUM_INDEX = 1
const val ROSTER_SCREENSHOT_MAXIMUM_INDEX = 3

data class RosterScreenshotSlotRange(
    val first: Int,
    val last: Int,
)

fun rosterScreenshotSlotRange(index: Int): RosterScreenshotSlotRange? = when (index) {
    1 -> RosterScreenshotSlotRange(first = 1, last = 4)
    2 -> RosterScreenshotSlotRange(first = 5, last = 8)
    3 -> RosterScreenshotSlotRange(first = 9, last = 12)
    else -> null
}

sealed interface RosterScreenshotAssociationSaveResult {
    data object Saved : RosterScreenshotAssociationSaveResult
    data object InvalidIndex : RosterScreenshotAssociationSaveResult
    data object DuplicateFingerprint : RosterScreenshotAssociationSaveResult
    data object AuthenticationRequired : RosterScreenshotAssociationSaveResult
    data object TournamentNotFound : RosterScreenshotAssociationSaveResult
}

sealed interface RosterScreenshotAssociationDeleteResult {
    data object Deleted : RosterScreenshotAssociationDeleteResult
    data object AuthenticationRequired : RosterScreenshotAssociationDeleteResult
    data object TournamentNotFound : RosterScreenshotAssociationDeleteResult
}

interface RosterScreenshotMetadataRepository {
    fun observeByTournamentId(tournamentId: String): Flow<List<RosterScreenshotMetadataEntity>>

    fun observeByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Flow<List<RosterScreenshotMetadataEntity>>

    suspend fun existsByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Boolean

    suspend fun readByTournamentAndIndexAndOwner(
        tournamentId: String,
        index: Int,
        ownerUserId: String,
    ): RosterScreenshotMetadataEntity?

    suspend fun findDuplicateFingerprintAndOwner(
        tournamentId: String,
        sha256: String,
        index: Int,
        ownerUserId: String,
    ): RosterScreenshotMetadataEntity?

    suspend fun saveOrReplace(
        metadata: RosterScreenshotMetadataEntity,
    ): RosterScreenshotAssociationSaveResult

    suspend fun saveOrReplaceByOwner(
        metadata: RosterScreenshotMetadataEntity,
        ownerUserId: String,
    ): RosterScreenshotAssociationSaveResult

    suspend fun deleteByTournamentAndIndex(
        tournamentId: String,
        index: Int,
    )

    suspend fun deleteByTournamentAndIndexAndOwner(
        tournamentId: String,
        index: Int,
        ownerUserId: String,
    ): RosterScreenshotAssociationDeleteResult
}

@Singleton
class RoomRosterScreenshotMetadataRepository @Inject constructor(
    private val dao: RosterScreenshotMetadataDao,
    private val database: RankForgeDatabase,
) : RosterScreenshotMetadataRepository {
    private val mutationMutex = Mutex()

    override fun observeByTournamentId(tournamentId: String): Flow<List<RosterScreenshotMetadataEntity>> =
        dao.observeByTournamentId(tournamentId)

    override fun observeByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Flow<List<RosterScreenshotMetadataEntity>> =
        if (ownerUserId.isBlank()) {
            emptyFlow()
        } else {
            dao.observeByTournamentIdAndOwner(tournamentId, ownerUserId)
        }

    override suspend fun existsByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Boolean = ownerUserId.isNotBlank() && database.tournamentDao().existsByIdAndOwner(tournamentId, ownerUserId)

    override suspend fun readByTournamentAndIndexAndOwner(
        tournamentId: String,
        index: Int,
        ownerUserId: String,
    ): RosterScreenshotMetadataEntity? =
        if (ownerUserId.isBlank()) null else dao.readByTournamentAndIndexAndOwner(tournamentId, index, ownerUserId)

    override suspend fun findDuplicateFingerprintAndOwner(
        tournamentId: String,
        sha256: String,
        index: Int,
        ownerUserId: String,
    ): RosterScreenshotMetadataEntity? =
        if (ownerUserId.isBlank()) null else dao.readDuplicateFingerprintAndOwner(tournamentId, sha256, index, ownerUserId)

    override suspend fun saveOrReplace(
        metadata: RosterScreenshotMetadataEntity,
    ): RosterScreenshotAssociationSaveResult {
        if (metadata.rosterScreenshotIndex !in ROSTER_SCREENSHOT_MINIMUM_INDEX..ROSTER_SCREENSHOT_MAXIMUM_INDEX) {
            return RosterScreenshotAssociationSaveResult.InvalidIndex
        }
        if (dao.readDuplicateFingerprint(
                tournamentId = metadata.tournamentId,
                sha256 = metadata.sha256,
                index = metadata.rosterScreenshotIndex,
            ) != null
        ) {
            return RosterScreenshotAssociationSaveResult.DuplicateFingerprint
        }
        dao.upsert(metadata)
        return RosterScreenshotAssociationSaveResult.Saved
    }

    override suspend fun saveOrReplaceByOwner(
        metadata: RosterScreenshotMetadataEntity,
        ownerUserId: String,
    ): RosterScreenshotAssociationSaveResult = mutationMutex.withLock {
        if (ownerUserId.isBlank()) return@withLock RosterScreenshotAssociationSaveResult.AuthenticationRequired
        if (metadata.rosterScreenshotIndex !in ROSTER_SCREENSHOT_MINIMUM_INDEX..ROSTER_SCREENSHOT_MAXIMUM_INDEX) {
            return@withLock RosterScreenshotAssociationSaveResult.InvalidIndex
        }
        database.withTransaction {
            if (!database.tournamentDao().existsByIdAndOwner(metadata.tournamentId, ownerUserId)) {
                return@withTransaction RosterScreenshotAssociationSaveResult.TournamentNotFound
            }
            if (database.deletionIntentDao().isLocalMutationBlocked(metadata.tournamentId, null, ownerUserId)) {
                return@withTransaction RosterScreenshotAssociationSaveResult.TournamentNotFound
            }
            if (dao.readDuplicateFingerprintAndOwner(
                    tournamentId = metadata.tournamentId,
                    sha256 = metadata.sha256,
                    index = metadata.rosterScreenshotIndex,
                    ownerUserId = ownerUserId,
                ) != null
            ) {
                return@withTransaction RosterScreenshotAssociationSaveResult.DuplicateFingerprint
            }
            dao.upsert(metadata)
            RosterScreenshotAssociationSaveResult.Saved
        }
    }

    override suspend fun deleteByTournamentAndIndex(
        tournamentId: String,
        index: Int,
    ) {
        if (index !in ROSTER_SCREENSHOT_MINIMUM_INDEX..ROSTER_SCREENSHOT_MAXIMUM_INDEX) return
        dao.deleteByTournamentAndIndex(tournamentId, index)
    }

    override suspend fun deleteByTournamentAndIndexAndOwner(
        tournamentId: String,
        index: Int,
        ownerUserId: String,
    ): RosterScreenshotAssociationDeleteResult = mutationMutex.withLock {
        if (ownerUserId.isBlank()) return@withLock RosterScreenshotAssociationDeleteResult.AuthenticationRequired
        if (index !in ROSTER_SCREENSHOT_MINIMUM_INDEX..ROSTER_SCREENSHOT_MAXIMUM_INDEX) {
            return@withLock RosterScreenshotAssociationDeleteResult.Deleted
        }
        database.withTransaction {
            if (!database.tournamentDao().existsByIdAndOwner(tournamentId, ownerUserId)) {
                return@withTransaction RosterScreenshotAssociationDeleteResult.TournamentNotFound
            }
            dao.deleteByTournamentAndIndexAndOwner(tournamentId, index, ownerUserId)
            RosterScreenshotAssociationDeleteResult.Deleted
        }
    }
}

class NoOpRosterScreenshotMetadataRepository : RosterScreenshotMetadataRepository {
    override fun observeByTournamentId(tournamentId: String): Flow<List<RosterScreenshotMetadataEntity>> =
        emptyFlow()

    override fun observeByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Flow<List<RosterScreenshotMetadataEntity>> = emptyFlow()

    override suspend fun existsByTournamentIdAndOwner(tournamentId: String, ownerUserId: String): Boolean = false

    override suspend fun readByTournamentAndIndexAndOwner(
        tournamentId: String,
        index: Int,
        ownerUserId: String,
    ): RosterScreenshotMetadataEntity? = null

    override suspend fun findDuplicateFingerprintAndOwner(
        tournamentId: String,
        sha256: String,
        index: Int,
        ownerUserId: String,
    ): RosterScreenshotMetadataEntity? = null

    override suspend fun saveOrReplace(
        metadata: RosterScreenshotMetadataEntity,
    ): RosterScreenshotAssociationSaveResult = RosterScreenshotAssociationSaveResult.Saved

    override suspend fun saveOrReplaceByOwner(
        metadata: RosterScreenshotMetadataEntity,
        ownerUserId: String,
    ): RosterScreenshotAssociationSaveResult = RosterScreenshotAssociationSaveResult.TournamentNotFound

    override suspend fun deleteByTournamentAndIndex(tournamentId: String, index: Int) = Unit

    override suspend fun deleteByTournamentAndIndexAndOwner(
        tournamentId: String,
        index: Int,
        ownerUserId: String,
    ): RosterScreenshotAssociationDeleteResult = RosterScreenshotAssociationDeleteResult.TournamentNotFound
}
