package com.hoggamers.rankforge.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchLobbyScreenshotAssetDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun lobbyAssetsPersistIndependentlyInDeterministicOrderAndCascadeWithMatch() = runBlocking {
        val database = createDatabase()
        try {
            insertTournamentAndMatches(database)
            val dao = database.matchLobbyScreenshotAssetDao()
            val assets = (1..3).map { index -> asset(index = index, sha256 = index.toString().repeat(64)) }
            assets.forEach { dao.upsert(it) }
            val matchTwoAsset = asset(
                matchId = "match-2",
                index = 1,
                sha256 = "4".repeat(64),
            )
            dao.upsert(matchTwoAsset)

            assertEquals(
                listOf(1, 2, 3),
                dao.observeByMatchId("match-1").first().map { it.lobbyScreenshotIndex },
            )
            assertEquals(assets[1], dao.readByMatchAndIndex("match-1", 2))
            assertEquals(
                listOf("match-1:1", "match-1:2", "match-1:3", "match-2:1"),
                dao.readByTournamentId("tournament-1").map { "${it.matchId}:${it.lobbyScreenshotIndex}" },
            )

            dao.upsert(assets[0].copy(localRelativePath = "replacement.png", revision = 2))
            assertEquals("replacement.png", dao.readByMatchAndIndex("match-1", 1)?.localRelativePath)
            assertEquals(assets[1], dao.readByMatchAndIndex("match-1", 2))

            database.openHelper.writableDatabase.execSQL("DELETE FROM matches WHERE id = 'match-1'")
            assertTrue(dao.observeByMatchId("match-1").first().isEmpty())
            assertEquals(listOf(matchTwoAsset), dao.observeByMatchId("match-2").first())

            database.openHelper.writableDatabase.execSQL("DELETE FROM matches WHERE id = 'match-2'")
            assertTrue(dao.observeByTournamentId("tournament-1").first().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun repositoryProtectsDuplicateFingerprintsAndClearsCropOnDifferentShaReplacement() = runBlocking {
        val database = createDatabase()
        try {
            insertTournamentAndMatches(database)
            val repository = RoomMatchLobbyScreenshotAssetRepository(database.matchLobbyScreenshotAssetDao())
            val first = asset(index = 1, sha256 = "a".repeat(64)).copy(
                cropProfileId = "lobby-profile",
                cropLeft = 0.1,
                cropTop = 0.1,
                cropRight = 0.9,
                cropBottom = 0.9,
            )

            assertEquals(MatchLobbyScreenshotAssetSaveResult.Saved, repository.saveOrReplace(first))
            assertEquals(
                MatchLobbyScreenshotAssetSaveResult.DuplicateFingerprint(first),
                repository.saveOrReplace(asset(matchId = "match-2", index = 1, sha256 = first.sha256)),
            )
            assertEquals(
                MatchLobbyScreenshotAssetSaveResult.Saved,
                repository.saveOrReplace(asset(index = 1, sha256 = "b".repeat(64))),
            )
            val replaced = repository.getByIdentity(MatchLobbyScreenshotIdentity("tournament-1", "match-1", 1))
            assertNull(replaced?.cropProfileId)
            assertNull(replaced?.cropLeft)
            assertNull(replaced?.cropTop)
            assertNull(replaced?.cropRight)
            assertNull(replaced?.cropBottom)
            assertEquals(
                MatchLobbyScreenshotAssetSaveResult.Saved,
                repository.saveOrReplace(asset(tournamentId = "tournament-2", matchId = "match-3", index = 1, sha256 = "b".repeat(64))),
            )
            assertEquals(
                MatchLobbyScreenshotAssetSaveResult.InvalidIdentity,
                repository.saveOrReplace(asset(index = 4, sha256 = "c".repeat(64))),
            )
            assertEquals(
                MatchLobbyScreenshotAssetSaveResult.InvalidIdentity,
                repository.saveOrReplace(asset(tournamentId = "", index = 1, sha256 = "d".repeat(64))),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun lobbyAssetTableHasRequiredIndexes() {
        val database = createDatabase()
        try {
            val indexNames = mutableSetOf<String>()
            database.openHelper.readableDatabase
                .query("PRAGMA index_list(`match_lobby_screenshot_assets`)")
                .use { cursor ->
                    val nameColumnIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) indexNames += cursor.getString(nameColumnIndex)
                }

            assertTrue(indexNames.contains("index_match_lobby_screenshot_assets_tournament_id"))
            assertTrue(indexNames.contains("index_match_lobby_screenshot_assets_sha256"))
            assertTrue(indexNames.contains("index_match_lobby_screenshot_assets_upload_status"))
        } finally {
            database.close()
        }
    }

    private fun createDatabase(): RankForgeDatabase =
        Room.inMemoryDatabaseBuilder(context, RankForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private suspend fun insertTournamentAndMatches(database: RankForgeDatabase) {
        database.tournamentDao().upsert(
            TournamentEntity(
                id = "tournament-1",
                name = "Cup",
                date = "2026-08-07",
                organizerName = "Org",
                organizerContactNumber = "123",
                status = "CONFIRMED",
            ),
        )
        database.tournamentDao().upsert(
            TournamentEntity(
                id = "tournament-2",
                name = "Other Cup",
                date = "2026-08-08",
                organizerName = "Org",
                organizerContactNumber = "123",
                status = "CONFIRMED",
            ),
        )
        listOf(
            MatchEntity("match-1", "tournament-1", 1, "2026-08-07", "Bermuda", "DRAFT"),
            MatchEntity("match-2", "tournament-1", 2, "2026-08-07", "Purgatory", "DRAFT"),
            MatchEntity("match-3", "tournament-2", 1, "2026-08-08", "Bermuda", "DRAFT"),
        ).forEach { database.matchDao().upsert(it) }
    }

    private fun asset(
        tournamentId: String = "tournament-1",
        matchId: String = "match-1",
        index: Int,
        sha256: String,
    ) = MatchLobbyScreenshotAssetEntity(
        tournamentId = tournamentId,
        matchId = matchId,
        lobbyScreenshotIndex = index,
        ownerUserId = "owner-1",
        localRelativePath = "screenshots/$tournamentId/$matchId/lobby/$index/original.png",
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 1600,
        originalHeight = 900,
        byteSize = 4,
        sha256 = sha256,
        localStatus = ScreenshotLocalStatus.PRESERVED.name,
        uploadStatus = ScreenshotUploadStatus.PENDING.name,
        uploadFailureCode = null,
        storageBucket = null,
        storageObjectPath = null,
        cropProfileId = null,
        cropLeft = null,
        cropTop = null,
        cropRight = null,
        cropBottom = null,
        createdAt = 1,
        updatedAt = 1,
        preservedAt = 1,
        uploadedAt = null,
        revision = 1,
    )
}
