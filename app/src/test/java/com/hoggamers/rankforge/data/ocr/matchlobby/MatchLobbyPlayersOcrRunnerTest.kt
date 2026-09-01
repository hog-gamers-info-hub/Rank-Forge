package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyOcrCacheFingerprint
import com.hoggamers.rankforge.data.local.MatchLobbyOcrCacheRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotCropSaveResult
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotIdentityResolver
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowCropBounds
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorSource
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrEvidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionInput
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterPanelInput
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionEvidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionIdentity
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionType
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseInput
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParser
import com.hoggamers.rankforge.domain.ocr.parsing.RosterPlayerNameCandidate
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotCandidate
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotNumberCandidate
import com.hoggamers.rankforge.domain.ocr.parsing.RosterTeamNameCandidate
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparer
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparationResult
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPreparedPanel
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrScreenshotSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import com.hoggamers.rankforge.presentation.screen.ScreenshotOwnerProvider

class MatchLobbyPlayersOcrRunnerTest {
    @Test
    fun ppPlayerAuthorityUsesCurrentCachePipelineVersion() {
        assertEquals(12, MATCH_LOBBY_OCR_CACHE_PIPELINE_VERSION)
    }

    @Test
    fun mapsAllLobbyScreenshotsToDeterministicTournamentSlotsAndReleasesPanels() = runTest {
        val assets = (1..3).associateWith(::asset)
        val preparer = FakePanelPreparer()
        val extractor = PositionTrackingExtractor()
        val slotRunner = FakeSlotNumberOcrRunner()
        val runner = AndroidMatchLobbyPlayersOcrRunner(
            assetRepository = FakeAssetRepository(assets),
            cacheRepository = FakeCacheRepository(),
            slotNumberOcrRunner = slotRunner,
            screenshotOwnerProvider = ownerProvider,
        )

        val result = runner.process("tournament-1", "match-1")

        assertEquals((1..12).toList(), result.slots.map { it.slotNumber })
        assertEquals(
            (1..12).map { slot -> (1..4).map { player -> "S${(slot - 1) / 4 + 1}P$player" } },
            result.slots.map { slot -> slot.players.map { it.playerName } },
        )
        assertEquals(1, slotRunner.processCount)
    }

    @Test
    fun cancellationFromOneScreenshotPropagates() {
        val runner = AndroidMatchLobbyPlayersOcrRunner(
            assetRepository = FakeAssetRepository(mapOf(1 to asset(1))),
            cacheRepository = FakeCacheRepository(),
            slotNumberOcrRunner = CancellingSlotNumberOcrRunner,
            screenshotOwnerProvider = ownerProvider,
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { runner.process("tournament-1", "match-1") }
        }
    }

    @Test
    fun missingScreenshotLeavesOnlyItsFourSlotsUnavailable() = runTest {
        val assets = mapOf(1 to asset(1), 3 to asset(3))
        val preparer = FakePanelPreparer()
        val extractor = PositionTrackingExtractor()
        val runner = AndroidMatchLobbyPlayersOcrRunner(
            assetRepository = FakeAssetRepository(assets),
            cacheRepository = FakeCacheRepository(),
            slotNumberOcrRunner = FakeSlotNumberOcrRunner(
                semanticSlotNumbers = mapOf(
                    RosterScreenshotPosition.ONE to listOf(1, 2, 3, 4),
                    RosterScreenshotPosition.THREE to listOf(9, 10, 11, 12),
                ),
            ),
            screenshotOwnerProvider = ownerProvider,
        )

        val result = runner.process("tournament-1", "match-1")

        assertEquals("S1P1", result.slots[0].players[0].playerName)
        assertEquals(null, result.slots[4].players[0].playerName)
        assertEquals("S3P1", result.slots[8].players[0].playerName)
    }

    @Test
    fun physicalScreenshotOneUsesSemanticFiveThroughEightFromSlotEvidence() = runTest {
        val result = runner(
            assets = FakeAssetRepository(mapOf(1 to asset(1))),
            cache = FakeCacheRepository(),
            extractor = PositionTrackingExtractor(),
            semanticSlotNumbers = mapOf(
                RosterScreenshotPosition.ONE to listOf(5, 6, 7, 8),
            ),
        ).process("tournament-1", "match-1")

        assertEquals(listOf(null, null, null, null), result.slots.take(4).map { it.players[0].playerName })
        assertEquals(
            listOf("S1P1", "S1P1", "S1P1", "S1P1"),
            result.slots.drop(4).take(4).map { it.players[0].playerName },
        )
    }

    @Test
    fun partialSemanticGroupPreservesAvailableTeamsWithoutShiftingSlots() = runTest {
        val result = runner(
            assets = FakeAssetRepository(mapOf(1 to asset(1))),
            cache = FakeCacheRepository(),
            extractor = PositionTrackingExtractor(),
            semanticSlotNumbers = mapOf(
                RosterScreenshotPosition.ONE to listOf(5, 6, 7, 8),
            ),
            unavailableVisibleSlots = setOf(RosterVisibleSlotPosition.TOP_RIGHT),
        ).process("tournament-1", "match-1")

        assertEquals((1..12).toList(), result.slots.map { it.slotNumber })
        assertEquals("S1P1", result.slots[4].players[0].playerName)
        assertEquals(null, result.slots[5].players[0].playerName)
        assertEquals("S1P1", result.slots[6].players[0].playerName)
        assertEquals("S1P1", result.slots[7].players[0].playerName)
    }

    @Test
    fun partialSemanticGroupIsCachedAndRestoredWithUnavailableTeamPreserved() = runTest {
        val assets = FakeAssetRepository(mapOf(1 to asset(1)))
        val cache = FakeCacheRepository()
        val runner = runner(
            assets = assets,
            cache = cache,
            extractor = PositionTrackingExtractor(),
            semanticSlotNumbers = mapOf(
                RosterScreenshotPosition.ONE to listOf(5, 6, 7, 8),
            ),
            unavailableVisibleSlots = setOf(RosterVisibleSlotPosition.TOP_RIGHT),
        )

        val first = runner.process("tournament-1", "match-1")
        val second = runner.process("tournament-1", "match-1")

        assertEquals(1, cache.saveCount)
        assertEquals(first, second)
        assertEquals(null, second.slots[5].players[0].playerName)
    }

    @Test
    fun contentDerivedSlotNumberOverridesConflictingDedicatedSlotNumber() = runTest {
        val extractor = PositionTrackingExtractor(
            semanticSlotNumbers = mapOf(
                RosterScreenshotPosition.ONE to listOf(9, 10, 11, 12),
            ),
        )
        val runner = runner(
            assets = FakeAssetRepository(mapOf(1 to asset(1))),
            cache = FakeCacheRepository(),
            extractor = extractor,
            semanticSlotNumbers = mapOf(RosterScreenshotPosition.ONE to listOf(9, 10, 11, 12)),
        )

        val result = runner.process("tournament-1", "match-1")

        assertEquals(listOf(null, null, null, null), result.slots.take(4).map { it.players[0].playerName })
        assertEquals(
            listOf("S1P1", "S1P1", "S1P1", "S1P1"),
            result.slots.drop(8).map { it.players[0].playerName },
        )
    }

    @Test
    fun shuffledPhysicalCardsProduceDeterministicSemanticSlotOrder() = runTest {
        val extractor = PositionTrackingExtractor()
        val result = runner(
            assets = FakeAssetRepository((1..3).associateWith(::asset)),
            cache = FakeCacheRepository(),
            extractor = extractor,
            semanticSlotNumbers = mapOf(
                RosterScreenshotPosition.ONE to listOf(9, 10, 11, 12),
                RosterScreenshotPosition.TWO to listOf(1, 2, 3, 4),
                RosterScreenshotPosition.THREE to listOf(5, 6, 7, 8),
            ),
        ).process("tournament-1", "match-1")

        assertEquals("S2P1", result.slots[0].players[0].playerName)
        assertEquals("S3P1", result.slots[4].players[0].playerName)
        assertEquals("S1P1", result.slots[8].players[0].playerName)
        assertEquals((1..12).toList(), result.slots.map { it.slotNumber })
    }

    @Test
    fun oneParsedQuadrantAnchorResolvesTheWholeSemanticGroupInTheRunner() = runTest {
        val extractor = PositionTrackingExtractor()
        val result = runner(
            assets = FakeAssetRepository(mapOf(1 to asset(1))),
            cache = FakeCacheRepository(),
            extractor = extractor,
            semanticSlotNumbers = mapOf(
                RosterScreenshotPosition.ONE to listOf(null, 6, null, null),
            ),
        ).process("tournament-1", "match-1")

        assertEquals(listOf("S1P1", "S1P1", "S1P1", "S1P1"), result.slots.drop(4).take(4).map { it.players[0].playerName })
        assertEquals(null, result.slots[0].players[0].playerName)
    }

    @Test
    fun unresolvedScreenshotContributesNothingAndDoesNotEraseAValidGroup() = runTest {
        val extractor = PositionTrackingExtractor()
        val result = runner(
            assets = FakeAssetRepository(mapOf(1 to asset(1), 2 to asset(2))),
            cache = FakeCacheRepository(),
            extractor = extractor,
            semanticSlotNumbers = mapOf(
                RosterScreenshotPosition.ONE to listOf(5, 6, 7, 8),
                RosterScreenshotPosition.TWO to listOf(null, null, null, null),
            ),
        ).process("tournament-1", "match-1")

        assertEquals("S1P1", result.slots[4].players[0].playerName)
        assertEquals(null, result.slots[0].players[0].playerName)
    }

    @Test
    fun duplicateSemanticGroupsAreSuppressedRatherThanOverwritten() = runTest {
        val extractor = PositionTrackingExtractor()
        val result = runner(
            assets = FakeAssetRepository((1..3).associateWith(::asset)),
            cache = FakeCacheRepository(),
            extractor = extractor,
            semanticSlotNumbers = mapOf(
                RosterScreenshotPosition.ONE to listOf(5, 6, 7, 8),
                RosterScreenshotPosition.TWO to listOf(5, 6, 7, 8),
                RosterScreenshotPosition.THREE to listOf(9, 10, 11, 12),
            ),
        ).process("tournament-1", "match-1")

        assertEquals(null, result.slots[4].players[0].playerName)
        assertEquals("S3P1", result.slots[8].players[0].playerName)
    }

    @Test
    fun swappedSemanticCachePayloadHitsUsingThePhysicalFingerprint() = runTest {
        val assetRepository = FakeAssetRepository(mapOf(1 to asset(1)))
        val cache = FakeCacheRepository()
        val extractor = PositionTrackingExtractor()
        val semanticSlotNumbers = mapOf(RosterScreenshotPosition.ONE to listOf(5, 6, 7, 8))
        val runner = runner(assetRepository, cache, extractor, semanticSlotNumbers)

        val first = runner.process("tournament-1", "match-1")
        val second = runner.process("tournament-1", "match-1")

        assertEquals("S1P1", first.slots[4].players[0].playerName)
        assertEquals("S1P1", second.slots[4].players[0].playerName)
        assertEquals(1, cache.saveCount)
    }

    @Test
    fun firstRunWritesOneCacheEntryPerProcessedScreenshotAndSecondRunHitsAllThree() = runTest {
        val assets = (1..3).associateWith(::asset)
        val assetRepository = FakeAssetRepository(assets)
        val cache = FakeCacheRepository()
        val extractor = PositionTrackingExtractor()
        val runner = runner(assetRepository, cache, extractor)

        runner.process("tournament-1", "match-1")
        runner.process("tournament-1", "match-1")

        assertEquals(3, cache.saveCount)
        assertEquals((1..3).toSet(), cache.entries.keys.map { it.screenshotPosition.index }.toSet())
    }

    @Test
    fun changedShaDimensionCropOrPipelineCausesOnlyThatScreenshotToMiss() = runTest {
        listOf(
            asset(2, sha = "changed"),
            asset(2, width = 200),
            asset(2, crop = OcrNormalizedCropRect(0.0, 0.0, 0.8, 1.0)),
        ).forEach { changedAsset ->
            val assetRepository = FakeAssetRepository((1..3).associateWith(::asset))
            val cache = FakeCacheRepository()
            val extractor = PositionTrackingExtractor()
            val runner = runner(assetRepository, cache, extractor)

            runner.process("tournament-1", "match-1")
            assetRepository.assets[2] = changedAsset
            runner.process("tournament-1", "match-1")

            assertEquals(4, cache.saveCount)
        }
    }

    @Test
    fun pipelineVersionMismatchIsACacheMiss() = runTest {
        val assetRepository = FakeAssetRepository(mapOf(1 to asset(1)))
        val cache = FakeCacheRepository()
        val fingerprint = assetRepository.fingerprint(RosterScreenshotPosition.ONE)
            .copy(ocrPipelineVersion = 0)
        cache.entries[fingerprint] = slotsFor(RosterScreenshotPosition.ONE)
        val extractor = PositionTrackingExtractor()

        runner(assetRepository, cache, extractor).process("tournament-1", "match-1")

        assertEquals(1, cache.saveCount)
    }

    @Test
    fun cacheReadFailureFallsBackToFreshOcr() = runTest {
        val cache = FakeCacheRepository().apply { readFailure = IllegalStateException("read") }
        val extractor = PositionTrackingExtractor()

        runner(FakeAssetRepository(mapOf(1 to asset(1))), cache, extractor)
            .process("tournament-1", "match-1")

    }

    @Test
    fun cacheWriteFailureStillReturnsFreshOcrResult() = runTest {
        val cache = FakeCacheRepository().apply { saveFailure = IllegalStateException("write") }
        val extractor = PositionTrackingExtractor()

        val result = runner(FakeAssetRepository(mapOf(1 to asset(1))), cache, extractor)
            .process("tournament-1", "match-1")

        assertEquals("S1P1", result.slots.first().players.first().playerName)
    }

    @Test
    fun changingOnlyScreenshotTwoLeavesOneAndThreeAsCacheHits() = runTest {
        val assetRepository = FakeAssetRepository((1..3).associateWith(::asset))
        val cache = FakeCacheRepository()
        val extractor = PositionTrackingExtractor()
        val runner = runner(assetRepository, cache, extractor)

        runner.process("tournament-1", "match-1")
        assetRepository.assets[2] = asset(2, sha = "new-sha-2")
        runner.process("tournament-1", "match-1")

        assertEquals(4, cache.saveCount)
    }

    private fun runner(
        assets: FakeAssetRepository,
        cache: FakeCacheRepository,
        extractor: PositionTrackingExtractor,
        semanticSlotNumbers: Map<RosterScreenshotPosition, List<Int?>> =
            RosterScreenshotPosition.entries.associateWith { position ->
                position.tournamentSlotRange.toList()
            },
        unavailableVisibleSlots: Set<RosterVisibleSlotPosition> = emptySet(),
    ): AndroidMatchLobbyPlayersOcrRunner {
        extractor.semanticSlotNumbers = semanticSlotNumbers
        return AndroidMatchLobbyPlayersOcrRunner(
            assetRepository = assets,
            cacheRepository = cache,
            slotNumberOcrRunner = FakeSlotNumberOcrRunner(semanticSlotNumbers, unavailableVisibleSlots),
            screenshotOwnerProvider = ownerProvider,
        )
    }

    private val ownerProvider = object : ScreenshotOwnerProvider {
        override suspend fun currentOwnerUserId(): String = "owner-1"
    }

    private class FakeSlotNumberOcrRunner(
        private val semanticSlotNumbers: Map<RosterScreenshotPosition, List<Int?>> =
            RosterScreenshotPosition.entries.associateWith { it.tournamentSlotRange.toList() },
        private val unavailableVisibleSlots: Set<RosterVisibleSlotPosition> = emptySet(),
    ) : MatchLobbySlotNumberOcrRunner {
        var processCount = 0
        override suspend fun process(tournamentId: String, matchId: String): MatchLobbySlotNumberOcrResult =
            MatchLobbySlotNumberOcrResult(
                RosterScreenshotPosition.entries.map { position ->
                    val requested = semanticSlotNumbers[position]
                    if (position.index !in 1..3 || requested?.let { values -> values.all { it == null } } == true) {
                        MatchLobbySlotNumberOcrScreenshotResult.Unavailable(
                            position,
                            MatchLobbySlotNumberOcrUnavailableReason.ASSET_UNAVAILABLE,
                        )
                    } else {
                        MatchLobbySlotNumberOcrScreenshotResult.Processed(
                            screenshotPosition = position,
                            slots = RosterVisibleSlotPosition.entries.map { visible ->
                                MatchLobbySlotNumberOcrSlot(visible, RosterSlotNumberCandidate.unavailable())
                            },
                            teamCropPreviews = MatchLobbyTeamCropPreviewResult.Available(
                                previews = RosterVisibleSlotPosition.entries
                                    .filterNot { it in unavailableVisibleSlots }
                                    .map { visible ->
                                        val explicit = requested?.getOrNull(visible.offset - 1)
                                        val firstValid = requested?.firstOrNull { it != null }
                                        val slot = if (requested != null && firstValid != null && requested.any { it == null }) {
                                            ((firstValid - 1) / 4) * 4 + visible.offset
                                        } else {
                                            explicit ?: position.tournamentSlotFor(visible)
                                        }
                                        MatchLobbyTeamCropPreview(
                                            visibleSlotPosition = visible,
                                            detectedSlotNumber = slot,
                                            image = FakeTeamCropImage,
                                            playerRowPreviews = LobbyPlayerRow.entries.map { row ->
                                                LobbyPlayerRowCropPreview(
                                                    row = row,
                                                    boundsInTeamCrop = LobbyPlayerRowCropBounds(0, row.ordinal * 10, 100, (row.ordinal + 1) * 10),
                                                    slotAnchorSource = LobbySlotAnchorSource.ML_KIT_SLOT,
                                                    slotAnchorY = 50.0,
                                                    structuralEvidence = "S${position.index}P${row.ordinal + 1}",
                                                )
                                            },
                                            authoritativeTeamSlotNumber = slot,
                                        )
                                    },
                                unavailable = unavailableVisibleSlots.map { visible ->
                                    MatchLobbyTeamCropPreviewOutcome.Unavailable(
                                        visibleSlotPosition = visible,
                                        reason = MatchLobbyTeamCropPreviewUnavailableReason.BITMAP_CREATION_FAILED,
                                    )
                                },
                            ),
                        )
                    }
                },
            ).also { processCount++ }
    }

    private object CancellingSlotNumberOcrRunner : MatchLobbySlotNumberOcrRunner {
        override suspend fun process(tournamentId: String, matchId: String): MatchLobbySlotNumberOcrResult =
            throw CancellationException("cancelled")
    }

    private object FakeTeamCropImage : MatchLobbyTeamCropPreviewImage

    private class FakeAssetRepository(
        initialAssets: Map<Int, MatchLobbyScreenshotAssetEntity>,
    ) : MatchLobbyScreenshotAssetRepository {
        val assets = initialAssets.toMutableMap()
        override fun observeByMatchId(matchId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = flowOf(emptyList())
        override fun observeByIdentity(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity): Flow<MatchLobbyScreenshotAssetEntity?> = flowOf(null)
        override suspend fun getByIdentity(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity) = assets[identity.lobbyScreenshotIndex]
        override suspend fun getByIdentityAndOwner(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity, ownerUserId: String) =
            assets[identity.lobbyScreenshotIndex]?.takeIf { it.ownerUserId == ownerUserId }
        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = flowOf(emptyList())
        override suspend fun findDuplicateFingerprint(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity, sha256: String) = null
        override suspend fun saveOrReplace(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetSaveResult = MatchLobbyScreenshotAssetSaveResult.Saved
        override suspend fun markLocalMissing(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun markCleanupFailure(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun deleteByIdentity(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity) = Unit
        override suspend fun deleteByMatchId(matchId: String) = Unit
        override suspend fun persistConfirmedCrop(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity, crop: OcrNormalizedCropRect, updatedAt: Long): MatchLobbyScreenshotCropSaveResult = MatchLobbyScreenshotCropSaveResult.MissingAsset
        override suspend fun clearConfirmedCrop(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity, updatedAt: Long): MatchLobbyScreenshotCropSaveResult = MatchLobbyScreenshotCropSaveResult.MissingAsset

        fun fingerprint(position: RosterScreenshotPosition): MatchLobbyOcrCacheFingerprint =
            assets.getValue(position.index).toMatchLobbyOcrCacheFingerprint(
                com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity(
                    "tournament-1",
                    "match-1",
                    position.index,
                ),
                position,
            )!!
    }

    private class FakePanelPreparer : RosterOcrPanelPreparer {
        var releasedPanels = 0
        override suspend fun prepare(source: RosterOcrScreenshotSource): RosterOcrPanelPreparationResult =
            RosterOcrPanelPreparationResult.Prepared(
                object : RosterOcrPreparedPanel {
                    override val croppedPanelImage: OcrPreprocessingImage = FakeImage
                    override val croppedPanelInput = CroppedRosterPanelInput(
                        screenshotPosition = source.screenshotPosition,
                        isPreparedRosterCrop = true,
                        imageWidth = 100,
                        imageHeight = 100,
                    )
                    override fun release() { releasedPanels++ }
                },
            )
    }

    private class PositionTrackingExtractor(
        var semanticSlotNumbers: Map<RosterScreenshotPosition, List<Int?>> =
            RosterScreenshotPosition.entries.associateWith { position ->
                position.tournamentSlotRange.toList()
            },
    ) : com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractor {
        var position: RosterScreenshotPosition? = null
        var extractCount = 0
        override suspend fun extract(input: RosterRawOcrExtractionInput): List<RosterRawOcrExtractionResult> {
            extractCount++
            position = input.croppedPanelInput.screenshotPosition
            val screenshotPosition = requireNotNull(position)
            return RosterVisibleSlotPosition.entries.map { visiblePosition ->
                val identity = RosterRawOcrRegionIdentity(
                    screenshotPosition = screenshotPosition,
                    visibleSlotPosition = visiblePosition,
                    regionType = RosterRawOcrRegionType.SLOT_CONTENT,
                )
                val slotNumber = semanticSlotNumbers[screenshotPosition]
                    ?.getOrNull(visiblePosition.offset - 1)
                if (slotNumber == null) {
                    RosterRawOcrExtractionResult.Empty(identity)
                } else {
                    RosterRawOcrExtractionResult.Extracted(
                        RosterRawOcrRegionEvidence(
                            regionIdentity = identity,
                            rawText = slotNumber.toString(),
                            blocks = listOf(
                                RawOcrBlock(
                                    text = slotNumber.toString(),
                                    geometry = RawOcrGeometry(
                                        boundingBox = RawOcrBoundingBox(1, 1, 5, 10),
                                        cornerPoints = null,
                                    ),
                                    recognizedLanguage = null,
                                    confidence = RawOcrConfidence.Unavailable,
                                    lines = listOf(
                                        RawOcrLine(
                                            text = slotNumber.toString(),
                                            geometry = RawOcrGeometry(
                                                boundingBox = RawOcrBoundingBox(1, 1, 5, 10),
                                                cornerPoints = null,
                                            ),
                                            recognizedLanguage = null,
                                            confidence = RawOcrConfidence.Unavailable,
                                            elements = listOf(
                                                RawOcrElement(
                                                    text = slotNumber.toString(),
                                                    geometry = RawOcrGeometry(
                                                        boundingBox = RawOcrBoundingBox(1, 1, 5, 10),
                                                        cornerPoints = null,
                                                    ),
                                                    recognizedLanguage = null,
                                                    confidence = RawOcrConfidence.Unavailable,
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                            rawEvidence = emptyList<RosterRawOcrEvidence>(),
                            regionWidth = 50,
                            regionHeight = 50,
                        ),
                    )
                }
            }
        }
    }

    private class PositionParser(
        private val extractor: PositionTrackingExtractor,
        private val semanticSlotNumbers: Map<RosterScreenshotPosition, List<Int?>> =
            RosterScreenshotPosition.entries.associateWith { position ->
                position.tournamentSlotRange.toList()
            },
    ) : RosterCandidateParser {
        override fun parse(input: RosterCandidateParseInput): RosterCandidateParseResult {
            val position = requireNotNull(extractor.position)
            return RosterCandidateParseResult(
                slots = RosterVisibleSlotPosition.entries.map { visible ->
                    RosterSlotCandidate(
                        screenshotPosition = position,
                        visibleSlotPosition = visible,
                        intendedTournamentSlotRange = position.tournamentSlotRange,
                        intendedTournamentSlot = position.tournamentSlotFor(visible),
                        teamNameCandidate = RosterTeamNameCandidate(
                            status = RosterCandidateParseStatus.UNSUPPORTED,
                            failure = com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseFailure.UNSUPPORTED_TEAM_NAME_REGION,
                            rawSourceResults = emptyList(),
                            confidence = com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence.Unavailable,
                        ),
                        playerNameCandidates = (1..4).map { player ->
                            RosterPlayerNameCandidate(
                                regionIdentity = com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionIdentity(
                                    position, visible,
                                    com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionType.PLAYER_ROW,
                                    player,
                                ),
                                playerRowIndex = player,
                                status = RosterCandidateParseStatus.PARSED,
                                candidateText = "S${position.index}P$player",
                                failure = null,
                                rawSourceResults = emptyList(),
                                confidence = com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence.Unavailable,
                            )
                        },
                        slotNumberCandidate = semanticSlotNumbers[position]
                            ?.getOrNull(visible.offset - 1)
                            ?.let { slotNumber ->
                                RosterSlotNumberCandidate(
                                    status = RosterCandidateParseStatus.PARSED,
                                    detectedSlotNumber = slotNumber,
                                    failure = null,
                                    rawSourceResults = emptyList(),
                                    confidence = com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence.Unavailable,
                                )
                            }
                            ?: RosterSlotNumberCandidate.unavailable(),
                    )
                },
                inputFailures = emptyList(),
            )
        }
    }

    private object FakeImage : OcrPreprocessingImage {
        override val width = 100
        override val height = 100
    }

    private fun asset(
        index: Int,
        sha: String = "sha-$index",
        width: Int = 100,
        crop: OcrNormalizedCropRect = OcrNormalizedCropRect(0.0, 0.0, 1.0, 1.0),
    ) = MatchLobbyScreenshotAssetEntity(
        tournamentId = "tournament-1",
        matchId = "match-1",
        lobbyScreenshotIndex = index,
        ownerUserId = "owner-1",
        localRelativePath = "screenshots/tournament-1/match-1/$index.jpg",
        fileExtension = "jpg",
        mimeType = "image/jpeg",
        originalWidth = width,
        originalHeight = 100,
        byteSize = 10,
        sha256 = sha,
        localStatus = "AVAILABLE",
        uploadStatus = "NOT_UPLOADED",
        uploadFailureCode = null,
        storageBucket = null,
        storageObjectPath = null,
        cropProfileId = OcrCropValidationProfiles.Lobby.id,
        cropLeft = crop.left,
        cropTop = crop.top,
        cropRight = crop.right,
        cropBottom = crop.bottom,
        createdAt = 1,
        updatedAt = 1,
        preservedAt = 1,
        uploadedAt = null,
        revision = 1,
    )

    private fun slotsFor(position: RosterScreenshotPosition) = position.tournamentSlotRange.map { slot ->
        MatchLobbyPlayersOcrSlot(
            slotNumber = slot,
            players = (1..4).map { player -> MatchLobbyPlayersOcrPlayer(player, "S${position.index}P$player") },
        )
    }

    private class FakeCacheRepository : MatchLobbyOcrCacheRepository {
        val entries = mutableMapOf<MatchLobbyOcrCacheFingerprint, List<MatchLobbyPlayersOcrSlot>>()
        var saveCount = 0
        var readFailure: Throwable? = null
        var saveFailure: Throwable? = null

        override suspend fun read(fingerprint: MatchLobbyOcrCacheFingerprint): List<MatchLobbyPlayersOcrSlot>? {
            readFailure?.let { throw it }
            return entries[fingerprint]
        }

        override suspend fun save(
            fingerprint: MatchLobbyOcrCacheFingerprint,
            slots: List<MatchLobbyPlayersOcrSlot>,
        ) {
            saveFailure?.let { throw it }
            saveCount++
            entries[fingerprint] = slots
        }

        override suspend fun readByOwner(
            fingerprint: MatchLobbyOcrCacheFingerprint,
            ownerUserId: String,
        ): List<MatchLobbyPlayersOcrSlot>? = if (ownerUserId == "owner-1") read(fingerprint) else null

        override suspend fun saveByOwner(
            fingerprint: MatchLobbyOcrCacheFingerprint,
            slots: List<MatchLobbyPlayersOcrSlot>,
            ownerUserId: String,
        ): Boolean {
            if (ownerUserId != "owner-1") return false
            save(fingerprint, slots)
            return true
        }

        override suspend fun deleteByMatchAndIndex(matchId: String, lobbyScreenshotIndex: Int) = Unit
    }
}
