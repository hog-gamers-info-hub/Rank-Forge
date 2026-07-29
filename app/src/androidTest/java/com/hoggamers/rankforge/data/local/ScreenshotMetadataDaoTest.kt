package com.hoggamers.rankforge.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenshotMetadataDaoTest {
    @Test
    fun insertsObservesReplacesUpdatesAndDeletesOneMetadataRecordPerMatch() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RankForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            database.tournamentDao().upsert(
                TournamentEntity("tournament-1", "Cup", "2026-07-29", "Org", "123", "CONFIRMED"),
            )
            database.matchDao().upsert(
                MatchEntity("match-1", "tournament-1", 1, "2026-07-29", "Bermuda", "DRAFT"),
            )
            val dao = database.screenshotMetadataDao()
            val first = metadata(matchId = "match-1", revision = 1)

            dao.upsert(first)

            assertEquals(first, dao.observeByMatchId("match-1").first())
            assertEquals(listOf(first), dao.observeByTournamentId("tournament-1").first())

            val replacement = first.copy(
                localRelativePath = "screenshots/tournament/match/original.jpg",
                fileExtension = "jpg",
                mimeType = "image/jpeg",
                revision = 2,
                updatedAt = 2,
                preservedAt = 2,
            )
            dao.upsert(replacement)
            assertEquals(replacement, dao.readByMatchId("match-1"))

            dao.updateUploadSuccess(
                matchId = "match-1",
                storageBucket = "match-screenshots",
                storageObjectPath = "users/user/tournaments/tournament-1/matches/match-1/original.jpg",
                uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
                uploadedAt = 3,
                updatedAt = 3,
            )
            val uploaded = dao.readByMatchId("match-1")!!
            assertEquals(ScreenshotUploadStatus.UPLOADED.name, uploaded.uploadStatus)
            assertEquals(3, uploaded.revision)

            dao.updateUploadFailure("match-1", ScreenshotUploadStatus.FAILED.name, "NETWORK", updatedAt = 4)
            val failed = dao.readByMatchId("match-1")!!
            assertEquals(ScreenshotUploadStatus.FAILED.name, failed.uploadStatus)
            assertEquals("NETWORK", failed.uploadFailureCode)
            assertEquals(4, failed.revision)

            dao.markLocalMissing("match-1", ScreenshotLocalStatus.MISSING.name, updatedAt = 5)
            assertEquals(ScreenshotLocalStatus.MISSING.name, dao.readByMatchId("match-1")!!.localStatus)

            dao.deleteByMatchId("match-1")
            assertNull(dao.readByMatchId("match-1"))
        } finally {
            database.close()
        }
    }

    @Test
    fun matchDeletionCascadesScreenshotMetadata() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RankForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            database.tournamentDao().upsert(
                TournamentEntity("tournament-1", "Cup", "2026-07-29", "Org", "123", "CONFIRMED"),
            )
            database.matchDao().upsert(
                MatchEntity("match-1", "tournament-1", 1, "2026-07-29", "Bermuda", "DRAFT"),
            )
            database.screenshotMetadataDao().upsert(metadata(matchId = "match-1"))

            database.matchDao().deleteByTournamentId("tournament-1")

            assertTrue(database.screenshotMetadataDao().observeByTournamentId("tournament-1").first().isEmpty())
        } finally {
            database.close()
        }
    }

    private fun metadata(
        matchId: String,
        revision: Long = 1,
    ) = ScreenshotMetadataEntity(
        matchId = matchId,
        tournamentId = "tournament-1",
        ownerUserId = "owner-1",
        localRelativePath = "screenshots/tournament/match/original.png",
        fileExtension = "png",
        mimeType = "image/png",
        width = 1080,
        height = 1920,
        byteSize = 3,
        sha256 = "a".repeat(64),
        storageBucket = null,
        storageObjectPath = null,
        localStatus = ScreenshotLocalStatus.PRESERVED.name,
        uploadStatus = ScreenshotUploadStatus.PENDING.name,
        uploadFailureCode = null,
        createdAt = 1,
        updatedAt = 1,
        preservedAt = 1,
        uploadedAt = null,
        revision = revision,
    )
}
