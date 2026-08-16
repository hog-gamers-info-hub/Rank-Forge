package com.hoggamers.rankforge.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchLobbyOcrCacheDaoTest {
    private lateinit var database: RankForgeDatabase

    @Before
    fun setUp() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, RankForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.tournamentDao().upsert(
            TournamentEntity("tournament-1", "Cache Cup", "2026-08-16", "Organizer", "123", "DRAFT"),
        )
        database.matchDao().upsert(
            MatchEntity("match-1", "tournament-1", 1, "2026-08-16", "Bermuda", "DRAFT"),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun screenshotEntriesReplaceIndependentlyAndCascadeWithMatch() = runBlocking {
        val dao = database.matchLobbyOcrCacheDao()
        val one = cache(1, "one-v1")
        val two = cache(2, "two-v1")

        dao.upsert(one)
        dao.upsert(two)
        assertEquals(one, dao.readByMatchAndIndex("match-1", 1))
        assertEquals(two, dao.readByMatchAndIndex("match-1", 2))

        val replaced = one.copy(screenshotSha256 = "changed", processedPayloadJson = "one-v2")
        dao.upsert(replaced)
        assertEquals(replaced, dao.readByMatchAndIndex("match-1", 1))
        assertEquals(two, dao.readByMatchAndIndex("match-1", 2))

        dao.deleteByMatchAndIndex("match-1", 1)
        assertNull(dao.readByMatchAndIndex("match-1", 1))
        assertEquals(two, dao.readByMatchAndIndex("match-1", 2))

        database.matchDao().deleteByTournamentId("tournament-1")
        assertNull(dao.readByMatchAndIndex("match-1", 2))
    }

    private fun cache(index: Int, payload: String) = MatchLobbyOcrCacheEntity(
        tournamentId = "tournament-1",
        matchId = "match-1",
        lobbyScreenshotIndex = index,
        screenshotSha256 = "sha-$index",
        originalWidth = 100,
        originalHeight = 100,
        cropProfileId = "roster",
        cropLeft = 0.0,
        cropTop = 0.0,
        cropRight = 1.0,
        cropBottom = 1.0,
        ocrPipelineVersion = 1,
        processedPayloadJson = payload,
        cachedAt = 1L,
    )
}
