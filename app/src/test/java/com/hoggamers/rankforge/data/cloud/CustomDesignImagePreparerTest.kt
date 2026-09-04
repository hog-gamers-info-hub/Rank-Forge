package com.hoggamers.rankforge.data.cloud

import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDesignImagePreparerTest {
    @Test
    fun supportedMimeTypesMapToStableExtensions() {
        assertEquals(CustomDesignImageFormat("png", "image/png"), CustomDesignImageFormat.fromMimeType("image/png"))
        assertEquals(CustomDesignImageFormat("jpg", "image/jpeg"), CustomDesignImageFormat.fromMimeType("IMAGE/JPEG"))
        assertEquals(CustomDesignImageFormat("webp", "image/webp"), CustomDesignImageFormat.fromMimeType("image/webp"))
        assertEquals(null, CustomDesignImageFormat.fromMimeType("image/gif"))
        assertEquals(null, CustomDesignImageFormat.fromPair("jpeg", "image/jpeg"))
    }

    @Test
    fun prepareCopiesExactBytesAndCalculatesMetadata() = runTest {
        val bytes = byteArrayOf(0, 1, 2, 127, -1, 42)
        val tempFiles = mutableListOf<File>()
        val preparer = preparer("image/jpeg", bytes, tempFiles)

        val prepared = preparer.prepare("content://custom-design")

        assertEquals("jpg", prepared.extension)
        assertEquals("image/jpeg", prepared.mimeType)
        assertEquals(bytes.size.toLong(), prepared.byteSize)
        assertEquals(sha256(bytes), prepared.sha256)
        assertArrayEquals(bytes, prepared.file.readBytes())
        assertTrue(tempFiles.single().isFile)
        prepared.cleanup()
        assertFalse(tempFiles.single().exists())
    }

    @Test
    fun emptyAndUnreadableSourcesAreRejectedAndPartialFileIsCleaned() = runTest {
        val emptyFiles = mutableListOf<File>()
        val empty = preparer("image/png", byteArrayOf(), emptyFiles)
        assertPreparationFailure { empty.prepare("content://empty") }
        assertFalse(emptyFiles.single().exists())

        val unreadableFiles = mutableListOf<File>()
        val unreadable = AndroidCustomDesignImagePreparer(
            readMimeType = { "image/png" },
            openInputStream = { null },
            createTempFile = {
                File.createTempFile("custom-design-test-", ".tmp").also(unreadableFiles::add)
            },
        )
        assertPreparationFailure { unreadable.prepare("content://missing") }
        assertFalse(unreadableFiles.single().exists())
    }

    @Test
    fun unsupportedMimeIsRejectedBeforeCreatingTemporaryFile() = runTest {
        var created = false
        val preparer = AndroidCustomDesignImagePreparer(
            readMimeType = { "image/gif" },
            openInputStream = { ByteArrayInputStream(byteArrayOf(1)) },
            createTempFile = {
                created = true
                File.createTempFile("custom-design-test-", ".tmp")
            },
        )

        assertPreparationFailure { preparer.prepare("content://gif") }
        assertFalse(created)
    }

    private fun preparer(
        mimeType: String?,
        bytes: ByteArray,
        tempFiles: MutableList<File>,
    ) = AndroidCustomDesignImagePreparer(
        readMimeType = { mimeType },
        openInputStream = { ByteArrayInputStream(bytes) },
        createTempFile = {
            File.createTempFile("custom-design-test-", ".tmp").also(tempFiles::add)
        },
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private suspend fun assertPreparationFailure(action: suspend () -> Unit) {
        try {
            action()
            throw AssertionError("Expected image preparation to fail")
        } catch (_: CustomDesignImagePreparationException) {
            // Expected.
        }
    }
}
