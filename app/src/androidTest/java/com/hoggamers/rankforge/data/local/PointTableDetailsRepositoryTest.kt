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
        repository = RoomPointTableDetailsRepository(database.pointTableDetailsDao())
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
    fun deletingTournamentCascadesItsPointTableDetails() = runBlocking {
        repository.savePointTableDetails(PointTableDetails("tournament-a", "HOG Gamers"))

        database.tournamentDao().deleteById("tournament-a")

        assertNull(repository.getPointTableDetails("tournament-a"))
    }

    private fun tournament(id: String) = TournamentEntity(
        id = id,
        name = "Tournament",
        date = "2026-09-28",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = "CONFIRMED",
        ownerUserId = "owner",
    )
}
