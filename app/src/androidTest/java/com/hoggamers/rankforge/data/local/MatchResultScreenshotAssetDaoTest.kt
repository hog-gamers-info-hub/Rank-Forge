package com.hoggamers.rankforge.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.OcrScreenshotKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchResultScreenshotAssetDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun daoStoresUpperAndLowerAssetsIndependentlyWithDeterministicOrderingAndCascadeDelete() = runBlocking {
        val database = createDatabase()
        try {
            insertTournamentAndMatches(database)
            val dao = database.matchResultScreenshotAssetDao()
            val lower = asset(
                role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                sha256 = "b".repeat(64),
            )
            val upper = asset(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                sha256 = "a".repeat(64),
            )

            dao.upsert(lower)
            dao.upsert(upper)

            assertEquals(
                listOf(
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER.name,
                    MatchResultScreenshotRole.MATCH_RESULT_LOWER.name,
                ),
                dao.observeByMatchId("match-1").first().map { it.screenshotRole },
            )

            assertEquals(
                lower,
                dao.readDuplicateFingerprint(
                    sha256 = lower.sha256,
                    matchId = "match-1",
                    screenshotRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER.name,
                ),
            )

            dao.upsert(
                upper.copy(
                    localRelativePath = "screenshots/new-upper.png",
                    revision = 2,
                ),
            )

            assertEquals(
                "screenshots/new-upper.png",
                dao.readByMatchAndRole(
                    "match-1",
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER.name,
                )?.localRelativePath,
            )

            assertEquals(
                lower.localRelativePath,
                dao.readByMatchAndRole(
                    "match-1",
                    MatchResultScreenshotRole.MATCH_RESULT_LOWER.name,
                )?.localRelativePath,
            )

            database.matchDao().deleteByTournamentId("tournament-1")

            assertTrue(
                dao.observeByTournamentId("tournament-1")
                    .first()
                    .isEmpty(),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun matchResultScreenshotAssetTableHasRequiredIndexes() {
        val database = createDatabase()
        try {
            val indexNames = mutableSetOf<String>()

            database.openHelper.readableDatabase
                .query("PRAGMA index_list(`match_result_screenshot_assets`)")
                .use { cursor ->
                    val nameColumnIndex = cursor.getColumnIndexOrThrow("name")

                    while (cursor.moveToNext()) {
                        indexNames += cursor.getString(nameColumnIndex)
                    }
                }

            assertTrue(
                indexNames.contains(
                    "index_match_result_screenshot_assets_tournament_id",
                ),
            )
            assertTrue(
                indexNames.contains(
                    "index_match_result_screenshot_assets_sha256",
                ),
            )
            assertTrue(
                indexNames.contains(
                    "index_match_result_screenshot_assets_upload_status",
                ),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun generationCasRejectsStaleRevisionWithoutChangingTheAsset() = runBlocking {
        val database = createDatabase()
        try {
            insertTournamentAndMatches(database)
            val dao = database.matchResultScreenshotAssetDao()
            val stored = asset(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                sha256 = "f".repeat(64),
            )
            dao.upsert(stored)

            assertEquals(
                0,
                dao.updateUploadSuccessIfGenerationMatches(
                    tournamentId = stored.tournamentId,
                    matchId = stored.matchId,
                    screenshotRole = stored.screenshotRole,
                    sha256 = stored.sha256,
                    expectedRevision = stored.revision + 1,
                    storageBucket = "ocr-screenshots",
                    storageObjectPath = "stale/path.png",
                    uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
                    uploadedAt = 2,
                    updatedAt = 2,
                ),
            )
            assertEquals(
                stored,
                dao.readByMatchAndRole(stored.matchId, stored.screenshotRole),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun repositoryProtectsDuplicateFingerprintsAndPersistsCropPerRole() = runBlocking {
        val database = createDatabase()
        try {
            insertTournamentAndMatches(database)

            val repository = RoomMatchResultScreenshotAssetRepository(
                database.matchResultScreenshotAssetDao(),
            )

            val upperIdentity = identity(
                "match-1",
                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            )
            val lowerIdentity = identity(
                "match-1",
                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            )

            val upper = asset(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                sha256 = "a".repeat(64),
            )
            val lower = asset(
                role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                sha256 = "b".repeat(64),
            )

            assertEquals(
                MatchResultScreenshotAssetSaveResult.Saved,
                repository.saveOrReplace(upper),
            )
            assertEquals(
                MatchResultScreenshotAssetSaveResult.Saved,
                repository.saveOrReplace(lower),
            )

            assertEquals(
                MatchResultScreenshotAssetSaveResult.DuplicateFingerprint(upper),
                repository.saveOrReplace(
                    asset(
                        matchId = "match-1",
                        role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                        sha256 = upper.sha256,
                    ),
                ),
            )
            assertEquals(
                MatchResultScreenshotAssetSaveResult.Saved,
                repository.saveOrReplace(
                    asset(
                        matchId = "match-2",
                        role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                        sha256 = upper.sha256,
                    ),
                ),
            )

            assertEquals(
                MatchResultScreenshotCropSaveResult.Saved,
                repository.persistConfirmedCrop(
                    upperIdentity,
                    OcrNormalizedCropRect(
                        left = 0.10,
                        top = 0.10,
                        right = 0.90,
                        bottom = 0.90,
                    ),
                    updatedAt = 2,
                ),
            )

            assertEquals(
                MatchResultScreenshotCropSaveResult.InvalidCrop,
                repository.persistConfirmedCrop(
                    lowerIdentity,
                    OcrNormalizedCropRect(
                        left = 0.10,
                        top = 0.10,
                        right = 0.11,
                        bottom = 0.11,
                    ),
                    updatedAt = 3,
                ),
            )

            assertEquals(
                OcrCropValidationProfiles.MatchResult.id,
                repository.getByIdentity(upperIdentity)?.cropProfileId,
            )
            assertNull(
                repository.getByIdentity(lowerIdentity)?.cropProfileId,
            )

            assertEquals(
                MatchResultScreenshotAssetSaveResult.Saved,
                repository.saveOrReplace(
                    upper.copy(
                        sha256 = "c".repeat(64),
                        updatedAt = 4,
                        revision = 4,
                    ),
                ),
            )

            assertNull(
                repository.getByIdentity(upperIdentity)?.cropProfileId,
            )
            assertEquals(
                lower,
                repository.getByIdentity(lowerIdentity),
            )
        } finally {
            database.close()
        }
    }

    private fun createDatabase(): RankForgeDatabase =
        Room.inMemoryDatabaseBuilder(
            context,
            RankForgeDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

    private suspend fun insertTournamentAndMatches(
        database: RankForgeDatabase,
    ) {
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

        database.matchDao().upsert(
            MatchEntity(
                id = "match-1",
                tournamentId = "tournament-1",
                matchNumber = 1,
                date = "2026-08-07",
                mapName = "Bermuda",
                status = "DRAFT",
            ),
        )

        database.matchDao().upsert(
            MatchEntity(
                id = "match-2",
                tournamentId = "tournament-1",
                matchNumber = 2,
                date = "2026-08-07",
                mapName = "Purgatory",
                status = "DRAFT",
            ),
        )
    }

    private fun identity(
        matchId: String,
        role: MatchResultScreenshotRole,
    ) = MatchResultScreenshotIdentity(
        tournamentId = "tournament-1",
        matchId = matchId,
        role = role,
    )

    private fun asset(
        matchId: String = "match-1",
        role: MatchResultScreenshotRole,
        sha256: String,
    ) = MatchResultScreenshotAssetEntity(
        tournamentId = "tournament-1",
        matchId = matchId,
        screenshotKind = OcrScreenshotKind.MATCH_RESULT.name,
        screenshotRole = role.name,
        ownerUserId = "owner-1",
        localRelativePath =
            "screenshots/tournament-1/$matchId/result/${role.name.lowercase()}/original.png",
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 1600,
        originalHeight = 720,
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
