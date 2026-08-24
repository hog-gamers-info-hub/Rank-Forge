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
            val repository = RoomRosterScreenshotMetadataRepository(
                database.rosterScreenshotMetadataDao(),
                database,
            )
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

    @Test
    fun deletingTournamentCascadesRosterScreenshotMetadata() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RankForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            database.tournamentDao().upsert(
                TournamentEntity("tournament-1", "Cup", "2026-07-30", "Org", "123", "CONFIRMED"),
            )
            val dao = database.rosterScreenshotMetadataDao()
            dao.upsert(metadata(index = 1))

            database.tournamentDao().deleteById("tournament-1")

            assertTrue(dao.observeByTournamentId("tournament-1").first().isEmpty())
            assertNull(dao.readByTournamentAndIndex("tournament-1", 1))
        } finally {
            database.close()
        }
    }

    @Test
    fun ownerScopedReadsAndMutationsQuarantineForeignAndLegacyRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RankForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            database.tournamentDao().upsert(tournament("tournament-a", "owner-a"))
            database.tournamentDao().upsert(tournament("tournament-b", "owner-b"))
            database.tournamentDao().upsert(tournament("tournament-legacy", null))
            val dao = database.rosterScreenshotMetadataDao()
            val repository = RoomRosterScreenshotMetadataRepository(
                dao,
                database,
            )
            val ownerA = metadata("tournament-a", 1)
            val ownerB = metadata("tournament-b", 1)
            val legacy = metadata("tournament-legacy", 1)
            repository.saveOrReplace(ownerA)
            repository.saveOrReplace(ownerB)
            repository.saveOrReplace(legacy)
            val tournamentBBeforeRejectedWrites = database.tournamentDao().observeById("tournament-b").first()

            val ownerAReplacement = ownerA.copy(
                cropLeft = 0.1,
                cropTop = 0.2,
                cropRight = 0.9,
                cropBottom = 0.8,
                updatedAt = 2,
            )
            assertEquals(
                RosterScreenshotAssociationSaveResult.Saved,
                repository.saveOrReplaceByOwner(ownerAReplacement, "owner-a"),
            )
            assertEquals(
                ownerAReplacement,
                repository.readByTournamentAndIndexAndOwner("tournament-a", 1, "owner-a"),
            )

            assertEquals(listOf(ownerA), repository.observeByTournamentIdAndOwner("tournament-a", "owner-a").first())
            assertTrue(repository.observeByTournamentIdAndOwner("tournament-b", "owner-a").first().isEmpty())
            assertTrue(repository.observeByTournamentIdAndOwner("tournament-legacy", "owner-a").first().isEmpty())
            assertEquals(ownerA, repository.readByTournamentAndIndexAndOwner("tournament-a", 1, "owner-a"))
            assertNull(repository.readByTournamentAndIndexAndOwner("tournament-b", 1, "owner-a"))
            assertNull(repository.findDuplicateFingerprintAndOwner("tournament-b", ownerB.sha256, 2, "owner-a"))
            assertEquals(ownerA, repository.findDuplicateFingerprintAndOwner("tournament-a", ownerA.sha256, 2, "owner-a"))

            assertEquals(
                RosterScreenshotAssociationSaveResult.TournamentNotFound,
                repository.saveOrReplaceByOwner(ownerB.copy(rosterScreenshotIndex = 2), "owner-a"),
            )
            assertEquals(
                RosterScreenshotAssociationSaveResult.TournamentNotFound,
                repository.saveOrReplaceByOwner(legacy.copy(rosterScreenshotIndex = 2), "owner-a"),
            )
            assertEquals(ownerB, dao.readByTournamentAndIndex("tournament-b", 1))
            assertEquals(legacy, dao.readByTournamentAndIndex("tournament-legacy", 1))
            assertEquals(tournamentBBeforeRejectedWrites, database.tournamentDao().observeById("tournament-b").first())

            assertEquals(
                RosterScreenshotAssociationDeleteResult.Deleted,
                repository.deleteByTournamentAndIndexAndOwner("tournament-a", 1, "owner-a"),
            )
            assertNull(repository.readByTournamentAndIndexAndOwner("tournament-a", 1, "owner-a"))
            assertEquals(ownerB, dao.readByTournamentAndIndex("tournament-b", 1))
            assertEquals(legacy, dao.readByTournamentAndIndex("tournament-legacy", 1))

            assertEquals(
                RosterScreenshotAssociationDeleteResult.TournamentNotFound,
                repository.deleteByTournamentAndIndexAndOwner("tournament-b", 1, "owner-a"),
            )
            assertEquals(
                RosterScreenshotAssociationDeleteResult.TournamentNotFound,
                repository.deleteByTournamentAndIndexAndOwner("tournament-legacy", 1, "owner-a"),
            )
            assertEquals(ownerB, dao.readByTournamentAndIndex("tournament-b", 1))
            assertEquals(legacy, dao.readByTournamentAndIndex("tournament-legacy", 1))

            database.tournamentDao().assignOwnerIfUnassigned("tournament-legacy", "owner-a")
            assertEquals(
                listOf(legacy),
                repository.observeByTournamentIdAndOwner("tournament-legacy", "owner-a").first(),
            )
        } finally {
            database.close()
        }
    }

    private fun metadata(tournamentId: String = "tournament-1", index: Int) = RosterScreenshotMetadataEntity(
        tournamentId = tournamentId,
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

    private fun tournament(id: String, ownerUserId: String?) = TournamentEntity(
        id = id,
        name = "Cup",
        date = "2026-07-30",
        organizerName = "Org",
        organizerContactNumber = "123",
        status = "CONFIRMED",
        ownerUserId = ownerUserId,
    )
}
