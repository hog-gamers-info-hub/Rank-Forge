package com.hoggamers.rankforge.data.sync

import androidx.room.withTransaction
import com.hoggamers.rankforge.data.local.GroupRotationV4SyncJournalDao
import com.hoggamers.rankforge.data.local.GroupRotationV4SyncJournalEntity
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.domain.sync.GROUP_ROTATION_V4_JOURNAL_PAYLOAD_FORMAT_VERSION
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalCreateResult
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalEntriesResult
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalEntry
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalMutationResult
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalOperationType
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalReadResult
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalRepository
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalRequest
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalStatus
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalValidationFailure
import com.hoggamers.rankforge.domain.tournament.DeletionIntentRepository
import com.hoggamers.rankforge.domain.tournament.NoOpDeletionIntentRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalPayload

@Singleton
class RoomGroupRotationV4SyncJournalRepository @Inject constructor(
    private val database: RankForgeDatabase,
    private val dao: GroupRotationV4SyncJournalDao,
    private val deletionIntentRepository: DeletionIntentRepository,
) : GroupRotationV4SyncJournalRepository {
    constructor(database: RankForgeDatabase) : this(
        database = database,
        dao = database.groupRotationV4SyncJournalDao(),
        deletionIntentRepository = NoOpDeletionIntentRepository,
    )

    private val mutationMutex = Mutex()

    override suspend fun createPending(
        request: GroupRotationV4SyncJournalRequest,
    ): GroupRotationV4SyncJournalCreateResult = mutationMutex.withLock<GroupRotationV4SyncJournalCreateResult> {
        validateRequest(request)?.let { return@withLock GroupRotationV4SyncJournalCreateResult.Invalid(it) }
        val payloadSha256 = GroupRotationV4SyncJournalPayload.sha256(request.payloadJson)
        database.withTransaction<GroupRotationV4SyncJournalCreateResult> {
            val existing = dao.readByRequestId(request.requestId)
            if (existing != null) {
                if (existing.ownerUserId != request.ownerUserId) {
                    return@withTransaction GroupRotationV4SyncJournalCreateResult.Unauthorized
                }
                val existingEntry = existing.toValidatedDomain()
                    ?: return@withTransaction GroupRotationV4SyncJournalCreateResult.Corrupt(existing.journalId)
                return@withTransaction if (sameRequest(existingEntry, request, payloadSha256)) {
                    GroupRotationV4SyncJournalCreateResult.Reused(existingEntry)
                } else {
                    GroupRotationV4SyncJournalCreateResult.Invalid(
                        GroupRotationV4SyncJournalValidationFailure.REQUEST_ID_REUSED_WITH_DIFFERENT_PAYLOAD,
                    )
                }
            }
            if (deletionIntentRepository.isBlockingByTournamentIdAndOwner(request.tournamentId, request.ownerUserId)) {
                return@withTransaction GroupRotationV4SyncJournalCreateResult.DeletionBlocked
            }

            val now = request.createdAtEpochMillis
            val entity = GroupRotationV4SyncJournalEntity(
                journalId = UUID.randomUUID().toString(),
                ownerUserId = request.ownerUserId,
                tournamentId = request.tournamentId,
                matchId = request.matchId,
                operationType = request.operationType.name,
                requestId = request.requestId,
                payloadJson = request.payloadJson,
                payloadFormatVersion = request.payloadFormatVersion,
                payloadSha256 = payloadSha256,
                expectedCloudRevision = request.expectedCloudRevision,
                status = GroupRotationV4SyncJournalStatus.PENDING.name,
                attemptCount = 0,
                lastFailureCategory = null,
                confirmedCloudRevision = null,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
            if (dao.insertIfAbsent(entity) == -1L) {
                val raced = dao.readByRequestId(request.requestId)
                    ?: return@withTransaction GroupRotationV4SyncJournalCreateResult.Corrupt(entity.journalId)
                if (raced.ownerUserId != request.ownerUserId) {
                    GroupRotationV4SyncJournalCreateResult.Unauthorized
                } else {
                    val racedEntry = raced.toValidatedDomain()
                    if (racedEntry == null) {
                        GroupRotationV4SyncJournalCreateResult.Corrupt(raced.journalId)
                    } else if (sameRequest(racedEntry, request, payloadSha256)) {
                        GroupRotationV4SyncJournalCreateResult.Reused(racedEntry)
                    } else {
                        GroupRotationV4SyncJournalCreateResult.Invalid(
                            GroupRotationV4SyncJournalValidationFailure.REQUEST_ID_REUSED_WITH_DIFFERENT_PAYLOAD,
                        )
                    }
                }
            } else {
                GroupRotationV4SyncJournalCreateResult.Created(entity.toDomain())
            }
        }
    }

    override suspend fun readPendingOrUnresolvedByOwner(
        ownerUserId: String,
        tournamentId: String,
    ): GroupRotationV4SyncJournalEntriesResult {
        if (ownerUserId.isBlank() || tournamentId.isBlank()) return GroupRotationV4SyncJournalEntriesResult.Loaded(emptyList())
        return readEntities(dao.readPendingOrUnresolvedByOwner(ownerUserId, tournamentId))
    }

    override suspend fun readByJournalIdAndOwner(
        journalId: String,
        ownerUserId: String,
    ): GroupRotationV4SyncJournalReadResult {
        val entity = dao.readByJournalId(journalId) ?: return GroupRotationV4SyncJournalReadResult.NotFound
        if (entity.ownerUserId != ownerUserId) return GroupRotationV4SyncJournalReadResult.Unauthorized
        return entity.toValidatedDomain()?.let(GroupRotationV4SyncJournalReadResult::Found)
            ?: GroupRotationV4SyncJournalReadResult.Corrupt(entity.journalId)
    }

    override suspend fun markInProgress(
        journalId: String,
        ownerUserId: String,
    ): GroupRotationV4SyncJournalMutationResult = mutate(
        journalId = journalId,
        ownerUserId = ownerUserId,
        expectedStatuses = listOf(
            GroupRotationV4SyncJournalStatus.PENDING,
            GroupRotationV4SyncJournalStatus.RETRYABLE_FAILURE,
        ),
        newStatus = GroupRotationV4SyncJournalStatus.IN_PROGRESS,
        lastFailureCategory = null,
        confirmedCloudRevision = null,
        attemptIncrement = 0,
    )

    override suspend fun recordRetryableFailure(
        journalId: String,
        ownerUserId: String,
        failureCategory: String,
    ): GroupRotationV4SyncJournalMutationResult = mutate(
        journalId = journalId,
        ownerUserId = ownerUserId,
        expectedStatuses = listOf(GroupRotationV4SyncJournalStatus.IN_PROGRESS),
        newStatus = GroupRotationV4SyncJournalStatus.RETRYABLE_FAILURE,
        lastFailureCategory = failureCategory,
        confirmedCloudRevision = null,
        attemptIncrement = 1,
    )

    override suspend fun recordTerminalFailure(
        journalId: String,
        ownerUserId: String,
        status: GroupRotationV4SyncJournalStatus,
        failureCategory: String,
    ): GroupRotationV4SyncJournalMutationResult {
        if (status !in setOf(
                GroupRotationV4SyncJournalStatus.TERMINAL_VALIDATION_FAILURE,
                GroupRotationV4SyncJournalStatus.TERMINAL_AUTHORIZATION_FAILURE,
                GroupRotationV4SyncJournalStatus.TERMINAL_CONFLICT,
            ) || failureCategory.isBlank()
        ) {
            return GroupRotationV4SyncJournalMutationResult.Invalid(
                GroupRotationV4SyncJournalValidationFailure.INVALID_STATUS_REQUEST,
            )
        }
        return mutate(
            journalId = journalId,
            ownerUserId = ownerUserId,
            expectedStatuses = listOf(
                GroupRotationV4SyncJournalStatus.PENDING,
                GroupRotationV4SyncJournalStatus.IN_PROGRESS,
                GroupRotationV4SyncJournalStatus.RETRYABLE_FAILURE,
            ),
            newStatus = status,
            lastFailureCategory = failureCategory,
            confirmedCloudRevision = null,
            attemptIncrement = 0,
        )
    }

    override suspend fun markConfirmed(
        journalId: String,
        ownerUserId: String,
        confirmedCloudRevision: Int,
    ): GroupRotationV4SyncJournalMutationResult {
        if (confirmedCloudRevision <= 0) {
            return GroupRotationV4SyncJournalMutationResult.Invalid(
                GroupRotationV4SyncJournalValidationFailure.INVALID_CONFIRMED_REVISION,
            )
        }
        return mutate(
            journalId = journalId,
            ownerUserId = ownerUserId,
            expectedStatuses = listOf(GroupRotationV4SyncJournalStatus.IN_PROGRESS),
            newStatus = GroupRotationV4SyncJournalStatus.CONFIRMED,
            lastFailureCategory = null,
            confirmedCloudRevision = confirmedCloudRevision,
            attemptIncrement = 0,
        )
    }

    override suspend fun recoverUnfinishedByOwner(
        ownerUserId: String,
        tournamentId: String,
    ): GroupRotationV4SyncJournalEntriesResult = mutationMutex.withLock {
        if (ownerUserId.isBlank() || tournamentId.isBlank()) {
            return@withLock GroupRotationV4SyncJournalEntriesResult.Loaded(emptyList())
        }
        database.withTransaction {
            if (deletionIntentRepository.isBlockingByTournamentIdAndOwner(tournamentId, ownerUserId)) {
                return@withTransaction GroupRotationV4SyncJournalEntriesResult.Loaded(emptyList())
            }
            val beforeRecovery = dao.readPendingOrUnresolvedByOwner(ownerUserId, tournamentId)
            readEntities(beforeRecovery).let { result ->
                if (result is GroupRotationV4SyncJournalEntriesResult.Corrupt) {
                    return@withTransaction result
                }
            }
            dao.recoverInProgressByOwner(ownerUserId, tournamentId, System.currentTimeMillis())
            readEntities(dao.readPendingOrUnresolvedByOwner(ownerUserId, tournamentId))
        }
    }

    override suspend fun invalidateByTournamentAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Int {
        if (ownerUserId.isBlank() || tournamentId.isBlank()) return 0
        return mutationMutex.withLock {
            dao.invalidateUnresolvedByTournamentAndOwner(
                tournamentId = tournamentId,
                ownerUserId = ownerUserId,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun mutate(
        journalId: String,
        ownerUserId: String,
        expectedStatuses: List<GroupRotationV4SyncJournalStatus>,
        newStatus: GroupRotationV4SyncJournalStatus,
        lastFailureCategory: String?,
        confirmedCloudRevision: Int?,
        attemptIncrement: Int,
    ): GroupRotationV4SyncJournalMutationResult = mutationMutex.withLock {
        val existing = dao.readByJournalId(journalId) ?: return@withLock GroupRotationV4SyncJournalMutationResult.NotFound
        if (existing.ownerUserId != ownerUserId) return@withLock GroupRotationV4SyncJournalMutationResult.Unauthorized
        val current = existing.toValidatedDomain()
            ?: return@withLock GroupRotationV4SyncJournalMutationResult.Corrupt(existing.journalId)
        if (current.status !in expectedStatuses) return@withLock GroupRotationV4SyncJournalMutationResult.Conflict
        val updated = dao.updateIfExpected(
            journalId = journalId,
            ownerUserId = ownerUserId,
            payloadSha256 = current.payloadSha256,
            expectedStatuses = expectedStatuses.map { it.name },
            newStatus = newStatus.name,
            lastFailureCategory = lastFailureCategory,
            confirmedCloudRevision = confirmedCloudRevision,
            attemptIncrement = attemptIncrement,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        if (updated == 0) return@withLock GroupRotationV4SyncJournalMutationResult.Conflict
        val reread = dao.readByJournalId(journalId)
            ?: return@withLock GroupRotationV4SyncJournalMutationResult.Corrupt(journalId)
        reread.toValidatedDomain()?.let(GroupRotationV4SyncJournalMutationResult::Updated)
            ?: GroupRotationV4SyncJournalMutationResult.Corrupt(journalId)
    }

    private fun validateRequest(
        request: GroupRotationV4SyncJournalRequest,
    ): GroupRotationV4SyncJournalValidationFailure? {
        if (request.ownerUserId.isBlank()) return GroupRotationV4SyncJournalValidationFailure.INVALID_OWNER
        if (request.tournamentId.isBlank()) return GroupRotationV4SyncJournalValidationFailure.INVALID_TOURNAMENT
        if (!isCanonicalUuid(request.requestId)) return GroupRotationV4SyncJournalValidationFailure.INVALID_REQUEST_ID
        if (request.payloadFormatVersion != GROUP_ROTATION_V4_JOURNAL_PAYLOAD_FORMAT_VERSION) {
            return GroupRotationV4SyncJournalValidationFailure.INVALID_PAYLOAD_FORMAT_VERSION
        }
        if (!GroupRotationV4SyncJournalPayload.isJsonObject(request.payloadJson)) {
            return GroupRotationV4SyncJournalValidationFailure.INVALID_PAYLOAD
        }
        if (
            request.expectedCloudRevision < 0 ||
            (request.operationType == GroupRotationV4SyncJournalOperationType.IDENTITY_SNAPSHOT && request.expectedCloudRevision <= 0)
        ) {
            return GroupRotationV4SyncJournalValidationFailure.INVALID_EXPECTED_REVISION
        }
        return when {
            request.operationType == GroupRotationV4SyncJournalOperationType.IDENTITY_SNAPSHOT && request.matchId != null ->
                GroupRotationV4SyncJournalValidationFailure.MATCH_ID_NOT_ALLOWED
            request.operationType != GroupRotationV4SyncJournalOperationType.IDENTITY_SNAPSHOT && request.matchId.isNullOrBlank() ->
                GroupRotationV4SyncJournalValidationFailure.MATCH_ID_REQUIRED
            else -> null
        }
    }

    private fun readEntities(
        entities: List<GroupRotationV4SyncJournalEntity>,
    ): GroupRotationV4SyncJournalEntriesResult {
        val entries = entities.map { entity ->
            entity.toValidatedDomain() ?: return GroupRotationV4SyncJournalEntriesResult.Corrupt(entity.journalId)
        }
        return GroupRotationV4SyncJournalEntriesResult.Loaded(entries)
    }

    private fun GroupRotationV4SyncJournalEntity.toValidatedDomain(): GroupRotationV4SyncJournalEntry? {
        if (ownerUserId.isBlank() || tournamentId.isBlank() || !isCanonicalUuid(requestId)) return null
        if (payloadFormatVersion != GROUP_ROTATION_V4_JOURNAL_PAYLOAD_FORMAT_VERSION) return null
        if (!GroupRotationV4SyncJournalPayload.isJsonObject(payloadJson)) return null
        if (payloadSha256 != GroupRotationV4SyncJournalPayload.sha256(payloadJson)) return null
        val operation = runCatching { GroupRotationV4SyncJournalOperationType.valueOf(operationType) }.getOrNull() ?: return null
        if (
            expectedCloudRevision < 0 ||
            (operation == GroupRotationV4SyncJournalOperationType.IDENTITY_SNAPSHOT && expectedCloudRevision <= 0) ||
            attemptCount < 0
        ) return null
        val status = runCatching { GroupRotationV4SyncJournalStatus.valueOf(status) }.getOrNull() ?: return null
        if (operation == GroupRotationV4SyncJournalOperationType.IDENTITY_SNAPSHOT && matchId != null) return null
        if (operation != GroupRotationV4SyncJournalOperationType.IDENTITY_SNAPSHOT && matchId.isNullOrBlank()) return null
        if (status == GroupRotationV4SyncJournalStatus.CONFIRMED && (confirmedCloudRevision == null || confirmedCloudRevision <= 0)) return null
        if (confirmedCloudRevision != null && confirmedCloudRevision <= 0) return null
        return GroupRotationV4SyncJournalEntry(
            journalId = journalId,
            ownerUserId = ownerUserId,
            tournamentId = tournamentId,
            matchId = matchId,
            operationType = operation,
            requestId = requestId,
            payloadJson = payloadJson,
            payloadFormatVersion = payloadFormatVersion,
            payloadSha256 = payloadSha256,
            expectedCloudRevision = expectedCloudRevision,
            status = status,
            attemptCount = attemptCount,
            lastFailureCategory = lastFailureCategory,
            confirmedCloudRevision = confirmedCloudRevision,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    private fun GroupRotationV4SyncJournalEntity.toDomain() = GroupRotationV4SyncJournalEntry(
        journalId = journalId,
        ownerUserId = ownerUserId,
        tournamentId = tournamentId,
        matchId = matchId,
        operationType = GroupRotationV4SyncJournalOperationType.valueOf(operationType),
        requestId = requestId,
        payloadJson = payloadJson,
        payloadFormatVersion = payloadFormatVersion,
        payloadSha256 = payloadSha256,
        expectedCloudRevision = expectedCloudRevision,
        status = GroupRotationV4SyncJournalStatus.valueOf(status),
        attemptCount = attemptCount,
        lastFailureCategory = lastFailureCategory,
        confirmedCloudRevision = confirmedCloudRevision,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private fun sameRequest(
        existing: GroupRotationV4SyncJournalEntry,
        request: GroupRotationV4SyncJournalRequest,
        payloadSha256: String,
    ): Boolean = existing.ownerUserId == request.ownerUserId &&
        existing.tournamentId == request.tournamentId &&
        existing.matchId == request.matchId &&
        existing.operationType == request.operationType &&
        existing.payloadJson == request.payloadJson &&
        existing.payloadFormatVersion == request.payloadFormatVersion &&
        existing.payloadSha256 == payloadSha256 &&
        existing.expectedCloudRevision == request.expectedCloudRevision

    private fun isCanonicalUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
}
