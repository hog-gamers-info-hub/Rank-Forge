package com.hoggamers.rankforge.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sync_revisions")
data class SyncRevisionEntity(
    @PrimaryKey
    @ColumnInfo(name = "tournament_id")
    val tournamentId: String,
    @ColumnInfo(name = "local_revision")
    val localRevision: Int,
    @ColumnInfo(name = "base_cloud_revision")
    val baseCloudRevision: Int?,
)

@Entity(tableName = "tournaments")
data class TournamentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val date: String,
    @ColumnInfo(name = "organizer_name") val organizerName: String,
    @ColumnInfo(name = "organizer_contact_number") val organizerContactNumber: String,
    val status: String,
    @ColumnInfo(name = "creation_order", defaultValue = "0") val creationOrder: Long = 0L,
)

@Entity(
    tableName = "team_slots",
    primaryKeys = ["tournament_id", "slot_number"],
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
data class TeamSlotEntity(
    @ColumnInfo(name = "tournament_id") val tournamentId: String,
    @ColumnInfo(name = "slot_number") val slotNumber: Int,
    @ColumnInfo(name = "team_name") val teamName: String,
)

@Entity(
    tableName = "roster_players",
    primaryKeys = ["tournament_id", "slot_number", "roster_position"],
    foreignKeys = [
        ForeignKey(
            entity = TeamSlotEntity::class,
            parentColumns = ["tournament_id", "slot_number"],
            childColumns = ["tournament_id", "slot_number"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tournament_id", "slot_number"])],
)
data class RosterPlayerEntity(
    @ColumnInfo(name = "tournament_id") val tournamentId: String,
    @ColumnInfo(name = "slot_number") val slotNumber: Int,
    @ColumnInfo(name = "roster_position") val rosterPosition: Int,
    @ColumnInfo(name = "display_name") val displayName: String,
)

@Entity(
    tableName = "matches",
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
data class MatchEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tournament_id") val tournamentId: String,
    @ColumnInfo(name = "match_number") val matchNumber: Int,
    val date: String,
    @ColumnInfo(name = "map_name") val mapName: String,
    val status: String,
)

enum class ScreenshotLocalStatus {
    PRESERVED,
    MISSING,
    CLEANUP_FAILED,
}

enum class ScreenshotUploadStatus {
    PENDING,
    UPLOADED,
    FAILED,
}

enum class RosterScreenshotValidationStatus {
    VALID,
    LOCAL_FILE_MISSING,
}

@Entity(
    tableName = "roster_screenshot_metadata",
    primaryKeys = ["tournament_id", "roster_screenshot_index"],
    foreignKeys = [
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournament_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tournament_id"]),
        Index(value = ["sha256"]),
    ],
)
data class RosterScreenshotMetadataEntity(
    @ColumnInfo(name = "tournament_id") val tournamentId: String,
    @ColumnInfo(name = "roster_screenshot_index") val rosterScreenshotIndex: Int,
    @ColumnInfo(name = "local_relative_path") val localRelativePath: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    val width: Int,
    val height: Int,
    val sha256: String,
    @ColumnInfo(name = "validation_status") val validationStatus: String,
    @ColumnInfo(name = "crop_left") val cropLeft: Double?,
    @ColumnInfo(name = "crop_top") val cropTop: Double?,
    @ColumnInfo(name = "crop_right") val cropRight: Double?,
    @ColumnInfo(name = "crop_bottom") val cropBottom: Double?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "screenshot_metadata",
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tournament_id"]),
        Index(value = ["owner_user_id"]),
        Index(value = ["sha256"]),
        Index(value = ["upload_status"]),
    ],
)
data class ScreenshotMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "match_id")
    val matchId: String,
    @ColumnInfo(name = "tournament_id")
    val tournamentId: String,
    @ColumnInfo(name = "owner_user_id")
    val ownerUserId: String,
    @ColumnInfo(name = "local_relative_path")
    val localRelativePath: String,
    @ColumnInfo(name = "file_extension")
    val fileExtension: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "byte_size")
    val byteSize: Long,
    val sha256: String,
    @ColumnInfo(name = "storage_bucket")
    val storageBucket: String?,
    @ColumnInfo(name = "storage_object_path")
    val storageObjectPath: String?,
    @ColumnInfo(name = "local_status")
    val localStatus: String,
    @ColumnInfo(name = "upload_status")
    val uploadStatus: String,
    @ColumnInfo(name = "upload_failure_code")
    val uploadFailureCode: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "preserved_at")
    val preservedAt: Long,
    @ColumnInfo(name = "uploaded_at")
    val uploadedAt: Long?,
    val revision: Long,
)

@Entity(
    tableName = "match_result_screenshot_assets",
    primaryKeys = ["match_id", "screenshot_role"],
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tournament_id"]),
        Index(value = ["sha256"]),
        Index(value = ["upload_status"]),
    ],
)
data class MatchResultScreenshotAssetEntity(
    @ColumnInfo(name = "tournament_id")
    val tournamentId: String,
    @ColumnInfo(name = "match_id")
    val matchId: String,
    @ColumnInfo(name = "screenshot_kind")
    val screenshotKind: String,
    @ColumnInfo(name = "screenshot_role")
    val screenshotRole: String,
    @ColumnInfo(name = "owner_user_id")
    val ownerUserId: String,
    @ColumnInfo(name = "local_relative_path")
    val localRelativePath: String,
    @ColumnInfo(name = "file_extension")
    val fileExtension: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "original_width")
    val originalWidth: Int,
    @ColumnInfo(name = "original_height")
    val originalHeight: Int,
    @ColumnInfo(name = "byte_size")
    val byteSize: Long,
    val sha256: String,
    @ColumnInfo(name = "local_status")
    val localStatus: String,
    @ColumnInfo(name = "upload_status")
    val uploadStatus: String,
    @ColumnInfo(name = "upload_failure_code")
    val uploadFailureCode: String?,
    @ColumnInfo(name = "storage_bucket")
    val storageBucket: String?,
    @ColumnInfo(name = "storage_object_path")
    val storageObjectPath: String?,
    @ColumnInfo(name = "crop_profile_id")
    val cropProfileId: String?,
    @ColumnInfo(name = "crop_left")
    val cropLeft: Double?,
    @ColumnInfo(name = "crop_top")
    val cropTop: Double?,
    @ColumnInfo(name = "crop_right")
    val cropRight: Double?,
    @ColumnInfo(name = "crop_bottom")
    val cropBottom: Double?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "preserved_at")
    val preservedAt: Long,
    @ColumnInfo(name = "uploaded_at")
    val uploadedAt: Long?,
    val revision: Long,
)

@Entity(
    tableName = "match_lobby_screenshot_assets",
    primaryKeys = ["match_id", "lobby_screenshot_index"],
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tournament_id"]),
        Index(value = ["sha256"]),
        Index(value = ["upload_status"]),
    ],
)
data class MatchLobbyScreenshotAssetEntity(
    @ColumnInfo(name = "tournament_id")
    val tournamentId: String,
    @ColumnInfo(name = "match_id")
    val matchId: String,
    @ColumnInfo(name = "lobby_screenshot_index")
    val lobbyScreenshotIndex: Int,
    @ColumnInfo(name = "owner_user_id")
    val ownerUserId: String,
    @ColumnInfo(name = "local_relative_path")
    val localRelativePath: String,
    @ColumnInfo(name = "file_extension")
    val fileExtension: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "original_width")
    val originalWidth: Int,
    @ColumnInfo(name = "original_height")
    val originalHeight: Int,
    @ColumnInfo(name = "byte_size")
    val byteSize: Long,
    val sha256: String,
    @ColumnInfo(name = "local_status")
    val localStatus: String,
    @ColumnInfo(name = "upload_status")
    val uploadStatus: String,
    @ColumnInfo(name = "upload_failure_code")
    val uploadFailureCode: String?,
    @ColumnInfo(name = "storage_bucket")
    val storageBucket: String?,
    @ColumnInfo(name = "storage_object_path")
    val storageObjectPath: String?,
    @ColumnInfo(name = "crop_profile_id")
    val cropProfileId: String?,
    @ColumnInfo(name = "crop_left")
    val cropLeft: Double?,
    @ColumnInfo(name = "crop_top")
    val cropTop: Double?,
    @ColumnInfo(name = "crop_right")
    val cropRight: Double?,
    @ColumnInfo(name = "crop_bottom")
    val cropBottom: Double?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "preserved_at")
    val preservedAt: Long,
    @ColumnInfo(name = "uploaded_at")
    val uploadedAt: Long?,
    val revision: Long,
)

@Entity(
    tableName = "tournament_lobby_template_assets",
    primaryKeys = ["tournament_id", "lobby_screenshot_index"],
    foreignKeys = [
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournament_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tournament_id"]),
        Index(value = ["sha256"]),
    ],
)
data class TournamentLobbyTemplateAssetEntity(
    @ColumnInfo(name = "tournament_id")
    val tournamentId: String,
    @ColumnInfo(name = "lobby_screenshot_index")
    val lobbyScreenshotIndex: Int,
    @ColumnInfo(name = "owner_user_id")
    val ownerUserId: String,
    @ColumnInfo(name = "local_relative_path")
    val localRelativePath: String,
    @ColumnInfo(name = "file_extension")
    val fileExtension: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "original_width")
    val originalWidth: Int,
    @ColumnInfo(name = "original_height")
    val originalHeight: Int,
    @ColumnInfo(name = "byte_size")
    val byteSize: Long,
    val sha256: String,
    @ColumnInfo(name = "crop_profile_id")
    val cropProfileId: String,
    @ColumnInfo(name = "crop_left")
    val cropLeft: Double,
    @ColumnInfo(name = "crop_top")
    val cropTop: Double,
    @ColumnInfo(name = "crop_right")
    val cropRight: Double,
    @ColumnInfo(name = "crop_bottom")
    val cropBottom: Double,
    @ColumnInfo(name = "source_match_id")
    val sourceMatchId: String,
    @ColumnInfo(name = "saved_at")
    val savedAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    val revision: Long,
)

@Entity(
    tableName = "match_ocr_evidence",
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tournament_id"])],
)
data class MatchOcrEvidenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "match_id")
    val matchId: String,
    @ColumnInfo(name = "tournament_id")
    val tournamentId: String,
    @ColumnInfo(name = "source_screenshot_id")
    val sourceScreenshotId: String?,
    @ColumnInfo(name = "preserved_at")
    val preservedAt: Long,
    val provenance: String,
)

@Entity(
    tableName = "match_ocr_row_evidence",
    primaryKeys = ["match_id", "row_index"],
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["match_id"]),
        Index(value = ["tournament_id"]),
    ],
)
data class MatchOcrRowEvidenceEntity(
    @ColumnInfo(name = "match_id")
    val matchId: String,
    @ColumnInfo(name = "tournament_id")
    val tournamentId: String,
    @ColumnInfo(name = "row_index")
    val rowIndex: Int,
    @ColumnInfo(name = "original_ocr_text")
    val originalOcrText: String?,
    @ColumnInfo(name = "original_placement")
    val originalPlacement: Int?,
    @ColumnInfo(name = "original_kills")
    val originalKills: Int?,
    @ColumnInfo(name = "original_suggested_team_slot")
    val originalSuggestedTeamSlot: Int?,
    @ColumnInfo(name = "confidence_summary")
    val confidenceSummary: String?,
    @ColumnInfo(name = "safety_summary")
    val safetySummary: String?,
    @ColumnInfo(name = "manual_review_required")
    val manualReviewRequired: Boolean,
)

@Entity(
    tableName = "match_ocr_correction_snapshots",
    primaryKeys = ["match_id", "row_index"],
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["match_id"]),
        Index(value = ["tournament_id"]),
    ],
)
data class MatchOcrCorrectionSnapshotEntity(
    @ColumnInfo(name = "match_id")
    val matchId: String,
    @ColumnInfo(name = "tournament_id")
    val tournamentId: String,
    @ColumnInfo(name = "row_index")
    val rowIndex: Int,
    @ColumnInfo(name = "corrected_placement")
    val correctedPlacement: Int,
    @ColumnInfo(name = "corrected_kills")
    val correctedKills: Int,
    @ColumnInfo(name = "corrected_team_slot")
    val correctedTeamSlot: Int,
    @ColumnInfo(name = "placement_changed")
    val placementChanged: Boolean,
    @ColumnInfo(name = "kills_changed")
    val killsChanged: Boolean,
    @ColumnInfo(name = "team_slot_changed")
    val teamSlotChanged: Boolean,
    @ColumnInfo(name = "preserved_at")
    val preservedAt: Long,
    val provenance: String,
)

@Entity(
    tableName = "match_placements",
    primaryKeys = ["match_id", "team_slot_number"],
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["match_id"])],
)
data class MatchPlacementEntity(
    @ColumnInfo(name = "match_id") val matchId: String,
    @ColumnInfo(name = "team_slot_number") val teamSlotNumber: Int,
    val position: Int,
)

@Entity(
    tableName = "match_kills",
    primaryKeys = ["match_id", "team_slot_number"],
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["match_id"])],
)
data class MatchKillEntity(
    @ColumnInfo(name = "match_id") val matchId: String,
    @ColumnInfo(name = "team_slot_number") val teamSlotNumber: Int,
    val kills: Int,
)

@Entity(
    tableName = "match_draft_values",
    primaryKeys = ["match_id", "team_slot_number"],
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["match_id"])],
)
data class MatchDraftValueEntity(
    @ColumnInfo(name = "match_id") val matchId: String,
    @ColumnInfo(name = "team_slot_number") val teamSlotNumber: Int,
    @ColumnInfo(name = "placement_input") val placementInput: String,
    @ColumnInfo(name = "kills_input") val killsInput: String,
)

@Entity(
    tableName = "match_corrections",
    primaryKeys = ["match_id", "correction_index"],
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["match_id"])],
)
data class MatchCorrectionEntity(
    @ColumnInfo(name = "match_id") val matchId: String,
    @ColumnInfo(name = "correction_index") val correctionIndex: Int,
    @ColumnInfo(name = "previous_placements") val previousPlacements: String,
    @ColumnInfo(name = "previous_kills") val previousKills: String,
    @ColumnInfo(name = "corrected_placements") val correctedPlacements: String,
    @ColumnInfo(name = "corrected_kills") val correctedKills: String,
)
