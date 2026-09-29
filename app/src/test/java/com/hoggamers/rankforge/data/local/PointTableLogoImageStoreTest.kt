package com.hoggamers.rankforge.data.local

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PointTableLogoImageStoreTest {
    @Test
    fun preservesPngJpegAndWebpWithSafeRelativePaths() = runTest {
        listOf(
            "image/png" to "png",
            "image/jpeg" to "jpg",
            "image/webp" to "webp",
        ).forEach { (mimeType, extension) ->
            val bytes = byteArrayOf(1, 2, 3, extension.length.toByte())
            val store = store(bytes, mimeType)

            val result = store.preserve("tournament /one", "content://picked/$extension")

            val preserved = result as PointTableLogoImageStoreResult.Preserved
            assertEquals(
                "point-table-details/746f75726e616d656e74202f6f6e65/logo/original.$extension",
                preserved.localRelativePath,
            )
            assertArrayEquals(bytes, File(java.net.URI(preserved.displayUri)).readBytes())
            assertFalse(preserved.localRelativePath.contains("content:"))
        }
    }

    @Test
    fun rejectsUnsupportedAndUnreadableSources() = runTest {
        assertEquals(
            PointTableLogoImageStoreResult.Failed,
            store(byteArrayOf(1), "image/gif").preserve("tournament", "uri"),
        )
        assertEquals(
            PointTableLogoImageStoreResult.Failed,
            store(byteArrayOf(1), "image/png", source = { null }).preserve("tournament", "uri"),
        )
    }

    @Test
    fun replacementRemovesStaleExtensionAndDisplayRejectsUnsafePaths() = runTest {
        var bytes = byteArrayOf(1, 2)
        var mimeType = "image/png"
        val store = store(
            source = { bytes.inputStream() },
            mimeTypeReader = { mimeType },
        )

        val first = store.preserve("tournament", "first") as PointTableLogoImageStoreResult.Preserved
        bytes = byteArrayOf(9, 8, 7)
        mimeType = "image/jpeg"
        val second = store.preserve("tournament", "second") as PointTableLogoImageStoreResult.Preserved

        assertTrue(File(java.net.URI(first.displayUri)).exists().not())
        assertArrayEquals(bytes, File(java.net.URI(second.displayUri)).readBytes())
        assertNull(store.displayUriOrNull("../outside.png"))
        assertNull(store.displayUriOrNull("point-table-details/../logo/original.jpg"))
        assertNotNull(store.displayUriOrNull(second.localRelativePath))
    }

    @Test
    fun cleanupRemovesLogoFilesAndIsSafeToRetry() = runTest {
        val root = Files.createTempDirectory("rank-forge-logo-cleanup").toFile()
        val store = store(root = root)
        val preserved = store.preserve("tournament", "uri") as PointTableLogoImageStoreResult.Preserved

        assertTrue(store.cleanup("tournament"))
        assertFalse(File(java.net.URI(preserved.displayUri)).exists())
        assertTrue(store.cleanup("tournament"))
        assertNull(store.displayUriOrNull(preserved.localRelativePath))
    }

    @Test
    fun cancellationDeletesTemporaryFileAndPropagates() = runTest {
        val root = Files.createTempDirectory("rank-forge-logo-cancellation").toFile()
        val store = store(
            root = root,
            source = {
                object : InputStream() {
                    override fun read(): Int = throw CancellationException("cancelled")
                }
            },
        )

        try {
            store.preserve("tournament", "uri")
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            // Cancellation must escape so the caller can retry.
        }

        val logoDirectory = File(root, "point-table-details/7475726e616d656e74/logo")
        assertTrue(logoDirectory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        assertTrue(logoDirectory.listFiles().orEmpty().none { it.name.startsWith("original.") })
    }

    @Test
    fun candidateCancellationLeavesExistingLogoUntouched() = runTest {
        val store = store()
        val existing = store.preserve("tournament", "existing")
            as PointTableLogoImageStoreResult.Preserved

        val candidate = store.prepareCandidate("tournament", "replacement")
            as PointTableLogoImageStoreResult.Preserved

        assertTrue(File(java.net.URI(existing.displayUri)).exists())
        assertTrue(File(java.net.URI(candidate.displayUri)).exists())

        store.discardCandidate(
            PointTableLogoCandidate(
                tournamentId = "tournament",
                localRelativePath = candidate.localRelativePath,
                displayUri = candidate.displayUri,
            ),
        )

        assertTrue(File(java.net.URI(existing.displayUri)).exists())
        assertFalse(File(java.net.URI(candidate.displayUri)).exists())
    }

    @Test
    fun candidateCommitKeepsPreviousLogoUntilCallerDeletesIt() = runTest {
        val store = store()
        val existing = store.preserve("tournament", "existing")
            as PointTableLogoImageStoreResult.Preserved
        val prepared = store.prepareCandidate("tournament", "replacement")
            as PointTableLogoImageStoreResult.Preserved

        val committed = store.commitCandidate(
            PointTableLogoCandidate(
                tournamentId = "tournament",
                localRelativePath = prepared.localRelativePath,
                displayUri = prepared.displayUri,
            ),
        ) as PointTableLogoImageStoreResult.Preserved

        assertTrue(File(java.net.URI(existing.displayUri)).exists())
        assertTrue(File(java.net.URI(committed.displayUri)).exists())
        assertFalse(File(java.net.URI(prepared.displayUri)).exists())

        assertTrue(store.deleteLogo(existing.localRelativePath))
        assertFalse(File(java.net.URI(existing.displayUri)).exists())
        assertTrue(File(java.net.URI(committed.displayUri)).exists())
    }

    @Test
    fun failedCandidatePreparationLeavesExistingLogoUntouched() = runTest {
        var source: suspend () -> InputStream? = { byteArrayOf(1, 2, 3).inputStream() }
        val store = store(source = { source() })
        val existing = store.preserve("tournament", "existing")
            as PointTableLogoImageStoreResult.Preserved
        source = { null }

        assertEquals(
            PointTableLogoImageStoreResult.Failed,
            store.prepareCandidate("tournament", "replacement"),
        )
        assertTrue(File(java.net.URI(existing.displayUri)).exists())
    }

    @Test
    fun undecodableCandidateIsRejectedBeforeEditorCanOpen() = runTest {
        val store = store(decoder = { file -> !file.name.startsWith("candidate-") })

        assertEquals(
            PointTableLogoImageStoreResult.Failed,
            store.prepareCandidate("tournament", "replacement"),
        )
    }

    private fun store(
        bytes: ByteArray = byteArrayOf(4, 5, 6),
        mimeType: String = "image/png",
        root: File = Files.createTempDirectory("rank-forge-logo").toFile(),
        source: suspend () -> InputStream? = { bytes.inputStream() },
        mimeTypeReader: () -> String? = { mimeType },
        decoder: (File) -> Boolean = { true },
    ): AndroidPointTableLogoImageStore = AndroidPointTableLogoImageStore(
        appPrivateRoot = root,
        sourceStreamOpener = PointTableLogoSourceStreamOpener { source() },
        mimeTypeReader = PointTableLogoMimeTypeReader { mimeTypeReader() },
        ioDispatcher = Dispatchers.Unconfined,
        logoFileDecoder = PointTableLogoFileDecoder(decoder),
    )
}
