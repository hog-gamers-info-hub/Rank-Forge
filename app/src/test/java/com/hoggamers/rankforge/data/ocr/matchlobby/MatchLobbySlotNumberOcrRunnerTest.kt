package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrEvidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionInput
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractor
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionEvidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionIdentity
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionSelection
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionType
import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterPanelInput
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowCropBounds
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorSource
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparer
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparationFailure
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparationResult
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPreparedPanel
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrScreenshotSource
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import com.hoggamers.rankforge.presentation.screen.ScreenshotOwnerProvider
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MatchLobbySlotNumberOcrRunnerTest {
    @Test
    fun blankContextOrMissingOwnerDoesNotRunOcr() = runTest {
        val assets = FakeAssetRepository()
        val preparer = FakePanelPreparer()
        val extractor = FakeExtractor()
        val runner = runner(assets, preparer, extractor, ownerId = null)

        val blankResult = runner.process("", "match-1")
        val ownerResult = runner.process("tournament-1", "match-1")

        assertUnavailableForAll(blankResult, MatchLobbySlotNumberOcrUnavailableReason.INVALID_MATCH_CONTEXT)
        assertUnavailableForAll(ownerResult, MatchLobbySlotNumberOcrUnavailableReason.OWNER_UNAVAILABLE)
        assertEquals(0, assets.requests.size)
        assertEquals(0, preparer.sources.size)
        assertEquals(0, extractor.inputs.size)
    }

    @Test
    fun validScreenshotUsesSlotContentOnlyAndReturnsNonPositionalOcrEvidence() = runTest {
        val assets = FakeAssetRepository(mapOf(1 to asset(1)))
        val preparer = FakePanelPreparer()
        val extractor = FakeExtractor(
            resultsByPosition = mapOf(
                RosterScreenshotPosition.ONE to extractionResults(
                    RosterScreenshotPosition.ONE,
                    listOf(listOf(4), listOf(10), listOf(3), listOf(2)),
                ),
            ),
        )

        val result = runner(assets, preparer, extractor).process("tournament-1", "match-1")
        val processed = result.processed(RosterScreenshotPosition.ONE)

        assertEquals(1, preparer.sources.size)
        assertEquals(RosterScreenshotPosition.ONE, preparer.sources.single().screenshotPosition)
        assertEquals(1, extractor.inputs.size)
        assertEquals(RosterRawOcrRegionSelection.SLOT_CONTENT_ONLY, extractor.inputs.single().regionSelection)
        assertEquals(
            RosterVisibleSlotPosition.entries,
            extractor.resultsByPosition.getValue(RosterScreenshotPosition.ONE).map {
                requireNotNull(it.regionIdentityOrNull()).visibleSlotPosition
            },
        )
        assertTrue(
            extractor.resultsByPosition.getValue(RosterScreenshotPosition.ONE).all {
                it.regionIdentityOrNull()?.regionType == RosterRawOcrRegionType.SLOT_CONTENT &&
                    it.regionIdentityOrNull()?.playerRowIndex == null
            },
        )
        assertEquals(RosterVisibleSlotPosition.entries, processed.slots.map { it.visibleSlotPosition })
        assertEquals(listOf(4, 10, 3, 2), processed.slots.map { it.candidate.detectedSlotNumber })
        assertTrue(processed.slots.all { it.candidate.status == RosterCandidateParseStatus.PARSED })
        assertEquals(1, preparer.releaseCount)
    }

    @Test
    fun missingAndAmbiguousOcrEvidenceRemainUnchanged() = runTest {
        val extractor = FakeExtractor(
            resultsByPosition = mapOf(
                RosterScreenshotPosition.ONE to extractionResults(
                    RosterScreenshotPosition.ONE,
                    listOf(emptyList(), listOf(10, 11), listOf(3), listOf(2)),
                ),
            ),
        )

        val processed = runner(
            FakeAssetRepository(mapOf(1 to asset(1))),
            FakePanelPreparer(),
            extractor,
        ).process("tournament-1", "match-1").processed(RosterScreenshotPosition.ONE)

        assertEquals(RosterCandidateParseStatus.MISSING, processed.slots[0].candidate.status)
        assertEquals(null, processed.slots[0].candidate.detectedSlotNumber)
        assertEquals(RosterCandidateParseStatus.AMBIGUOUS, processed.slots[1].candidate.status)
        assertEquals(null, processed.slots[1].candidate.detectedSlotNumber)
        assertEquals(3, processed.slots[2].candidate.detectedSlotNumber)
        assertEquals(2, processed.slots[3].candidate.detectedSlotNumber)
    }

    @Test
    fun typedRecognizerFailureRemainsMissingWithoutPositionalFallback() = runTest {
        val position = RosterScreenshotPosition.ONE
        val results = extractionResults(position, listOf(listOf(1), listOf(2), listOf(3), listOf(4))).toMutableList()
        results[0] = RosterRawOcrExtractionResult.Failed(
            failure = com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrFailure.RECOGNIZER_FAILED,
            regionIdentity = RosterRawOcrRegionIdentity(
                screenshotPosition = position,
                visibleSlotPosition = RosterVisibleSlotPosition.TOP_LEFT,
                regionType = RosterRawOcrRegionType.SLOT_CONTENT,
            ),
        )

        val processed = runner(
            FakeAssetRepository(mapOf(1 to asset(1))),
            FakePanelPreparer(),
            FakeExtractor(mapOf(position to results)),
        ).process("tournament-1", "match-1").processed(position)

        assertEquals(RosterCandidateParseStatus.MISSING, processed.slots.first().candidate.status)
        assertEquals(null, processed.slots.first().candidate.detectedSlotNumber)
    }

    @Test
    fun invalidAssetCropAndPanelPreparationFailureDoNotRunExtractor() = runTest {
        val invalidAssets = FakeAssetRepository(mapOf(1 to asset(1, cropProfileId = null)))
        val invalidPreparer = FakePanelPreparer()
        val invalidExtractor = FakeExtractor()
        val invalidResult = runner(invalidAssets, invalidPreparer, invalidExtractor)
            .process("tournament-1", "match-1")

        assertEquals(
            MatchLobbySlotNumberOcrUnavailableReason.INVALID_ASSET_CROP,
            invalidResult.unavailable(RosterScreenshotPosition.ONE).reason,
        )
        assertEquals(0, invalidPreparer.sources.size)
        assertEquals(0, invalidExtractor.inputs.size)

        val failedPreparer = FakePanelPreparer(
            result = RosterOcrPanelPreparationResult.Failed(RosterOcrPanelPreparationFailure.INVALID_CROP),
        )
        val failedExtractor = FakeExtractor()
        val failedResult = runner(
            FakeAssetRepository(mapOf(1 to asset(1))),
            failedPreparer,
            failedExtractor,
        ).process("tournament-1", "match-1")

        assertEquals(
            MatchLobbySlotNumberOcrUnavailableReason.PANEL_PREPARATION_FAILED,
            failedResult.unavailable(RosterScreenshotPosition.ONE).reason,
        )
        assertEquals(1, failedPreparer.sources.size)
        assertEquals(0, failedExtractor.inputs.size)
    }

    @Test
    fun extractionFailureReturnsUnavailableAndReleasesPreparedPanelOnce() = runTest {
        val preparer = FakePanelPreparer()
        val result = runner(
            FakeAssetRepository(mapOf(1 to asset(1))),
            preparer,
            FakeExtractor(failure = IllegalStateException("ocr")),
        ).process("tournament-1", "match-1")

        assertEquals(
            MatchLobbySlotNumberOcrUnavailableReason.EXTRACTION_FAILED,
            result.unavailable(RosterScreenshotPosition.ONE).reason,
        )
        assertEquals(1, preparer.releaseCount)
    }

    @Test
    fun extractionCancellationPropagatesAfterReleasingPreparedPanel() = runTest {
        val preparer = FakePanelPreparer()
        val runner = runner(
            FakeAssetRepository(mapOf(1 to asset(1))),
            preparer,
            FakeExtractor(failure = CancellationException("cancelled")),
        )

        try {
            runner.process("tournament-1", "match-1")
            fail("Expected cancellation to propagate.")
        } catch (cancellation: CancellationException) {
            assertEquals("cancelled", cancellation.message)
        }
        assertEquals(1, preparer.releaseCount)
    }

    @Test
    fun threePreparedScreenshotsInvokeSlotOnlyExtractionThreeTimes() = runTest {
        val positions = RosterScreenshotPosition.entries
        val preparer = FakePanelPreparer()
        val extractor = FakeExtractor(
            resultsByPosition = positions.associateWith { position ->
                extractionResults(position, listOf(listOf(1), listOf(2), listOf(3), listOf(4)))
            },
        )

        val result = runner(
            FakeAssetRepository((1..3).associateWith(::asset)),
            preparer,
            extractor,
        ).process("tournament-1", "match-1")

        assertEquals(3, extractor.inputs.size)
        assertTrue(extractor.inputs.all { it.regionSelection == RosterRawOcrRegionSelection.SLOT_CONTENT_ONLY })
        assertEquals(12, extractor.resultsByPosition.values.sumOf { it.size })
        assertEquals(3, preparer.releaseCount)
        assertEquals(3, result.screenshots.filterIsInstance<MatchLobbySlotNumberOcrScreenshotResult.Processed>().size)
    }

    @Test
    fun completeSlotGeometryCopiesFourPreviewsBeforeReleasingThePanel() = runTest {
        val preparer = FakePanelPreparer(imageWidth = 1_000, imageHeight = 800)
        var copiedCount = 0
        val runner = runner(
            assets = FakeAssetRepository(mapOf(1 to asset(1))),
            preparer = preparer,
            extractor = FakeExtractor(
                mapOf(RosterScreenshotPosition.ONE to geometricExtractionResults(RosterScreenshotPosition.ONE)),
            ),
            previewFactory = MatchLobbyTeamCropPreviewFactory { _, _ ->
                copiedCount++
                FakeTeamCropPreviewImage
            },
        )

        val processed = runner.process("tournament-1", "match-1").processed(RosterScreenshotPosition.ONE)

        assertTrue("${processed.teamCropPreviews}", processed.teamCropPreviews is MatchLobbyTeamCropPreviewResult.Available)
        val previews = processed.teamCropPreviews as MatchLobbyTeamCropPreviewResult.Available
        assertEquals(4, copiedCount)
        assertEquals(1, preparer.releaseCount)
        assertEquals(RosterVisibleSlotPosition.entries, previews.previews.map { it.visibleSlotPosition })
        assertEquals(listOf(4, 10, 3, 2), previews.previews.map { it.detectedSlotNumber })
    }

    @Test
    fun validCanonicalTeamCropsGenerateFourTransientPlayerRowsPerTeam() = runTest {
        val pipeline = RecordingRowCropPipeline()
        val processed = runner(
            assets = FakeAssetRepository(mapOf(1 to asset(1))),
            preparer = FakePanelPreparer(imageWidth = 1_000, imageHeight = 800),
            extractor = FakeExtractor(
                mapOf(RosterScreenshotPosition.ONE to geometricExtractionResults(RosterScreenshotPosition.ONE)),
            ),
            previewFactory = MatchLobbyTeamCropPreviewFactory { _, _ -> FakeTeamCropPreviewImage },
            rowCropPipeline = pipeline,
        ).process("tournament-1", "match-1").processed(RosterScreenshotPosition.ONE)

        assertEquals(listOf(4, 10, 3, 2), pipeline.authoritativeSlots)
        assertTrue("${processed.teamCropPreviews}", processed.teamCropPreviews is MatchLobbyTeamCropPreviewResult.Available)
        val previews = processed.teamCropPreviews as MatchLobbyTeamCropPreviewResult.Available
        assertEquals(4, previews.previews.size)
        assertEquals(listOf(4, 10, 3, 2), previews.previews.map { it.detectedSlotNumber })
        assertTrue(previews.previews.all { it.playerRowPreviews.map { row -> row.row } == LobbyPlayerRow.entries })
    }

    @Test
    fun missingOrAmbiguousCandidatesMakeOnlyTheCropResultUnavailable() = runTest {
        val processed = runner(
            FakeAssetRepository(mapOf(1 to asset(1))),
            FakePanelPreparer(imageWidth = 1_000, imageHeight = 800),
            FakeExtractor(
                mapOf(
                    RosterScreenshotPosition.ONE to extractionResults(
                        RosterScreenshotPosition.ONE,
                        listOf(emptyList(), listOf(10, 11), listOf(3), listOf(2)),
                    ),
                ),
            ),
        ).process("tournament-1", "match-1").processed(RosterScreenshotPosition.ONE)

        assertTrue(processed.teamCropPreviews is MatchLobbyTeamCropPreviewResult.Unavailable)
        assertEquals(
            MatchLobbyTeamCropPreviewUnavailableReason.REQUIRED_SLOT_NUMBER_UNAVAILABLE,
            (processed.teamCropPreviews as MatchLobbyTeamCropPreviewResult.Unavailable).reason,
        )
        assertEquals(RosterCandidateParseStatus.MISSING, processed.slots.first().candidate.status)
    }

    @Test
    fun previewCreationFailureDoesNotChangeSlotOcrAndReleasesOnce() = runTest {
        val preparer = FakePanelPreparer(imageWidth = 1_000, imageHeight = 800)
        val processed = runner(
            assets = FakeAssetRepository(mapOf(1 to asset(1))),
            preparer = preparer,
            extractor = FakeExtractor(
                mapOf(RosterScreenshotPosition.ONE to geometricExtractionResults(RosterScreenshotPosition.ONE)),
            ),
            previewFactory = MatchLobbyTeamCropPreviewFactory { _, _ ->
                throw IllegalStateException("preview")
            },
        ).process("tournament-1", "match-1").processed(RosterScreenshotPosition.ONE)

        assertTrue(processed.slots.all { it.candidate.status == RosterCandidateParseStatus.PARSED })
        assertEquals(
            MatchLobbyTeamCropPreviewUnavailableReason.BITMAP_CREATION_FAILED,
            (processed.teamCropPreviews as MatchLobbyTeamCropPreviewResult.Unavailable).reason,
        )
        assertEquals(1, preparer.releaseCount)
    }

    private fun runner(
        assets: FakeAssetRepository,
        preparer: FakePanelPreparer,
        extractor: FakeExtractor,
        ownerId: String? = "owner-1",
        previewFactory: MatchLobbyTeamCropPreviewFactory? = null,
        rowCropPipeline: LobbyPlayerRowCropPipeline = NoOpLobbyPlayerRowCropPipeline,
    ) = AndroidMatchLobbySlotNumberOcrRunner(
        assetRepository = assets,
        panelPreparer = preparer,
        extractor = extractor,
        screenshotOwnerProvider = object : ScreenshotOwnerProvider {
            override suspend fun currentOwnerUserId(): String? = ownerId
        },
        playerRowCropPipeline = rowCropPipeline,
    ).also { runner ->
        previewFactory?.let { runner.teamCropPreviewFactory = it }
    }

    private fun MatchLobbySlotNumberOcrResult.processed(
        position: RosterScreenshotPosition,
    ): MatchLobbySlotNumberOcrScreenshotResult.Processed =
        screenshots.single { it.screenshotPosition == position } as MatchLobbySlotNumberOcrScreenshotResult.Processed

    private fun MatchLobbySlotNumberOcrResult.unavailable(
        position: RosterScreenshotPosition,
    ): MatchLobbySlotNumberOcrScreenshotResult.Unavailable =
        screenshots.single { it.screenshotPosition == position } as MatchLobbySlotNumberOcrScreenshotResult.Unavailable

    private fun assertUnavailableForAll(
        result: MatchLobbySlotNumberOcrResult,
        reason: MatchLobbySlotNumberOcrUnavailableReason,
    ) {
        assertEquals(RosterScreenshotPosition.entries, result.screenshots.map { it.screenshotPosition })
        assertTrue(result.screenshots.all {
            it is MatchLobbySlotNumberOcrScreenshotResult.Unavailable && it.reason == reason
        })
    }

    private fun extractionResults(
        position: RosterScreenshotPosition,
        numbersByVisiblePosition: List<List<Int>>,
    ): List<RosterRawOcrExtractionResult> = RosterVisibleSlotPosition.entries.map { visiblePosition ->
        val numbers = numbersByVisiblePosition[visiblePosition.offset - 1]
        val identity = RosterRawOcrRegionIdentity(
            screenshotPosition = position,
            visibleSlotPosition = visiblePosition,
            regionType = RosterRawOcrRegionType.SLOT_CONTENT,
        )
        if (numbers.isEmpty()) {
            RosterRawOcrExtractionResult.Empty(identity)
        } else {
            RosterRawOcrExtractionResult.Extracted(
                RosterRawOcrRegionEvidence(
                    regionIdentity = identity,
                    rawText = numbers.joinToString(" "),
                    blocks = numbers.map(::block),
                    rawEvidence = emptyList<RosterRawOcrEvidence>(),
                    regionWidth = 100,
                    regionHeight = 100,
                ),
            )
        }
    }

    private fun block(number: Int): RawOcrBlock {
        val geometry = RawOcrGeometry(RawOcrBoundingBox(1, 10, 8, 20), null)
        val text = number.toString()
        return RawOcrBlock(
            text = text,
            geometry = geometry,
            recognizedLanguage = null,
            confidence = RawOcrConfidence.Unavailable,
            lines = listOf(
                RawOcrLine(
                    text = text,
                    geometry = geometry,
                    recognizedLanguage = null,
                    confidence = RawOcrConfidence.Unavailable,
                    elements = listOf(
                        RawOcrElement(
                            text = text,
                            geometry = geometry,
                            recognizedLanguage = null,
                            confidence = RawOcrConfidence.Unavailable,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun geometricExtractionResults(
        position: RosterScreenshotPosition,
    ): List<RosterRawOcrExtractionResult> {
        val values = listOf(
            Triple(4, 90, 90),
            Triple(10, 490, 90),
            Triple(3, 90, 290),
            Triple(2, 490, 290),
        )
        return RosterVisibleSlotPosition.entries.mapIndexed { index, visiblePosition ->
            val (number, originX, originY) = values[index]
            val identity = RosterRawOcrRegionIdentity(
                screenshotPosition = position,
                visibleSlotPosition = visiblePosition,
                regionType = RosterRawOcrRegionType.SLOT_CONTENT,
            )
            val numberGeometry = RawOcrGeometry(RawOcrBoundingBox(4, 5, 16, 15), null)
            RosterRawOcrExtractionResult.Extracted(
                RosterRawOcrRegionEvidence(
                    regionIdentity = identity,
                    rawText = number.toString(),
                    blocks = listOf(
                        RawOcrBlock(
                            text = number.toString(),
                            geometry = numberGeometry,
                            recognizedLanguage = null,
                            confidence = RawOcrConfidence.Unavailable,
                            lines = listOf(
                                RawOcrLine(
                                    text = number.toString(),
                                    geometry = numberGeometry,
                                    recognizedLanguage = null,
                                    confidence = RawOcrConfidence.Unavailable,
                                    elements = listOf(
                                        RawOcrElement(
                                            text = number.toString(),
                                            geometry = numberGeometry,
                                            recognizedLanguage = null,
                                            confidence = RawOcrConfidence.Unavailable,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    rawEvidence = listOf(
                        RosterRawOcrEvidence(
                            text = "team",
                            geometry = RawOcrGeometry(RawOcrBoundingBox(25, 5, 90, 25), null),
                            recognizedLanguage = null,
                            confidence = RawOcrConfidence.Unavailable,
                        ),
                    ),
                    regionWidth = 100,
                    regionHeight = 100,
                    panelPixelRect = OcrPixelRect(originX, originY, 100, 100),
                ),
            )
        }
    }

    private fun RosterRawOcrExtractionResult.regionIdentityOrNull(): RosterRawOcrRegionIdentity? = when (this) {
        is RosterRawOcrExtractionResult.Empty -> regionIdentity
        is RosterRawOcrExtractionResult.Extracted -> evidence.regionIdentity
        is RosterRawOcrExtractionResult.Failed -> regionIdentity
    }

    private fun asset(
        index: Int,
        cropProfileId: String? = OcrCropValidationProfiles.Lobby.id,
    ) = MatchLobbyScreenshotAssetEntity(
        tournamentId = "tournament-1",
        matchId = "match-1",
        lobbyScreenshotIndex = index,
        ownerUserId = "owner-1",
        localRelativePath = "screenshots/tournament-1/match-1/$index.jpg",
        fileExtension = "jpg",
        mimeType = "image/jpeg",
        originalWidth = 100,
        originalHeight = 100,
        byteSize = 10,
        sha256 = "sha-$index",
        localStatus = "AVAILABLE",
        uploadStatus = "NOT_UPLOADED",
        uploadFailureCode = null,
        storageBucket = null,
        storageObjectPath = null,
        cropProfileId = cropProfileId,
        cropLeft = 0.0,
        cropTop = 0.0,
        cropRight = 1.0,
        cropBottom = 1.0,
        createdAt = 1,
        updatedAt = 1,
        preservedAt = 1,
        uploadedAt = null,
        revision = 1,
    )

    private class FakeAssetRepository(
        private val assets: Map<Int, MatchLobbyScreenshotAssetEntity> = emptyMap(),
    ) : MatchLobbyScreenshotAssetRepository {
        val requests = mutableListOf<MatchLobbyScreenshotIdentity>()

        override fun observeByMatchId(matchId: String) = throw UnsupportedOperationException()
        override fun observeByIdentity(identity: MatchLobbyScreenshotIdentity) = throw UnsupportedOperationException()
        override suspend fun getByIdentity(identity: MatchLobbyScreenshotIdentity) = null
        override fun observeByTournamentId(tournamentId: String) = throw UnsupportedOperationException()
        override suspend fun getByIdentityAndOwner(
            identity: MatchLobbyScreenshotIdentity,
            ownerUserId: String,
        ): MatchLobbyScreenshotAssetEntity? {
            requests += identity
            return assets[identity.lobbyScreenshotIndex]?.takeIf { it.ownerUserId == ownerUserId }
        }

        override suspend fun findDuplicateFingerprint(identity: MatchLobbyScreenshotIdentity, sha256: String) = null
        override suspend fun saveOrReplace(asset: MatchLobbyScreenshotAssetEntity) = throw UnsupportedOperationException()
        override suspend fun markLocalMissing(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun markCleanupFailure(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) = Unit
        override suspend fun deleteByMatchId(matchId: String) = Unit
        override suspend fun persistConfirmedCrop(
            identity: MatchLobbyScreenshotIdentity,
            crop: com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect,
            updatedAt: Long,
        ) = throw UnsupportedOperationException()
        override suspend fun clearConfirmedCrop(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) =
            throw UnsupportedOperationException()
    }

    private class FakePanelPreparer(
        private val result: RosterOcrPanelPreparationResult? = null,
        private val imageWidth: Int = 100,
        private val imageHeight: Int = 100,
    ) : RosterOcrPanelPreparer {
        val sources = mutableListOf<RosterOcrScreenshotSource>()
        var releaseCount = 0

        override suspend fun prepare(source: RosterOcrScreenshotSource): RosterOcrPanelPreparationResult {
            sources += source
            return result ?: RosterOcrPanelPreparationResult.Prepared(
                object : RosterOcrPreparedPanel {
                    override val croppedPanelImage: OcrPreprocessingImage = FakeImage(imageWidth, imageHeight)
                    override val croppedPanelInput = CroppedRosterPanelInput(
                        screenshotPosition = source.screenshotPosition,
                        isPreparedRosterCrop = true,
                        imageWidth = imageWidth,
                        imageHeight = imageHeight,
                    )

                    override fun release() {
                        releaseCount++
                    }
                },
            )
        }
    }

    private class FakeExtractor(
        val resultsByPosition: Map<RosterScreenshotPosition, List<RosterRawOcrExtractionResult>> = emptyMap(),
        private val failure: Throwable? = null,
    ) : RosterRawOcrExtractor {
        val inputs = mutableListOf<RosterRawOcrExtractionInput>()

        override suspend fun extract(input: RosterRawOcrExtractionInput): List<RosterRawOcrExtractionResult> {
            inputs += input
            failure?.let { throw it }
            return resultsByPosition[input.croppedPanelInput.screenshotPosition] ?: emptyList()
        }
    }

    private data class FakeImage(
        override val width: Int,
        override val height: Int,
    ) : OcrPreprocessingImage

    private data object FakeTeamCropPreviewImage : MatchLobbyTeamCropPreviewImage

    private class RecordingRowCropPipeline : LobbyPlayerRowCropPipeline {
        val authoritativeSlots = mutableListOf<Int>()

        override suspend fun generate(
            authoritativeTeamSlotNumber: Int,
            teamCropImage: MatchLobbyTeamCropPreviewImage,
        ): LobbyPlayerRowCropGenerationResult {
            authoritativeSlots += authoritativeTeamSlotNumber
            return LobbyPlayerRowCropGenerationResult.Generated(
                rows = LobbyPlayerRow.entries.map { row ->
                    LobbyPlayerRowCropPreview(
                        row = row,
                        boundsInTeamCrop = LobbyPlayerRowCropBounds(0, row.ordinal, 10, row.ordinal + 1),
                        slotAnchorSource = LobbySlotAnchorSource.TEAM_CROP_CENTER_FALLBACK,
                        slotAnchorY = 5.0,
                        structuralEvidence = null,
                        image = FakeTeamCropPreviewImage,
                    )
                },
            )
        }
    }
}
