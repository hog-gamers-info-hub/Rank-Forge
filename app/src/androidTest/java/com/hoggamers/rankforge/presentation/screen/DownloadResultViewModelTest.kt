package com.hoggamers.rankforge.presentation.screen

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.data.cloud.CustomDesignDeleteAction
import com.hoggamers.rankforge.data.cloud.CustomDesignDeleteResult
import com.hoggamers.rankforge.data.cloud.CustomDesignRestoreAction
import com.hoggamers.rankforge.data.cloud.CustomDesignRestoreFailure
import com.hoggamers.rankforge.data.cloud.CustomDesignRestoreResult
import com.hoggamers.rankforge.data.cloud.CustomDesignSavedIdDiscoveryAction
import com.hoggamers.rankforge.data.cloud.CustomDesignSavedIdDiscoveryResult
import com.hoggamers.rankforge.data.export.CustomDesignBitmapComposer
import com.hoggamers.rankforge.data.export.CustomDesignResultRowsResolver
import com.hoggamers.rankforge.data.export.FreeDesignBitmapComposer
import com.hoggamers.rankforge.data.export.FreeDesignResultDownloadCoordinator
import com.hoggamers.rankforge.data.export.FreeDesignTemplateRegistry
import com.hoggamers.rankforge.data.export.NoOpCustomDesignResultDownloadCoordinator
import com.hoggamers.rankforge.data.export.NoOpFreeDesignResultDownloadCoordinator
import com.hoggamers.rankforge.data.export.NoOpResultDocumentWriter
import com.hoggamers.rankforge.data.export.NoOpResultDownloadCoordinator
import com.hoggamers.rankforge.data.export.ResultDownloadExecutionResult
import com.hoggamers.rankforge.data.export.ResultDownloadFailure
import com.hoggamers.rankforge.domain.export.ResultExportModelBuilder
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadResultViewModelTest {
    @Test
    fun templateSelectionDefaultsToGoldAndRejectsUnknownIds() {
        val viewModel = createViewModel()
        assertEquals(
            FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID,
            viewModel.selectedFreeDesignTemplateId.value,
        )
        assertEquals(
            listOf("Gold", "Blue Neon"),
            viewModel.uiState.value.freeDesignOptions.map { it.displayName },
        )

        viewModel.selectFreeDesignTemplate(
            tournamentId = "tournament-id",
            templateId = FreeDesignTemplateRegistry.BLUE_TEMPLATE_ID,
        )
        assertEquals(
            FreeDesignTemplateRegistry.BLUE_TEMPLATE_ID,
            viewModel.selectedFreeDesignTemplateId.value,
        )

        viewModel.selectFreeDesignTemplate(
            tournamentId = "tournament-id",
            templateId = "does_not_exist",
        )
        assertEquals(
            FreeDesignTemplateRegistry.BLUE_TEMPLATE_ID,
            viewModel.selectedFreeDesignTemplateId.value,
        )
    }

    @Test
    fun selectedTemplateIdReachesFreeDesignDownload() = runBlocking {
        val receivedTemplateId = CompletableDeferred<String>()
        val coordinator = object : FreeDesignResultDownloadCoordinator {
            override suspend fun execute(
                request: com.hoggamers.rankforge.data.export.ResultDownloadRequest,
                templateId: String,
                onSaving: suspend () -> Unit,
            ): ResultDownloadExecutionResult {
                receivedTemplateId.complete(templateId)
                return ResultDownloadExecutionResult.Failure(
                    ResultDownloadFailure.GENERATION_FAILED,
                )
            }
        }
        val viewModel = createViewModel(coordinator)
        viewModel.selectFreeDesignTemplate(
            tournamentId = "tournament-id",
            templateId = FreeDesignTemplateRegistry.BLUE_TEMPLATE_ID,
        )
        viewModel.requestDownload(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.FREE_DESIGN,
        )

        assertEquals(
            FreeDesignTemplateRegistry.BLUE_TEMPLATE_ID,
            withTimeout(TimeUnit.SECONDS.toMillis(3)) { receivedTemplateId.await() },
        )
    }

    private fun createViewModel(
        freeDesignCoordinator: FreeDesignResultDownloadCoordinator =
            NoOpFreeDesignResultDownloadCoordinator,
    ): DownloadResultViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return DownloadResultViewModel(
            observeMatches = ObserveMatchesUseCase { flowOf(listOf(match())) },
            getTournamentById = GetTournamentByIdUseCase { flowOf(tournament()) },
            observeTournamentSlots = ObserveTournamentSlotsUseCase { flowOf(teamSlots()) },
            observeRoster = ObserveRosterByTournamentUseCase { flowOf(emptyMap()) },
            customDesignSavedIdDiscovery = CustomDesignSavedIdDiscoveryAction {
                CustomDesignSavedIdDiscoveryResult.None
            },
            customDesignRestore = CustomDesignRestoreAction {
                CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.NOT_FOUND)
            },
            customDesignDelete = CustomDesignDeleteAction {
                CustomDesignDeleteResult.Success
            },
            customDesignRowsResolver = com.hoggamers.rankforge.data.export.CustomDesignResultRowsResolver(
                ResultExportModelBuilder(),
            ),
            customDesignBitmapComposer = CustomDesignBitmapComposer(),
            freeDesignBitmapComposer = FreeDesignBitmapComposer(context.assets),
            resultDownloadCoordinator = NoOpResultDownloadCoordinator,
            freeDesignResultDownloadCoordinator = freeDesignCoordinator,
            customDesignResultDownloadCoordinator = NoOpCustomDesignResultDownloadCoordinator,
            resultDocumentWriter = NoOpResultDocumentWriter,
        )
    }

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
        matchNumber = 1,
        date = LocalDate.of(2026, 9, 5),
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
