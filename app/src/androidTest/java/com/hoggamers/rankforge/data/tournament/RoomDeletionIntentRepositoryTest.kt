package com.hoggamers.rankforge.data.tournament

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.domain.tournament.DeletionIntent
import com.hoggamers.rankforge.domain.tournament.DeletionIntentPhase
import com.hoggamers.rankforge.domain.tournament.DeletionTargetType
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDeletionIntentRepositoryTest {
    @Test
    fun intentOperationsAreOwnerScopedAndInsertIgnorePreservesCollisionOwner() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "deletion-intent-owner-${UUID.randomUUID()}.db"
        val database = Room.databaseBuilder(context, RankForgeDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = RoomDeletionIntentRepository(database.deletionIntentDao())
            val intentA = intent(owner = "owner-a")
            val intentB = intent(owner = "owner-b")

            assertTrue(repository.startIfAbsent(intentA))
            assertFalse(repository.startIfAbsent(intentB))
            assertTrue(repository.findByTargetAndOwner(DeletionTargetType.MATCH, "match-a", "owner-a") != null)
            assertNull(repository.findByTargetAndOwner(DeletionTargetType.MATCH, "match-a", "owner-b"))
            assertTrue(repository.isBlockingByTournamentIdAndOwner("tournament-a", "owner-a"))
            assertFalse(repository.isBlockingByTournamentIdAndOwner("tournament-a", "owner-b"))
            assertFalse(repository.markRemoteDeletedByTargetAndOwner(DeletionTargetType.MATCH, "match-a", "owner-b"))
            assertTrue(repository.markRemoteDeletedByTargetAndOwner(DeletionTargetType.MATCH, "match-a", "owner-a"))
            assertTrue(repository.readPendingLocalCleanupByOwner("owner-a").isNotEmpty())
            assertTrue(repository.readPendingLocalCleanupByOwner("owner-b").isEmpty())
            assertFalse(repository.clearByTargetAndOwner(DeletionTargetType.MATCH, "match-a", "owner-b"))
            assertTrue(repository.clearByTargetAndOwner(DeletionTargetType.MATCH, "match-a", "owner-a"))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun intent(owner: String) = DeletionIntent(
        targetType = DeletionTargetType.MATCH,
        targetId = "match-a",
        tournamentId = "tournament-a",
        ownerUserId = owner,
        phase = DeletionIntentPhase.DELETE_STARTED,
        updatedAtEpochMillis = 1,
    )
}
