package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.ScreenshotMetadataEntity
import com.hoggamers.rankforge.data.local.ScreenshotMetadataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScreenshotDuplicateDetectorTest {
    @Test
    fun fingerprintIsDeterministicForIdenticalBytesAndDiffersForDifferentBytes() = runTest {
        val generator = fingerprintGenerator(
            mapOf(
                "content://picker/one" to "abc".encodeToByteArray(),
                "content://picker/two" to "abc".encodeToByteArray(),
                "content://picker/three" to "different".encodeToByteArray(),
            ),
        )

        val first = generator.fingerprint("content://picker/one") as ImageSourceFingerprintResult.Success
        val second = generator.fingerprint("content://picker/two") as ImageSourceFingerprintResult.Success
        val different = generator.fingerprint("content://picker/three") as ImageSourceFingerprintResult.Success

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", first.value)
        assertEquals(first.value, second.value)
        assertNotEquals(first.value, different.value)
    }

    @Test
    fun unreadableSourceProducesFingerprintFailure() = runTest {
        val generator = ImageSourceFingerprintGenerator(
            ImageSourceStreamOpener { null },
            Dispatchers.Unconfined,
        )

        assertEquals(
            ImageSourceFingerprintResult.Failure,
            generator.fingerprint("content://picker/unreadable"),
        )
    }

    @Test
    fun detectorRejectsOtherMatchButAllowsTheSameFingerprintInAnotherTournament() = runTest {
        val detector = ScreenshotDuplicateDetector(
            fingerprintGenerator(
                mapOf(
                    "content://picker/one" to "same".encodeToByteArray(),
                    "content://picker/two" to "same".encodeToByteArray(),
                ),
            ),
            FakeScreenshotMetadataRepository(),
        )

        val firstLink = detector.link("tournament-one", "match-one", "content://picker/one", null)
        val sameMatch = detector.link(
            "tournament-one",
            "match-one",
            "content://picker/two",
            (firstLink as ScreenshotDuplicateLinkResult.Linked).fingerprint,
        )
        val otherMatch = detector.link("tournament-one", "match-two", "content://picker/two", null)
        val otherTournament = detector.link("tournament-two", "match-two", "content://picker/two", null)

        assertEquals(ScreenshotDuplicateLinkResult.SameMatch, sameMatch)
        assertEquals(
            ScreenshotDuplicateLinkResult.LinkedToOtherMatch("match-one"),
            otherMatch,
        )
        assertEquals(true, otherTournament is ScreenshotDuplicateLinkResult.Linked)
    }

    @Test
    fun unlinkReleasesTheFingerprintForAnotherMatch() = runTest {
        val detector = ScreenshotDuplicateDetector(
            fingerprintGenerator(mapOf("content://picker/one" to "same".encodeToByteArray())),
            FakeScreenshotMetadataRepository(),
        )
        val linked = detector.link("tournament-id", "match-one", "content://picker/one", null)
            as ScreenshotDuplicateLinkResult.Linked

        assertEquals(
            ScreenshotDuplicateUnlinkResult.Unlinked,
            detector.unlink("tournament-id", "match-one", linked.fingerprint),
        )
        assertEquals(
            true,
            detector.link("tournament-id", "match-two", "content://picker/one", null)
                is ScreenshotDuplicateLinkResult.Linked,
        )
    }

    @Test
    fun freshDetectorRejectsPersistedDuplicateFromAnotherMatch() = runTest {
        val bytesByUri = mapOf("content://picker/same" to "same".encodeToByteArray())
        val fingerprint = fingerprintGenerator(bytesByUri)
            .fingerprint("content://picker/same") as ImageSourceFingerprintResult.Success
        val detector = ScreenshotDuplicateDetector(
            fingerprintGenerator(bytesByUri),
            FakeScreenshotMetadataRepository(
                listOf(screenshotMetadata("tournament-one", "match-one", fingerprint.value)),
            ),
        )

        assertEquals(
            ScreenshotDuplicateLinkResult.LinkedToOtherMatch("match-one"),
            detector.link("tournament-one", "match-two", "content://picker/same", null),
        )
    }

    @Test
    fun persistedSameMatchFingerprintRemainsSameMatchSafe() = runTest {
        val bytesByUri = mapOf("content://picker/same" to "same".encodeToByteArray())
        val fingerprint = fingerprintGenerator(bytesByUri)
            .fingerprint("content://picker/same") as ImageSourceFingerprintResult.Success
        val detector = ScreenshotDuplicateDetector(
            fingerprintGenerator(bytesByUri),
            FakeScreenshotMetadataRepository(
                listOf(screenshotMetadata("tournament-one", "match-one", fingerprint.value)),
            ),
        )

        assertEquals(
            ScreenshotDuplicateLinkResult.SameMatch,
            detector.link("tournament-one", "match-one", "content://picker/same", null),
        )
    }

    @Test
    fun persistedFingerprintInAnotherTournamentIsAllowed() = runTest {
        val bytesByUri = mapOf("content://picker/same" to "same".encodeToByteArray())
        val fingerprint = fingerprintGenerator(bytesByUri)
            .fingerprint("content://picker/same") as ImageSourceFingerprintResult.Success
        val detector = ScreenshotDuplicateDetector(
            fingerprintGenerator(bytesByUri),
            FakeScreenshotMetadataRepository(
                listOf(screenshotMetadata("tournament-one", "match-one", fingerprint.value)),
            ),
        )

        assertEquals(
            true,
            detector.link("tournament-two", "match-two", "content://picker/same", null)
                is ScreenshotDuplicateLinkResult.Linked,
        )
    }

    @Test
    fun persistedMetadataLookupFailureFailsClosed() = runTest {
        val detector = ScreenshotDuplicateDetector(
            fingerprintGenerator(mapOf("content://picker/same" to "same".encodeToByteArray())),
            FakeScreenshotMetadataRepository(failure = IllegalStateException("Room unavailable")),
        )

        assertEquals(
            ScreenshotDuplicateLinkResult.StateConflict,
            detector.link("tournament-one", "match-two", "content://picker/same", null),
        )
    }

    @Test
    fun unlinkWithoutInMemoryOwnershipAfterRestartIsUnlinked() {
        val detector = ScreenshotDuplicateDetector(
            fingerprintGenerator(emptyMap()),
            FakeScreenshotMetadataRepository(),
        )

        assertEquals(
            ScreenshotDuplicateUnlinkResult.Unlinked,
            detector.unlink("tournament-one", "match-one", "fingerprint"),
        )
    }

    @Test
    fun unlinkProtectsFingerprintOwnedByAnotherMatch() = runTest {
        val detector = ScreenshotDuplicateDetector(
            fingerprintGenerator(mapOf("content://picker/same" to "same".encodeToByteArray())),
            FakeScreenshotMetadataRepository(),
        )
        val linked = detector.link("tournament-one", "match-one", "content://picker/same", null)
            as ScreenshotDuplicateLinkResult.Linked

        assertEquals(
            ScreenshotDuplicateUnlinkResult.StateConflict,
            detector.unlink("tournament-one", "match-two", linked.fingerprint),
        )
    }

    private fun screenshotMetadata(
        tournamentId: String,
        matchId: String,
        sha256: String,
    ) = ScreenshotMetadataEntity(
        matchId = matchId,
        tournamentId = tournamentId,
        ownerUserId = "owner-one",
        localRelativePath = "screenshots/$matchId/original.png",
        fileExtension = "png",
        mimeType = "image/png",
        width = 1080,
        height = 1920,
        byteSize = 4,
        sha256 = sha256,
        storageBucket = null,
        storageObjectPath = null,
        localStatus = "PRESERVED",
        uploadStatus = "PENDING",
        uploadFailureCode = null,
        createdAt = 1L,
        updatedAt = 1L,
        preservedAt = 1L,
        uploadedAt = null,
        revision = 1L,
    )

    private class FakeScreenshotMetadataRepository(
        private val metadata: List<ScreenshotMetadataEntity> = emptyList(),
        private val failure: Throwable? = null,
    ) : ScreenshotMetadataRepository {
        override fun observeByMatchId(matchId: String): Flow<ScreenshotMetadataEntity?> =
            flowOf(metadata.firstOrNull { it.matchId == matchId })

        override suspend fun getByMatchId(matchId: String): ScreenshotMetadataEntity? =
            metadata.firstOrNull { it.matchId == matchId }

        override fun observeByTournamentId(tournamentId: String): Flow<List<ScreenshotMetadataEntity>> =
            if (failure == null) {
                flowOf(metadata.filter { it.tournamentId == tournamentId })
            } else {
                flow { throw failure }
            }

        override suspend fun createOrReplace(metadata: ScreenshotMetadataEntity) = Unit

        override suspend fun updateUploadSuccess(
            matchId: String,
            storageBucket: String,
            storageObjectPath: String,
            uploadedAt: Long,
            updatedAt: Long,
        ) = Unit

        override suspend fun updateUploadFailure(
            matchId: String,
            failureCode: String,
            updatedAt: Long,
        ) = Unit

        override suspend fun markLocalMissing(matchId: String, updatedAt: Long) = Unit

        override suspend fun markCleanupFailure(matchId: String, updatedAt: Long) = Unit

        override suspend fun deleteByMatchId(matchId: String) = Unit

        override suspend fun deleteByTournamentId(tournamentId: String) = Unit
    }

    private fun fingerprintGenerator(
        bytesByUri: Map<String, ByteArray>,
    ) = ImageSourceFingerprintGenerator(
        ImageSourceStreamOpener { uri -> bytesByUri[uri]?.inputStream() },
        Dispatchers.Unconfined,
    )
}
