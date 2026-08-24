package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalImagePreserverTest {
    @Test
    fun preservesBytesInTournamentAndMatchScopedMimeDerivedFile() = runTest {
        val bytes = byteArrayOf(0, 1, 2, 3, 255.toByte())
        val preserver = preserver(bytes, "image/jpeg")

        val result = preserver.preserve("tournament one", "match/one", "content://picked/image")

        val file = (result as LocalImagePreservationResult.Preserved).file
        assertTrue(file.path.contains("screenshots"))
        assertTrue(file.path.contains("746f75726e616d656e74206f6e65"))
        assertTrue(file.path.contains("6d617463682f6f6e65"))
        assertEquals("original.jpg", file.name)
        assertArrayEquals(bytes, file.readBytes())
        assertFalse(file.path.contains("content:"))
    }

    @Test
    fun webpUsesWebpExtension() = runTest {
        val file = (preserver(byteArrayOf(7), "image/webp").preserve(
            "tournament",
            "match",
            "uri",
        ) as LocalImagePreservationResult.Preserved).file

        assertEquals("original.webp", file.name)
    }

    @Test
    fun replacementReplacesPreviousFileAndRemovesOldExtension() = runTest {
        var bytes = byteArrayOf(1, 2)
        var mime = "image/png"
        val preserver = LocalImagePreserver(
            appPrivateRoot = Files.createTempDirectory("rank-forge-replacement").toFile(),
            sourceStreamOpener = ImageSourceStreamOpener { bytes.inputStream() },
            mimeTypeReader = ImageSourceMimeTypeReader { mime },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val first = preserver.preserve("tournament", "match", "first")
        bytes = byteArrayOf(9, 8, 7)
        mime = "image/jpeg"
        val second = preserver.preserve("tournament", "match", "second")

        assertEquals("original.png", (first as LocalImagePreservationResult.Preserved).file.name)
        val secondFile = (second as LocalImagePreservationResult.Preserved).file
        assertEquals("original.jpg", secondFile.name)
        assertArrayEquals(bytes, secondFile.readBytes())
        assertFalse(File(secondFile.parentFile, "original.png").exists())
    }

    @Test
    fun atomicMoveFailureLeavesNoFinalFile() = runTest {
        val operations = TestFileOperations(failAtomicMove = true)
        val preserver = preserver(byteArrayOf(1, 2), "image/png", operations)

        val result = preserver.preserve("tournament", "match", "uri")

        assertEquals(
            LocalImagePreservationFailure.ATOMIC_MOVE_FAILED,
            (result as LocalImagePreservationResult.Failed).error,
        )
        assertTrue(operations.createdFiles.none { it.name.startsWith("original.") })
        assertTrue(operations.createdFiles.none { it.name.endsWith(".tmp") && it.exists() })
    }

    @Test
    fun sourceReadFailureIsControlled() = runTest {
        val preserver = LocalImagePreserver(
            appPrivateRoot = Files.createTempDirectory("rank-forge-source-failure").toFile(),
            sourceStreamOpener = ImageSourceStreamOpener { null },
            mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = preserver.preserve("tournament", "match", "uri")

        assertEquals(
            LocalImagePreservationFailure.SOURCE_READ_FAILED,
            (result as LocalImagePreservationResult.Failed).error,
        )
    }

    @Test
    fun cleanupFailureIsReportedAfterPreservation() = runTest {
        val operations = TestFileOperations(failDelete = true)
        val preserver = preserver(byteArrayOf(1), "image/png", operations)
        val preserved = preserver.preserve("tournament", "match", "uri")

        assertTrue(preserved is LocalImagePreservationResult.Preserved)
        assertEquals(
            LocalImageCleanupResult.Failed,
            preserver.cleanup("tournament", "match"),
        )
    }

    @Test
    fun cleanupMissingDirectoryIsIdempotentlyCleaned() = runTest {
        assertEquals(
            LocalImageCleanupResult.Cleaned,
            preserver(byteArrayOf(1), "image/png").cleanup("missing-tournament", "missing-match"),
        )
    }

    @Test
    fun partialCleanupFailureLeavesRemainingFileForRetry() = runTest {
        val operations = TestFileOperations().apply { failDeleteAfter = 1 }
        val preserver = preserver(byteArrayOf(1), "image/png", operations)
        val first = preserver.preservedFile("tournament", "match", "png")
        val second = preserver.preservedFile("tournament", "match", "jpg")
        writeFile(first)
        writeFile(second)

        assertEquals(LocalImageCleanupResult.Failed, preserver.cleanup("tournament", "match"))
        assertTrue(first.exists() xor second.exists())

        operations.failDeleteAfter = null
        operations.deleteCount = 0
        assertEquals(LocalImageCleanupResult.Cleaned, preserver.cleanup("tournament", "match"))
        assertFalse(first.exists())
        assertFalse(second.exists())
    }

    @Test
    fun cleanupCancellationPropagatesAndRetryCanFinish() = runTest {
        val operations = TestFileOperations().apply { cancellationAfterDelete = 1 }
        val preserver = preserver(byteArrayOf(1), "image/png", operations)
        val first = preserver.preservedFile("tournament", "match", "png")
        val second = preserver.preservedFile("tournament", "match", "jpg")
        writeFile(first)
        writeFile(second)

        try {
            preserver.cleanup("tournament", "match")
            throw AssertionError("Expected cleanup cancellation")
        } catch (_: CancellationException) {
            // Cancellation must escape the cleanup boundary so the caller can retry.
        }
        assertTrue(first.exists() xor second.exists())

        operations.cancellationAfterDelete = null
        operations.deleteCount = 0
        assertEquals(LocalImageCleanupResult.Cleaned, preserver.cleanup("tournament", "match"))
        assertFalse(first.exists())
        assertFalse(second.exists())
    }

    @Test
    fun templateCleanupCancellationPropagatesAndRetryCanFinish() = runTest {
        val operations = TestFileOperations().apply { cancellationAfterDelete = 0 }
        val preserver = preserver(byteArrayOf(1), "image/png", operations)
        val file = preserver.lobbyTemplatePreservedFile("tournament", "generation", 1, "png")
        writeFile(file)

        try {
            preserver.cleanupLobbyTemplateGeneration("tournament", "generation")
            throw AssertionError("Expected template cleanup cancellation")
        } catch (_: CancellationException) {
            // Cancellation must escape template cleanup as well.
        }
        assertTrue(file.exists())

        operations.cancellationAfterDelete = null
        operations.deleteCount = 0
        assertEquals(
            LocalImageCleanupResult.Cleaned,
            preserver.cleanupLobbyTemplateGeneration("tournament", "generation"),
        )
        assertFalse(file.exists())
    }

    @Test
    fun matchResultPreservationUsesRoleScopedDeterministicPaths() = runTest {
        val bytes = byteArrayOf(4, 5, 6)
        val preserver = preserver(bytes, "image/webp")

        val result = preserver.preserveMatchResultScreenshot(
            tournamentId = "tournament one",
            matchId = "match/one",
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            selectedUri = "content://picked/upper",
        )

        val file = (result as LocalImagePreservationResult.Preserved).file
        assertEquals("original.webp", file.name)
        assertTrue(file.path.contains("${File.separator}result${File.separator}upper${File.separator}"))
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(
            "screenshots/746f75726e616d656e74206f6e65/6d617463682f6f6e65/result/upper/original.webp",
            preserver.matchResultRelativePath(
                tournamentId = "tournament one",
                matchId = "match/one",
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                extension = "webp",
            ),
        )
    }

    @Test
    fun matchResultRoleCleanupDoesNotDeleteTheOtherRole() = runTest {
        var bytes = byteArrayOf(1)
        var mime = "image/png"
        val preserver = LocalImagePreserver(
            appPrivateRoot = Files.createTempDirectory("rank-forge-role-cleanup").toFile(),
            sourceStreamOpener = ImageSourceStreamOpener { bytes.inputStream() },
            mimeTypeReader = ImageSourceMimeTypeReader { mime },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val upper = preserver.preserveMatchResultScreenshot(
            "tournament",
            "match",
            MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            "upper",
        ) as LocalImagePreservationResult.Preserved
        bytes = byteArrayOf(2)
        mime = "image/jpeg"
        val lower = preserver.preserveMatchResultScreenshot(
            "tournament",
            "match",
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            "lower",
        ) as LocalImagePreservationResult.Preserved

        assertEquals(
            LocalImageCleanupResult.Cleaned,
            preserver.cleanupMatchResultScreenshot(
                "tournament",
                "match",
                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            ),
        )

        assertFalse(upper.file.exists())
        assertTrue(lower.file.exists())
        assertArrayEquals(byteArrayOf(2), lower.file.readBytes())
    }

    @Test
    fun lobbyScreenshotPathIsDeterministicAndEncoded() = runTest {
        val preserver = preserver(byteArrayOf(8), "image/png")

        val result = preserver.preserveLobbyScreenshot(
            tournamentId = "tournament one",
            matchId = "match/one",
            lobbyScreenshotIndex = 2,
            selectedUri = "content://picked/lobby",
        )

        val file = (result as LocalImagePreservationResult.Preserved).file
        assertEquals("original.png", file.name)
        assertEquals(
            "screenshots/746f75726e616d656e74206f6e65/6d617463682f6f6e65/lobby/2/original.png",
            preserver.lobbyRelativePath("tournament one", "match/one", 2, "png"),
        )
        assertEquals(
            preserver.lobbyPreservedFile("tournament one", "match/one", 2, "png").canonicalFile,
            file.canonicalFile,
        )
    }

    @Test
    fun lobbyIndexesAreIsolatedForReplacementAndCleanup() = runTest {
        var bytes = byteArrayOf(1)
        var mime = "image/png"
        val preserver = LocalImagePreserver(
            appPrivateRoot = Files.createTempDirectory("rank-forge-lobby-isolation").toFile(),
            sourceStreamOpener = ImageSourceStreamOpener { bytes.inputStream() },
            mimeTypeReader = ImageSourceMimeTypeReader { mime },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val lobbyOne = preserver.preserveLobbyScreenshot("tournament", "match", 1, "one")
            as LocalImagePreservationResult.Preserved
        bytes = byteArrayOf(2)
        mime = "image/jpeg"
        val lobbyTwo = preserver.preserveLobbyScreenshot("tournament", "match", 2, "two")
            as LocalImagePreservationResult.Preserved
        bytes = byteArrayOf(4)
        val lobbyThree = preserver.preserveLobbyScreenshot("tournament", "match", 3, "three")
            as LocalImagePreservationResult.Preserved
        bytes = byteArrayOf(3)
        val replacedLobbyOne = preserver.preserveLobbyScreenshot("tournament", "match", 1, "replacement")
            as LocalImagePreservationResult.Preserved

        assertArrayEquals(byteArrayOf(3), replacedLobbyOne.file.readBytes())
        assertTrue(lobbyTwo.file.exists())
        assertArrayEquals(byteArrayOf(2), lobbyTwo.file.readBytes())
        assertTrue(lobbyThree.file.exists())
        assertArrayEquals(byteArrayOf(4), lobbyThree.file.readBytes())
        assertFalse(lobbyOne.file.exists())

        assertEquals(
            LocalImageCleanupResult.Cleaned,
            preserver.cleanupLobbyScreenshot("tournament", "match", 1),
        )
        assertFalse(replacedLobbyOne.file.exists())
        assertTrue(lobbyTwo.file.exists())
        assertTrue(lobbyThree.file.exists())
    }

    @Test
    fun invalidLobbyScreenshotIndexIsRejected() = runTest {
        val preserver = preserver(byteArrayOf(1), "image/png")

        assertEquals(
            LocalImagePreservationFailure.COPY_FAILED,
            (preserver.preserveLobbyScreenshot("tournament", "match", 4, "uri")
                as LocalImagePreservationResult.Failed).error,
        )
        assertEquals(
            LocalImageCleanupResult.Failed,
            preserver.cleanupLobbyScreenshot("tournament", "match", 0),
        )
    }

    private fun preserver(
        bytes: ByteArray,
        mimeType: String,
        operations: TestFileOperations = TestFileOperations(),
    ) = LocalImagePreserver(
        appPrivateRoot = Files.createTempDirectory("rank-forge-preserver").toFile(),
        sourceStreamOpener = ImageSourceStreamOpener { bytes.inputStream() },
        mimeTypeReader = ImageSourceMimeTypeReader { mimeType },
        fileOperations = operations,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private class TestFileOperations(
        private val failAtomicMove: Boolean = false,
        private val failDelete: Boolean = false,
    ) : LocalImageFileOperations {
        val createdFiles = mutableListOf<File>()
        var failDeleteAfter: Int? = null
        var cancellationAfterDelete: Int? = null
        var deleteCount: Int = 0

        override fun ensureDirectory(directory: File): Boolean =
            directory.isDirectory || (directory.mkdirs() && directory.isDirectory)

        override fun createTempFile(directory: File): File =
            File.createTempFile("original-", ".tmp", directory).also(createdFiles::add)

        override fun openOutput(file: File): OutputStream = FileOutputStream(file)

        override fun atomicMove(source: File, target: File): Boolean {
            if (failAtomicMove) return false
            if (target.exists()) target.delete()
            return source.renameTo(target).also { if (it) createdFiles.add(target) }
        }

        override fun listFiles(directory: File): List<File>? =
            if (!directory.exists()) emptyList() else directory.listFiles()?.toList()

        override fun delete(file: File): Boolean {
            if (cancellationAfterDelete?.let { deleteCount >= it } == true) {
                throw CancellationException("cleanup cancelled")
            }
            if (failDeleteAfter?.let { deleteCount >= it } == true) {
                deleteCount++
                return false
            }
            deleteCount++
            return if (failDelete) false else !file.exists() || file.delete()
        }
    }

    private fun writeFile(file: File) {
        file.parentFile?.mkdirs()
        file.writeText("test")
    }
}
