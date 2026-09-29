package com.hoggamers.rankforge.presentation.screen

import android.content.Context
import android.graphics.BitmapFactory
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
import com.hoggamers.rankforge.data.export.PointTableLogoRenderResolver
import com.hoggamers.rankforge.data.local.PointTableDetails
import com.hoggamers.rankforge.data.local.PointTableDetailsRepository
import com.hoggamers.rankforge.data.local.PointTableLogoCandidate
import com.hoggamers.rankforge.data.local.PointTableLogoImageStore
import com.hoggamers.rankforge.data.local.PointTableLogoImageStoreResult
import com.hoggamers.rankforge.data.local.PointTableLogoPlacement
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
            details = PointTableDetailsUiState(
                organizationName = "HOG Gamers",
                date = LocalDate.of(2026, 9, 28),
            ),
        )

        val overriddenPreview = withTimeout(TimeUnit.SECONDS.toMillis(5)) {
            viewModel.previewState.first {
                it is DownloadResultPreviewState.ResultImage &&
                    !it.pngBytes.contentEquals(fallbackPreview.pngBytes)
            }
        }

        assertTrue(overriddenPreview is DownloadResultPreviewState.ResultImage)
        assertEquals("HOG Gamers", viewModel.pointTableDetails.value.organizationName)
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
    fun pointTableDetailsApplyPreservesCurrentFreeDesignTemplate() = runBlocking {
        val repository = FakePointTableDetailsRepository()
        val viewModel = createViewModel(pointTableDetailsRepository = repository)
        val template = requireNotNull(
            FreeDesignTemplateRegistry.findById(FreeDesignTemplateRegistry.V6_TEMPLATE_ID),
        )
        viewModel.load("tournament-id")
        viewModel.select(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.IMAGE,
        )
        viewModel.selectFreeDesignTemplate("tournament-id", template.id)
        viewModel.select(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.FREE_DESIGN,
        )

        viewModel.savePointTableDetails(
            tournamentId = "tournament-id",
            details = PointTableDetailsUiState(organizationName = "Updated Org"),
        )

        val preview = withTimeout(TimeUnit.SECONDS.toMillis(5)) {
            viewModel.previewState.first { state ->
                state is DownloadResultPreviewState.ResultImage &&
                    pngDimensions(state.pngBytes) == (template.sourceWidth to template.sourceHeight)
            }
        }
        assertTrue(preview is DownloadResultPreviewState.ResultImage)
        assertEquals(template.id, viewModel.selectionContext.value.freeDesignTemplateId)
    }

    @Test
    fun lifecycleRefreshPreservesCurrentFreeDesignSelection() = runBlocking {
        val repository = FakePointTableDetailsRepository()
        val viewModel = createViewModel(pointTableDetailsRepository = repository)
        val template = requireNotNull(
            FreeDesignTemplateRegistry.findById(FreeDesignTemplateRegistry.V6_TEMPLATE_ID),
        )
        viewModel.load("tournament-id")
        viewModel.selectFreeDesignTemplate("tournament-id", template.id)
        viewModel.select(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.FREE_DESIGN,
        )
        withTimeout(TimeUnit.SECONDS.toMillis(5)) {
            viewModel.previewState.first { state ->
                state is DownloadResultPreviewState.ResultImage &&
                    pngDimensions(state.pngBytes) == (template.sourceWidth to template.sourceHeight)
            }
        }

        viewModel.refreshSelection()

        withTimeout(TimeUnit.SECONDS.toMillis(5)) {
            viewModel.previewState.first { state ->
                state is DownloadResultPreviewState.ResultImage &&
                    pngDimensions(state.pngBytes) == (template.sourceWidth to template.sourceHeight)
            }
        }
        assertEquals(DownloadResultDesignType.FREE_DESIGN, viewModel.selectionContext.value.design)
        assertEquals(template.id, viewModel.selectionContext.value.freeDesignTemplateId)
    }

    @Test
    fun existingLogoEditRefreshesUsingCurrentFreeDesignTemplate() = runBlocking {
        val repository = FakePointTableDetailsRepository().apply {
            saved.value = PointTableDetails(
                tournamentId = "tournament-id",
                organizationLogoPath = "point-table-details/746f/logo/logo-old.png",
            )
        }
        val viewModel = createViewModel(
            pointTableDetailsRepository = repository,
            pointTableLogoImageStore = FakePointTableLogoImageStore(),
        )
        val template = requireNotNull(
            FreeDesignTemplateRegistry.findById(FreeDesignTemplateRegistry.V6_TEMPLATE_ID),
        )
        viewModel.load("tournament-id")
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.pointTableDetails.first { it.organizationLogoPath != null }
        }
        viewModel.select(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.IMAGE,
        )
        viewModel.selectFreeDesignTemplate("tournament-id", template.id)
        viewModel.select(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.FREE_DESIGN,
        )
        viewModel.openOrganizationLogoEditor(
            tournamentId = "tournament-id",
            designKey = "FREE_DESIGN:${template.id}",
            previewPngBytes = byteArrayOf(1),
        )
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.logoEditorState.first { it != null }
        }
        viewModel.saveOrganizationLogoPlacement(
            PointTableLogoPlacementGeometry(0.5f, 0.5f, 0.2f),
        )

        withTimeout(TimeUnit.SECONDS.toMillis(5)) {
            viewModel.previewState.first { state ->
                state is DownloadResultPreviewState.ResultImage &&
                    pngDimensions(state.pngBytes) == (template.sourceWidth to template.sourceHeight)
            }
        }
        assertEquals(template.id, viewModel.selectionContext.value.freeDesignTemplateId)
    }

    @Test
    fun newLogoSaveRefreshesUsingCurrentFreeDesignTemplate() = runBlocking {
        val repository = FakePointTableDetailsRepository()
        val viewModel = createViewModel(
            pointTableDetailsRepository = repository,
            pointTableLogoImageStore = FakePointTableLogoImageStore(),
        )
        val template = requireNotNull(
            FreeDesignTemplateRegistry.findById(FreeDesignTemplateRegistry.V6_TEMPLATE_ID),
        )
        viewModel.load("tournament-id")
        viewModel.select(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.IMAGE,
        )
        viewModel.selectFreeDesignTemplate("tournament-id", template.id)
        viewModel.select(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.FREE_DESIGN,
        )
        viewModel.prepareOrganizationLogoCandidate(
            tournamentId = "tournament-id",
            designKey = "FREE_DESIGN:${template.id}",
            previewPngBytes = byteArrayOf(1),
            selectedUri = "content://picked/logo",
        )
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.logoEditorState.first { it != null }
        }
        viewModel.saveOrganizationLogoPlacement(
            PointTableLogoPlacementGeometry(0.5f, 0.5f, 0.2f),
        )

        withTimeout(TimeUnit.SECONDS.toMillis(5)) {
            viewModel.previewState.first { state ->
                state is DownloadResultPreviewState.ResultImage &&
                    pngDimensions(state.pngBytes) == (template.sourceWidth to template.sourceHeight)
            }
        }
        assertEquals(template.id, viewModel.selectionContext.value.freeDesignTemplateId)
    }

    @Test
    fun cancellingV6LogoReplacementKeepsCurrentFreeDesignTemplateForApply() = runBlocking {
        val repository = FakePointTableDetailsRepository().apply {
            saved.value = PointTableDetails(
                tournamentId = "tournament-id",
                organizationLogoPath = "point-table-details/746f/logo/logo-old.png",
            )
        }
        val viewModel = createViewModel(
            pointTableDetailsRepository = repository,
            pointTableLogoImageStore = FakePointTableLogoImageStore(),
        )
        val template = requireNotNull(
            FreeDesignTemplateRegistry.findById(FreeDesignTemplateRegistry.V6_TEMPLATE_ID),
        )
        viewModel.load("tournament-id")
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.pointTableDetails.first { it.organizationLogoPath != null }
        }
        viewModel.select(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.IMAGE,
        )
        viewModel.selectFreeDesignTemplate("tournament-id", template.id)
        viewModel.select(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.FREE_DESIGN,
        )
        viewModel.prepareOrganizationLogoCandidate(
            tournamentId = "tournament-id",
            designKey = "FREE_DESIGN:${template.id}",
            previewPngBytes = byteArrayOf(1),
            selectedUri = "content://picked/logo",
        )
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.logoEditorState.first { it != null }
        }
        viewModel.cancelOrganizationLogoEditor()
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.logoEditorState.first { it == null }
        }

        viewModel.savePointTableDetails(
            tournamentId = "tournament-id",
            details = PointTableDetailsUiState(organizationName = "Updated Org"),
        )
        withTimeout(TimeUnit.SECONDS.toMillis(5)) {
            viewModel.previewState.first { state ->
                state is DownloadResultPreviewState.ResultImage &&
                    pngDimensions(state.pngBytes) == (template.sourceWidth to template.sourceHeight)
            }
        }
        assertEquals(template.id, viewModel.selectionContext.value.freeDesignTemplateId)
    }

    @Test
    fun v6LogoReplacementSaveRefreshesUsingCurrentFreeDesignTemplate() = runBlocking {
        val repository = FakePointTableDetailsRepository().apply {
            saved.value = PointTableDetails(
                tournamentId = "tournament-id",
                organizationLogoPath = "point-table-details/746f/logo/logo-old.png",
            )
        }
        val viewModel = createViewModel(
            pointTableDetailsRepository = repository,
            pointTableLogoImageStore = FakePointTableLogoImageStore(),
        )
        val template = requireNotNull(
            FreeDesignTemplateRegistry.findById(FreeDesignTemplateRegistry.V6_TEMPLATE_ID),
        )
        viewModel.load("tournament-id")
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.pointTableDetails.first { it.organizationLogoPath != null }
        }
        viewModel.select(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.IMAGE,
        )
        viewModel.selectFreeDesignTemplate("tournament-id", template.id)
        viewModel.select(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.FREE_DESIGN,
        )
        viewModel.prepareOrganizationLogoCandidate(
            tournamentId = "tournament-id",
            designKey = "FREE_DESIGN:${template.id}",
            previewPngBytes = byteArrayOf(1),
            selectedUri = "content://picked/logo",
        )
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.logoEditorState.first { it != null }
        }
        viewModel.saveOrganizationLogoPlacement(
            PointTableLogoPlacementGeometry(0.5f, 0.6f, 0.25f),
        )

        withTimeout(TimeUnit.SECONDS.toMillis(5)) {
            viewModel.previewState.first { state ->
                state is DownloadResultPreviewState.ResultImage &&
                    pngDimensions(state.pngBytes) == (template.sourceWidth to template.sourceHeight)
            }
        }
        assertEquals(template.id, viewModel.selectionContext.value.freeDesignTemplateId)
    }

    @Test
    fun cancellingV6ExistingLogoEditKeepsCurrentFreeDesignTemplateForApply() = runBlocking {
        val repository = FakePointTableDetailsRepository().apply {
            saved.value = PointTableDetails(
                tournamentId = "tournament-id",
                organizationLogoPath = "point-table-details/746f/logo/logo-old.png",
            )
        }
        val viewModel = createViewModel(
            pointTableDetailsRepository = repository,
            pointTableLogoImageStore = FakePointTableLogoImageStore(),
        )
        val template = requireNotNull(
            FreeDesignTemplateRegistry.findById(FreeDesignTemplateRegistry.V6_TEMPLATE_ID),
        )
        viewModel.load("tournament-id")
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.pointTableDetails.first { it.organizationLogoPath != null }
        }
        viewModel.select(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.IMAGE,
        )
        viewModel.selectFreeDesignTemplate("tournament-id", template.id)
        viewModel.select(
            tournamentId = "tournament-id",
            result = DownloadResultSelection.Overall,
            design = DownloadResultDesignType.FREE_DESIGN,
        )
        viewModel.openOrganizationLogoEditor(
            tournamentId = "tournament-id",
            designKey = "FREE_DESIGN:${template.id}",
            previewPngBytes = byteArrayOf(1),
        )
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.logoEditorState.first { it != null }
        }
        viewModel.cancelOrganizationLogoEditor()
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.logoEditorState.first { it == null }
        }

        viewModel.savePointTableDetails(
            tournamentId = "tournament-id",
            details = PointTableDetailsUiState(organizationName = "Updated Org"),
        )
        withTimeout(TimeUnit.SECONDS.toMillis(5)) {
            viewModel.previewState.first { state ->
                state is DownloadResultPreviewState.ResultImage &&
                    pngDimensions(state.pngBytes) == (template.sourceWidth to template.sourceHeight)
            }
        }
        assertEquals(template.id, viewModel.selectionContext.value.freeDesignTemplateId)
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
                organizationName = "  HOG Gamers  ",
                displayDate = LocalDate.of(2026, 9, 28),
            ),
        )
        val receivedDisplayDate = CompletableDeferred<LocalDate?>()
        val receivedOrganizationName = CompletableDeferred<String>()
        val coordinator = object : FreeDesignResultDownloadCoordinator {
            override suspend fun execute(
                request: com.hoggamers.rankforge.data.export.ResultDownloadRequest,
                templateId: String,
                onSaving: suspend () -> Unit,
                displayDate: LocalDate?,
                organizationName: String,
            ): ResultDownloadExecutionResult {
                receivedDisplayDate.complete(displayDate)
                receivedOrganizationName.complete(organizationName)
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
        assertEquals(
            "HOG Gamers",
            withTimeout(TimeUnit.SECONDS.toMillis(3)) { receivedOrganizationName.await() },
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
                organizationName: String,
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
    fun pointTableDetailsApplyPreservesSavedOrganizationLogoPath() = runBlocking {
        val repository = FakePointTableDetailsRepository().apply {
            saved.value = PointTableDetails(
                tournamentId = "tournament-id",
                organizationName = "Saved Org",
                organizationLogoPath = "point-table-details/746f/logo/logo-old.png",
            )
        }
        val viewModel = createViewModel(pointTableDetailsRepository = repository)
        viewModel.load("tournament-id")

        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.pointTableDetails.first { it.organizationLogoPath != null }
        }
        viewModel.savePointTableDetails(
            tournamentId = "tournament-id",
            details = PointTableDetailsUiState(organizationName = "Updated Org"),
        )

        assertEquals(
            "point-table-details/746f/logo/logo-old.png",
            withTimeout(TimeUnit.SECONDS.toMillis(3)) {
                repository.saved.first()?.organizationLogoPath
            },
        )
    }

    @Test
    fun selectedLogoPreparesCandidateAndOpensEditor() = runBlocking {
        val repository = FakePointTableDetailsRepository()
        val store = FakePointTableLogoImageStore()
        val viewModel = createViewModel(
            pointTableDetailsRepository = repository,
            pointTableLogoImageStore = store,
        )
        viewModel.load("tournament-id")
        viewModel.prepareOrganizationLogoCandidate(
            tournamentId = "tournament-id",
            designKey = "FREE_DESIGN:free_design_v1",
            previewPngBytes = byteArrayOf(1, 2, 3),
            selectedUri = "content://picked/logo",
        )

        val editor = withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.logoEditorState.first { it != null }
        } ?: error("Editor state was not opened")
        assertEquals("FREE_DESIGN:free_design_v1", editor.designKey)
        assertEquals(store.preparedCandidate.localRelativePath, editor.candidate?.localRelativePath)
    }

    @Test
    fun cancellingReplacementDiscardsCandidateAndKeepsExistingLogo() = runBlocking {
        val repository = FakePointTableDetailsRepository().apply {
            saved.value = PointTableDetails(
                tournamentId = "tournament-id",
                organizationLogoPath = "point-table-details/746f/logo/logo-old.png",
            )
        }
        val store = FakePointTableLogoImageStore()
        val viewModel = createViewModel(
            pointTableDetailsRepository = repository,
            pointTableLogoImageStore = store,
        )
        viewModel.load("tournament-id")
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.pointTableDetails.first { it.organizationLogoPath != null }
        }
        viewModel.prepareOrganizationLogoCandidate(
            "tournament-id",
            "IMAGE",
            byteArrayOf(1),
            "content://picked/logo",
        )
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.logoEditorState.first { it != null }
        }

        viewModel.cancelOrganizationLogoEditor()

        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            store.discardedCandidate.first { it != null }
        }
        assertEquals(
            "point-table-details/746f/logo/logo-old.png",
            repository.saved.value?.organizationLogoPath,
        )
    }

    @Test
    fun replacementSaveClearsOldPlacementsAndCleansOldLogoAfterPersistence() = runBlocking {
        val repository = FakePointTableDetailsRepository().apply {
            saved.value = PointTableDetails(
                tournamentId = "tournament-id",
                organizationLogoPath = "point-table-details/746f/logo/logo-old.png",
            )
            placements["IMAGE"] = PointTableLogoPlacement(
                "tournament-id", "IMAGE", 0.2f, 0.2f, 0.2f,
            )
            placements["FREE_DESIGN:free_design_v1"] = PointTableLogoPlacement(
                "tournament-id", "FREE_DESIGN:free_design_v1", 0.8f, 0.8f, 0.2f,
            )
        }
        val store = FakePointTableLogoImageStore()
        val viewModel = createViewModel(
            pointTableDetailsRepository = repository,
            pointTableLogoImageStore = store,
        )
        viewModel.load("tournament-id")
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.pointTableDetails.first { it.organizationLogoPath != null }
        }
        viewModel.prepareOrganizationLogoCandidate(
            "tournament-id",
            "FREE_DESIGN:free_design_v1",
            byteArrayOf(1),
            "content://picked/logo",
        )
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.logoEditorState.first { it != null }
        }
        viewModel.saveOrganizationLogoPlacement(
            PointTableLogoPlacementGeometry(0.5f, 0.6f, 0.25f),
        )

        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.logoEditorState.first { it == null }
        }
        assertEquals("point-table-details/746f/logo/logo-new.png", repository.saved.value?.organizationLogoPath)
        assertEquals(1, repository.placements.size)
        assertEquals(0.6f, repository.placements.getValue("FREE_DESIGN:free_design_v1").centerYRatio)
        assertEquals(
            listOf("point-table-details/746f/logo/logo-old.png"),
            store.deletedPaths,
        )
    }

    @Test
    fun existingLogoEditUpdatesOnlyCurrentDesignPlacement() = runBlocking {
        val repository = FakePointTableDetailsRepository().apply {
            saved.value = PointTableDetails(
                tournamentId = "tournament-id",
                organizationLogoPath = "point-table-details/746f/logo/logo-old.png",
            )
            placements["IMAGE"] = PointTableLogoPlacement(
                "tournament-id", "IMAGE", 0.2f, 0.2f, 0.2f,
            )
            placements["FREE_DESIGN:free_design_v1"] = PointTableLogoPlacement(
                "tournament-id", "FREE_DESIGN:free_design_v1", 0.8f, 0.8f, 0.2f,
            )
        }
        val viewModel = createViewModel(
            pointTableDetailsRepository = repository,
            pointTableLogoImageStore = FakePointTableLogoImageStore(),
        )
        viewModel.load("tournament-id")
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.pointTableDetails.first { it.organizationLogoPath != null }
        }
        viewModel.openOrganizationLogoEditor(
            "tournament-id",
            "FREE_DESIGN:free_design_v1",
            byteArrayOf(1),
        )
        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.logoEditorState.first { it != null }
        }
        viewModel.saveOrganizationLogoPlacement(
            PointTableLogoPlacementGeometry(0.4f, 0.45f, 0.3f),
        )

        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.logoEditorState.first { it == null }
        }
        assertEquals(
            "point-table-details/746f/logo/logo-old.png",
            repository.saved.value?.organizationLogoPath,
        )
        assertEquals(2, repository.placements.size)
        assertEquals(0.45f, repository.placements.getValue("FREE_DESIGN:free_design_v1").centerYRatio)
        assertTrue(repository.configurationCalls.isEmpty())
    }

    @Test
    fun failedCandidatePreparationLeavesSavedLogoAndEditorStateUnchanged() = runBlocking {
        val repository = FakePointTableDetailsRepository().apply {
            saved.value = PointTableDetails(
                tournamentId = "tournament-id",
                organizationLogoPath = "point-table-details/746f/logo/logo-old.png",
            )
        }
        val store = FakePointTableLogoImageStore().apply {
            prepareResult = PointTableLogoImageStoreResult.Failed
        }
        val viewModel = createViewModel(
            pointTableDetailsRepository = repository,
            pointTableLogoImageStore = store,
        )
        viewModel.load("tournament-id")
        viewModel.prepareOrganizationLogoCandidate(
            "tournament-id",
            "IMAGE",
            byteArrayOf(1),
            "content://picked/logo",
        )

        withTimeout(TimeUnit.SECONDS.toMillis(3)) {
            viewModel.logoOperationError.first { it != null }
        }
        assertEquals(null, viewModel.logoEditorState.value)
        assertEquals(
            "point-table-details/746f/logo/logo-old.png",
            repository.saved.value?.organizationLogoPath,
        )
    }

    @Test
    fun templateSelectionDefaultsToGoldAndRejectsUnknownIds() {
        val viewModel = createViewModel()
        assertEquals(
            FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID,
            viewModel.selectionContext.value.freeDesignTemplateId,
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
            viewModel.selectionContext.value.freeDesignTemplateId,
        )

        viewModel.selectFreeDesignTemplate(
            tournamentId = "tournament-id",
            templateId = "does_not_exist",
        )
        assertEquals(
            FreeDesignTemplateRegistry.BLUE_TEMPLATE_ID,
            viewModel.selectionContext.value.freeDesignTemplateId,
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
                organizationName: String,
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

    private fun pngDimensions(bytes: ByteArray): Pair<Int, Int> {
        val bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        return try {
            bitmap.width to bitmap.height
        } finally {
            bitmap.recycle()
        }
    }

    private fun createViewModel(
        freeDesignCoordinator: FreeDesignResultDownloadCoordinator =
            NoOpFreeDesignResultDownloadCoordinator,
        pointTableDetailsRepository: PointTableDetailsRepository =
            FakePointTableDetailsRepository(),
        resultCoordinator: ResultDownloadCoordinator = NoOpResultDownloadCoordinator,
        pointTableLogoImageStore: PointTableLogoImageStore = FakePointTableLogoImageStore(),
        pointTableLogoRenderResolver: PointTableLogoRenderResolver? = null,
    ): DownloadResultViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logoResolver = pointTableLogoRenderResolver ?: PointTableLogoRenderResolver(
            pointTableDetailsRepository = pointTableDetailsRepository,
            pointTableLogoImageStore = pointTableLogoImageStore,
        )
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
            pointTableLogoImageStore = pointTableLogoImageStore,
            pointTableLogoRenderResolver = logoResolver,
        )
    }

    private class FakePointTableDetailsRepository : PointTableDetailsRepository {
        val saved = kotlinx.coroutines.flow.MutableStateFlow<PointTableDetails?>(null)
        val placements = mutableMapOf<String, PointTableLogoPlacement>()
        val configurationCalls = mutableListOf<PointTableLogoPlacement>()

        override suspend fun getPointTableDetails(tournamentId: String): PointTableDetails? =
            saved.value?.takeIf { it.tournamentId == tournamentId }

        override suspend fun savePointTableDetails(details: PointTableDetails) {
            saved.value = details
        }

        override suspend fun getPointTableLogoPlacement(
            tournamentId: String,
            designKey: String,
        ): PointTableLogoPlacement? = placements[designKey]

        override suspend fun savePointTableLogoPlacement(placement: PointTableLogoPlacement) {
            placements[placement.designKey] = placement
        }

        override suspend fun savePointTableLogoConfiguration(
            tournamentId: String,
            organizationLogoPath: String,
            placement: PointTableLogoPlacement,
            clearExistingPlacements: Boolean,
        ): Boolean {
            configurationCalls += placement
            saved.value = (saved.value ?: PointTableDetails(tournamentId = tournamentId)).copy(
                organizationLogoPath = organizationLogoPath,
            )
            if (clearExistingPlacements) placements.clear()
            placements[placement.designKey] = placement
            return true
        }
    }

    private class FakePointTableLogoImageStore : PointTableLogoImageStore {
        val preparedCandidate = PointTableLogoImageStoreResult.Preserved(
            "point-table-details/746f/logo/candidate-logo.png",
            "file:///candidate-logo.png",
        )
        var prepareResult: PointTableLogoImageStoreResult = preparedCandidate
        val discardedCandidate = kotlinx.coroutines.flow.MutableStateFlow<PointTableLogoCandidate?>(null)
        val deletedPaths = mutableListOf<String>()

        override suspend fun preserve(
            tournamentId: String,
            selectedUri: String,
        ): PointTableLogoImageStoreResult = PointTableLogoImageStoreResult.Failed

        override suspend fun prepareCandidate(
            tournamentId: String,
            selectedUri: String,
        ): PointTableLogoImageStoreResult = prepareResult

        override suspend fun commitCandidate(
            candidate: PointTableLogoCandidate,
        ): PointTableLogoImageStoreResult = PointTableLogoImageStoreResult.Preserved(
            "point-table-details/746f/logo/logo-new.png",
            "file:///logo-new.png",
        )

        override suspend fun discardCandidate(candidate: PointTableLogoCandidate) {
            discardedCandidate.value = candidate
        }

        override suspend fun deleteLogo(localRelativePath: String): Boolean {
            deletedPaths += localRelativePath
            return true
        }

        override suspend fun cleanup(tournamentId: String): Boolean = true

        override fun displayUriOrNull(localRelativePath: String): String? = "file:///$localRelativePath"
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
