package com.hoggamers.rankforge.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreRoomDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: RankForgeDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
        database = openDatabase()
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun stateDaoReadsEmptySavesReplacesAndRereadsPersistedPayload() = runBlocking {
        val dao = database.stateDao()

        assertNull(dao.readPayload())
        dao.save(RankForgeStateEntity(payload = "first"))
        assertEquals("first", dao.readPayload())

        dao.save(RankForgeStateEntity(payload = "replacement"))
        database.close()
        database = openDatabase()

        assertEquals("replacement", database.stateDao().readPayload())
    }

    @Test
    fun syncRevisionDaoReadsMissingUpsertsReplacesIncrementsAndRereads() = runBlocking {
        val dao = database.syncRevisionDao()

        assertNull(dao.readByTournamentId("missing-tournament"))
        dao.upsert(SyncRevisionEntity("tournament-1", 1, null))
        assertEquals(
            SyncRevisionEntity("tournament-1", 1, null),
            dao.readByTournamentId("tournament-1"),
        )

        dao.upsert(SyncRevisionEntity("tournament-1", 4, 3))
        dao.incrementLocalRevision("tournament-1")
        database.close()
        database = openDatabase()

        assertEquals(
            SyncRevisionEntity("tournament-1", 5, 3),
            database.syncRevisionDao().readByTournamentId("tournament-1"),
        )
    }

    @Test
    fun syncQueueDaoAppliesOrderingFilteringUpdatesAttemptsDeletionAndPersistence() = runBlocking {
        val dao = database.syncQueueDao()
        val tournamentOperation = SyncQueueOperationType.TOURNAMENT_UPLOAD.name
        val otherOperation = SyncQueueOperationType.MATCH_RESTORATION.name
        val entries = listOf(
            SyncQueueEntity(
                id = "global-old",
                operationType = tournamentOperation,
                tournamentId = null,
                createdAtEpochMillis = 100,
                status = SyncQueueStatus.BLOCKED_NETWORK.name,
                failureCategory = "network",
                attemptCount = 0,
                ownerUserId = OWNER_ID,
            ),
            SyncQueueEntity(
                id = "completed",
                operationType = tournamentOperation,
                tournamentId = "tournament-1",
                createdAtEpochMillis = 150,
                status = SyncQueueStatus.COMPLETED.name,
                failureCategory = null,
                attemptCount = 2,
                ownerUserId = OWNER_ID,
            ),
            SyncQueueEntity(
                id = "tournament-old",
                operationType = tournamentOperation,
                tournamentId = "tournament-1",
                createdAtEpochMillis = 200,
                status = SyncQueueStatus.BLOCKED_NETWORK.name,
                failureCategory = "network",
                attemptCount = 1,
                ownerUserId = OWNER_ID,
            ),
            SyncQueueEntity(
                id = "tournament-new",
                operationType = tournamentOperation,
                tournamentId = "tournament-1",
                createdAtEpochMillis = 300,
                status = SyncQueueStatus.FAILED_UNKNOWN.name,
                failureCategory = "unknown",
                attemptCount = 0,
                ownerUserId = OWNER_ID,
            ),
            SyncQueueEntity(
                id = "other-operation",
                operationType = otherOperation,
                tournamentId = "tournament-1",
                createdAtEpochMillis = 400,
                status = SyncQueueStatus.BLOCKED_NETWORK.name,
                failureCategory = "network",
                attemptCount = 0,
                ownerUserId = OWNER_ID,
            ),
        )
        entries.forEach { entry -> dao.insert(entry) }

        assertEquals(
            entries.map { it.id },
            dao.observeAll().first().map { it.id },
        )
        assertEquals(
            "tournament-old",
            dao.findOldestUnresolvedByOwner(OWNER_ID, tournamentOperation, "tournament-1")?.id,
        )
        assertEquals(
            "global-old",
            dao.findOldestUnresolvedByOwner(OWNER_ID, tournamentOperation, null)?.id,
        )
        assertNull(dao.findOldestUnresolvedByOwner(OWNER_ID, tournamentOperation, "missing-tournament"))

        dao.updateStatusByIdAndOwner(
            id = "tournament-old",
            ownerUserId = OWNER_ID,
            status = SyncQueueStatus.FAILED_UNKNOWN.name,
            failureCategory = "retry_unknown",
        )
        dao.incrementAttemptCountByIdAndOwner("tournament-old", OWNER_ID)
        val updated = dao.observeAll().first().single { it.id == "tournament-old" }
        assertEquals(SyncQueueStatus.FAILED_UNKNOWN.name, updated.status)
        assertEquals("retry_unknown", updated.failureCategory)
        assertEquals(2, updated.attemptCount)

        dao.deleteByIdAndOwner("tournament-old", OWNER_ID)
        database.close()
        database = openDatabase()

        val reread = database.syncQueueDao().observeAll().first()
        assertTrue(reread.none { it.id == "tournament-old" })
        assertEquals(4, reread.size)
        assertEquals("completed", reread.first { it.id == "completed" }.id)
    }

    @Test
    fun retryableSyncQueueEntryPreservesIdentityAndRetryStateAcrossDatabaseReopen() = runBlocking {
        val operationType = SyncQueueOperationType.FINALIZED_MATCH_SYNC.name
        val entry = SyncQueueEntity(
            id = "recovery-entry",
            operationType = operationType,
            tournamentId = "tournament-recovery",
            createdAtEpochMillis = 1_234,
            status = SyncQueueStatus.BLOCKED_NETWORK.name,
            failureCategory = "network_unavailable",
            attemptCount = 1,
            ownerUserId = OWNER_ID,
        )
        val dao = database.syncQueueDao()

        dao.insert(entry)
        dao.incrementAttemptCountByIdAndOwner(entry.id, OWNER_ID)
        dao.updateStatusByIdAndOwner(
            id = entry.id,
            ownerUserId = OWNER_ID,
            status = SyncQueueStatus.BLOCKED_NETWORK.name,
            failureCategory = "retry_interrupted",
        )

        database.close()
        database = openDatabase()

        val reread = database.syncQueueDao().observeAll().first().single { it.id == entry.id }
        assertEquals(entry.id, reread.id)
        assertEquals(operationType, reread.operationType)
        assertEquals(entry.tournamentId, reread.tournamentId)
        assertEquals(entry.createdAtEpochMillis, reread.createdAtEpochMillis)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK.name, reread.status)
        assertEquals("retry_interrupted", reread.failureCategory)
        assertEquals(2, reread.attemptCount)
        assertEquals(entry.id, database.syncQueueDao().findOldestUnresolvedByOwner(OWNER_ID, operationType, entry.tournamentId)?.id)
    }

    private fun openDatabase(): RankForgeDatabase = Room.databaseBuilder(
        context,
        RankForgeDatabase::class.java,
        DATABASE_NAME,
    )
        .allowMainThreadQueries()
        .build()

    private companion object {
        const val DATABASE_NAME = "core-room-dao-test.db"
        const val OWNER_ID = "owner-a"
    }
}
