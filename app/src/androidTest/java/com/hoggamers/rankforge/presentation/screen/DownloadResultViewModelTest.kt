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
import com.hoggamers.rankforge.data.export.ResultDownloadCoordinator
import com.hoggamers.rankforge.data.export.NoOpResultDownloadCoordinator
import com.hoggamers.rankforge.data.export.ResultDownloadExecutionResult
import com.hoggamers.rankforge.data.export.ResultDownloadFailure
import com.hoggamers.rankforge.data.local.PointTableDetails
import com.hoggamers.rankforge.data.local.PointTableDetailsRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadResultViewModelTest {
    @Test
    fun pointTableDateOverridesFreeDesignPreviewAndApplyRefreshesIt() = runBlocking {
        val repository = FakePointTableDetailsRepository()
        val viewModel = createViewModel(pointTableDetailsRepository = repository)
        viewModel.load("tournament-id")
        viewModel.select(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.FREE_DESIGN,
        )

        val fallbackPreview = withTimeout(TimeUnit.SECONDS.toMillis(5)) {
            viewModel.previewState.first { it is DownloadResultPreviewState.ResultImage }
        } as DownloadResultPreviewState.ResultImage

        viewModel.savePointTableDetails(
            tournamentId = "tournament-id",
            details = PointTableDetailsUiState(date = LocalDate.of(2026, 9, 28)),
        )

        val overriddenPreview = withTimeout(TimeUnit.SECONDS.toMillis(5)) {
            viewModel.previewState.first {
                it is DownloadResultPreviewState.ResultImage &&
                    !it.pngBytes.contentEquals(fallbackPreview.pngBytes)
            }
        }

        assertTrue(overriddenPreview is DownloadResultPreviewState.ResultImage)
        assertEquals(LocalDate.of(2026, 9, 28), viewModel.pointTableDetails.value.date)

        viewModel.savePointTableDetails(
            tournamentId = "tournament-id",
            details = PointTableDetailsUiState(),
        )

        val clearedPreview = withTimeout(TimeUnit.SECONDS.toMillis(5)) {
            viewModel.previewState.first {
                it is DownloadResultPreviewState.ResultImage &&
                    !it.pngBytes.contentEquals((overriddenPreview as DownloadResultPreviewState.ResultImage).pngBytes)
            }
        }

        assertTrue(clearedPreview is DownloadResultPreviewState.ResultImage)
        assertTrue(viewModel.pointTableDetails.value.date == null)
    }

    @Test
    fun pointTableDateOverridesImagePreviewAndApplyRefreshesIt() = runBlocking {
        val repository = FakePointTableDetailsRepository()
        val viewModel = createViewModel(pointTableDetailsRepository = repository)
        viewModel.load("tournament-id")
        viewModel.select(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Match("match-id"),
            design = DownloadResultDesignType.IMAGE,
        )

        val nullDatePreview = withTimeout(TimeUnit.SECONDS.toMillis(5)) {
            viewModel.previewState.first { it is DownloadResultPreviewState.ResultImage }
        } as DownloadResultPreviewState.ResultImage

        viewModel.savePointTableDetails(
            tournamentId = "tournament-id",
            details = PointTableDetailsUiState(date = LocalDate.of(2026, 9, 28)),
        )

        val datedPreview = withTimeout(TimeUnit.SECONDS.toMillis(5)) {
            viewModel.previewState.first {
                it is DownloadResultPreviewState.ResultImage &&
                    !it.pngBytes.contentEquals(nullDatePreview.pngBytes)
            }
        }
        assertTrue(datedPreview is DownloadResultPreviewState.ResultImage)

        viewModel.savePointTableDetails(
            tournamentId = "tournament-id",
            details = PointTableDetailsUiState(),
        )

        val clearedPreview = withTimeout(TimeUnit.SECONDS.toMillis(5)) {
            viewModel.previewState.first {
                it is DownloadResultPreviewState.ResultImage &&
                    !it.pngBytes.contentEquals((datedPreview as DownloadResultPreviewState.ResultImage).pngBytes)
            }
        }
        assertTrue(clearedPreview is DownloadResultPreviewState.ResultImage)
        assertTrue(viewModel.pointTableDetails.value.date == null)
    }

    @Test
    fun restoredPointTableDateReachesFinalImageDownload() = runBlocking {
        val repository = FakePointTableDetailsRepository()
        repository.savePointTableDetails(
            PointTableDetails(
                tournamentId = "tournament-id",
                displayDate = LocalDate.of(2026, 9, 28),
            ),
        )
        val receivedDisplayDate = CompletableDeferred<LocalDate?>()
        val coordinator = object : ResultDownloadCoordinator {
            override suspend fun execute(
                request: com.hoggamers.rankforge.data.export.ResultDownloadRequest,
                format: com.hoggamers.rankforge.data.export.ResultExportFileFormat,
                onSaving: suspend () -> Unit,
            ): ResultDownloadExecutionResult = ResultDownloadExecutionResult.Failure(
                ResultDownloadFailure.GENERATION_FAILED,
            )

            override suspend fun executeImage(
                request: com.hoggamers.rankforge.data.export.ResultDownloadRequest,
                displayDate: LocalDate?,
                onSaving: suspend () -> Unit,
            ): ResultDownloadExecutionResult {
                receivedDisplayDate.complete(displayDate)
                return ResultDownloadExecutionResult.Failure(
                    ResultDownloadFailure.GENERATION_FAILED,
                )
            }
        }
        val viewModel = createViewModel(
            resultCoordinator = coordinator,
            pointTableDetailsRepository = repository,
        )
        viewModel.load("tournament-id")
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.pointTableDetails.first { it.date == LocalDate.of(2026, 9, 28) }
        }

        viewModel.requestDownload(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Match("match-id"),
            design = DownloadResultDesignType.IMAGE,
        )

        assertEquals(
            LocalDate.of(2026, 9, 28),
            withTimeout(TimeUnit.SECONDS.toMillis(3)) { receivedDisplayDate.await() },
        )
    }

    @Test
    fun nullPointTableDateRemainsNullForFinalImageDownload() = runBlocking {
        val receivedDisplayDate = CompletableDeferred<LocalDate?>()
        val coordinator = object : ResultDownloadCoordinator {
            override suspend fun execute(
                request: com.hoggamers.rankforge.data.export.ResultDownloadRequest,
                format: com.hoggamers.rankforge.data.export.ResultExportFileFormat,
                onSaving: suspend () -> Unit,
            ): ResultDownloadExecutionResult = ResultDownloadExecutionResult.Failure(
                ResultDownloadFailure.GENERATION_FAILED,
            )

            override suspend fun executeImage(
                request: com.hoggamers.rankforge.data.export.ResultDownloadRequest,
                displayDate: LocalDate?,
                onSaving: suspend () -> Unit,
            ): ResultDownloadExecutionResult {
                receivedDisplayDate.complete(displayDate)
                return ResultDownloadExecutionResult.Failure(
                    ResultDownloadFailure.GENERATION_FAILED,
                )
            }
        }
        val viewModel = createViewModel(resultCoordinator = coordinator)
        viewModel.load("tournament-id")
        viewModel.requestDownload(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.IMAGE,
        )

        assertTrue(
            withTimeout(TimeUnit.SECONDS.toMillis(3)) { receivedDisplayDate.await() } == null,
        )
    }

    @Test
    fun restoredPointTableDateReachesFinalFreeDesignDownload() = runBlocking {
        val repository = FakePointTableDetailsRepository()
        repository.savePointTableDetails(
            PointTableDetails(
                tournamentId = "tournament-id",
                displayDate = LocalDate.of(2026, 9, 28),
            ),
        )
        val receivedDisplayDate = CompletableDeferred<LocalDate?>()
        val coordinator = object : FreeDesignResultDownloadCoordinator {
            override suspend fun execute(
                request: com.hoggamers.rankforge.data.export.ResultDownloadRequest,
                templateId: String,
                onSaving: suspend () -> Unit,
                displayDate: LocalDate?,
            ): ResultDownloadExecutionResult {
                receivedDisplayDate.complete(displayDate)
                return ResultDownloadExecutionResult.Failure(
                    ResultDownloadFailure.GENERATION_FAILED,
                )
            }
        }
        val viewModel = createViewModel(
            freeDesignCoordinator = coordinator,
            pointTableDetailsRepository = repository,
        )
        viewModel.load("tournament-id")
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.pointTableDetails.first { it.date == LocalDate.of(2026, 9, 28) }
        }
        viewModel.selectFreeDesignTemplate(
            tournamentId = "tournament-id",
            templateId = FreeDesignTemplateRegistry.BLUE_TEMPLATE_ID,
        )
        assertEquals(LocalDate.of(2026, 9, 28), viewModel.pointTableDetails.value.date)

        viewModel.requestDownload(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.FREE_DESIGN,
        )

        assertEquals(
            LocalDate.of(2026, 9, 28),
            withTimeout(TimeUnit.SECONDS.toMillis(3)) { receivedDisplayDate.await() },
        )
    }

    @Test
    fun nullPointTableDateRemainsNullThroughTemplateSwitchAndFinalDownload() = runBlocking {
        val receivedDisplayDate = CompletableDeferred<LocalDate?>()
        val coordinator = object : FreeDesignResultDownloadCoordinator {
            override suspend fun execute(
                request: com.hoggamers.rankforge.data.export.ResultDownloadRequest,
                templateId: String,
                onSaving: suspend () -> Unit,
                displayDate: LocalDate?,
            ): ResultDownloadExecutionResult {
                receivedDisplayDate.complete(displayDate)
                return ResultDownloadExecutionResult.Failure(
                    ResultDownloadFailure.GENERATION_FAILED,
                )
            }
        }
        val viewModel = createViewModel(freeDesignCoordinator = coordinator)
        viewModel.load("tournament-id")
        viewModel.selectFreeDesignTemplate(
            tournamentId = "tournament-id",
            templateId = FreeDesignTemplateRegistry.BLUE_TEMPLATE_ID,
        )

        assertTrue(viewModel.pointTableDetails.value.date == null)

        viewModel.requestDownload(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.FREE_DESIGN,
        )

        assertTrue(
            withTimeout(TimeUnit.SECONDS.toMillis(3)) { receivedDisplayDate.await() } == null,
        )
    }

    @Test
    fun pointTableDetailsApplyIsTrimmedAndRestoredForTheSameTournament() = runBlocking {
        val repository = FakePointTableDetailsRepository()
        val viewModel = createViewModel(pointTableDetailsRepository = repository)
        viewModel.load("tournament-id")

        viewModel.savePointTableDetails(
            tournamentId = "tournament-id",
            details = PointTableDetailsUiState(
                organizationName = "  HOG Gamers  ",
                date = LocalDate.of(2026, 9, 28),
            ),
        )

        assertEquals(
            PointTableDetails(
                tournamentId = "tournament-id",
                organizationName = "HOG Gamers",
                displayDate = LocalDate.of(2026, 9, 28),
            ),
            withTimeout(TimeUnit.SECONDS.toMillis(3)) {
                repository.saved.first { it?.organizationName == "HOG Gamers" }
            },
        )

        val reloadedViewModel = createViewModel(pointTableDetailsRepository = repository)
        reloadedViewModel.load("tournament-id")
        assertEquals(
            PointTableDetailsUiState(
                organizationName = "HOG Gamers",
                date = LocalDate.of(2026, 9, 28),
            ),
            withTimeout(TimeUnit.SECONDS.toMillis(3)) {
                reloadedViewModel.pointTableDetails.first {
                    it.organizationName == "HOG Gamers"
                }
            },
        )
    }

    @Test
    fun templateSelectionDefaultsToGoldAndRejectsUnknownIds() {
        val viewModel = createViewModel()
        assertEquals(
            FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID,
            viewModel.selectedFreeDesignTemplateId.value,
        )
        assertEquals(
            FreeDesignTemplateRegistry.all.map { it.displayName },
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
                displayDate: LocalDate?,
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
        pointTableDetailsRepository: PointTableDetailsRepository =
            FakePointTableDetailsRepository(),
        resultCoordinator: ResultDownloadCoordinator = NoOpResultDownloadCoordinator,
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
            resultDownloadCoordinator = resultCoordinator,
            freeDesignResultDownloadCoordinator = freeDesignCoordinator,
            customDesignResultDownloadCoordinator = NoOpCustomDesignResultDownloadCoordinator,
            resultDocumentWriter = NoOpResultDocumentWriter,
            pointTableDetailsRepository = pointTableDetailsRepository,
        )
    }

    private class FakePointTableDetailsRepository : PointTableDetailsRepository {
        val saved = kotlinx.coroutines.flow.MutableStateFlow<PointTableDetails?>(null)

        override suspend fun getPointTableDetails(tournamentId: String): PointTableDetails? =
            saved.value?.takeIf { it.tournamentId == tournamentId }

        override suspend fun savePointTableDetails(details: PointTableDetails) {
            saved.value = details
        }
    }

    private fun tournament() = Tournament(
        id = "tournament-id",
        name = "Tournament",
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
