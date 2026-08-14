package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotCropSaveResult
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionInput
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterPanelInput
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseInput
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParser
import com.hoggamers.rankforge.domain.ocr.parsing.RosterPlayerNameCandidate
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociationInput
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociationResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociator
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotCandidate
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociationStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterTeamNameCandidate
import com.hoggamers.rankforge.domain.ocr.parsing.RosterTournamentSlotCandidate
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

class MatchLobbyPlayersOcrRunnerTest {
    @Test
    fun mapsAllLobbyScreenshotsToDeterministicTournamentSlotsAndReleasesPanels() = runTest {
        val assets = (1..3).associateWith(::asset)
        val preparer = FakePanelPreparer()
        val extractor = PositionTrackingExtractor()
        val runner = AndroidMatchLobbyPlayersOcrRunner(
            assetRepository = FakeAssetRepository(assets),
            panelPreparer = preparer,
            extractor = extractor,
            parser = PositionParser(extractor),
            associator = PositionAssociator(),
        )

        val result = runner.process("tournament-1", "match-1")

        assertEquals((1..12).toList(), result.slots.map { it.slotNumber })
        assertEquals(
            (1..12).map { slot -> (1..4).map { player -> "S${(slot - 1) / 4 + 1}P$player" } },
            result.slots.map { slot -> slot.players.map { it.playerName } },
        )
        assertEquals(3, preparer.releasedPanels)
    }

    @Test
    fun cancellationFromOneScreenshotPropagates() {
        val runner = AndroidMatchLobbyPlayersOcrRunner(
            assetRepository = FakeAssetRepository(mapOf(1 to asset(1))),
            panelPreparer = object : RosterOcrPanelPreparer {
                override suspend fun prepare(source: RosterOcrScreenshotSource): RosterOcrPanelPreparationResult =
                    throw CancellationException("cancelled")
            },
            extractor = PositionTrackingExtractor(),
            parser = PositionParser(PositionTrackingExtractor()),
            associator = PositionAssociator(),
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
            panelPreparer = preparer,
            extractor = extractor,
            parser = PositionParser(extractor),
            associator = PositionAssociator(),
        )

        val result = runner.process("tournament-1", "match-1")

        assertEquals("S1P1", result.slots[0].players[0].playerName)
        assertEquals(null, result.slots[4].players[0].playerName)
        assertEquals("S3P1", result.slots[8].players[0].playerName)
        assertEquals(2, preparer.releasedPanels)
    }

    private class FakeAssetRepository(
        private val assets: Map<Int, MatchLobbyScreenshotAssetEntity>,
    ) : MatchLobbyScreenshotAssetRepository {
        override fun observeByMatchId(matchId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = flowOf(emptyList())
        override fun observeByIdentity(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity): Flow<MatchLobbyScreenshotAssetEntity?> = flowOf(null)
        override suspend fun getByIdentity(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity) = assets[identity.lobbyScreenshotIndex]
        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = flowOf(emptyList())
        override suspend fun findDuplicateFingerprint(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity, sha256: String) = null
        override suspend fun saveOrReplace(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetSaveResult = MatchLobbyScreenshotAssetSaveResult.Saved
        override suspend fun markLocalMissing(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun markCleanupFailure(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun deleteByIdentity(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity) = Unit
        override suspend fun deleteByMatchId(matchId: String) = Unit
        override suspend fun persistConfirmedCrop(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity, crop: OcrNormalizedCropRect, updatedAt: Long): MatchLobbyScreenshotCropSaveResult = MatchLobbyScreenshotCropSaveResult.MissingAsset
        override suspend fun clearConfirmedCrop(identity: com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity, updatedAt: Long): MatchLobbyScreenshotCropSaveResult = MatchLobbyScreenshotCropSaveResult.MissingAsset
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

    private class PositionTrackingExtractor : com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractor {
        var position: RosterScreenshotPosition? = null
        override suspend fun extract(input: RosterRawOcrExtractionInput): List<RosterRawOcrExtractionResult> {
            position = input.croppedPanelInput.screenshotPosition
            return emptyList()
        }
    }

    private class PositionParser(private val extractor: PositionTrackingExtractor) : RosterCandidateParser {
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
                    )
                },
                inputFailures = emptyList(),
            )
        }
    }

    private class PositionAssociator : RosterSlotAssociator {
        override fun associate(input: RosterSlotAssociationInput): RosterSlotAssociationResult =
            RosterSlotAssociationResult(
                tournamentSlotCandidates = input.parsedCandidates.slots.map { candidate ->
                    RosterTournamentSlotCandidate(
                        tournamentSlotNumber = candidate.intendedTournamentSlot,
                        sourceScreenshotPosition = candidate.screenshotPosition,
                        sourceVisibleSlotPosition = candidate.visibleSlotPosition,
                        teamNameCandidate = candidate.teamNameCandidate,
                        playerNameCandidates = candidate.playerNameCandidates,
                        associationStatus = RosterSlotAssociationStatus.ASSOCIATED,
                    )
                },
                failures = emptyList(),
            )
    }

    private object FakeImage : OcrPreprocessingImage {
        override val width = 100
        override val height = 100
    }

    private fun asset(index: Int) = MatchLobbyScreenshotAssetEntity(
        tournamentId = "tournament-1",
        matchId = "match-1",
        lobbyScreenshotIndex = index,
        ownerUserId = "owner",
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
        cropProfileId = OcrCropValidationProfiles.Lobby.id,
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
}
