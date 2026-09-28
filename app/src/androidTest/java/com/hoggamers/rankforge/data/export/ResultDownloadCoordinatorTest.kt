package com.hoggamers.rankforge.data.export

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.export.MatchCsvExportInput
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultDownloadCoordinatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val coordinator = DefaultResultDownloadCoordinator(
        ResultFileSaver(
            contentResolver = context.contentResolver,
            sdkInt = 28,
        ),
    )

    @Test
    fun imageDownloadUsesPassedDateAndKeepsNullWithoutTournamentFallback() {
        val explicitResult = runBlocking {
            coordinator.executeImage(
                request = currentMatchRequest(),
                displayDate = LocalDate.of(2026, 9, 28),
            )
        }
        val nullResult = runBlocking {
            coordinator.executeImage(
                request = currentMatchRequest(),
                displayDate = null,
            )
        }

        val explicitBytes = (explicitResult as ResultDownloadExecutionResult.UserDestinationRequired).bytes
        val nullBytes = (nullResult as ResultDownloadExecutionResult.UserDestinationRequired).bytes
        assertTrue(explicitBytes.isNotEmpty())
        assertTrue(nullBytes.isNotEmpty())
        assertTrue(!explicitBytes.contentEquals(nullBytes))
    }

    @Test
    fun existingPdfDownloadPathRemainsPdf() {
        val result = runBlocking {
            coordinator.execute(
                request = currentMatchRequest(),
                format = ResultExportFileFormat.PDF,
            )
        }

        val bytes = (result as ResultDownloadExecutionResult.UserDestinationRequired).bytes
        assertTrue(bytes.copyOfRange(0, 5).contentEquals("%PDF-".encodeToByteArray()))
    }

    private fun currentMatchRequest() = ResultDownloadRequest.CurrentMatch(
        MatchCsvExportInput(
            tournament = tournament(),
            match = match(),
            teamSlots = teamSlots(),
            rosterPlayers = emptyList(),
        ),
    )

    private fun tournament() = Tournament(
        id = "tournament-id",
        name = "Tournament",
        date = LocalDate.of(2026, 9, 5),
        stageName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.CONFIRMED,
    )

    private fun match() = Match(
        id = "match-id",
        tournamentId = "tournament-id",
        matchNumber = 4,
        date = LocalDate.of(2026, 9, 7),
        mapName = "Bermuda",
        status = MatchStatus.FINALIZED,
        participantResults = listOf(
            MatchParticipantResult(
                teamSlotNumber = 1,
                participationStatus = MatchParticipationStatus.PARTICIPATED,
                placement = 1,
                kills = 5,
            ),
        ),
    )

    private fun teamSlots(): List<TeamSlot> = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
        TeamSlot.create("tournament-id", slotNumber, "Team $slotNumber")
    }
}
