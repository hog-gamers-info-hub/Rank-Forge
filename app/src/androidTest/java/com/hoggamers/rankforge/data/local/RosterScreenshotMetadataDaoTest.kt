package com.hoggamers.rankforge.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RosterScreenshotMetadataDaoTest {
    @Test
    fun savesUpdatesObservesAndRemovesTournamentScopedRosterScreenshotMetadata() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RankForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            database.tournamentDao().upsert(
                TournamentEntity("tournament-1", "Cup", "2026-07-30", "Org", "123", "CONFIRMED"),
            )
            val dao = database.rosterScreenshotMetadataDao()
            val first = metadata(index = 1)
            val third = metadata(index = 3)

            dao.upsert(third)
            dao.upsert(first)

            assertEquals(listOf(first, third), dao.observeByTournamentId("tournament-1").first())
            assertEquals(first, dao.readByTournamentAndIndex("tournament-1", 1))
            assertEquals(third, dao.readDuplicateFingerprint("tournament-1", third.sha256, 1))

            val replacement = first.copy(cropLeft = 0.1, cropTop = 0.2, cropRight = 0.9, cropBottom = 0.8, updatedAt = 2)
            dao.upsert(replacement)
            assertEquals(replacement, dao.readByTournamentAndIndex("tournament-1", 1))

            dao.deleteByTournamentAndIndex("tournament-1", 1)
            assertNull(dao.readByTournamentAndIndex("tournament-1", 1))
            assertEquals(listOf(third), dao.observeByTournamentId("tournament-1").first())
        } finally {
            database.close()
        }
    }

    @Test
    fun repositoryRejectsInvalidAndDuplicateFingerprintAssociations() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RankForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            database.tournamentDao().upsert(
                TournamentEntity("tournament-1", "Cup", "2026-07-30", "Org", "123", "CONFIRMED"),
            )
            val repository = RoomRosterScreenshotMetadataRepository(database.rosterScreenshotMetadataDao())
            val first = metadata(index = 1)

            assertEquals(RosterScreenshotAssociationSaveResult.Saved, repository.saveOrReplace(first))
            assertEquals(
                RosterScreenshotAssociationSaveResult.DuplicateFingerprint,
                repository.saveOrReplace(first.copy(rosterScreenshotIndex = 2)),
            )
            assertEquals(
                RosterScreenshotAssociationSaveResult.InvalidIndex,
                repository.saveOrReplace(first.copy(rosterScreenshotIndex = 4)),
            )
            assertEquals(listOf(first), repository.observeByTournamentId("tournament-1").first())
        } finally {
            database.close()
        }
    }

    private fun metadata(index: Int) = RosterScreenshotMetadataEntity(
        tournamentId = "tournament-1",
        rosterScreenshotIndex = index,
        localRelativePath = "screenshots/tournament/roster/$index/original.png",
        mimeType = "image/png",
        width = 100,
        height = 100,
        sha256 = "$index".repeat(64),
        validationStatus = RosterScreenshotValidationStatus.VALID.name,
        cropLeft = null,
        cropTop = null,
        cropRight = null,
        cropBottom = null,
        createdAt = 1,
        updatedAt = 1,
    )
}
