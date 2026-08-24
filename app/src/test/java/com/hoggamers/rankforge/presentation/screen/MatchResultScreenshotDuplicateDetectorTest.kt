package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchResultScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.OcrScreenshotKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultScreenshotDuplicateDetectorTest {
    @Test
    fun sameIdentityDuplicateIsControlledNoOp() = runTest {
        val detector = detector(mapOf("first" to "same".encodeToByteArray(), "second" to "same".encodeToByteArray()))
        val identity = identity(matchId = "match-1", role = MatchResultScreenshotRole.MATCH_RESULT_UPPER)

        val first = detector.link(identity, "first", currentFingerprint = null)
            as MatchResultScreenshotDuplicateLinkResult.Linked

        assertEquals(
            MatchResultScreenshotDuplicateLinkResult.SameIdentity,
            detector.link(identity, "second", currentFingerprint = first.fingerprint),
        )
    }

    @Test
    fun sameMatchOtherRoleDuplicateIsRejected() = runTest {
        val detector = detector(mapOf("upper" to "same".encodeToByteArray(), "lower" to "same".encodeToByteArray()))
        val upper = identity(matchId = "match-1", role = MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        val lower = identity(matchId = "match-1", role = MatchResultScreenshotRole.MATCH_RESULT_LOWER)

        detector.link(upper, "upper", currentFingerprint = null)

        assertEquals(
            MatchResultScreenshotDuplicateLinkResult.LinkedToOtherIdentity(upper),
            detector.link(lower, "lower", currentFingerprint = null),
        )
    }

    @Test
    fun otherMatchDuplicateInSameTournamentIsAllowed() = runTest {
        val detector = detector(mapOf("one" to "same".encodeToByteArray(), "two" to "same".encodeToByteArray()))
        val first = identity(tournamentId = "tournament-1", matchId = "match-1")
        val second = identity(tournamentId = "tournament-1", matchId = "match-2")
        val otherTournament = identity(tournamentId = "tournament-2", matchId = "match-2")

        detector.link(first, "one", currentFingerprint = null)

        assertTrue(detector.link(second, "two", currentFingerprint = null) is MatchResultScreenshotDuplicateLinkResult.Linked)
        assertTrue(detector.link(otherTournament, "two", currentFingerprint = null) is MatchResultScreenshotDuplicateLinkResult.Linked)
    }

    @Test
    fun freshDetectorAllowsPersistedDuplicateInAnotherMatchAfterRestart() = runTest {
        val bytesByUri = mapOf("same" to "same".encodeToByteArray())
        val fingerprint = fingerprintGenerator(bytesByUri).fingerprint("same") as ImageSourceFingerprintResult.Success
        val existing = identity(matchId = "match-1")
        val detector = detector(
            bytesByUri = bytesByUri,
            repository = FakeMatchResultScreenshotAssetRepository(
                assets = listOf(asset(existing, fingerprint.value)),
            ),
        )

        assertTrue(
            detector.link(identity(matchId = "match-2"), "same", currentFingerprint = null)
                is MatchResultScreenshotDuplicateLinkResult.Linked,
        )
    }

    @Test
    fun persistedSameIdentityFingerprintIsNoOp() = runTest {
        val bytesByUri = mapOf("same" to "same".encodeToByteArray())
        val fingerprint = fingerprintGenerator(bytesByUri).fingerprint("same") as ImageSourceFingerprintResult.Success
        val existing = identity(matchId = "match-1")
        val detector = detector(
            bytesByUri = bytesByUri,
            repository = FakeMatchResultScreenshotAssetRepository(
                assets = listOf(asset(existing, fingerprint.value)),
            ),
        )

        assertEquals(
            MatchResultScreenshotDuplicateLinkResult.SameIdentity,
            detector.link(existing, "same", currentFingerprint = null),
        )
    }

    @Test
    fun repositoryReadFailureFailsClosed() = runTest {
        val detector = detector(
            bytesByUri = mapOf("same" to "same".encodeToByteArray()),
            repository = FakeMatchResultScreenshotAssetRepository(failure = IllegalStateException("Room unavailable")),
        )

        assertEquals(
            MatchResultScreenshotDuplicateLinkResult.StateConflict,
            detector.link(identity(matchId = "match-2"), "same", currentFingerprint = null),
        )
    }

    @Test
    fun fingerprintFailureIsControlled() = runTest {
        val detector = detector(emptyMap())

        assertEquals(
            MatchResultScreenshotDuplicateLinkResult.FingerprintFailure,
            detector.link(identity(), "missing", currentFingerprint = null),
        )
    }

    private fun detector(
        bytesByUri: Map<String, ByteArray>,
        repository: MatchResultScreenshotAssetRepository = FakeMatchResultScreenshotAssetRepository(),
    ) = MatchResultScreenshotDuplicateDetector(
        fingerprintGenerator = fingerprintGenerator(bytesByUri),
        assetRepository = repository,
        screenshotOwnerProvider = object : ScreenshotOwnerProvider {
            override suspend fun currentOwnerUserId(): String = "test-owner"
        },
    )

    private fun fingerprintGenerator(
        bytesByUri: Map<String, ByteArray>,
    ) = ImageSourceFingerprintGenerator(
        ImageSourceStreamOpener { uri -> bytesByUri[uri]?.inputStream() },
        Dispatchers.Unconfined,
    )

    private fun identity(
        tournamentId: String = "tournament-1",
        matchId: String = "match-1",
        role: MatchResultScreenshotRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
    ) = MatchResultScreenshotIdentity(
        tournamentId = tournamentId,
        matchId = matchId,
        role = role,
    )

    private fun asset(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
    ) = MatchResultScreenshotAssetEntity(
        tournamentId = identity.tournamentId,
        matchId = identity.matchId,
        screenshotKind = OcrScreenshotKind.MATCH_RESULT.name,
        screenshotRole = identity.role.name,
        ownerUserId = "owner-1",
        localRelativePath = "screenshots/${identity.tournamentId}/${identity.matchId}/result/original.png",
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 1600,
        originalHeight = 720,
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

    private class FakeMatchResultScreenshotAssetRepository(
        private val assets: List<MatchResultScreenshotAssetEntity> = emptyList(),
        private val failure: Throwable? = null,
    ) : MatchResultScreenshotAssetRepository {
        override fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>> = emptyFlow()
        override fun observeByIdentityAndOwner(identity: MatchResultScreenshotIdentity, ownerUserId: String): Flow<MatchResultScreenshotAssetEntity?> =
            if (ownerUserId.isBlank()) emptyFlow() else observeByIdentity(identity)

        override fun observeByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): Flow<MatchResultScreenshotAssetEntity?> = flowOf(assets.firstOrNull { it.matches(identity) })

        override suspend fun getByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): MatchResultScreenshotAssetEntity? {
            failure?.let { throw it }
            return assets.firstOrNull { it.matches(identity) }
        }
        override suspend fun getByIdentityAndOwner(identity: MatchResultScreenshotIdentity, ownerUserId: String) =
            if (ownerUserId.isBlank()) null else getByIdentity(identity)

        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
            flowOf(assets.filter { it.tournamentId == tournamentId })

        override suspend fun findDuplicateFingerprint(
            identity: MatchResultScreenshotIdentity,
            sha256: String,
        ): MatchResultScreenshotAssetEntity? {
            failure?.let { throw it }
            return assets.firstOrNull { asset ->
                asset.matchId == identity.matchId &&
                    asset.sha256 == sha256 &&
                    asset.screenshotRole != identity.role.name
            }
        }
        override suspend fun findDuplicateFingerprintAndOwner(identity: MatchResultScreenshotIdentity, sha256: String, ownerUserId: String) =
            if (ownerUserId.isBlank()) null else findDuplicateFingerprint(identity, sha256)

        override suspend fun saveOrReplace(
            asset: MatchResultScreenshotAssetEntity,
        ): MatchResultScreenshotAssetSaveResult = MatchResultScreenshotAssetSaveResult.Saved

        override suspend fun markLocalMissing(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit

        override suspend fun markCleanupFailure(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit

        override suspend fun persistConfirmedCrop(
            identity: MatchResultScreenshotIdentity,
            crop: OcrNormalizedCropRect,
            updatedAt: Long,
        ): MatchResultScreenshotCropSaveResult = MatchResultScreenshotCropSaveResult.Saved

        override suspend fun clearConfirmedCrop(
            identity: MatchResultScreenshotIdentity,
            updatedAt: Long,
        ): MatchResultScreenshotCropSaveResult = MatchResultScreenshotCropSaveResult.Saved

        override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) = Unit

        override suspend fun deleteByMatchId(matchId: String) = Unit

        private fun MatchResultScreenshotAssetEntity.matches(identity: MatchResultScreenshotIdentity): Boolean =
            tournamentId == identity.tournamentId &&
                matchId == identity.matchId &&
                screenshotRole == identity.role.name
    }
}
