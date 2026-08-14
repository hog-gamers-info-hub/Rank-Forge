package com.hoggamers.rankforge.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.ocr.matchresult.MATCH_RESULT_OCR_CACHE_PIPELINE_VERSION
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchResultOcrCacheDaoTest {
    private lateinit var database: RankForgeDatabase

    @Before
    fun setUp() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, RankForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.tournamentDao().upsert(
            TournamentEntity("tournament-1", "Cache Cup", "2026-08-14", "Organizer", "123", "DRAFT"),
        )
        database.matchDao().upsert(
            MatchEntity("match-1", "tournament-1", 1, "2026-08-14", "Bermuda", "DRAFT"),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun rolesPersistIndependentlyReplaceIndependentlyAndCascadeWithMatch() = runBlocking {
        val dao = database.matchResultOcrCacheDao()
        val upper = cache(MatchResultScreenshotRole.MATCH_RESULT_UPPER, payload = "upper-v1")
        val lower = cache(MatchResultScreenshotRole.MATCH_RESULT_LOWER, payload = "lower-v1")

        dao.upsert(upper)
        dao.upsert(lower)

        assertEquals(upper, dao.readByMatchAndRole("match-1", MatchResultScreenshotRole.MATCH_RESULT_UPPER.name))
        assertEquals(lower, dao.readByMatchAndRole("match-1", MatchResultScreenshotRole.MATCH_RESULT_LOWER.name))

        val replacedUpper = upper.copy(
            screenshotSha256 = "b".repeat(64),
            processedPayloadJson = "upper-v2",
        )
        dao.upsert(replacedUpper)

        assertEquals(replacedUpper, dao.readByMatchAndRole("match-1", MatchResultScreenshotRole.MATCH_RESULT_UPPER.name))
        assertEquals(lower, dao.readByMatchAndRole("match-1", MatchResultScreenshotRole.MATCH_RESULT_LOWER.name))

        database.tournamentDao().deleteById("tournament-1")

        assertNull(dao.readByMatchAndRole("match-1", MatchResultScreenshotRole.MATCH_RESULT_UPPER.name))
        assertNull(dao.readByMatchAndRole("match-1", MatchResultScreenshotRole.MATCH_RESULT_LOWER.name))
    }

    private fun cache(
        role: MatchResultScreenshotRole,
        payload: String,
    ) = MatchResultOcrCacheEntity(
        tournamentId = "tournament-1",
        matchId = "match-1",
        screenshotRole = role.name,
        screenshotSha256 = "a".repeat(64),
        originalWidth = 1000,
        originalHeight = 800,
        cropProfileId = "match-result",
        cropLeft = 0.0,
        cropTop = 0.0,
        cropRight = 1.0,
        cropBottom = 1.0,
        ocrPipelineVersion = MATCH_RESULT_OCR_CACHE_PIPELINE_VERSION,
        processedPayloadJson = payload,
        cachedAt = 1L,
    )
}
