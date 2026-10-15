package com.hoggamers.rankforge.data.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalCreateResult
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalEntriesResult
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalMutationResult
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalOperationType
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalReadResult
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalRequest
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalStatus
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalValidationFailure
import com.hoggamers.rankforge.domain.tournament.DeletionIntentRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupRotationV4SyncJournalRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: RankForgeDatabase
    private lateinit var repository: RoomGroupRotationV4SyncJournalRepository

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(DATABASE_NAME)
        openDatabase()
        database.tournamentDao().upsert(
            com.hoggamers.rankforge.data.local.TournamentEntity(
                id = TOURNAMENT_ID,
                name = "UUID journal tournament",
                stageName = "Stage",
                organizerContactNumber = "123",
                status = "DRAFT",
                ownerUserId = OWNER_A,
                format = "GROUP_ROTATION",
                groupCount = 3,
            ),
        )
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun pendingOperationPersistsFrozenPayloadRequestAndRevisionAcrossReopen() = runBlocking {
        val request = identityRequest()
        val created = repository.createPending(request).created()

        database.close()
        openDatabase()

        val reread = repository.readByJournalIdAndOwner(created.journalId, OWNER_A).found()
        assertEquals(created.journalId, reread.journalId)
        assertEquals(request.requestId, reread.requestId)
        assertEquals(request.payloadJson, reread.payloadJson)
        assertEquals(request.expectedCloudRevision, reread.expectedCloudRevision)
        assertEquals(created.payloadSha256, reread.payloadSha256)
    }

    @Test
    fun identicalRequestIsReusedButDifferentPayloadWithSameRequestIdIsRejected() = runBlocking {
        val request = identityRequest()
        val first = repository.createPending(request).created()
        val reused = repository.createPending(request.copy(createdAtEpochMillis = 999L))
        val changed = repository.createPending(request.copy(payloadJson = "{\"changed\":true}"))

        assertEquals(first.journalId, reused.reused().journalId)
        assertEquals(
            GroupRotationV4SyncJournalValidationFailure.REQUEST_ID_REUSED_WITH_DIFFERENT_PAYLOAD,
            changed.invalidReason(),
        )
    }

    @Test
    fun retryableFailureAndInterruptedInProgressOperationRemainRecoverable() = runBlocking {
        val created = repository.createPending(matchDraftRequest()).created()
        repository.markInProgress(created.journalId, OWNER_A).updated()

        database.close()
        openDatabase()

        val recovered = repository.recoverUnfinishedByOwner(OWNER_A, TOURNAMENT_ID).loaded().single()
        assertEquals(GroupRotationV4SyncJournalStatus.RETRYABLE_FAILURE, recovered.status)
        assertEquals(1, recovered.attemptCount)
        assertEquals(created.requestId, recovered.requestId)
        assertEquals(created.payloadJson, recovered.payloadJson)
        assertEquals(created.expectedCloudRevision, recovered.expectedCloudRevision)

        repository.markInProgress(created.journalId, OWNER_A).updated()
        val failed = repository.recordRetryableFailure(created.journalId, OWNER_A, "network").updated()
        assertEquals(2, failed.attemptCount)
        val confirmed = repository.markInProgress(created.journalId, OWNER_A).updated()
            .let { repository.markConfirmed(it.journalId, OWNER_A, 8).updated() }
        assertEquals(GroupRotationV4SyncJournalStatus.CONFIRMED, confirmed.status)
        assertEquals(8, confirmed.confirmedCloudRevision)
    }

    @Test
    fun staleStatusMutationAndInvalidConfirmationAreRejected() = runBlocking {
        val created = repository.createPending(identityRequest()).created()

        val invalidConfirmation = repository.markConfirmed(created.journalId, OWNER_A, 0)
        assertEquals(
            GroupRotationV4SyncJournalValidationFailure.INVALID_CONFIRMED_REVISION,
            (invalidConfirmation as GroupRotationV4SyncJournalMutationResult.Invalid).reason,
        )
        repository.markInProgress(created.journalId, OWNER_A).updated()
        repository.markConfirmed(created.journalId, OWNER_A, 4).updated()

        assertEquals(GroupRotationV4SyncJournalMutationResult.Conflict, repository.markInProgress(created.journalId, OWNER_A))
        assertEquals(GroupRotationV4SyncJournalMutationResult.Conflict, repository.recordRetryableFailure(created.journalId, OWNER_A, "late"))
    }

    @Test
    fun ownerScopeRejectsForeignReadsMutationsAndRequestReuse() = runBlocking {
        val created = repository.createPending(identityRequest()).created()

        assertEquals(
            GroupRotationV4SyncJournalCreateResult.Unauthorized,
            repository.createPending(identityRequest(ownerUserId = OWNER_B)),
        )
        assertEquals(
            GroupRotationV4SyncJournalReadResult.Unauthorized,
            repository.readByJournalIdAndOwner(created.journalId, OWNER_B),
        )
        assertEquals(
            GroupRotationV4SyncJournalMutationResult.Unauthorized,
            repository.markInProgress(created.journalId, OWNER_B),
        )
    }

    @Test
    fun separateSequentialOperationsRemainDistinct() = runBlocking {
        val first = repository.createPending(identityRequest()).created()
        val second = repository.createPending(matchDraftRequest()).created()

        val unresolved = repository.readPendingOrUnresolvedByOwner(OWNER_A, TOURNAMENT_ID).loaded()
        assertEquals(2, unresolved.size)
        assertTrue(first.journalId != second.journalId)
        assertTrue(first.requestId != second.requestId)
    }

    @Test
    fun corruptPayloadHashFailsClosed() = runBlocking {
        val created = repository.createPending(identityRequest()).created()
        database.openHelper.writableDatabase.execSQL(
            "UPDATE group_rotation_v4_sync_journal SET payload_sha256 = 'corrupt' WHERE journal_id = ?",
            arrayOf(created.journalId),
        )

        assertEquals(
            GroupRotationV4SyncJournalReadResult.Corrupt(created.journalId),
            repository.readByJournalIdAndOwner(created.journalId, OWNER_A),
        )
        assertEquals(
            GroupRotationV4SyncJournalMutationResult.Corrupt(created.journalId),
            repository.markInProgress(created.journalId, OWNER_A),
        )
    }

    @Test
    fun deletionBlockedCreationIsRejectedAndExplicitInvalidationIsOwnerScoped() = runBlocking {
        val blockingRepository = RoomGroupRotationV4SyncJournalRepository(
            database = database,
            dao = database.groupRotationV4SyncJournalDao(),
            deletionIntentRepository = object : DeletionIntentRepository {
                override suspend fun isBlockingByTournamentIdAndOwner(tournamentId: String, ownerUserId: String): Boolean =
                    tournamentId == TOURNAMENT_ID && ownerUserId == OWNER_A
            },
        )
        assertEquals(
            GroupRotationV4SyncJournalCreateResult.DeletionBlocked,
            blockingRepository.createPending(identityRequest()),
        )

        val created = repository.createPending(identityRequest()).created()
        assertEquals(1, repository.invalidateByTournamentAndOwner(TOURNAMENT_ID, OWNER_A))
        assertEquals(
            GroupRotationV4SyncJournalStatus.INVALIDATED,
            repository.readByJournalIdAndOwner(created.journalId, OWNER_A).found().status,
        )
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(
            context,
            RankForgeDatabase::class.java,
            DATABASE_NAME,
        ).allowMainThreadQueries().build()
        repository = RoomGroupRotationV4SyncJournalRepository(database)
    }

    private fun identityRequest(ownerUserId: String = OWNER_A) = GroupRotationV4SyncJournalRequest(
        ownerUserId = ownerUserId,
        tournamentId = TOURNAMENT_ID,
        matchId = null,
        operationType = GroupRotationV4SyncJournalOperationType.IDENTITY_SNAPSHOT,
        requestId = REQUEST_ID_IDENTITY,
        payloadJson = "{\"p_tournament_id\":\"$TOURNAMENT_ID\",\"p_expected_revision\":4}",
        expectedCloudRevision = 4,
        createdAtEpochMillis = 1_000L,
    )

    private fun matchDraftRequest() = GroupRotationV4SyncJournalRequest(
        ownerUserId = OWNER_A,
        tournamentId = TOURNAMENT_ID,
        matchId = MATCH_ID,
        operationType = GroupRotationV4SyncJournalOperationType.MATCH_DRAFT,
        requestId = REQUEST_ID_MATCH,
        payloadJson = "{\"p_tournament_id\":\"$TOURNAMENT_ID\",\"p_match\":{\"id\":\"$MATCH_ID\"},\"p_expected_revision\":5}",
        expectedCloudRevision = 5,
        createdAtEpochMillis = 2_000L,
    )

    private fun GroupRotationV4SyncJournalCreateResult.created() =
        (this as GroupRotationV4SyncJournalCreateResult.Created).entry

    private fun GroupRotationV4SyncJournalCreateResult.reused() =
        (this as GroupRotationV4SyncJournalCreateResult.Reused).entry

    private fun GroupRotationV4SyncJournalCreateResult.invalidReason() =
        (this as GroupRotationV4SyncJournalCreateResult.Invalid).reason

    private fun GroupRotationV4SyncJournalReadResult.found() =
        (this as GroupRotationV4SyncJournalReadResult.Found).entry

    private fun GroupRotationV4SyncJournalEntriesResult.loaded() =
        (this as GroupRotationV4SyncJournalEntriesResult.Loaded).entries

    private fun GroupRotationV4SyncJournalMutationResult.updated() =
        (this as GroupRotationV4SyncJournalMutationResult.Updated).entry

    private companion object {
        const val DATABASE_NAME = "group-rotation-v4-journal-test.db"
        const val OWNER_A = "owner-a"
        const val OWNER_B = "owner-b"
        const val TOURNAMENT_ID = "tournament-v4-journal"
        const val MATCH_ID = "match-v4-journal"
        const val REQUEST_ID_IDENTITY = "00000000-0000-4000-8000-000000000001"
        const val REQUEST_ID_MATCH = "00000000-0000-4000-8000-000000000002"
    }
}
