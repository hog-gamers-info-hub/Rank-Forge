package com.hoggamers.rankforge.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.withTransaction
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class PointTableDetails(
    val tournamentId: String,
    val organizationName: String = "",
    val displayDate: LocalDate? = null,
    val organizationLogoPath: String? = null,
)

@Entity(
    tableName = "point_table_details",
    foreignKeys = [
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournament_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PointTableDetailsEntity(
    @PrimaryKey
    @androidx.room.ColumnInfo(name = "tournament_id")
    val tournamentId: String,
    @androidx.room.ColumnInfo(name = "organization_name")
    val organizationName: String,
    @androidx.room.ColumnInfo(name = "display_date")
    val displayDate: String?,
    @androidx.room.ColumnInfo(name = "organization_logo_path")
    val organizationLogoPath: String?,
)

data class PointTableLogoPlacement(
    val tournamentId: String,
    val designKey: String,
    val centerXRatio: Float,
    val centerYRatio: Float,
    val widthRatio: Float,
) {
    fun isValid(): Boolean = tournamentId.isNotBlank() &&
        designKey.isNotBlank() &&
        centerXRatio.isFinite() && centerXRatio in 0f..1f &&
        centerYRatio.isFinite() && centerYRatio in 0f..1f &&
        widthRatio.isFinite() && widthRatio > 0f && widthRatio <= 1f
}

@Entity(
    tableName = "point_table_logo_placements",
    primaryKeys = ["tournament_id", "design_key"],
    foreignKeys = [
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournament_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tournament_id"])],
)
data class PointTableLogoPlacementEntity(
    @androidx.room.ColumnInfo(name = "tournament_id")
    val tournamentId: String,
    @androidx.room.ColumnInfo(name = "design_key")
    val designKey: String,
    @androidx.room.ColumnInfo(name = "center_x_ratio")
    val centerXRatio: Float,
    @androidx.room.ColumnInfo(name = "center_y_ratio")
    val centerYRatio: Float,
    @androidx.room.ColumnInfo(name = "width_ratio")
    val widthRatio: Float,
)

@Dao
interface PointTableDetailsDao {
    @Query("SELECT * FROM point_table_details WHERE tournament_id = :tournamentId")
    suspend fun readByTournamentId(tournamentId: String): PointTableDetailsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(details: PointTableDetailsEntity)
}

@Dao
interface PointTableLogoPlacementDao {
    @Query(
        "SELECT * FROM point_table_logo_placements " +
            "WHERE tournament_id = :tournamentId AND design_key = :designKey",
    )
    suspend fun readByTournamentIdAndDesignKey(
        tournamentId: String,
        designKey: String,
    ): PointTableLogoPlacementEntity?

    @Upsert
    suspend fun upsert(placement: PointTableLogoPlacementEntity)

    @Query(
        "DELETE FROM point_table_logo_placements " +
            "WHERE tournament_id = :tournamentId AND design_key = :designKey",
    )
    suspend fun deleteByTournamentIdAndDesignKey(tournamentId: String, designKey: String)

    @Query("DELETE FROM point_table_logo_placements WHERE tournament_id = :tournamentId")
    suspend fun deleteByTournamentId(tournamentId: String)
}

interface PointTableDetailsRepository {
    suspend fun getPointTableDetails(tournamentId: String): PointTableDetails?

    suspend fun savePointTableDetails(details: PointTableDetails)

    suspend fun getPointTableLogoPlacement(
        tournamentId: String,
        designKey: String,
    ): PointTableLogoPlacement? = null

    suspend fun savePointTableLogoPlacement(placement: PointTableLogoPlacement) = Unit

    suspend fun deletePointTableLogoPlacement(tournamentId: String, designKey: String) = Unit

    suspend fun deletePointTableLogoPlacements(tournamentId: String) = Unit

    suspend fun savePointTableLogoConfiguration(
        tournamentId: String,
        organizationLogoPath: String,
        placement: PointTableLogoPlacement,
        clearExistingPlacements: Boolean,
    ): Boolean = false

    suspend fun removePointTableLogoConfiguration(tournamentId: String): Boolean = false
}

@Singleton
class RoomPointTableDetailsRepository @Inject constructor(
    private val dao: PointTableDetailsDao,
    private val logoPlacementDao: PointTableLogoPlacementDao,
    private val database: RankForgeDatabase,
) : PointTableDetailsRepository {
    override suspend fun getPointTableDetails(tournamentId: String): PointTableDetails? {
        if (tournamentId.isBlank()) return null
        return dao.readByTournamentId(tournamentId)?.toModel()
    }

    override suspend fun savePointTableDetails(details: PointTableDetails) {
        if (details.tournamentId.isBlank()) return
        dao.upsert(
            PointTableDetailsEntity(
                tournamentId = details.tournamentId,
                organizationName = details.organizationName.trim(),
                displayDate = details.displayDate?.toString(),
                organizationLogoPath = details.organizationLogoPath?.takeIf(::isSafeOrganizationLogoPath),
            ),
        )
    }

    override suspend fun getPointTableLogoPlacement(
        tournamentId: String,
        designKey: String,
    ): PointTableLogoPlacement? {
        if (tournamentId.isBlank() || designKey.isBlank()) return null
        return logoPlacementDao.readByTournamentIdAndDesignKey(tournamentId, designKey)?.toModel()
    }

    override suspend fun savePointTableLogoPlacement(placement: PointTableLogoPlacement) {
        if (!placement.isValid()) return
        logoPlacementDao.upsert(
            PointTableLogoPlacementEntity(
                tournamentId = placement.tournamentId,
                designKey = placement.designKey,
                centerXRatio = placement.centerXRatio,
                centerYRatio = placement.centerYRatio,
                widthRatio = placement.widthRatio,
            ),
        )
    }

    override suspend fun deletePointTableLogoPlacement(tournamentId: String, designKey: String) {
        if (tournamentId.isBlank() || designKey.isBlank()) return
        logoPlacementDao.deleteByTournamentIdAndDesignKey(tournamentId, designKey)
    }

    override suspend fun deletePointTableLogoPlacements(tournamentId: String) {
        if (tournamentId.isBlank()) return
        logoPlacementDao.deleteByTournamentId(tournamentId)
    }

    override suspend fun savePointTableLogoConfiguration(
        tournamentId: String,
        organizationLogoPath: String,
        placement: PointTableLogoPlacement,
        clearExistingPlacements: Boolean,
    ): Boolean {
        if (tournamentId.isBlank() ||
            !isSafeOrganizationLogoPath(organizationLogoPath) ||
            placement.tournamentId != tournamentId ||
            !placement.isValid()
        ) {
            return false
        }
        return database.withTransaction {
            val existing = dao.readByTournamentId(tournamentId)
            dao.upsert(
                (existing ?: PointTableDetailsEntity(
                    tournamentId = tournamentId,
                    organizationName = "",
                    displayDate = null,
                    organizationLogoPath = null,
                )).copy(organizationLogoPath = organizationLogoPath),
            )
            if (clearExistingPlacements) {
                logoPlacementDao.deleteByTournamentId(tournamentId)
            }
            logoPlacementDao.upsert(placement.toEntity())
            true
        }
    }

    override suspend fun removePointTableLogoConfiguration(tournamentId: String): Boolean {
        if (tournamentId.isBlank()) return false
        return database.withTransaction {
            dao.readByTournamentId(tournamentId)?.let { existing ->
                dao.upsert(existing.copy(organizationLogoPath = null))
            }
            logoPlacementDao.deleteByTournamentId(tournamentId)
            true
        }
    }
}

private fun PointTableDetailsEntity.toModel(): PointTableDetails = PointTableDetails(
    tournamentId = tournamentId,
    organizationName = organizationName,
    displayDate = displayDate?.let { value ->
        runCatching { LocalDate.parse(value) }.getOrNull()
    },
    organizationLogoPath = organizationLogoPath?.takeIf(::isSafeOrganizationLogoPath),
)

private fun PointTableLogoPlacementEntity.toModel(): PointTableLogoPlacement = PointTableLogoPlacement(
    tournamentId = tournamentId,
    designKey = designKey,
    centerXRatio = centerXRatio,
    centerYRatio = centerYRatio,
    widthRatio = widthRatio,
)

private fun PointTableLogoPlacement.toEntity(): PointTableLogoPlacementEntity =
    PointTableLogoPlacementEntity(
        tournamentId = tournamentId,
        designKey = designKey,
        centerXRatio = centerXRatio,
        centerYRatio = centerYRatio,
        widthRatio = widthRatio,
    )

internal fun isSafeOrganizationLogoPath(path: String): Boolean {
    val normalized = path.replace('\\', '/')
    val segments = normalized.split('/')
    return normalized.isNotBlank() &&
        !normalized.startsWith('/') &&
        !normalized.contains("://") &&
        !normalized.contains(':') &&
        segments.none { it.isBlank() || it == "." || it == ".." }
}
