package com.hoggamers.rankforge.data.tournament

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.cloud.CloudUploadExecutionResult
import com.hoggamers.rankforge.data.cloud.SupabaseTournamentCloudUploadRepository
import com.hoggamers.rankforge.data.cloud.TournamentCloudUploadRemoteDataSource
import com.hoggamers.rankforge.data.cloud.TournamentCloudUploadPayloads
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import com.hoggamers.rankforge.domain.sync.RecordSyncQueueOutcome
import com.hoggamers.rankforge.domain.sync.SyncQueueEntry
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.UploadTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.defaultGroupPairings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupRotationInitialCloudSyncTest {
    @Test
    fun threeGroupCreationImmediatelyUploadsCompleteSnapshotThroughRoomRepository() = runBlocking {
        val snapshot = uploadInitialGroupRotationTournament(
            databaseName = "group-rotation-initial-upload-three.db",
            groupCount = 3,
        )

        assertEquals(TournamentFormat.GROUP_ROTATION, snapshot.tournament.format)
        assertEquals(3, snapshot.tournament.groupCount)
        assertEquals(defaultGroupPairings(3), snapshot.tournament.selectedGroupPairings)
        assertEquals(18, snapshot.slots.size)
        assertEquals((1..6).toList(), snapshot.slots.filter { it.group == TournamentGroup.A }.map { it.slotNumber })
        assertEquals((7..12).toList(), snapshot.slots.filter { it.group == TournamentGroup.B }.map { it.slotNumber })
        assertEquals((13..18).toList(), snapshot.slots.filter { it.group == TournamentGroup.C }.map { it.slotNumber })
        assertEquals(0, snapshot.expectedCloudRevision)
    }

    @Test
    fun fourGroupCreationImmediatelyUploadsTwentyFourSlotSnapshotThroughRoomRepository() = runBlocking {
        val snapshot = uploadInitialGroupRotationTournament(
            databaseName = "group-rotation-initial-upload-four.db",
            groupCount = 4,
        )

        assertEquals(TournamentFormat.GROUP_ROTATION, snapshot.tournament.format)
        assertEquals(4, snapshot.tournament.groupCount)
        assertEquals(defaultGroupPairings(4), snapshot.tournament.selectedGroupPairings)
        assertEquals(24, snapshot.slots.size)
        assertEquals((19..24).toList(), snapshot.slots.filter { it.group == TournamentGroup.D }.map { it.slotNumber })
        assertEquals(0, snapshot.expectedCloudRevision)
    }

    private suspend fun uploadInitialGroupRotationTournament(
        databaseName: String,
        groupCount: Int,
    ): TournamentCloudUploadSnapshot {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val database = Room.databaseBuilder(
                context,
                RankForgeDatabase::class.java,
                databaseName,
            ).build().also { databases += it }
            val repository = RoomTournamentRepository(database)
            val tournamentId = tournamentIdFor(groupCount)
            repository.create(
                Tournament(
                    id = tournamentId,
                    name = "Group Cup",
                    stageName = "Final",
                    organizerContactNumber = "1234567890",
                    status = TournamentStatus.DRAFT,
                    ownerUserId = OWNER_ID,
                    format = TournamentFormat.GROUP_ROTATION,
                    groupCount = groupCount,
                    selectedGroupPairings = defaultGroupPairings(groupCount),
                ),
            )
            val remote = RecordingCloudUploadRemoteDataSource()
            val cloud = RecordingCloudUploadRepository(
                SupabaseTournamentCloudUploadRepository(remote),
            )
            val result = UploadTournamentUseCase(
                tournamentRepository = repository,
                localSnapshotRepository = repository,
                authRepository = SignedInAuthRepository,
                cloudUploadRepository = cloud,
                queueRecorder = RecordSyncQueueOutcome(NoOpPersistentSyncQueueRepository),
            )(
                tournamentId,
            )

            assertEquals(TournamentCloudUploadResult.Success(1), result.primaryResult)
            assertNotNull(remote.payloads)
            assertEquals(1, remote.calls)
            assertEquals(0, remote.expectedRevision)
            val payloads = remote.payloads!!
            assertEquals("group_rotation", payloads.tournament.format)
            assertEquals(groupCount, payloads.tournament.groupCount)
            assertEquals(groupCount, payloads.tournament.selectedGroupPairings.size)
            assertEquals(groupCount * 6, payloads.teamSlots.size)
            assertNotNull(cloud.snapshot)
            return cloud.snapshot!!
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    private class RecordingCloudUploadRepository(
        private val delegate: TournamentCloudUploadRepository,
    ) : TournamentCloudUploadRepository {
        var snapshot: TournamentCloudUploadSnapshot? = null

        override suspend fun upload(
            snapshot: TournamentCloudUploadSnapshot,
            ownerId: String,
        ): TournamentCloudUploadResult {
            this.snapshot = snapshot
            assertEquals(OWNER_ID, ownerId)
            return delegate.upload(snapshot, ownerId)
        }
    }

    private class RecordingCloudUploadRemoteDataSource : TournamentCloudUploadRemoteDataSource {
        var calls = 0
        var payloads: TournamentCloudUploadPayloads? = null
        var expectedRevision: Int? = null

        override suspend fun upload(
            payloads: TournamentCloudUploadPayloads,
            expectedRevision: Int,
        ): CloudUploadExecutionResult {
            calls += 1
            this.payloads = payloads
            this.expectedRevision = expectedRevision
            return CloudUploadExecutionResult.Success(1)
        }
    }

    private object SignedInAuthRepository : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(
            AuthState.SignedIn(AuthUser(OWNER_ID, "owner@example.test")),
        )

        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession

        override suspend fun signUp(email: String, password: String): AuthOperationResult = error("unused")

        override suspend fun login(email: String, password: String): AuthOperationResult = error("unused")

        override suspend fun logout(): AuthOperationResult = error("unused")
    }

    private object NoOpPersistentSyncQueueRepository : PersistentSyncQueueRepository {
        override fun observeAll(): Flow<List<SyncQueueEntry>> = flowOf(emptyList())
    }

    private companion object {
        const val OWNER_ID = "22222222-2222-2222-2222-222222222222"

        fun tournamentIdFor(groupCount: Int): String = when (groupCount) {
            3 -> "11111111-1111-1111-1111-111111111113"
            4 -> "11111111-1111-1111-1111-111111111114"
            else -> error("Unsupported test group count: $groupCount")
        }
    }
}
