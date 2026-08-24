package com.hoggamers.rankforge.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TournamentLobbyTemplateAssetDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun ownerJoinedReadsAndMutationsIsolateTemplatesAndLegacyRowsReconcileByParentOnly() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, RankForgeDatabase::class.java).build()
        try {
            val tournamentDao = database.tournamentDao()
            tournamentDao.upsert(tournament("tournament-a", "owner-a"))
            tournamentDao.upsert(tournament("tournament-b", "owner-b"))
            tournamentDao.upsert(tournament("tournament-legacy", null))
            val dao = database.tournamentLobbyTemplateAssetDao()
            dao.upsertAll(
                listOf(
                    template("tournament-a", "owner-a", 1),
                    template("tournament-b", "owner-b", 1),
                    template("tournament-legacy", "legacy-child", 1),
                ),
            )
            val repository = RoomTournamentLobbyTemplateAssetRepository(dao, database)

            assertEquals(listOf("tournament-a"), repository.getByTournamentIdAndOwner("tournament-a", "owner-a").map { it.tournamentId })
            assertTrue(repository.getByTournamentIdAndOwner("tournament-a", "owner-b").isEmpty())
            assertTrue(repository.getByTournamentIdAndOwner("tournament-legacy", "legacy-child").isEmpty())
            assertFalse(repository.replaceForTournamentByOwner("tournament-a", "owner-b", listOf(template("tournament-a", "owner-b", 2))))
            assertEquals(1, repository.getByTournamentIdAndOwner("tournament-a", "owner-a").single().lobbyScreenshotIndex)
            assertTrue(repository.replaceForTournamentByOwner("tournament-a", "owner-a", listOf(template("tournament-a", "owner-a", 2))))
            assertEquals(2, repository.getByTournamentIdAndOwner("tournament-a", "owner-a").single().lobbyScreenshotIndex)
            assertFalse(repository.deleteByTournamentIdAndOwner("tournament-a", "owner-b"))
            assertEquals(1, dao.observeByTournamentId("tournament-a").first().size)

            tournamentDao.assignOwnerIfUnassigned("tournament-legacy", "owner-a")
            assertEquals(1, repository.getByTournamentIdAndOwner("tournament-legacy", "owner-a").size)
        } finally {
            database.close()
        }
    }

    private fun tournament(id: String, owner: String?) = TournamentEntity(
        id = id,
        name = id,
        date = "2026-01-01",
        organizerName = "organizer",
        organizerContactNumber = "contact",
        status = "DRAFT",
        ownerUserId = owner,
    )

    private fun template(tournamentId: String, owner: String, index: Int) = TournamentLobbyTemplateAssetEntity(
        tournamentId = tournamentId,
        lobbyScreenshotIndex = index,
        ownerUserId = owner,
        localRelativePath = "templates/$tournamentId/$index.png",
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 100,
        originalHeight = 100,
        byteSize = 1,
        sha256 = index.toString().repeat(64),
        cropProfileId = "lobby",
        cropLeft = 0.1,
        cropTop = 0.1,
        cropRight = 0.9,
        cropBottom = 0.9,
        sourceMatchId = "source-$tournamentId",
        savedAt = 1,
        updatedAt = 1,
        revision = 1,
    )
}
