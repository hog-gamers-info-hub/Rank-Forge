package com.hoggamers.rankforge.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PointTableDetailsRepositoryTest {
    private lateinit var database: RankForgeDatabase
    private lateinit var repository: PointTableDetailsRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RankForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.tournamentDao().upsert(tournament("tournament-a"))
        database.tournamentDao().upsert(tournament("tournament-b"))
        repository = RoomPointTableDetailsRepository(
            database.pointTableDetailsDao(),
            database.pointTableLogoPlacementDao(),
            database,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun savesAndRestoresDetailsPerTournament() = runBlocking {
        val details = PointTableDetails(
            tournamentId = "tournament-a",
            organizationName = "  HOG Gamers  ",
            displayDate = LocalDate.of(2026, 9, 28),
        )

        repository.savePointTableDetails(details)

        assertEquals(
            details.copy(organizationName = "HOG Gamers"),
            repository.getPointTableDetails("tournament-a"),
        )
        assertNull(repository.getPointTableDetails("tournament-b"))
    }

    @Test
    fun blankOrganizationAndNullDateAreSafe() = runBlocking {
        repository.savePointTableDetails(
            PointTableDetails(
                tournamentId = "tournament-a",
                organizationName = "   ",
                displayDate = null,
            ),
        )

        assertEquals(
            PointTableDetails(tournamentId = "tournament-a"),
            repository.getPointTableDetails("tournament-a"),
        )
    }

    @Test
    fun savesAndDeletesValidatedLogoPlacements() = runBlocking {
        val placement = PointTableLogoPlacement(
            tournamentId = "tournament-a",
            designKey = "free_design_v1",
            centerXRatio = 0.5f,
            centerYRatio = 0.25f,
            widthRatio = 0.2f,
        )

        repository.savePointTableLogoPlacement(placement)

        assertEquals(placement, repository.getPointTableLogoPlacement("tournament-a", "free_design_v1"))
        repository.deletePointTableLogoPlacement("tournament-a", "free_design_v1")
        assertNull(repository.getPointTableLogoPlacement("tournament-a", "free_design_v1"))
    }

    @Test
    fun invalidLogoPlacementsAreIgnoredAndDeleteAllIsScopedToTournament() = runBlocking {
        repository.savePointTableLogoPlacement(
            PointTableLogoPlacement("tournament-a", "valid", 0.1f, 0.2f, 0.3f),
        )
        repository.savePointTableLogoPlacement(
            PointTableLogoPlacement("tournament-a", "also-valid", 0.2f, 0.3f, 0.4f),
        )
        repository.savePointTableLogoPlacement(
            PointTableLogoPlacement("tournament-a", "invalid", Float.NaN, 0.3f, 0.4f),
        )
        repository.savePointTableLogoPlacement(
            PointTableLogoPlacement("tournament-b", "valid", 0.4f, 0.5f, 0.6f),
        )

        repository.deletePointTableLogoPlacements("tournament-a")

        assertNull(repository.getPointTableLogoPlacement("tournament-a", "valid"))
        assertNull(repository.getPointTableLogoPlacement("tournament-a", "also-valid"))
        assertNull(repository.getPointTableLogoPlacement("tournament-a", "invalid"))
        assertEquals(
            PointTableLogoPlacement("tournament-b", "valid", 0.4f, 0.5f, 0.6f),
            repository.getPointTableLogoPlacement("tournament-b", "valid"),
        )
    }

    @Test
    fun organizationLogoPathRoundTripsOnlyAsSafeRelativePath() = runBlocking {
        repository.savePointTableDetails(
            PointTableDetails(
                tournamentId = "tournament-a",
                organizationName = "  HOG Gamers  ",
                organizationLogoPath = "point-table-details/746f/logo/original.png",
            ),
        )
        assertEquals(
            "point-table-details/746f/logo/original.png",
            repository.getPointTableDetails("tournament-a")?.organizationLogoPath,
        )

        repository.savePointTableDetails(
            PointTableDetails(
                tournamentId = "tournament-a",
                organizationName = "HOG Gamers",
                organizationLogoPath = "../outside.png",
            ),
        )
        assertNull(repository.getPointTableDetails("tournament-a")?.organizationLogoPath)
    }

    @Test
    fun deletingTournamentCascadesItsPointTableDetails() = runBlocking {
        repository.savePointTableDetails(PointTableDetails("tournament-a", "HOG Gamers"))
        repository.savePointTableLogoPlacement(
            PointTableLogoPlacement("tournament-a", "free_design_v1", 0.5f, 0.5f, 0.2f),
        )

        database.tournamentDao().deleteById("tournament-a")

        assertNull(repository.getPointTableDetails("tournament-a"))
        assertNull(repository.getPointTableLogoPlacement("tournament-a", "free_design_v1"))
    }

    private fun tournament(id: String) = TournamentEntity(
        id = id,
        name = "Tournament",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = "CONFIRMED",
        ownerUserId = "owner",
    )
}
