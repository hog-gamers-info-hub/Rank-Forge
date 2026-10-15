package com.hoggamers.rankforge.domain.sync

import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

const val GROUP_ROTATION_V4_JOURNAL_PAYLOAD_FORMAT_VERSION = 1

internal object GroupRotationV4SyncJournalPayload {
    fun isJsonObject(payloadJson: String): Boolean =
        payloadJson.isNotBlank() && runCatching { Json.parseToJsonElement(payloadJson).jsonObject }.isSuccess

    fun sha256(payloadJson: String): String = MessageDigest.getInstance("SHA-256")
        .digest(payloadJson.encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
}

enum class GroupRotationV4SyncJournalOperationType {
    IDENTITY_SNAPSHOT,
    MATCH_DRAFT,
    MATCH_FINALIZATION,
}

enum class GroupRotationV4SyncJournalStatus {
    PENDING,
    IN_PROGRESS,
    RETRYABLE_FAILURE,
    TERMINAL_VALIDATION_FAILURE,
    TERMINAL_AUTHORIZATION_FAILURE,
    TERMINAL_CONFLICT,
    CONFIRMED,
    INVALIDATED,
}

enum class GroupRotationV4SyncJournalValidationFailure {
    INVALID_OWNER,
    INVALID_TOURNAMENT,
    INVALID_REQUEST_ID,
    INVALID_PAYLOAD,
    INVALID_PAYLOAD_FORMAT_VERSION,
    INVALID_EXPECTED_REVISION,
    INVALID_STATUS_REQUEST,
    MATCH_ID_REQUIRED,
    MATCH_ID_NOT_ALLOWED,
    REQUEST_ID_REUSED_WITH_DIFFERENT_PAYLOAD,
    INVALID_CONFIRMED_REVISION,
}

data class GroupRotationV4SyncJournalRequest(
    val ownerUserId: String,
    val tournamentId: String,
    val matchId: String?,
    val operationType: GroupRotationV4SyncJournalOperationType,
    val requestId: String,
    val payloadJson: String,
    val expectedCloudRevision: Int,
    val payloadFormatVersion: Int = GROUP_ROTATION_V4_JOURNAL_PAYLOAD_FORMAT_VERSION,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

data class GroupRotationV4SyncJournalEntry(
    val journalId: String,
    val ownerUserId: String,
    val tournamentId: String,
    val matchId: String?,
    val operationType: GroupRotationV4SyncJournalOperationType,
    val requestId: String,
    val payloadJson: String,
    val payloadFormatVersion: Int,
    val payloadSha256: String,
    val expectedCloudRevision: Int,
    val status: GroupRotationV4SyncJournalStatus,
    val attemptCount: Int,
    val lastFailureCategory: String?,
    val confirmedCloudRevision: Int?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

sealed interface GroupRotationV4SyncJournalCreateResult {
    data class Created(val entry: GroupRotationV4SyncJournalEntry) : GroupRotationV4SyncJournalCreateResult
    data class Reused(val entry: GroupRotationV4SyncJournalEntry) : GroupRotationV4SyncJournalCreateResult
    data class Invalid(val reason: GroupRotationV4SyncJournalValidationFailure) : GroupRotationV4SyncJournalCreateResult
    data object Unauthorized : GroupRotationV4SyncJournalCreateResult
    data object DeletionBlocked : GroupRotationV4SyncJournalCreateResult
    data class Corrupt(val journalId: String) : GroupRotationV4SyncJournalCreateResult
}

sealed interface GroupRotationV4SyncJournalReadResult {
    data class Found(val entry: GroupRotationV4SyncJournalEntry) : GroupRotationV4SyncJournalReadResult
    data object NotFound : GroupRotationV4SyncJournalReadResult
    data object Unauthorized : GroupRotationV4SyncJournalReadResult
    data class Corrupt(val journalId: String) : GroupRotationV4SyncJournalReadResult
}

sealed interface GroupRotationV4SyncJournalEntriesResult {
    data class Loaded(val entries: List<GroupRotationV4SyncJournalEntry>) : GroupRotationV4SyncJournalEntriesResult
    data class Corrupt(val journalId: String) : GroupRotationV4SyncJournalEntriesResult
}

sealed interface GroupRotationV4SyncJournalMutationResult {
    data class Updated(val entry: GroupRotationV4SyncJournalEntry) : GroupRotationV4SyncJournalMutationResult
    data object NotFound : GroupRotationV4SyncJournalMutationResult
    data object Unauthorized : GroupRotationV4SyncJournalMutationResult
    data object Conflict : GroupRotationV4SyncJournalMutationResult
    data class Invalid(val reason: GroupRotationV4SyncJournalValidationFailure) : GroupRotationV4SyncJournalMutationResult
    data class Corrupt(val journalId: String) : GroupRotationV4SyncJournalMutationResult
}

interface GroupRotationV4SyncJournalRepository {
    suspend fun createPending(
        request: GroupRotationV4SyncJournalRequest,
    ): GroupRotationV4SyncJournalCreateResult

    suspend fun readPendingOrUnresolvedByOwner(
        ownerUserId: String,
        tournamentId: String,
    ): GroupRotationV4SyncJournalEntriesResult

    suspend fun readByJournalIdAndOwner(
        journalId: String,
        ownerUserId: String,
    ): GroupRotationV4SyncJournalReadResult

    suspend fun markInProgress(
        journalId: String,
        ownerUserId: String,
    ): GroupRotationV4SyncJournalMutationResult

    suspend fun recordRetryableFailure(
        journalId: String,
        ownerUserId: String,
        failureCategory: String,
    ): GroupRotationV4SyncJournalMutationResult

    suspend fun recordTerminalFailure(
        journalId: String,
        ownerUserId: String,
        status: GroupRotationV4SyncJournalStatus,
        failureCategory: String,
    ): GroupRotationV4SyncJournalMutationResult

    suspend fun markConfirmed(
        journalId: String,
        ownerUserId: String,
        confirmedCloudRevision: Int,
    ): GroupRotationV4SyncJournalMutationResult

    suspend fun recoverUnfinishedByOwner(
        ownerUserId: String,
        tournamentId: String,
    ): GroupRotationV4SyncJournalEntriesResult

    suspend fun invalidateByTournamentAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Int
}
