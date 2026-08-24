package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudResult
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.data.local.TournamentLobbyTemplateAssetEntity
import com.hoggamers.rankforge.data.local.TournamentLobbyTemplateAssetRepository
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.nio.file.Files
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MatchLobbyScreenshotIntakeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var tournamentRepository: InMemoryTournamentRepository
    private lateinit var lobbyRepository: FakeLobbyRepository
    private lateinit var templateRepository: FakeTemplateRepository
    private val tournamentId = "lobby-intake-tournament"
    private val matchId = "lobby-intake-match"

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        Dispatchers.setMain(dispatcher)
        tournamentRepository = InMemoryTournamentRepository()
        templateRepository = FakeTemplateRepository()
        tournamentRepository.create(
            Tournament(
                id = tournamentId,
                name = "Lobby Cup",
                date = LocalDate.of(2026, 8, 13),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        tournamentRepository.saveTeamNames(
            tournamentId,
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        tournamentRepository.createDraftMatch(
            Match(
                id = matchId,
                tournamentId = tournamentId,
                matchNumber = 1,
                date = LocalDate.of(2026, 8, 13),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        lobbyRepository = FakeLobbyRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadRestoresOnlyTheExactMatchAcrossThreeLobbySlots() = runTest {
        val root = Files.createTempDirectory("lobby-intake-restore").toFile()
        val preserver = preserver(root)
        val firstFile = preserver.lobbyPreservedFile(tournamentId, matchId, 1, "png")
        firstFile.parentFile.mkdirs()
        firstFile.writeBytes(byteArrayOf(1, 2, 3))
        lobbyRepository.saveOrReplace(asset(1, matchId, preserver.relativePathFor(firstFile)!!, "one"))
        lobbyRepository.saveOrReplace(asset(2, matchId, "missing/path.png", "two"))
        lobbyRepository.saveOrReplace(asset(3, "other-match", "other/path.png", "three"))

        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isAvailable)
        assertEquals(3, state.slots.size)
        assertTrue(state.slot(1)?.selectedScreenshotUri != null)
        assertTrue(state.slot(2)?.isLocalFileMissing == true)
        assertFalse(state.slot(3)?.fingerprint == "three")
    }

    @Test
    fun validSelectionPersistsAndRequestsExactCropIndex() = runTest {
        val root = Files.createTempDirectory("lobby-intake-select").toFile()
        val preserver = preserver(root)
        val validator = ImageCandidateValidator(
            ImageCandidateMetadataReader {
                ImageCandidateReadResult.Metadata("image/png", 100, 100)
            },
        )
        val viewModel = viewModel(preserver, validator)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(2)
        viewModel.onPhotoPickerResult("picked")
        advanceUntilIdle()

        assertTrue(lobbyRepository.readByMatchAndIndex(matchId, 2) != null)
        assertEquals(2, viewModel.uiState.value.pendingCropNavigationSlotIndex)
        assertTrue(viewModel.uiState.value.slot(2)?.hasLinkedAsset == true)
    }

    @Test
    fun multiSelectionCapturesEmptySlotsAndMapsThreeUrisInPickerOrder() = runTest {
        val viewModel = viewModel(
            preserver(Files.createTempDirectory("lobby-batch-three").toFile()),
            bytesByUri = mapOf("one" to byteArrayOf(1), "two" to byteArrayOf(2), "three" to byteArrayOf(3)),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.requestMultiPhotoPicker()
        assertEquals(listOf(1, 2, 3), viewModel.uiState.value.multiPhotoPickerRequest?.targetSlotIndices)
        viewModel.onMultiPhotoPickerResult(listOf("one", "two", "three"))
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3), lobbyRepository.snapshot().map { it.lobbyScreenshotIndex }.sorted())
        assertEquals(
            MatchLobbyScreenshotCropBatch(1, listOf(2, 3)),
            viewModel.uiState.value.pendingCropBatch,
        )
        assertEquals(1, viewModel.uiState.value.pendingCropNavigationSlotIndex)
    }

    @Test
    fun multiSelectionTruncatesAtTwoUrisAndSkipsOccupiedSlot() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-batch-skip").toFile())
        val viewModel = viewModel(
            preserver,
            bytesByUri = mapOf("existing" to byteArrayOf(0), "two" to byteArrayOf(2), "three" to byteArrayOf(3)),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult("existing")
        advanceUntilIdle()
        viewModel.onCropNavigationHandled()

        viewModel.requestMultiPhotoPicker()
        assertEquals(listOf(2, 3), viewModel.uiState.value.multiPhotoPickerRequest?.targetSlotIndices)
        viewModel.onMultiPhotoPickerResult(listOf("two", "three", "ignored"))
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3), lobbyRepository.snapshot().map { it.lobbyScreenshotIndex }.sorted())
        assertEquals(MatchLobbyScreenshotCropBatch(2, listOf(3)), viewModel.uiState.value.pendingCropBatch)
        assertEquals(2, viewModel.uiState.value.pendingCropNavigationSlotIndex)
    }

    @Test
    fun failedBatchCandidateIsExcludedAndOnlyFirstSuccessfulItemNavigates() = runTest {
        val validator = ImageCandidateValidator(
            ImageCandidateMetadataReader { uri ->
                if (uri == "bad") ImageCandidateReadResult.Metadata("image/gif", 100, 100)
                else ImageCandidateReadResult.Metadata("image/png", 100, 100)
            },
        )
        val viewModel = viewModel(
            preserver(Files.createTempDirectory("lobby-batch-failure").toFile()),
            validator = validator,
            bytesByUri = mapOf("one" to byteArrayOf(1), "bad" to byteArrayOf(2), "three" to byteArrayOf(3)),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.requestMultiPhotoPicker()
        viewModel.onMultiPhotoPickerResult(listOf("one", "bad", "three"))
        advanceUntilIdle()

        assertEquals(listOf(1, 3), lobbyRepository.snapshot().map { it.lobbyScreenshotIndex }.sorted())
        assertEquals(MatchLobbyScreenshotCropBatch(1, listOf(3)), viewModel.uiState.value.pendingCropBatch)
        assertEquals(1, viewModel.uiState.value.pendingCropNavigationSlotIndex)
    }

    @Test
    fun successfulLobbyCropConfirmationAdvancesOrderedBatchAndClearsAfterLast() = runTest {
        val viewModel = viewModel(
            preserver(Files.createTempDirectory("lobby-crop-confirm-batch").toFile()),
            bytesByUri = mapOf(
                "one" to byteArrayOf(1),
                "two" to byteArrayOf(2),
                "three" to byteArrayOf(3),
            ),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.requestMultiPhotoPicker()
        viewModel.onMultiPhotoPickerResult(listOf("one", "two", "three"))
        advanceUntilIdle()

        assertEquals(2, viewModel.onCropConfirmed(tournamentId, matchId, 1))
        assertEquals(MatchLobbyScreenshotCropBatch(2, listOf(3)), viewModel.uiState.value.pendingCropBatch)
        assertEquals(3, viewModel.onCropConfirmed(tournamentId, matchId, 2))
        assertEquals(MatchLobbyScreenshotCropBatch(3, emptyList()), viewModel.uiState.value.pendingCropBatch)
        assertNull(viewModel.onCropConfirmed(tournamentId, matchId, 3))
        assertNull(viewModel.uiState.value.pendingCropBatch)
    }

    @Test
    fun lobbyCropCancelClearsBatchAndManualCropDoesNotResumeIt() = runTest {
        val viewModel = viewModel(
            preserver(Files.createTempDirectory("lobby-crop-cancel-batch").toFile()),
            bytesByUri = mapOf("one" to byteArrayOf(1), "two" to byteArrayOf(2)),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestMultiPhotoPicker()
        viewModel.onMultiPhotoPickerResult(listOf("one", "two"))
        advanceUntilIdle()

        viewModel.cancelCropBatch(tournamentId, matchId)
        assertNull(viewModel.uiState.value.pendingCropBatch)
        assertNull(viewModel.onCropConfirmed(tournamentId, matchId, 1))
    }

    @Test
    fun singleLobbyBatchConfirmationReturnsToReviewAndClearsBatch() = runTest {
        val viewModel = viewModel(
            preserver(Files.createTempDirectory("lobby-single-crop-confirm").toFile()),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestMultiPhotoPicker()
        viewModel.onMultiPhotoPickerResult(listOf("picked"))
        advanceUntilIdle()

        assertNull(viewModel.onCropConfirmed(tournamentId, matchId, 1))
        assertNull(viewModel.uiState.value.pendingCropBatch)
    }

    @Test
    fun cancelledBatchClearsRequestAndDoesNotNavigate() = runTest {
        val viewModel = viewModel(preserver(Files.createTempDirectory("lobby-batch-cancel").toFile()))
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.requestMultiPhotoPicker()
        viewModel.onMultiPhotoPickerResult(emptyList())

        assertEquals(null, viewModel.uiState.value.multiPhotoPickerRequest)
        assertEquals(null, viewModel.uiState.value.pendingCropBatch)
        assertEquals(null, viewModel.uiState.value.pendingCropNavigationSlotIndex)
    }

    @Test
    fun individualReplacementClearsAnOlderPendingBatch() = runTest {
        val viewModel = viewModel(
            preserver(Files.createTempDirectory("lobby-batch-replacement").toFile()),
            bytesByUri = mapOf("first" to byteArrayOf(1), "replacement" to byteArrayOf(2)),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestMultiPhotoPicker()
        viewModel.onMultiPhotoPickerResult(listOf("first"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.pendingCropBatch != null)

        viewModel.requestPhotoPicker(1)

        assertEquals(null, viewModel.uiState.value.pendingCropBatch)
        assertTrue(viewModel.uiState.value.slot(1)?.isPhotoPickerRequestActive == true)
    }

    @Test
    fun cancellingLobbyBatchMidValidationClearsOldFlagsAndKeepsReplacementState() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val validator = ImageCandidateValidator(
            ImageCandidateMetadataReader { uri ->
                if (uri == "mid") {
                    started.complete(Unit)
                    release.await()
                }
                ImageCandidateReadResult.Metadata("image/png", 100, 100)
            },
        )
        val viewModel = viewModel(
            preserver(Files.createTempDirectory("lobby-cancel-validation").toFile()),
            validator = validator,
            bytesByUri = mapOf("one" to byteArrayOf(1), "mid" to byteArrayOf(2), "three" to byteArrayOf(3)),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.requestMultiPhotoPicker()
        viewModel.onMultiPhotoPickerResult(listOf("one", "mid", "three"))
        advanceUntilIdle()

        assertTrue(started.isCompleted)
        assertTrue(viewModel.uiState.value.slot(2)?.isValidationInProgress == true)
        assertTrue(lobbyRepository.readByMatchAndIndex(matchId, 1) != null)

        viewModel.requestPhotoPicker(2)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.slot(2)?.isPhotoPickerRequestActive == true)
        assertFalse(viewModel.uiState.value.slot(2)?.isValidationInProgress == true)
        assertFalse(viewModel.uiState.value.slot(3)?.isBusy == true)
        assertTrue(lobbyRepository.readByMatchAndIndex(matchId, 1) != null)
    }

    @Test
    fun cancellingLobbyBatchMidPreservationClearsOldFlagsAndKeepsReplacementState() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val gatedPreserver = LocalImagePreserver(
            appPrivateRoot = Files.createTempDirectory("lobby-cancel-preservation").toFile(),
            sourceStreamOpener = ImageSourceStreamOpener { uri ->
                if (uri == "mid") {
                    started.complete(Unit)
                    release.await()
                }
                byteArrayOf(1, 2, 3).inputStream()
            },
            mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val viewModel = viewModel(
            gatedPreserver,
            bytesByUri = mapOf("one" to byteArrayOf(1), "mid" to byteArrayOf(2), "three" to byteArrayOf(3)),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.requestMultiPhotoPicker()
        viewModel.onMultiPhotoPickerResult(listOf("one", "mid", "three"))
        advanceUntilIdle()

        assertTrue(started.isCompleted)
        assertTrue(viewModel.uiState.value.slot(2)?.isPreservationInProgress == true)
        assertTrue(lobbyRepository.readByMatchAndIndex(matchId, 1) != null)

        viewModel.requestPhotoPicker(2)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.slot(2)?.isPhotoPickerRequestActive == true)
        assertFalse(viewModel.uiState.value.slot(2)?.isPreservationInProgress == true)
        assertFalse(viewModel.uiState.value.slot(3)?.isBusy == true)
        assertTrue(lobbyRepository.readByMatchAndIndex(matchId, 1) != null)
    }

    @Test
    fun lateLobbyBatchCompletionCannotMutateReplacementState() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val validator = ImageCandidateValidator(
            ImageCandidateMetadataReader { uri ->
                if (uri == "late") {
                    started.complete(Unit)
                    try {
                        release.await()
                    } catch (_: CancellationException) {
                        withContext(NonCancellable) { release.await() }
                    }
                }
                ImageCandidateReadResult.Metadata("image/png", 100, 100)
            },
        )
        val viewModel = viewModel(
            preserver(Files.createTempDirectory("lobby-late-completion").toFile()),
            validator = validator,
            bytesByUri = mapOf("one" to byteArrayOf(1), "late" to byteArrayOf(2)),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.requestMultiPhotoPicker()
        viewModel.onMultiPhotoPickerResult(listOf("one", "late"))
        advanceUntilIdle()
        assertTrue(started.isCompleted)

        viewModel.requestPhotoPicker(2)
        assertTrue(viewModel.uiState.value.slot(2)?.isPhotoPickerRequestActive == true)
        release.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.slot(2)?.isPhotoPickerRequestActive == true)
        assertFalse(lobbyRepository.readByMatchAndIndex(matchId, 2) != null)
        assertTrue(lobbyRepository.readByMatchAndIndex(matchId, 1) != null)
    }

    @Test
    fun lateInvalidValidationFromCancelledBatchIsIgnoredAndReplacementStateRemainsUnchanged() = runTest {
        val oldStarted = CompletableDeferred<Unit>()
        val oldRelease = CompletableDeferred<Unit>()
        val replacementStarted = CompletableDeferred<Unit>()
        val replacementRelease = CompletableDeferred<Unit>()
        val validator = ImageCandidateValidator(
            ImageCandidateMetadataReader { uri ->
                when (uri) {
                    "late-invalid" -> {
                        oldStarted.complete(Unit)
                        try {
                            oldRelease.await()
                        } catch (_: CancellationException) {
                            withContext(NonCancellable) { oldRelease.await() }
                        }
                        ImageCandidateReadResult.Metadata("image/gif", 100, 100)
                    }
                    "replacement" -> {
                        replacementStarted.complete(Unit)
                        replacementRelease.await()
                        ImageCandidateReadResult.Metadata("image/png", 100, 100)
                    }
                    else -> ImageCandidateReadResult.Metadata("image/png", 100, 100)
                }
            },
        )
        val viewModel = viewModel(
            preserver(Files.createTempDirectory("lobby-late-invalid").toFile()),
            validator = validator,
            bytesByUri = mapOf(
                "first" to byteArrayOf(1),
                "late-invalid" to byteArrayOf(2),
                "replacement" to byteArrayOf(3),
            ),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.requestMultiPhotoPicker()
        viewModel.onMultiPhotoPickerResult(listOf("first", "late-invalid"))
        advanceUntilIdle()
        assertTrue(oldStarted.isCompleted)

        viewModel.requestPhotoPicker(2)
        viewModel.onPhotoPickerResult("replacement")
        advanceUntilIdle()
        assertTrue(replacementStarted.isCompleted)

        val beforeLateCompletion = viewModel.uiState.value.slot(2)!!
        assertTrue(beforeLateCompletion.isValidationInProgress)
        assertNull(beforeLateCompletion.imageValidationError)

        oldRelease.complete(Unit)
        advanceUntilIdle()

        val afterLateCompletion = viewModel.uiState.value.slot(2)!!
        assertTrue(afterLateCompletion.isValidationInProgress)
        assertNull(afterLateCompletion.imageValidationError)

        replacementRelease.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.slot(2)?.hasLinkedAsset == true)
    }

    @Test
    fun lateOwnerFailureFromCancelledBatchCannotClearOrOverwriteNewerOperation() = runTest {
        val oldStarted = CompletableDeferred<Unit>()
        val oldRelease = CompletableDeferred<Unit>()
        val replacementStarted = CompletableDeferred<Unit>()
        val replacementRelease = CompletableDeferred<Unit>()
        var ownerCalls = 0
        val ownerProvider = object : ScreenshotOwnerProvider {
            override suspend fun currentOwnerUserId(): String? {
                ownerCalls += 1
                return when (ownerCalls) {
                    2 -> {
                        oldStarted.complete(Unit)
                        try {
                            oldRelease.await()
                        } catch (_: CancellationException) {
                            withContext(NonCancellable) { oldRelease.await() }
                        }
                        null
                    }
                    3 -> {
                        replacementStarted.complete(Unit)
                        replacementRelease.await()
                        "owner-1"
                    }
                    else -> "owner-1"
                }
            }
        }
        val viewModel = viewModel(
            preserver(Files.createTempDirectory("lobby-late-owner").toFile()),
            ownerProvider = ownerProvider,
            bytesByUri = mapOf(
                "first" to byteArrayOf(1),
                "late-owner" to byteArrayOf(2),
                "replacement" to byteArrayOf(3),
            ),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.requestMultiPhotoPicker()
        viewModel.onMultiPhotoPickerResult(listOf("first", "late-owner"))
        advanceUntilIdle()
        assertTrue(oldStarted.isCompleted)

        viewModel.requestPhotoPicker(2)
        viewModel.onPhotoPickerResult("replacement")
        advanceUntilIdle()
        assertTrue(replacementStarted.isCompleted)

        oldRelease.complete(Unit)
        advanceUntilIdle()

        val afterLateCompletion = viewModel.uiState.value.slot(2)!!
        assertTrue(afterLateCompletion.isValidationInProgress)
        assertNull(afterLateCompletion.preservationError)

        replacementRelease.complete(Unit)
        advanceUntilIdle()
        val completedReplacement = viewModel.uiState.value.slot(2)!!
        assertTrue(completedReplacement.hasLinkedAsset)
        assertNull(completedReplacement.preservationError)
    }

    @Test
    fun currentGenerationInvalidValidationStillReportsCorrectError() = runTest {
        val validator = ImageCandidateValidator(
            ImageCandidateMetadataReader { uri ->
                if (uri == "bad") {
                    ImageCandidateReadResult.Metadata("image/gif", 100, 100)
                } else {
                    ImageCandidateReadResult.Metadata("image/png", 100, 100)
                }
            },
        )
        val viewModel = viewModel(
            preserver(Files.createTempDirectory("lobby-current-invalid").toFile()),
            validator = validator,
            bytesByUri = mapOf("bad" to byteArrayOf(1)),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult("bad")
        advanceUntilIdle()

        val slot = viewModel.uiState.value.slot(1)!!
        assertEquals(ImageValidationError.UNSUPPORTED_FORMAT, slot.imageValidationError)
        assertFalse(slot.isBusy)
    }

    @Test
    fun currentGenerationOwnerFailureStillReportsCorrectError() = runTest {
        val viewModel = viewModel(
            preserver(Files.createTempDirectory("lobby-current-owner-failure").toFile()),
            ownerProvider = object : ScreenshotOwnerProvider {
                override suspend fun currentOwnerUserId(): String? = null
            },
            bytesByUri = mapOf("owner-failure" to byteArrayOf(1)),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult("owner-failure")
        advanceUntilIdle()

        val slot = viewModel.uiState.value.slot(1)!!
        assertEquals(MatchLobbyScreenshotPreservationError.OWNER_MISSING, slot.preservationError)
        assertFalse(slot.isBusy)
        assertFalse(slot.hasLinkedAsset)
        assertNull(lobbyRepository.readByMatchAndIndex(matchId, 1))
    }

    @Test
    fun staleDuplicateFingerprintCleanupCompletionIsIgnoredAndCannotMutateReplacementState() = runTest {
        val cleanupStarted = CompletableDeferred<Unit>()
        val cleanupRelease = CompletableDeferred<Unit>()
        val replacementStarted = CompletableDeferred<Unit>()
        val replacementRelease = CompletableDeferred<Unit>()
        val operations = TestFileOperations(
            gateListFilesCall = 3,
            gateStarted = cleanupStarted,
            gateRelease = cleanupRelease,
        )
        val validator = ImageCandidateValidator(
            ImageCandidateMetadataReader { uri ->
                if (uri == "replacement") {
                    replacementStarted.complete(Unit)
                    replacementRelease.await()
                }
                ImageCandidateReadResult.Metadata("image/png", 100, 100)
            },
        )
        val viewModel = viewModel(
            preserver(
                Files.createTempDirectory("lobby-stale-cleanup").toFile(),
                operations = operations,
                ioDispatcher = Dispatchers.IO,
            ),
            validator = validator,
            bytesByUri = mapOf(
                "first" to byteArrayOf(1),
                "duplicate" to byteArrayOf(2),
                "replacement" to byteArrayOf(3),
            ),
        )
        lobbyRepository.saveResults += MatchLobbyScreenshotAssetSaveResult.Saved
        lobbyRepository.saveResults += MatchLobbyScreenshotAssetSaveResult.DuplicateFingerprint(
            existing = asset(2, matchId, "screenshots/existing.png", "existing"),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.requestMultiPhotoPicker()
        viewModel.onMultiPhotoPickerResult(listOf("first", "duplicate"))
        advanceUntilIdle()
        cleanupStarted.await()

        viewModel.requestPhotoPicker(2)
        viewModel.onPhotoPickerResult("replacement")
        advanceUntilIdle()
        assertTrue(replacementStarted.isCompleted)

        val beforeLateCleanup = viewModel.uiState.value.slot(2)!!
        assertTrue(beforeLateCleanup.isValidationInProgress)
        assertNull(beforeLateCleanup.preservationError)

        cleanupRelease.complete(Unit)
        advanceUntilIdle()

        val afterLateCleanup = viewModel.uiState.value.slot(2)!!
        assertTrue(afterLateCleanup.isValidationInProgress)
        assertNull(afterLateCleanup.preservationError)

        replacementRelease.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun currentGenerationDuplicateFingerprintCleanupStillReportsSaveFailure() = runTest {
        val viewModel = viewModel(
            preserver(
                Files.createTempDirectory("lobby-current-cleanup").toFile(),
                operations = TestFileOperations(failDelete = true),
            ),
            bytesByUri = mapOf("duplicate" to byteArrayOf(1)),
        )
        lobbyRepository.saveResults += MatchLobbyScreenshotAssetSaveResult.DuplicateFingerprint(
            existing = asset(1, matchId, "screenshots/existing.png", "existing"),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult("duplicate")
        advanceUntilIdle()

        val slot = viewModel.uiState.value.slot(1)!!
        assertEquals(MatchLobbyScreenshotPreservationError.SAVE_FAILED, slot.preservationError)
        assertFalse(slot.isBusy)
        assertFalse(slot.hasLinkedAsset)
    }

    @Test
    fun loadingAnotherLobbyMatchClearsActiveBatchStateAndIgnoresLateCompletion() = runTest {
        val otherMatchId = "lobby-other-match"
        tournamentRepository.createDraftMatch(
            Match(
                id = otherMatchId,
                tournamentId = tournamentId,
                matchNumber = 2,
                date = LocalDate.of(2026, 8, 13),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val validator = ImageCandidateValidator(
            ImageCandidateMetadataReader { uri ->
                if (uri == "mid") {
                    started.complete(Unit)
                    try {
                        release.await()
                    } catch (_: CancellationException) {
                        withContext(NonCancellable) { release.await() }
                    }
                }
                ImageCandidateReadResult.Metadata("image/png", 100, 100)
            },
        )
        val viewModel = viewModel(
            preserver(Files.createTempDirectory("lobby-match-change").toFile()),
            validator = validator,
            bytesByUri = mapOf("one" to byteArrayOf(1), "mid" to byteArrayOf(2)),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestMultiPhotoPicker()
        viewModel.onMultiPhotoPickerResult(listOf("one", "mid"))
        advanceUntilIdle()
        assertTrue(started.isCompleted)

        viewModel.load(tournamentId, otherMatchId)
        advanceUntilIdle()
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(otherMatchId, viewModel.uiState.value.matchId)
        assertFalse(viewModel.uiState.value.slots.any { it.isBusy })
        assertEquals(null, viewModel.uiState.value.multiPhotoPickerRequest)
        assertEquals(null, viewModel.uiState.value.pendingCropBatch)
        assertEquals(null, viewModel.uiState.value.pendingCropNavigationSlotIndex)
    }

    @Test
    fun freshSelectionRemainsPendingAndDoesNotUploadBeforeCropConfirmation() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-intake-local-first").toFile())
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(2)
        viewModel.onPhotoPickerResult("picked")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.pendingCropNavigationSlotIndex)
        assertEquals(
            ScreenshotUploadStatus.PENDING.name,
            lobbyRepository.readByMatchAndIndex(matchId, 2)?.uploadStatus,
        )
        assertTrue(lobbyRepository.readByMatchAndIndex(matchId, 2)?.localRelativePath?.isNotBlank() == true)
    }

    @Test
    fun freshSelectionSupportsEachLobbyIndexWithoutPreCropUpload() = runTest {
        val uris = listOf("picked-one", "picked-two", "picked-three")
        val viewModel = viewModel(
            preserver(Files.createTempDirectory("lobby-intake-all-indices").toFile()),
            bytesByUri = uris.mapIndexed { index, uri -> uri to byteArrayOf((index + 1).toByte()) }.toMap(),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        uris.forEachIndexed { offset, uri ->
            val index = offset + 1
            viewModel.requestPhotoPicker(index)
            viewModel.onPhotoPickerResult(uri)
            advanceUntilIdle()

            assertEquals(index, viewModel.uiState.value.pendingCropNavigationSlotIndex)
            assertEquals(ScreenshotUploadStatus.PENDING.name, lobbyRepository.readByMatchAndIndex(matchId, index)?.uploadStatus)
            viewModel.onCropNavigationHandled()
        }

        assertEquals(uris.indices.map { it + 1 }, lobbyRepository.snapshot().map { it.lobbyScreenshotIndex }.sorted())
    }

    @Test
    fun cancellingPhotoPickerDoesNotChangeExistingAsset() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-intake-picker-cancel").toFile())
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult("picked")
        advanceUntilIdle()
        val beforeCancel = lobbyRepository.readByMatchAndIndex(matchId, 1)

        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult(null)
        advanceUntilIdle()

        assertEquals(beforeCancel, lobbyRepository.readByMatchAndIndex(matchId, 1))
    }

    @Test
    fun sameIdentityRecoveryRetainsSuccessfulCloudState() = runTest {
        val root = Files.createTempDirectory("lobby-intake-cloud-recovery").toFile()
        val preserver = preserver(root)
        val fingerprint = byteArrayOf(1, 2, 3).sha256()
        lobbyRepository.saveOrReplace(
            asset(1, matchId, "missing/path.png", fingerprint).copy(
                storageBucket = "ocr-screenshots",
                storageObjectPath = "cloud/path.png",
                uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
                uploadedAt = 4,
                cropProfileId = "lobby",
                cropLeft = 0.1,
                cropTop = 0.1,
                cropRight = 0.9,
                cropBottom = 0.9,
            ),
        )
        val cloudStarted = CompletableDeferred<Unit>()
        val cloudResult = CompletableDeferred<MatchLobbyScreenshotAssetCloudResult>()
        val cloud = SuspendingCloudDataSource(cloudStarted, cloudResult)
        val viewModel = viewModel(preserver, cloudDataSource = cloud)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult("picked")
        advanceUntilIdle()

        assertTrue(preserver.lobbyPreservedFile(tournamentId, matchId, 1, "png").isFile)
        assertTrue(cloudStarted.isCompleted)
        assertFalse(cloudResult.isCompleted)
        assertEquals(1, viewModel.uiState.value.pendingCropNavigationSlotIndex)
        cloudResult.complete(MatchLobbyScreenshotAssetCloudResult.Success)
        advanceUntilIdle()

        val restored = lobbyRepository.readByMatchAndIndex(matchId, 1)
        assertEquals(ScreenshotUploadStatus.UPLOADED.name, restored?.uploadStatus)
        assertEquals("cloud/path.png", restored?.storageObjectPath)
        assertEquals("lobby", restored?.cropProfileId)
        assertEquals(2L, restored?.revision)
        assertEquals(ScreenshotLocalStatus.PRESERVED.name, restored?.localStatus)
        assertEquals(1, cloud.upserts.size)
        assertEquals(restored, cloud.upserts.single())
        assertEquals("ocr-screenshots", cloud.upserts.single().storageBucket)
        assertEquals("cloud/path.png", cloud.upserts.single().storageObjectPath)
        assertEquals(4L, cloud.upserts.single().uploadedAt)
        assertEquals(0.1, cloud.upserts.single().cropLeft)
    }

    @Test
    fun sameIdentityWithMissingLocalFileRepreservesBeforeCropNavigation() = runTest {
        val root = Files.createTempDirectory("lobby-intake-recover").toFile()
        val preserver = preserver(root)
        val fingerprint = byteArrayOf(1, 2, 3).sha256()
        val existingAsset = asset(1, matchId, "missing/lobby.png", fingerprint).copy(
            cropProfileId = "lobby",
            cropLeft = 0.1,
            cropTop = 0.1,
            cropRight = 0.9,
            cropBottom = 0.9,
            createdAt = 41,
            updatedAt = 42,
            preservedAt = 42,
            revision = 7,
        )
        lobbyRepository.saveOrReplace(existingAsset)

        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.slot(1)?.isLocalFileMissing == true)

        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult("picked")
        advanceUntilIdle()

        assertTrue(preserver.lobbyPreservedFile(tournamentId, matchId, 1, "png").isFile)
        val restored = lobbyRepository.readByMatchAndIndex(matchId, 1)
        assertTrue(restored != null)
        assertEquals(fingerprint, restored?.sha256)
        assertEquals(41L, restored?.createdAt)
        assertEquals(8L, restored?.revision)
        assertEquals(ScreenshotUploadStatus.PENDING.name, restored?.uploadStatus)
        assertEquals("lobby", restored?.cropProfileId)
        assertTrue(viewModel.uiState.value.slot(1)?.hasLinkedAsset == true)
        assertFalse(viewModel.uiState.value.slot(1)?.isLocalFileMissing == true)
        assertEquals(1, viewModel.uiState.value.pendingCropNavigationSlotIndex)
    }

    @Test
    fun finalizedMatchBlocksPickerAndRemoval() = runTest {
        tournamentRepository.finalizeDraftMatch(
            matchId = matchId,
            placements = (1..12).map { com.hoggamers.rankforge.domain.tournament.MatchPlacement(it, it) },
            kills = (1..12).map { com.hoggamers.rankforge.domain.tournament.MatchKill(it, 0) },
        )
        val viewModel = viewModel(preserver(Files.createTempDirectory("lobby-intake-finalized").toFile()))
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(1)
        viewModel.removeScreenshot(1)
        assertEquals(MatchLobbyScreenshotIntakeError.FINALIZED_MATCH, viewModel.uiState.value.intakeError)
        assertFalse(viewModel.uiState.value.slot(1)?.isPhotoPickerRequestActive == true)
    }

    @Test
    fun noTemplateRestoresOffStateAfterLoad() = runTest {
        val viewModel = viewModel(preserver(Files.createTempDirectory("lobby-toggle-off").toFile()))

        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLobbySavedForNextMatches)
    }

    @Test
    fun completeTemplateBeforeLoadRestoresOnState() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-toggle-restore").toFile())
        seedActiveTemplate(preserver)
        val viewModel = viewModel(preserver)

        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLobbySavedForNextMatches)
    }

    @Test
    fun saveTransitionsToOnOnlyAfterTemplateRepositoryEmits() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-toggle-save").toFile())
        seedReadyCurrentAssets(preserver)
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canSaveLobbyForNextMatches)
        viewModel.saveLobbyForNextMatches()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLobbySavedForNextMatches)
        assertEquals(MatchLobbyTemplateSaveStatus.SAVED, viewModel.uiState.value.lobbyTemplateSaveStatus)
    }

    @Test
    fun unsaveRemainsAllowedWhenCurrentLobbyIsIncomplete() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-toggle-unsave-incomplete").toFile())
        seedActiveTemplate(preserver)
        lobbyRepository.deleteByIdentity(MatchLobbyScreenshotIdentity(tournamentId, matchId, 2))
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLobbySavedForNextMatches)
        assertTrue(viewModel.uiState.value.canUnsaveLobbyForNextMatches)
        viewModel.unsaveLobbyForNextMatches()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLobbySavedForNextMatches)
    }

    @Test
    fun finalizedMatchWithTemplateOnCanUnsaveButFinalizedOffCannotSave() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-toggle-finalized").toFile())
        seedActiveTemplate(preserver)
        tournamentRepository.finalizeDraftMatch(
            matchId = matchId,
            placements = (1..12).map { com.hoggamers.rankforge.domain.tournament.MatchPlacement(it, it) },
            kills = (1..12).map { com.hoggamers.rankforge.domain.tournament.MatchKill(it, 0) },
        )
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canUnsaveLobbyForNextMatches)
        viewModel.unsaveLobbyForNextMatches()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLobbySavedForNextMatches)
        assertFalse(viewModel.uiState.value.canSaveLobbyForNextMatches)
    }

    @Test
    fun unsaveFailureKeepsObservedStateOnAndReportsFailure() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-toggle-failure").toFile())
        seedActiveTemplate(preserver)
        templateRepository.throwOnDelete = true
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.unsaveLobbyForNextMatches()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLobbySavedForNextMatches)
        assertEquals(MatchLobbyTemplateSaveStatus.FAILED, viewModel.uiState.value.lobbyTemplateSaveStatus)
    }

    @Test
    fun currentLobbyChangesDoNotChangeSavedTemplateState() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-toggle-snapshot").toFile())
        seedActiveTemplate(preserver)
        val savedBefore = templateRepository.getByTournamentId(tournamentId)
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.removeScreenshot(1)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLobbySavedForNextMatches)
        assertEquals(savedBefore, templateRepository.getByTournamentId(tournamentId))
    }

    @Test
    fun repeatedUnsaveIsIgnoredWhileMutationIsInProgress() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-toggle-concurrency").toFile())
        seedActiveTemplate(preserver)
        val gate = CompletableDeferred<Unit>()
        templateRepository.deleteGate = gate
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.unsaveLobbyForNextMatches()
        advanceUntilIdle()
        viewModel.unsaveLobbyForNextMatches()
        assertEquals(1, templateRepository.deleteCalls)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    private suspend fun seedReadyCurrentAssets(preserver: LocalImagePreserver) {
        (1..3).forEach { index ->
            val file = preserver.lobbyPreservedFile(tournamentId, matchId, index, "png")
            file.parentFile?.mkdirs()
            file.writeBytes(byteArrayOf(index.toByte(), 1, 2))
            lobbyRepository.saveOrReplace(
                asset(index, matchId, preserver.relativePathFor(file)!!, "ready-$index").copy(
                    cropProfileId = "lobby",
                    cropLeft = 0.1,
                    cropTop = 0.1,
                    cropRight = 0.9,
                    cropBottom = 0.9,
                ),
            )
        }
    }

    private suspend fun seedActiveTemplate(preserver: LocalImagePreserver) {
        seedReadyCurrentAssets(preserver)
        assertEquals(
            SaveLobbyTemplateResult.Saved,
            SaveLobbyTemplateUseCase(
                assetRepository = lobbyRepository,
                templateRepository = templateRepository,
                localImagePreserver = preserver,
                clock = java.time.Clock.systemUTC(),
            )(tournamentId, matchId),
        )
    }

    private fun viewModel(
        preserver: LocalImagePreserver,
        validator: ImageCandidateValidator = ImageCandidateValidator(ImageCandidateMetadataReader { ImageCandidateReadResult.Metadata("image/png", 100, 100) }),
        cloudDataSource: MatchLobbyScreenshotAssetCloudDataSource = FakeCloudDataSource(),
        bytesByUri: Map<String, ByteArray> = mapOf("picked" to byteArrayOf(1, 2, 3)),
        ownerProvider: ScreenshotOwnerProvider? = null,
    ) = MatchLobbyScreenshotIntakeViewModel(
        observeMatches = ObserveMatchesUseCase(tournamentRepository),
        imageCandidateValidator = validator,
        duplicateDetector = MatchLobbyScreenshotDuplicateDetector(
            ImageSourceFingerprintGenerator(
                ImageSourceStreamOpener { uri -> bytesByUri.getValue(uri).inputStream() },
                Dispatchers.Unconfined,
            ),
            lobbyRepository,
            screenshotOwnerProvider = ownerProvider ?: object : ScreenshotOwnerProvider {
                override suspend fun currentOwnerUserId(): String = "owner-1"
            },
        ),
        localImagePreserver = preserver,
        assetRepository = lobbyRepository,
        screenshotOwnerProvider = ownerProvider ?: object : ScreenshotOwnerProvider {
            override suspend fun currentOwnerUserId(): String = "owner-1"
        },
        clock = java.time.Clock.systemUTC(),
        saveLobbyTemplate = SaveLobbyTemplateUseCase(
            assetRepository = lobbyRepository,
            templateRepository = templateRepository,
            localImagePreserver = preserver,
            clock = java.time.Clock.systemUTC(),
        ),
        unsaveLobbyTemplate = UnsaveLobbyTemplateUseCase(
            templateRepository = templateRepository,
            localImagePreserver = preserver,
        ),
        templateRepository = templateRepository,
        cloudDataSource = cloudDataSource,
    )

    private fun preserver(
        root: java.io.File,
        operations: LocalImageFileOperations = TestFileOperations(),
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = LocalImagePreserver(
        appPrivateRoot = root,
        sourceStreamOpener = ImageSourceStreamOpener { byteArrayOf(1, 2, 3).inputStream() },
        mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
        fileOperations = operations,
        ioDispatcher = ioDispatcher,
    )

    private class TestFileOperations(
        private val failDelete: Boolean = false,
        private val gateListFilesCall: Int? = null,
        private val gateStarted: CompletableDeferred<Unit>? = null,
        private val gateRelease: CompletableDeferred<Unit>? = null,
    ) : LocalImageFileOperations {
        private var listFilesCalls = 0

        override fun ensureDirectory(directory: java.io.File): Boolean =
            directory.isDirectory || (directory.mkdirs() && directory.isDirectory)

        override fun createTempFile(directory: java.io.File): java.io.File =
            java.io.File.createTempFile("original-", ".tmp", directory)

        override fun openOutput(file: java.io.File): java.io.OutputStream =
            java.io.FileOutputStream(file)

        override fun atomicMove(source: java.io.File, target: java.io.File): Boolean {
            if (target.exists()) target.delete()
            return source.renameTo(target)
        }

        override fun listFiles(directory: java.io.File): List<java.io.File>? {
            listFilesCalls += 1
            if (listFilesCalls == gateListFilesCall) {
                gateStarted?.complete(Unit)
                gateRelease?.let { release ->
                    kotlinx.coroutines.runBlocking { release.await() }
                }
            }
            return if (!directory.exists()) emptyList() else directory.listFiles()?.toList()
        }

        override fun delete(file: java.io.File): Boolean =
            if (failDelete) false else !file.exists() || file.delete()
    }

    private fun asset(index: Int, assetMatchId: String, path: String, sha: String) = MatchLobbyScreenshotAssetEntity(
        tournamentId = tournamentId,
        matchId = assetMatchId,
        lobbyScreenshotIndex = index,
        ownerUserId = "owner-1",
        localRelativePath = path,
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 100,
        originalHeight = 100,
        byteSize = 3,
        sha256 = sha,
        localStatus = ScreenshotLocalStatus.PRESERVED.name,
        uploadStatus = ScreenshotUploadStatus.PENDING.name,
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

    private class FakeLobbyRepository : MatchLobbyScreenshotAssetRepository {
        private val state = MutableStateFlow<List<MatchLobbyScreenshotAssetEntity>>(emptyList())
        val saveResults = mutableListOf<MatchLobbyScreenshotAssetSaveResult>()
        override fun observeByMatchId(matchId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> =
            state.asStateFlow().let { flow -> kotlinx.coroutines.flow.flow { flow.collect { emit(it.filter { asset -> asset.matchId == matchId }) } } }
        override fun observeByMatchIdAndOwner(matchId: String, ownerUserId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> =
            state.asStateFlow().let { flow -> kotlinx.coroutines.flow.flow { flow.collect { emit(it.filter { asset -> asset.matchId == matchId && asset.ownerUserId == ownerUserId }) } } }
        override fun observeByIdentity(identity: MatchLobbyScreenshotIdentity): Flow<MatchLobbyScreenshotAssetEntity?> =
            state.asStateFlow().let { flow -> kotlinx.coroutines.flow.flow { flow.collect { emit(it.firstOrNull { asset -> asset.matchId == identity.matchId && asset.lobbyScreenshotIndex == identity.lobbyScreenshotIndex }) } } }
        override fun observeByIdentityAndOwner(identity: MatchLobbyScreenshotIdentity, ownerUserId: String): Flow<MatchLobbyScreenshotAssetEntity?> =
            state.asStateFlow().let { flow -> kotlinx.coroutines.flow.flow { flow.collect { emit(it.firstOrNull { asset -> asset.ownerUserId == ownerUserId && asset.matchId == identity.matchId && asset.lobbyScreenshotIndex == identity.lobbyScreenshotIndex }) } } }
        override suspend fun getByIdentity(identity: MatchLobbyScreenshotIdentity) = state.value.firstOrNull { it.matchId == identity.matchId && it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex }
        override suspend fun getByIdentityAndOwner(identity: MatchLobbyScreenshotIdentity, ownerUserId: String) = state.value.firstOrNull { it.ownerUserId == ownerUserId && it.matchId == identity.matchId && it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex }
        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = state.asStateFlow()
        override suspend fun findDuplicateFingerprint(identity: MatchLobbyScreenshotIdentity, sha256: String) = state.value.firstOrNull { it.tournamentId == identity.tournamentId && it.sha256 == sha256 && !(it.matchId == identity.matchId && it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex) }
        override suspend fun saveOrReplace(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetSaveResult {
            val result = saveResults.removeFirstOrNull() ?: MatchLobbyScreenshotAssetSaveResult.Saved
            if (result == MatchLobbyScreenshotAssetSaveResult.Saved) {
                state.value = state.value.filterNot { it.matchId == asset.matchId && it.lobbyScreenshotIndex == asset.lobbyScreenshotIndex } + asset
            }
            return result
        }
        override suspend fun saveOrReplaceByOwner(asset: MatchLobbyScreenshotAssetEntity, ownerUserId: String): MatchLobbyScreenshotAssetSaveResult =
            if (asset.ownerUserId != ownerUserId) MatchLobbyScreenshotAssetSaveResult.AuthenticationRequired else saveOrReplace(asset)
        override suspend fun markLocalMissing(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun markLocalMissingByOwner(identity: MatchLobbyScreenshotIdentity, ownerUserId: String, updatedAt: Long): Boolean = getByIdentityAndOwner(identity, ownerUserId) != null
        override suspend fun markCleanupFailure(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) { state.value = state.value.filterNot { it.matchId == identity.matchId && it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex } }
        override suspend fun deleteByIdentityAndOwner(identity: MatchLobbyScreenshotIdentity, ownerUserId: String): Boolean {
            if (getByIdentityAndOwner(identity, ownerUserId) == null) return false
            deleteByIdentity(identity)
            return true
        }
        override suspend fun deleteByMatchId(matchId: String) { state.value = state.value.filterNot { it.matchId == matchId } }
        override suspend fun persistConfirmedCrop(identity: MatchLobbyScreenshotIdentity, crop: OcrNormalizedCropRect, updatedAt: Long) = MatchLobbyScreenshotCropSaveResult.Saved
        override suspend fun persistConfirmedCropByOwner(identity: MatchLobbyScreenshotIdentity, ownerUserId: String, crop: OcrNormalizedCropRect, updatedAt: Long) =
            if (getByIdentityAndOwner(identity, ownerUserId) == null) MatchLobbyScreenshotCropSaveResult.MissingAsset else persistConfirmedCrop(identity, crop, updatedAt)
        override suspend fun clearConfirmedCrop(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = MatchLobbyScreenshotCropSaveResult.Saved
        override suspend fun clearConfirmedCropByOwner(identity: MatchLobbyScreenshotIdentity, ownerUserId: String, updatedAt: Long) =
            if (getByIdentityAndOwner(identity, ownerUserId) == null) MatchLobbyScreenshotCropSaveResult.MissingAsset else clearConfirmedCrop(identity, updatedAt)
        fun readByMatchAndIndex(matchId: String, index: Int) = state.value.firstOrNull { it.matchId == matchId && it.lobbyScreenshotIndex == index }
        fun snapshot() = state.value
    }

    private class FakeCloudDataSource : MatchLobbyScreenshotAssetCloudDataSource {
        val upserts = mutableListOf<MatchLobbyScreenshotAssetEntity>()

        override suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetCloudResult {
            upserts += asset
            return MatchLobbyScreenshotAssetCloudResult.Success
        }

        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) =
            MatchLobbyScreenshotAssetCloudResult.Success
    }

    private class FakeTemplateRepository : TournamentLobbyTemplateAssetRepository {
        private val state = MutableStateFlow<List<TournamentLobbyTemplateAssetEntity>>(emptyList())
        var throwOnDelete = false
        var deleteGate: CompletableDeferred<Unit>? = null
        var deleteCalls = 0

        override fun observeByTournamentId(tournamentId: String): Flow<List<TournamentLobbyTemplateAssetEntity>> =
            state.asStateFlow().let { flow ->
                kotlinx.coroutines.flow.flow {
                    flow.collect { templates ->
                        emit(templates.filter { it.tournamentId == tournamentId })
                    }
                }
            }

        override suspend fun getByTournamentId(tournamentId: String): List<TournamentLobbyTemplateAssetEntity> =
            state.value.filter { it.tournamentId == tournamentId }

        override suspend fun replaceForTournament(
            tournamentId: String,
            assets: List<TournamentLobbyTemplateAssetEntity>,
        ) {
            state.value = state.value.filterNot { it.tournamentId == tournamentId } + assets
        }

        override suspend fun deleteByTournamentId(tournamentId: String) {
            deleteCalls += 1
            deleteGate?.await()
            if (throwOnDelete) error("delete failed")
            state.value = state.value.filterNot { it.tournamentId == tournamentId }
        }

        fun set(templates: List<TournamentLobbyTemplateAssetEntity>) {
            state.value = templates
        }
    }

    private class SuspendingCloudDataSource(
        private val started: CompletableDeferred<Unit>,
        private val result: CompletableDeferred<MatchLobbyScreenshotAssetCloudResult>,
    ) : MatchLobbyScreenshotAssetCloudDataSource {
        val upserts = mutableListOf<MatchLobbyScreenshotAssetEntity>()

        override suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetCloudResult {
            upserts += asset
            started.complete(Unit)
            return result.await()
        }

        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) =
            MatchLobbyScreenshotAssetCloudResult.Success
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}
