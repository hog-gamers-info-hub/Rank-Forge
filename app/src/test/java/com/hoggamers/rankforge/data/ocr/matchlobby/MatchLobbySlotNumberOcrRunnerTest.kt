package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparer
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparationFailure
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparationResult
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrScreenshotSource
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import com.hoggamers.rankforge.presentation.screen.ScreenshotOwnerProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchLobbySlotNumberOcrRunnerTest {
    @Test
    fun invalidContextOrOwnerDoesNotReadAssetsOrRunPanelPreparation() = runTest {
        val assets = FakeAssetRepository()
        val preparer = RecordingPanelPreparer()
        val runner = runner(assets, preparer, ownerId = null)

        val blank = runner.process("", "match-1")
        val noOwner = runner.process("tournament-1", "match-1")

        assertUnavailableForAll(blank, MatchLobbySlotNumberOcrUnavailableReason.INVALID_MATCH_CONTEXT)
        assertUnavailableForAll(noOwner, MatchLobbySlotNumberOcrUnavailableReason.OWNER_UNAVAILABLE)
        assertEquals(0, assets.requests.size)
        assertEquals(0, preparer.prepareCount)
    }

    @Test
    fun panelPreparationFailureIsReportedForEachPreparedScreenshot() = runTest {
        val assets = FakeAssetRepository((1..3).associateWith(::asset))
        val preparer = RecordingPanelPreparer(
            RosterOcrPanelPreparationResult.Failed(RosterOcrPanelPreparationFailure.INVALID_CROP),
        )
        val result = runner(assets, preparer).process("tournament-1", "match-1")

        assertEquals(3, preparer.prepareCount)
        assertTrue(result.screenshots.all {
            it is MatchLobbySlotNumberOcrScreenshotResult.Unavailable &&
                it.reason == MatchLobbySlotNumberOcrUnavailableReason.PANEL_PREPARATION_FAILED
        })
    }

    private fun runner(
        assets: FakeAssetRepository,
        preparer: RecordingPanelPreparer,
        ownerId: String? = "owner-1",
    ) = AndroidMatchLobbySlotNumberOcrRunner(
        assetRepository = assets,
        panelPreparer = preparer,
        screenshotOwnerProvider = object : ScreenshotOwnerProvider {
            override suspend fun currentOwnerUserId(): String? = ownerId
        },
    )

    private fun assertUnavailableForAll(
        result: MatchLobbySlotNumberOcrResult,
        reason: MatchLobbySlotNumberOcrUnavailableReason,
    ) {
        assertTrue(result.screenshots.all {
            it is MatchLobbySlotNumberOcrScreenshotResult.Unavailable && it.reason == reason
        })
    }

    private fun asset(index: Int) = MatchLobbyScreenshotAssetEntity(
        tournamentId = "tournament-1", matchId = "match-1", lobbyScreenshotIndex = index,
        ownerUserId = "owner-1", localRelativePath = "screenshots/$index.jpg", fileExtension = "jpg",
        mimeType = "image/jpeg", originalWidth = 100, originalHeight = 100, byteSize = 10,
        sha256 = "sha-$index", localStatus = "AVAILABLE", uploadStatus = "NOT_UPLOADED",
        uploadFailureCode = null, storageBucket = null, storageObjectPath = null,
        cropProfileId = OcrCropValidationProfiles.Lobby.id, cropLeft = 0.0, cropTop = 0.0,
        cropRight = 1.0, cropBottom = 1.0, createdAt = 1, updatedAt = 1, preservedAt = 1,
        uploadedAt = null, revision = 1,
    )

    private class FakeAssetRepository(
        private val assets: Map<Int, MatchLobbyScreenshotAssetEntity> = emptyMap(),
    ) : MatchLobbyScreenshotAssetRepository {
        val requests = mutableListOf<MatchLobbyScreenshotIdentity>()

        override fun observeByMatchId(matchId: String) = throw UnsupportedOperationException()
        override fun observeByIdentity(identity: MatchLobbyScreenshotIdentity) = throw UnsupportedOperationException()
        override suspend fun getByIdentity(identity: MatchLobbyScreenshotIdentity) = null
        override fun observeByTournamentId(tournamentId: String) = throw UnsupportedOperationException()
        override suspend fun getByIdentityAndOwner(identity: MatchLobbyScreenshotIdentity, ownerUserId: String) =
            assets[identity.lobbyScreenshotIndex]?.also { requests += identity }
        override suspend fun findDuplicateFingerprint(identity: MatchLobbyScreenshotIdentity, sha256: String) = null
        override suspend fun saveOrReplace(asset: MatchLobbyScreenshotAssetEntity) = throw UnsupportedOperationException()
        override suspend fun markLocalMissing(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun markCleanupFailure(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) = Unit
        override suspend fun deleteByMatchId(matchId: String) = Unit
        override suspend fun persistConfirmedCrop(identity: MatchLobbyScreenshotIdentity, crop: com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect, updatedAt: Long) = throw UnsupportedOperationException()
        override suspend fun clearConfirmedCrop(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = throw UnsupportedOperationException()
    }

    private class RecordingPanelPreparer(
        private val result: RosterOcrPanelPreparationResult? = null,
    ) : RosterOcrPanelPreparer {
        var prepareCount = 0

        override suspend fun prepare(source: RosterOcrScreenshotSource): RosterOcrPanelPreparationResult {
            prepareCount++
            return result ?: RosterOcrPanelPreparationResult.Failed(
                RosterOcrPanelPreparationFailure.INVALID_CROP,
            )
        }
    }
}
