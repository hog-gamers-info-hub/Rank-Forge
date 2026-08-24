package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchLobbyScreenshotDuplicateDetectorTest {
    @Test
    fun lobbyFingerprintsAreScopedToMatch() = runTest {
        val detector = detector(mapOf("one" to "same".encodeToByteArray(), "two" to "same".encodeToByteArray()))
        val first = identity("tournament-1", "match-1", 1)
        val secondIndex = identity("tournament-1", "match-1", 2)
        val secondMatch = identity("tournament-1", "match-2", 1)
        val otherTournament = identity("tournament-2", "match-2", 1)

        val linked = detector.link(first, "one", null) as MatchLobbyScreenshotDuplicateLinkResult.Linked
        assertEquals(
            MatchLobbyScreenshotDuplicateLinkResult.SameIdentity,
            detector.link(first, "two", linked.fingerprint,),
        )
        assertEquals(
            MatchLobbyScreenshotDuplicateLinkResult.LinkedToOtherIdentity(first),
            detector.link(secondIndex, "two", null),
        )
        assertEquals(
            MatchLobbyScreenshotDuplicateLinkResult.Linked("same".encodeToByteArray().sha256()),
            detector.link(secondMatch, "two", null),
        )
        assertTrue(detector.link(otherTournament, "two", null) is MatchLobbyScreenshotDuplicateLinkResult.Linked)
    }

    @Test
    fun rollbackAndUnlinkReleaseTheFingerprint() = runTest {
        val detector = detector(mapOf("one" to "one".encodeToByteArray(), "two" to "two".encodeToByteArray()))
        val identity = identity("tournament-1", "match-1", 1)
        val first = detector.link(identity, "one", null) as MatchLobbyScreenshotDuplicateLinkResult.Linked
        val second = detector.link(identity, "two", first.fingerprint) as MatchLobbyScreenshotDuplicateLinkResult.Linked

        assertTrue(detector.rollback(identity, second.fingerprint, first.fingerprint))
        assertEquals(
            MatchLobbyScreenshotDuplicateLinkResult.Linked(second.fingerprint),
            detector.link(identity, "two", first.fingerprint),
        )
        assertEquals(
            MatchLobbyScreenshotDuplicateUnlinkResult.Unlinked,
            detector.unlink(identity, second.fingerprint),
        )
    }

    @Test
    fun persistedDuplicateAndFingerprintFailureAreControlled() = runTest {
        val identity = identity("tournament-1", "match-1", 1)
        val persisted = asset(identity, "same".encodeToByteArray().sha256())
        val detector = detector(
            mapOf("same" to "same".encodeToByteArray()),
            FakeRepository(listOf(persisted)),
        )
        assertEquals(
            MatchLobbyScreenshotDuplicateLinkResult.Linked("same".encodeToByteArray().sha256()),
            detector.link(identity("tournament-1", "match-2", 1), "same", null),
        )
        assertEquals(
            MatchLobbyScreenshotDuplicateLinkResult.FingerprintFailure,
            detector(mapOf()).link(identity, "missing", null),
        )
    }

    private fun detector(
        bytesByUri: Map<String, ByteArray>,
        repository: MatchLobbyScreenshotAssetRepository = FakeRepository(),
    ) = MatchLobbyScreenshotDuplicateDetector(
        fingerprintGenerator = ImageSourceFingerprintGenerator(
            ImageSourceStreamOpener { uri -> bytesByUri[uri]?.inputStream() },
            Dispatchers.Unconfined,
        ),
        assetRepository = repository,
        screenshotOwnerProvider = object : ScreenshotOwnerProvider {
            override suspend fun currentOwnerUserId(): String = "test-owner"
        },
    )

    private fun identity(tournamentId: String, matchId: String, index: Int) =
        MatchLobbyScreenshotIdentity(tournamentId, matchId, index)

    private fun asset(identity: MatchLobbyScreenshotIdentity, sha256: String) =
        MatchLobbyScreenshotAssetEntity(
            tournamentId = identity.tournamentId,
            matchId = identity.matchId,
            lobbyScreenshotIndex = identity.lobbyScreenshotIndex,
            ownerUserId = "owner",
            localRelativePath = "screenshots/${identity.tournamentId}/${identity.matchId}/lobby/${identity.lobbyScreenshotIndex}/original.png",
            fileExtension = "png",
            mimeType = "image/png",
            originalWidth = 100,
            originalHeight = 100,
            byteSize = 4,
            sha256 = sha256,
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

    private class FakeRepository(
        private val assets: List<MatchLobbyScreenshotAssetEntity> = emptyList(),
        private val fail: AtomicBoolean = AtomicBoolean(false),
    ) : MatchLobbyScreenshotAssetRepository {
        override fun observeByMatchId(matchId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = emptyFlow()
        override fun observeByIdentityAndOwner(identity: MatchLobbyScreenshotIdentity, ownerUserId: String): Flow<MatchLobbyScreenshotAssetEntity?> =
            if (ownerUserId.isBlank()) emptyFlow() else observeByIdentity(identity)
        override fun observeByIdentity(identity: MatchLobbyScreenshotIdentity): Flow<MatchLobbyScreenshotAssetEntity?> = flowOf(null)
        override suspend fun getByIdentity(identity: MatchLobbyScreenshotIdentity): MatchLobbyScreenshotAssetEntity? {
            if (fail.get()) error("repository failure")
            return assets.firstOrNull { it.tournamentId == identity.tournamentId && it.matchId == identity.matchId && it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex }
        }
        override suspend fun getByIdentityAndOwner(identity: MatchLobbyScreenshotIdentity, ownerUserId: String) =
            if (ownerUserId.isBlank()) null else getByIdentity(identity)
        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = flowOf(emptyList())
        override suspend fun findDuplicateFingerprint(identity: MatchLobbyScreenshotIdentity, sha256: String): MatchLobbyScreenshotAssetEntity? {
            if (fail.get()) error("repository failure")
            return assets.firstOrNull { it.matchId == identity.matchId && it.sha256 == sha256 && it.lobbyScreenshotIndex != identity.lobbyScreenshotIndex }
        }
        override suspend fun findDuplicateFingerprintAndOwner(identity: MatchLobbyScreenshotIdentity, sha256: String, ownerUserId: String) =
            if (ownerUserId.isBlank()) null else findDuplicateFingerprint(identity, sha256)
        override suspend fun saveOrReplace(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetSaveResult = MatchLobbyScreenshotAssetSaveResult.Saved
        override suspend fun markLocalMissing(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun markCleanupFailure(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) = Unit
        override suspend fun deleteByMatchId(matchId: String) = Unit
        override suspend fun persistConfirmedCrop(identity: MatchLobbyScreenshotIdentity, crop: OcrNormalizedCropRect, updatedAt: Long) = MatchLobbyScreenshotCropSaveResult.Saved
        override suspend fun clearConfirmedCrop(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = MatchLobbyScreenshotCropSaveResult.Saved
    }
}

private fun ByteArray.sha256(): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
