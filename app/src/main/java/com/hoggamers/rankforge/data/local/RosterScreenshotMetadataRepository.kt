package com.hoggamers.rankforge.data.local

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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
}

interface RosterScreenshotMetadataRepository {
    fun observeByTournamentId(tournamentId: String): Flow<List<RosterScreenshotMetadataEntity>>

    suspend fun saveOrReplace(
        metadata: RosterScreenshotMetadataEntity,
    ): RosterScreenshotAssociationSaveResult

    suspend fun deleteByTournamentAndIndex(
        tournamentId: String,
        index: Int,
    )
}

@Singleton
class RoomRosterScreenshotMetadataRepository @Inject constructor(
    private val dao: RosterScreenshotMetadataDao,
) : RosterScreenshotMetadataRepository {
    override fun observeByTournamentId(tournamentId: String): Flow<List<RosterScreenshotMetadataEntity>> =
        dao.observeByTournamentId(tournamentId)

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

    override suspend fun deleteByTournamentAndIndex(
        tournamentId: String,
        index: Int,
    ) {
        if (index !in ROSTER_SCREENSHOT_MINIMUM_INDEX..ROSTER_SCREENSHOT_MAXIMUM_INDEX) return
        dao.deleteByTournamentAndIndex(tournamentId, index)
    }
}

class NoOpRosterScreenshotMetadataRepository : RosterScreenshotMetadataRepository {
    override fun observeByTournamentId(tournamentId: String): Flow<List<RosterScreenshotMetadataEntity>> =
        emptyFlow()

    override suspend fun saveOrReplace(
        metadata: RosterScreenshotMetadataEntity,
    ): RosterScreenshotAssociationSaveResult = RosterScreenshotAssociationSaveResult.Saved

    override suspend fun deleteByTournamentAndIndex(tournamentId: String, index: Int) = Unit
}
