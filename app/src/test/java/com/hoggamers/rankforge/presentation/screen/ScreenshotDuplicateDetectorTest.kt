package com.hoggamers.rankforge.presentation.screen

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
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

    private fun fingerprintGenerator(
        bytesByUri: Map<String, ByteArray>,
    ) = ImageSourceFingerprintGenerator(
        ImageSourceStreamOpener { uri -> bytesByUri[uri]?.inputStream() },
        Dispatchers.Unconfined,
    )
}
