package com.hoggamers.rankforge.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class PointTableDetails(
    val tournamentId: String,
    val organizationName: String = "",
    val displayDate: LocalDate? = null,
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
)

@Dao
interface PointTableDetailsDao {
    @Query("SELECT * FROM point_table_details WHERE tournament_id = :tournamentId")
    suspend fun readByTournamentId(tournamentId: String): PointTableDetailsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(details: PointTableDetailsEntity)
}

interface PointTableDetailsRepository {
    suspend fun getPointTableDetails(tournamentId: String): PointTableDetails?

    suspend fun savePointTableDetails(details: PointTableDetails)
}

@Singleton
class RoomPointTableDetailsRepository @Inject constructor(
    private val dao: PointTableDetailsDao,
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
            ),
        )
    }
}

private fun PointTableDetailsEntity.toModel(): PointTableDetails = PointTableDetails(
    tournamentId = tournamentId,
    organizationName = organizationName,
    displayDate = displayDate?.let { value ->
        runCatching { LocalDate.parse(value) }.getOrNull()
    },
)
