package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchResultScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultCropContentValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropProposer
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropResult
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.OcrScreenshotKind
import com.hoggamers.rankforge.domain.ocr.validation.OcrCropContentIndeterminateReason
import com.hoggamers.rankforge.domain.ocr.validation.OcrCropContentInvalidReason
import com.hoggamers.rankforge.domain.ocr.validation.OcrCropContentValidationResult
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val GATE_TOURNAMENT_ID = "44444444-4444-4444-4444-444444444444"
private const val GATE_MATCH_ID = "gate-match-id"

@OptIn(ExperimentalCoroutinesApi::class)
class MatchResultScreenshotCropValidationGateTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var tournamentRepository: InMemoryTournamentRepository

    @Before
    fun setUp(): Unit = kotlinx.coroutines.runBlocking {
        Dispatchers.setMain(dispatcher)
        tournamentRepository = InMemoryTournamentRepository()
        tournamentRepository.create(
            Tournament(
                id = GATE_TOURNAMENT_ID,
                name = "Gate Cup",
                date = LocalDate.of(2026, 8, 18),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        tournamentRepository.saveTeamNames(GATE_TOURNAMENT_ID, mapOf(1 to "Team 1"))
        tournamentRepository.createDraftMatch(
            Match(
                id = GATE_MATCH_ID,
                tournamentId = GATE_TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 8, 18),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun validContentPersistsAndConfirmsOnce() = runTest {
        val repository = GateAssetRepository()
        val validator = ControlledValidator(OcrCropContentValidationResult.Valid)
        val viewModel = viewModel(repository, validator)
        load(viewModel)
        var confirmations = 0

        viewModel.confirmCrop { confirmations++ }
        advanceUntilIdle()

        assertEquals(1, validator.calls)
        assertEquals(1, repository.persistConfirmedCropCalls)
        assertEquals(1, confirmations)
    }

    @Test
    fun backToBackConfirmBeforeFirstCoroutineAdvancesLaunchesOneAttempt() = runTest {
        val repository = GateAssetRepository()
        val validator = ControlledValidator(OcrCropContentValidationResult.Valid)
        val viewModel = viewModel(repository, validator)
        load(viewModel)
        var confirmations = 0

        viewModel.confirmCrop { confirmations++ }
        viewModel.confirmCrop { confirmations++ }
        advanceUntilIdle()

        assertEquals(1, validator.calls)
        assertEquals(1, repository.persistConfirmedCropCalls)
        assertEquals(1, repository.uploadCheckpointCalls)
        assertEquals(1, confirmations)
    }

    @Test
    fun invalidContentDoesNotPersistOrConfirm() = runTest {
        val repository = GateAssetRepository()
        val viewModel = viewModel(
            repository,
            ControlledValidator(OcrCropContentValidationResult.Invalid(OcrCropContentInvalidReason.CROP_INCOMPLETE)),
        )
        load(viewModel)
        var confirmations = 0

        viewModel.confirmCrop { confirmations++ }
        advanceUntilIdle()

        assertEquals(0, repository.persistConfirmedCropCalls)
        assertEquals(0, repository.uploadCheckpointCalls)
        assertEquals(0, confirmations)
        assertEquals(MatchResultScreenshotCropError.CONTENT_INVALID, viewModel.uiState.value.error)
    }

    @Test
    fun autoProposedDraftStillRequiresCv03AndInvalidContentDoesNotPersist() = runTest {
        val repository = GateAssetRepository()
        val automatic = OcrNormalizedCropRect(0.15, 0.15, 0.85, 0.85)
        val proposer = FixedAutoCropProposer(MatchResultAutoCropResult.Proposed(automatic))
        val viewModel = viewModel(
            repository,
            ControlledValidator(OcrCropContentValidationResult.Invalid(OcrCropContentInvalidReason.CROP_INCOMPLETE)),
            proposer,
        )
        load(viewModel)

        assertEquals(automatic, viewModel.uiState.value.draftCrop)
        viewModel.confirmCrop {}
        advanceUntilIdle()

        assertEquals(1, proposer.calls)
        assertEquals(MatchResultScreenshotCropError.CONTENT_INVALID, viewModel.uiState.value.error)
        assertEquals(0, repository.persistConfirmedCropCalls)
        assertEquals(0, repository.uploadCheckpointCalls)
    }

    @Test
    fun autoProposedDraftUsesExistingValidPersistencePath() = runTest {
        val repository = GateAssetRepository()
        val automatic = OcrNormalizedCropRect(0.15, 0.15, 0.85, 0.85)
        val proposer = FixedAutoCropProposer(MatchResultAutoCropResult.Proposed(automatic))
        val viewModel = viewModel(
            repository,
            ControlledValidator(OcrCropContentValidationResult.Valid),
            proposer,
        )
        load(viewModel)
        var confirmations = 0

        viewModel.confirmCrop { confirmations++ }
        advanceUntilIdle()

        assertEquals(automatic, viewModel.uiState.value.confirmedCrop)
        assertEquals(1, repository.persistConfirmedCropCalls)
        assertEquals(1, repository.uploadCheckpointCalls)
        assertEquals(1, confirmations)
    }

    @Test
    fun userModifiedAutoProposalIsValidatedAndPersistedInsteadOfOriginalProposal() = runTest {
        val repository = GateAssetRepository()
        val automatic = OcrNormalizedCropRect(0.15, 0.15, 0.85, 0.85)
        val manual = OcrNormalizedCropRect(0.2, 0.1, 0.8, 0.9)
        val validator = ControlledValidator(OcrCropContentValidationResult.Valid)
        val viewModel = viewModel(
            repository,
            validator,
            FixedAutoCropProposer(MatchResultAutoCropResult.Proposed(automatic)),
        )
        load(viewModel)
        viewModel.onCropChanged(manual)
        viewModel.confirmCrop {}
        advanceUntilIdle()

        assertTrue(validator.lastPixelCrop != null)
        assertEquals(manual.left, repository.asset.cropLeft!!, 0.0)
        assertEquals(manual.right, repository.asset.cropRight!!, 0.0)
        assertEquals(1, repository.persistConfirmedCropCalls)
    }

    @Test
    fun indeterminateContentDoesNotPersistOrConfirm() = runTest {
        val repository = GateAssetRepository()
        val viewModel = viewModel(
            repository,
            ControlledValidator(
                OcrCropContentValidationResult.Indeterminate(
                    OcrCropContentIndeterminateReason.OCR_RECOGNITION_FAILED,
                ),
            ),
        )
        load(viewModel)
        var confirmations = 0

        viewModel.confirmCrop { confirmations++ }
        advanceUntilIdle()

        assertEquals(0, repository.persistConfirmedCropCalls)
        assertEquals(0, repository.uploadCheckpointCalls)
        assertEquals(0, confirmations)
        assertEquals(MatchResultScreenshotCropError.CONTENT_VALIDATION_FAILED, viewModel.uiState.value.error)
    }

    @Test
    fun duplicateConfirmWhileValidationIsSuspendedLaunchesOneValidator() = runTest {
        val repository = GateAssetRepository()
        val validator = ControlledValidator()
        val viewModel = viewModel(repository, validator)
        load(viewModel)

        viewModel.confirmCrop {}
        runCurrent()
        viewModel.confirmCrop {}
        assertEquals(1, validator.calls)

        validator.release.complete(OcrCropContentValidationResult.Valid)
        advanceUntilIdle()
    }

    @Test
    fun assetFlowEmissionDuringSuspendedValidationDoesNotAllowDuplicateConfirm() = runTest {
        val repository = GateAssetRepository()
        val validator = ControlledValidator()
        val viewModel = viewModel(repository, validator)
        load(viewModel)
        var confirmations = 0

        viewModel.confirmCrop { confirmations++ }
        runCurrent()
        repository.updateAsset(
            repository.asset.copy(
                uploadStatus = ScreenshotUploadStatus.PENDING.name,
                revision = repository.asset.revision + 1L,
            ),
        )
        runCurrent()

        assertTrue(viewModel.uiState.value.isValidating)
        viewModel.confirmCrop { confirmations++ }
        assertEquals(1, validator.calls)

        validator.release.complete(OcrCropContentValidationResult.Valid)
        advanceUntilIdle()

        assertEquals(1, repository.persistConfirmedCropCalls)
        assertEquals(1, repository.uploadCheckpointCalls)
        assertEquals(1, confirmations)
    }

    @Test
    fun staleCropResultCannotPersist() = runTest {
        val repository = GateAssetRepository()
        val validator = ControlledValidator()
        val viewModel = viewModel(repository, validator)
        load(viewModel)
        var confirmations = 0

        viewModel.confirmCrop { confirmations++ }
        runCurrent()
        viewModel.onCropChanged(OcrNormalizedCropRect(0.2, 0.1, 0.8, 0.9))
        validator.release.complete(OcrCropContentValidationResult.Valid)
        advanceUntilIdle()

        assertEquals(0, repository.persistConfirmedCropCalls)
        assertEquals(0, repository.uploadCheckpointCalls)
        assertEquals(0, confirmations)
        assertFalse(viewModel.uiState.value.isValidating)
    }

    @Test
    fun cancellationPropagatesWithoutContentInvalidError() = runTest {
        val repository = GateAssetRepository()
        val validator = object : MatchResultCropContentValidator {
            override suspend fun validate(
                role: MatchResultScreenshotRole,
                localFile: java.io.File,
                pixelCrop: OcrPixelCropRect,
            ): OcrCropContentValidationResult = throw CancellationException("cancelled")
        }
        val viewModel = viewModel(repository, validator)
        load(viewModel)
        var confirmations = 0

        viewModel.confirmCrop { confirmations++ }
        runCurrent()
        advanceUntilIdle()

        assertEquals(0, repository.persistConfirmedCropCalls)
        assertEquals(0, repository.uploadCheckpointCalls)
        assertEquals(0, confirmations)
        assertTrue(viewModel.uiState.value.error != MatchResultScreenshotCropError.CONTENT_INVALID)
        assertTrue(viewModel.uiState.value.error != MatchResultScreenshotCropError.CONTENT_VALIDATION_FAILED)
        assertFalse(viewModel.uiState.value.isValidating)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun invalidGeometrySkipsSemanticValidationAndPersistence() = runTest {
        val repository = GateAssetRepository()
        val validator = ControlledValidator(OcrCropContentValidationResult.Valid)
        val viewModel = viewModel(repository, validator)
        load(viewModel)
        viewModel.onCropChanged(OcrNormalizedCropRect(0.0, 0.0, 1.0, 0.05))

        viewModel.confirmCrop {}
        advanceUntilIdle()

        assertEquals(0, validator.calls)
        assertEquals(0, repository.persistConfirmedCropCalls)
        assertEquals(MatchResultScreenshotCropError.INVALID_CROP, viewModel.uiState.value.error)
    }

    private fun TestScope.load(viewModel: MatchResultScreenshotCropViewModel) {
        viewModel.load(
            GATE_TOURNAMENT_ID,
            GATE_MATCH_ID,
            MatchResultScreenshotRole.MATCH_RESULT_UPPER.name,
        )
        advanceUntilIdle()
    }

    private fun viewModel(
        repository: GateAssetRepository,
        validator: MatchResultCropContentValidator,
        autoCropProposer: MatchResultAutoCropProposer = MatchResultAutoCropProposer {
            MatchResultAutoCropResult.OcrFailed
        },
    ): MatchResultScreenshotCropViewModel {
        val root = Files.createTempDirectory("rank-forge-gate").toFile()
        val preserver = LocalImagePreserver(
            appPrivateRoot = root,
            sourceStreamOpener = ImageSourceStreamOpener { null },
            mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val file = preserver.matchResultPreservedFile(
            tournamentId = GATE_TOURNAMENT_ID,
            matchId = GATE_MATCH_ID,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            extension = "png",
        )
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        repository.updateAsset(repository.asset.copy(localRelativePath = preserver.relativePathFor(file)!!))
        return MatchResultScreenshotCropViewModel(
            observeMatches = ObserveMatchesUseCase(tournamentRepository),
            assetRepository = repository,
            localImagePreserver = preserver,
            uploadCheckpoint = MatchResultScreenshotUploadCheckpointAction {
                repository.uploadCheckpointCalls++
                MatchResultScreenshotUploadCheckpointResult.Completed
            },
            reconciliationScheduler = ScreenshotReconciliationScheduler(
                scope = CoroutineScope(SupervisorJob() + dispatcher),
                testOnly = true,
            ),
            contentValidator = validator,
            autoCropProposer = autoCropProposer,
        )
    }

    private class FixedAutoCropProposer(
        private val result: MatchResultAutoCropResult,
    ) : MatchResultAutoCropProposer {
        var calls = 0

        override suspend fun propose(localFile: java.io.File): MatchResultAutoCropResult {
            calls++
            return result
        }
    }

    private class ControlledValidator(
        private val immediateResult: OcrCropContentValidationResult? = null,
    ) : MatchResultCropContentValidator {
        var calls = 0
        var lastPixelCrop: OcrPixelCropRect? = null
        val release = CompletableDeferred<OcrCropContentValidationResult>()

        override suspend fun validate(
            role: MatchResultScreenshotRole,
            localFile: java.io.File,
            pixelCrop: OcrPixelCropRect,
        ): OcrCropContentValidationResult {
            calls++
            lastPixelCrop = pixelCrop
            return immediateResult ?: release.await()
        }
    }

    private class GateAssetRepository : MatchResultScreenshotAssetRepository {
        var asset = MatchResultScreenshotAssetEntity(
            tournamentId = GATE_TOURNAMENT_ID,
            matchId = GATE_MATCH_ID,
            screenshotKind = OcrScreenshotKind.MATCH_RESULT.name,
            screenshotRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER.name,
            ownerUserId = "owner",
            localRelativePath = "result.png",
            fileExtension = "png",
            mimeType = "image/png",
            originalWidth = 1600,
            originalHeight = 720,
            byteSize = 3,
            sha256 = "a".repeat(64),
            localStatus = ScreenshotLocalStatus.PRESERVED.name,
            uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
            uploadFailureCode = null,
            storageBucket = null,
            storageObjectPath = null,
            cropProfileId = null,
            cropLeft = null,
            cropTop = null,
            cropRight = null,
            cropBottom = null,
            createdAt = 1,
            updatedAt = 1,
            preservedAt = 1,
            uploadedAt = null,
            revision = 1,
        )
        private val state = MutableStateFlow(asset)
        var persistConfirmedCropCalls = 0
        var uploadCheckpointCalls = 0

        fun updateAsset(value: MatchResultScreenshotAssetEntity) {
            asset = value
            state.value = value
        }

        override fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
            state.map { listOf(it).filter { asset -> asset.matchId == matchId } }

        override fun observeByIdentity(identity: MatchResultScreenshotIdentity): Flow<MatchResultScreenshotAssetEntity?> =
            state.map { it.takeIf { asset -> asset.matches(identity) } }

        override suspend fun getByIdentity(identity: MatchResultScreenshotIdentity): MatchResultScreenshotAssetEntity? =
            state.value.takeIf { it.matches(identity) }

        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
            state.map { listOf(it).filter { asset -> asset.tournamentId == tournamentId } }

        override suspend fun findDuplicateFingerprint(identity: MatchResultScreenshotIdentity, sha256: String): MatchResultScreenshotAssetEntity? = null
        override suspend fun saveOrReplace(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetSaveResult {
            updateAsset(asset)
            return MatchResultScreenshotAssetSaveResult.Saved
        }
        override suspend fun markLocalMissing(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun markCleanupFailure(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit

        override suspend fun persistConfirmedCrop(
            identity: MatchResultScreenshotIdentity,
            crop: OcrNormalizedCropRect,
            updatedAt: Long,
        ): MatchResultScreenshotCropSaveResult {
            persistConfirmedCropCalls++
            updateAsset(asset.copy(cropProfileId = "match-result", cropLeft = crop.left, cropTop = crop.top, cropRight = crop.right, cropBottom = crop.bottom))
            return MatchResultScreenshotCropSaveResult.Saved
        }

        override suspend fun clearConfirmedCrop(identity: MatchResultScreenshotIdentity, updatedAt: Long): MatchResultScreenshotCropSaveResult =
            MatchResultScreenshotCropSaveResult.Saved
        override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) = Unit
        override suspend fun deleteByMatchId(matchId: String) = Unit

        private fun MatchResultScreenshotAssetEntity.matches(identity: MatchResultScreenshotIdentity): Boolean =
            tournamentId == identity.tournamentId && matchId == identity.matchId && screenshotRole == identity.role.name
    }
}
