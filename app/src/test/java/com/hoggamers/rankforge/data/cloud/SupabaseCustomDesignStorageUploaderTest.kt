package com.hoggamers.rankforge.data.cloud

import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseCustomDesignStorageUploaderTest {
    private val ownerId = "a1000000-0000-0000-0000-000000000001"
    private val designId = "a2000000-0000-0000-0000-000000000001"

    @Test
    fun uploadUsesExactBucketPathMimeAndFalseUpsert() = runTest {
        var call: List<Any>? = null
        val uploader = uploader(
            upload = { bucket, path, file, mime, upsert ->
                call = listOf(bucket, path, file, mime, upsert)
            },
        )
        val file = temporaryFile()

        val result = uploader.upload(ownerId, designId, file, "png", "image/png")

        assertEquals(
            CustomDesignStorageUploadResult.Uploaded(
                "users/$ownerId/custom-designs/$designId/original.png",
            ),
            result,
        )
        assertEquals(CUSTOM_DESIGNS_BUCKET, call?.get(0))
        assertEquals("image/png", call?.get(3))
        assertEquals(false, call?.get(4))
    }

    @Test
    fun uploadRejectsMissingSessionOwnerMismatchAndUnsupportedFormat() = runTest {
        val file = temporaryFile()
        assertEquals(
            CustomDesignStorageFailure.MISSING_AUTH_SESSION,
            (uploader(userId = null).upload(ownerId, designId, file, "png", "image/png") as CustomDesignStorageUploadResult.Failed).failure,
        )
        assertEquals(
            CustomDesignStorageFailure.AUTHORIZATION,
            (uploader().upload("other", designId, file, "png", "image/png") as CustomDesignStorageUploadResult.Failed).failure,
        )
        assertEquals(
            CustomDesignStorageFailure.UNSUPPORTED_FORMAT,
            (uploader().upload(ownerId, designId, file, "jpeg", "image/jpeg") as CustomDesignStorageUploadResult.Failed).failure,
        )
    }

    @Test
    fun deleteDerivesTheSameOwnerScopedPath() = runTest {
        var deleted: Pair<String, List<String>>? = null
        val uploader = uploader(delete = { bucket, paths -> deleted = bucket to paths })

        val result = uploader.delete(ownerId, designId, "webp")

        assertEquals(CustomDesignStorageDeleteResult.Deleted, result)
        assertEquals(
            CUSTOM_DESIGNS_BUCKET to listOf("users/$ownerId/custom-designs/$designId/original.webp"),
            deleted,
        )
    }

    @Test
    fun deleteTreatsMissingObjectsAsAlreadyDeleted() = runTest {
        listOf("404", "object not found", "object does not exist").forEach { message ->
            assertEquals(
                CustomDesignStorageDeleteResult.Deleted,
                uploader(delete = { _, _ -> error(message) }).delete(ownerId, designId, "png"),
            )
        }
    }

    @Test
    fun deletePreservesAuthorizationAndNetworkFailures() = runTest {
        assertEquals(
            CustomDesignStorageDeleteResult.Failed(CustomDesignStorageFailure.AUTHORIZATION),
            uploader(delete = { _, _ -> error("403 forbidden") }).delete(ownerId, designId, "png"),
        )
        assertEquals(
            CustomDesignStorageDeleteResult.Failed(CustomDesignStorageFailure.NETWORK),
            uploader(delete = { _, _ -> throw IOException("network timeout") }).delete(ownerId, designId, "png"),
        )
    }

    private fun uploader(
        userId: String? = ownerId,
        upload: suspend (String, String, File, String, Boolean) -> Unit = { _, _, _, _, _ -> },
        delete: suspend (String, List<String>) -> Unit = { _, _ -> },
    ) = SupabaseCustomDesignStorageUploader(
        isConfigured = { true },
        currentUserId = { userId },
        uploadFile = upload,
        deleteFile = delete,
    )

    private fun temporaryFile(): File = File.createTempFile("custom-design-upload-", ".tmp").apply {
        writeText("image")
        deleteOnExit()
        assertTrue(isFile)
    }
}
