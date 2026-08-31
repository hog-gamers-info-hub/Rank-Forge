package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchResultScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.OcrScreenshotKind
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import java.io.File
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val CANCEL_CONTRACT_TOURNAMENT_ID = "33333333-3333-3333-3333-333333333333"

@OptIn(ExperimentalCoroutinesApi::class)
class MatchReviewResultScreenshotCancelContractTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var tournamentRepository: InMemoryTournamentRepository
    private lateinit var matchId: String

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        Dispatchers.setMain(dispatcher)
        tournamentRepository = InMemoryTournamentRepository()
        tournamentRepository.create(
            Tournament(
                id = CANCEL_CONTRACT_TOURNAMENT_ID,
                name = "Cancel Contract Cup",
                date = LocalDate.of(2026, 8, 31),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        tournamentRepository.saveTeamNames(
            CANCEL_CONTRACT_TOURNAMENT_ID,
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        matchId = "cancel-contract-match"
        tournamentRepository.createDraftMatch(
            Match(
                id = matchId,
                tournamentId = CANCEL_CONTRACT_TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 8, 31),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cancelAfterNewSelectionRemovesUnconfirmedResultScreenshot() = runTest {
        val selectedUri = "content://picker/new-upper"
        val root = Files.createTempDirectory("pointiq-result-cancel-new").toFile()
        val assetRepository = CancelContractAssetRepository()
        val viewModel = viewModel(
            bytesByUri = mapOf(selectedUri to byteArrayOf(1, 2, 3)),
            assetRepository = assetRepository,
            root = root,
        )

        viewModel.load(CANCEL_CONTRACT_TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_UPPER, selectedUri)
        advanceUntilIdle()

        val identity = identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        val selectedAsset = assetRepository.getByIdentity(identity)
        assertNotNull(selectedAsset)
        val selectedFile = File(root, selectedAsset!!.localRelativePath)
        assertTrue(selectedFile.isFile)
        assertTrue(
            viewModel.uiState.value.resultScreenshots
                .slot(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
                .hasLinkedAsset,
        )

        viewModel.onNavigationHandled()
        viewModel.cancelResultScreenshotCrop(
            CANCEL_CONTRACT_TOURNAMENT_ID,
            matchId,
            MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )
        advanceUntilIdle()

        assertFalse(
            viewModel.uiState.value.resultScreenshots
                .slot(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
                .hasLinkedAsset,
        )
        assertNull(assetRepository.getByIdentity(identity))
        assertFalse(selectedFile.exists())
    }

    @Test
    fun cancelAfterConfirmedSelectionKeepsCommittedResultScreenshotAndCrop() = runTest {
        val selectedUri = "content://picker/confirmed-upper"
        val root = Files.createTempDirectory("pointiq-result-cancel-confirmed").toFile()
        val assetRepository = CancelContractAssetRepository()
        val viewModel = viewModel(
            bytesByUri = mapOf(selectedUri to byteArrayOf(4, 5, 6)),
            assetRepository = assetRepository,
            root = root,
        )

        viewModel.load(CANCEL_CONTRACT_TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_UPPER, selectedUri)
        advanceUntilIdle()
        viewModel.onNavigationHandled()

        val identity = identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        val crop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9)
        assertEquals(
            MatchResultScreenshotCropSaveResult.Saved,
            assetRepository.persistConfirmedCrop(identity, crop, updatedAt = 20L),
        )
        viewModel.onResultCropConfirmed(
            CANCEL_CONTRACT_TOURNAMENT_ID,
            matchId,
            MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )
        advanceUntilIdle()

        val committedAsset = assetRepository.getByIdentity(identity)
        assertNotNull(committedAsset)
        val committedFile = File(root, committedAsset!!.localRelativePath)
        assertTrue(committedFile.isFile)

        viewModel.cancelResultScreenshotCrop(
            CANCEL_CONTRACT_TOURNAMENT_ID,
            matchId,
            MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )
        advanceUntilIdle()

        val afterCancel = assetRepository.getByIdentity(identity)
        assertNotNull(afterCancel)
        assertEquals(committedAsset.sha256, afterCancel!!.sha256)
        assertEquals(crop.left, afterCancel.cropLeft!!, 0.0)
        assertEquals(crop.top, afterCancel.cropTop!!, 0.0)
        assertEquals(crop.right, afterCancel.cropRight!!, 0.0)
        assertEquals(crop.bottom, afterCancel.cropBottom!!, 0.0)
        assertTrue(committedFile.isFile)
    }

    @Test
    fun cancelForExistingUnconfirmedResultScreenshotDoesNotRemoveIt() = runTest {
        val role = MatchResultScreenshotRole.MATCH_RESULT_UPPER
        val root = Files.createTempDirectory("pointiq-result-cancel-existing").toFile()
        val assetRepository = CancelContractAssetRepository()
        val pathHelper = LocalImagePreserver(
            appPrivateRoot = root,
            sourceStreamOpener = ImageSourceStreamOpener {
                ByteArrayInputStream(byteArrayOf(9, 8, 7))
            },
            mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val existingAsset = MatchResultScreenshotAssetEntity(
            tournamentId = CANCEL_CONTRACT_TOURNAMENT_ID,
            matchId = matchId,
            screenshotKind = OcrScreenshotKind.MATCH_RESULT.name,
            screenshotRole = role.name,
            ownerUserId = "owner-id",
            localRelativePath = pathHelper.matchResultRelativePath(
                CANCEL_CONTRACT_TOURNAMENT_ID,
                matchId,
                role,
                "png",
            ),
            fileExtension = "png",
            mimeType = "image/png",
            originalWidth = 1600,
            originalHeight = 720,
            byteSize = 3L,
            sha256 = "d".repeat(64),
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
            createdAt = 1L,
            updatedAt = 1L,
            preservedAt = 1L,
            uploadedAt = null,
            revision = 1L,
        )
        val existingFile = pathHelper.matchResultPreservedFile(
            CANCEL_CONTRACT_TOURNAMENT_ID,
            matchId,
            role,
            "png",
        )
        existingFile.parentFile?.mkdirs()
        existingFile.writeBytes(byteArrayOf(9, 8, 7))
        assetRepository.saveOrReplace(existingAsset)

        val viewModel = viewModel(
            bytesByUri = emptyMap(),
            assetRepository = assetRepository,
            root = root,
        )
        viewModel.load(CANCEL_CONTRACT_TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.cancelResultScreenshotCrop(
            CANCEL_CONTRACT_TOURNAMENT_ID,
            matchId,
            role,
        )
        advanceUntilIdle()

        assertNotNull(assetRepository.getByIdentity(identity(role)))
        assertTrue(existingFile.isFile)
        assertTrue(viewModel.uiState.value.resultScreenshots.slot(role).hasLinkedAsset)
    }

    private fun viewModel(
        bytesByUri: Map<String, ByteArray>,
        assetRepository: CancelContractAssetRepository,
        root: File,
    ): MatchReviewViewModel {
        val fingerprintGenerator = ImageSourceFingerprintGenerator(
            ImageSourceStreamOpener { uri -> bytesByUri.getValue(uri).inputStream() },
            Dispatchers.Unconfined,
        )
        val ownerProvider = CancelContractOwnerProvider("owner-id")
        return MatchReviewViewModel(
            getTournamentById = GetTournamentByIdUseCase(tournamentRepository),
            observeMatches = ObserveMatchesUseCase(tournamentRepository),
            observeTournamentSlots = ObserveTournamentSlotsUseCase(tournamentRepository),
            observeRoster = ObserveRosterByTournamentUseCase(tournamentRepository),
            observeDraftValues = ObserveMatchDraftValuesUseCase(tournamentRepository),
            validateMatchResult = ValidateMatchResultUseCase(),
            finalizeMatch = FinalizeMatchUseCase(tournamentRepository, ValidateMatchResultUseCase()),
            imageCandidateValidator = ImageCandidateValidator(
                ImageCandidateMetadataReader {
                    ImageCandidateReadResult.Metadata("image/png", width = 1600, height = 720)
                },
            ),
            screenshotDuplicateDetector = ScreenshotDuplicateDetector(fingerprintGenerator),
            matchResultScreenshotDuplicateDetector = MatchResultScreenshotDuplicateDetector(
                fingerprintGenerator = fingerprintGenerator,
                assetRepository = assetRepository,
                screenshotOwnerProvider = ownerProvider,
            ),
            localImagePreserver = LocalImagePreserver(
                appPrivateRoot = root,
                sourceStreamOpener = ImageSourceStreamOpener { uri -> bytesByUri.getValue(uri).inputStream() },
                mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
                ioDispatcher = Dispatchers.Unconfined,
            ),
            matchResultScreenshotAssetRepository = assetRepository,
            screenshotOwnerProvider = ownerProvider,
        )
    }

    private fun identity(role: MatchResultScreenshotRole) = MatchResultScreenshotIdentity(
        tournamentId = CANCEL_CONTRACT_TOURNAMENT_ID,
        matchId = matchId,
        role = role,
    )

    private class CancelContractOwnerProvider(
        private val ownerId: String?,
    ) : ScreenshotOwnerProvider {
        override suspend fun currentOwnerUserId(): String? = ownerId
    }

    private class CancelContractAssetRepository : MatchResultScreenshotAssetRepository {
        private val assets = MutableStateFlow<List<MatchResultScreenshotAssetEntity>>(emptyList())

        override fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
            assets.map { list -> list.filter { it.matchId == matchId } }

        override fun observeByMatchIdAndOwner(
            matchId: String,
            ownerUserId: String,
        ): Flow<List<MatchResultScreenshotAssetEntity>> =
            assets.map { list -> list.filter { it.matchId == matchId && it.ownerUserId == ownerUserId } }

        override fun observeByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): Flow<MatchResultScreenshotAssetEntity?> =
            assets.map { list -> list.firstOrNull { it.matches(identity) } }

        override fun observeByIdentityAndOwner(
            identity: MatchResultScreenshotIdentity,
            ownerUserId: String,
        ): Flow<MatchResultScreenshotAssetEntity?> =
            assets.map { list ->
                list.firstOrNull { it.ownerUserId == ownerUserId && it.matches(identity) }
            }

        override suspend fun getByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): MatchResultScreenshotAssetEntity? = assets.value.firstOrNull { it.matches(identity) }

        override suspend fun getByIdentityAndOwner(
            identity: MatchResultScreenshotIdentity,
            ownerUserId: String,
        ): MatchResultScreenshotAssetEntity? = assets.value.firstOrNull {
            it.ownerUserId == ownerUserId && it.matches(identity)
        }

        override fun observeByTournamentId(
            tournamentId: String,
        ): Flow<List<MatchResultScreenshotAssetEntity>> =
            assets.map { list -> list.filter { it.tournamentId == tournamentId } }

        override suspend fun findDuplicateFingerprint(
            identity: MatchResultScreenshotIdentity,
            sha256: String,
        ): MatchResultScreenshotAssetEntity? = assets.value.firstOrNull {
            it.tournamentId == identity.tournamentId &&
                it.sha256 == sha256 &&
                !it.matches(identity)
        }

        override suspend fun saveOrReplace(
            asset: MatchResultScreenshotAssetEntity,
        ): MatchResultScreenshotAssetSaveResult {
            val identity = MatchResultScreenshotIdentity(
                tournamentId = asset.tournamentId,
                matchId = asset.matchId,
                role = MatchResultScreenshotRole.valueOf(asset.screenshotRole),
            )
            val existing = getByIdentity(identity)
            val toSave = if (existing != null && existing.sha256 != asset.sha256) {
                asset.copy(
                    cropProfileId = null,
                    cropLeft = null,
                    cropTop = null,
                    cropRight = null,
                    cropBottom = null,
                )
            } else {
                asset
            }
            assets.value = assets.value.filterNot { it.matches(identity) } + toSave
            return MatchResultScreenshotAssetSaveResult.Saved
        }

        override suspend fun saveOrReplaceByOwner(
            asset: MatchResultScreenshotAssetEntity,
            ownerUserId: String,
        ): MatchResultScreenshotAssetSaveResult =
            if (asset.ownerUserId != ownerUserId) {
                MatchResultScreenshotAssetSaveResult.AuthenticationRequired
            } else {
                saveOrReplace(asset)
            }

        override suspend fun markLocalMissing(
            identity: MatchResultScreenshotIdentity,
            updatedAt: Long,
        ) {
            assets.value = assets.value.map { asset ->
                if (asset.matches(identity)) {
                    asset.copy(localStatus = ScreenshotLocalStatus.MISSING.name, updatedAt = updatedAt)
                } else {
                    asset
                }
            }
        }

        override suspend fun markCleanupFailure(
            identity: MatchResultScreenshotIdentity,
            updatedAt: Long,
        ) = Unit

        override suspend fun persistConfirmedCrop(
            identity: MatchResultScreenshotIdentity,
            crop: OcrNormalizedCropRect,
            updatedAt: Long,
        ): MatchResultScreenshotCropSaveResult {
            val existing = getByIdentity(identity)
                ?: return MatchResultScreenshotCropSaveResult.MissingAsset
            assets.value = assets.value.filterNot { it.matches(identity) } + existing.copy(
                cropProfileId = "match-result",
                cropLeft = crop.left,
                cropTop = crop.top,
                cropRight = crop.right,
                cropBottom = crop.bottom,
                updatedAt = updatedAt,
                revision = existing.revision + 1,
            )
            return MatchResultScreenshotCropSaveResult.Saved
        }

        override suspend fun persistConfirmedCropByOwner(
            identity: MatchResultScreenshotIdentity,
            ownerUserId: String,
            crop: OcrNormalizedCropRect,
            updatedAt: Long,
        ): MatchResultScreenshotCropSaveResult =
            if (getByIdentityAndOwner(identity, ownerUserId) == null) {
                MatchResultScreenshotCropSaveResult.MissingAsset
            } else {
                persistConfirmedCrop(identity, crop, updatedAt)
            }

        override suspend fun clearConfirmedCrop(
            identity: MatchResultScreenshotIdentity,
            updatedAt: Long,
        ): MatchResultScreenshotCropSaveResult = MatchResultScreenshotCropSaveResult.Saved

        override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) {
            assets.value = assets.value.filterNot { it.matches(identity) }
        }

        override suspend fun deleteByIdentityAndOwner(
            identity: MatchResultScreenshotIdentity,
            ownerUserId: String,
        ): Boolean {
            val before = assets.value.size
            assets.value = assets.value.filterNot {
                it.ownerUserId == ownerUserId && it.matches(identity)
            }
            return assets.value.size != before
        }

        override suspend fun deleteByMatchId(matchId: String) {
            assets.value = assets.value.filterNot { it.matchId == matchId }
        }

        private fun MatchResultScreenshotAssetEntity.matches(
            identity: MatchResultScreenshotIdentity,
        ): Boolean =
            tournamentId == identity.tournamentId &&
                matchId == identity.matchId &&
                screenshotRole == identity.role.name &&
                screenshotKind == OcrScreenshotKind.MATCH_RESULT.name
    }
}
