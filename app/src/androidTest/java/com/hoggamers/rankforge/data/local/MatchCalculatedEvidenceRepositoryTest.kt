package com.hoggamers.rankforge.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchCalculatedEvidenceRepositoryTest {
    private lateinit var database: RankForgeDatabase
    private lateinit var repository: MatchCalculatedEvidenceRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RankForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.tournamentDao().upsert(
            TournamentEntity(
                id = TOURNAMENT_ID,
                name = "Evidence Cup",
                date = "2026-09-02",
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = "CONFIRMED",
                ownerUserId = OWNER_A,
            ),
        )
        database.matchDao().upsert(
            MatchEntity(
                id = MATCH_ID,
                tournamentId = TOURNAMENT_ID,
                matchNumber = 1,
                date = "2026-09-02",
                mapName = "Bermuda",
                status = "DRAFT",
            ),
        )
        repository = RoomMatchCalculatedEvidenceRepository(
            dao = database.matchCalculatedEvidenceDao(),
            codec = MatchCalculatedEvidenceCodec(),
            clock = Clock.fixed(Instant.ofEpochMilli(1234L), ZoneOffset.UTC),
            database = database,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveThenReadPreservesEvidence() = runBlocking {
        val evidence = evidence("first")

        assertTrue(repository.save(OWNER_A, TOURNAMENT_ID, MATCH_ID, evidence))
        assertEquals(evidence, repository.read(OWNER_A, TOURNAMENT_ID, MATCH_ID))
    }

    @Test
    fun savingAgainForSameMatchReplacesPreviousEvidence() = runBlocking {
        assertTrue(repository.save(OWNER_A, TOURNAMENT_ID, MATCH_ID, evidence("first")))
        val replacement = evidence("replacement")

        assertTrue(repository.save(OWNER_A, TOURNAMENT_ID, MATCH_ID, replacement))
        assertEquals(replacement, repository.read(OWNER_A, TOURNAMENT_ID, MATCH_ID))
        database.openHelper.writableDatabase.query(
            "SELECT COUNT(*) FROM match_calculated_evidence",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun deleteRemovesEvidenceButKeepsParentMatch() = runBlocking {
        assertTrue(repository.save(OWNER_A, TOURNAMENT_ID, MATCH_ID, evidence("delete")))

        assertTrue(repository.delete(OWNER_A, TOURNAMENT_ID, MATCH_ID))
        assertNull(repository.read(OWNER_A, TOURNAMENT_ID, MATCH_ID))
        assertEquals(MATCH_ID, database.matchDao().observeById(MATCH_ID).first()?.id)
    }

    @Test
    fun deletingParentMatchCascadesCalculatedEvidence() = runBlocking {
        assertTrue(repository.save(OWNER_A, TOURNAMENT_ID, MATCH_ID, evidence("cascade")))

        database.matchDao().deleteById(MATCH_ID)

        assertNull(repository.read(OWNER_A, TOURNAMENT_ID, MATCH_ID))
        database.openHelper.writableDatabase.query(
            "SELECT COUNT(*) FROM match_calculated_evidence",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun wrongOwnerCannotReadDeleteOrOverwriteEvidence() = runBlocking {
        val original = evidence("owner-a")
        assertTrue(repository.save(OWNER_A, TOURNAMENT_ID, MATCH_ID, original))

        assertNull(repository.read(OWNER_B, TOURNAMENT_ID, MATCH_ID))
        assertFalse(repository.delete(OWNER_B, TOURNAMENT_ID, MATCH_ID))
        assertFalse(repository.save(OWNER_B, TOURNAMENT_ID, MATCH_ID, evidence("owner-b")))
        assertEquals(original, repository.read(OWNER_A, TOURNAMENT_ID, MATCH_ID))
    }

    private fun evidence(label: String): MatchCalculatedEvidence = MatchCalculatedEvidence(
        lobby = LobbyCalculatedEvidence(
            teams = listOf(
                LobbyTeamCalculatedEvidence(
                    slotNumber = 11,
                    teamName = label,
                    sourceScreenshotIndex = 1,
                    cropLeft = 11.0,
                    cropTop = 22.0,
                    cropRight = 333.0,
                    cropBottom = 444.0,
                    playerNames = listOf("A", "B", "C", "D"),
                ),
            ),
        ),
        result = ResultCalculatedEvidence(
            positions = listOf(
                ResultPositionCalculatedEvidence(
                    position = 1,
                    sourceScreenshotRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    cropLeft = 1,
                    cropTop = 2,
                    cropRight = 3,
                    cropBottom = 4,
                    slotNumber = 11,
                    teamName = label,
                    playerNames = listOf("A", "B", "C", "D"),
                    playerKills = listOf(1, 2, 3, 4),
                    totalKills = 10,
                ),
            ),
        ),
    )

    private companion object {
        const val OWNER_A = "owner-a"
        const val OWNER_B = "owner-b"
        const val TOURNAMENT_ID = "tournament-evidence"
        const val MATCH_ID = "match-evidence"
    }
}
