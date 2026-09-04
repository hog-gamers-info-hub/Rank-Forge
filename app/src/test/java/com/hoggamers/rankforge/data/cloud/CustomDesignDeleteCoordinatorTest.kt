package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.presentation.screen.ImageSourceStreamOpener
import com.hoggamers.rankforge.presentation.screen.ImageSourceMimeTypeReader
import com.hoggamers.rankforge.presentation.screen.LocalImageCleanupResult
import com.hoggamers.rankforge.presentation.screen.LocalImagePreserver
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDesignDeleteCoordinatorTest {
    private val ownerId = "a1000000-0000-0000-0000-000000000001"
    private val designId = "a2000000-0000-0000-0000-000000000001"

    @Test
    fun deletesVerifiedDatabaseStorageAndLocalAssetsInOrder() = runTest {
        val events = mutableListOf<String>()
        val root = temporaryRoot()
        val local = localPreserver(root)
        val localFile = local.customDesignPreservedFile(ownerId, designId, "png").apply {
            parentFile?.mkdirs()
            writeText("image")
        }
        val coordinator = coordinator(
            local = local,
            read = { _, _ -> events += "read"; CustomDesignTemplateCloudReadResult.Success(payload()) },
            databaseDelete = { _, _ -> events += "database" },
            storageDelete = { _, _, extension -> events += "storage:$extension" },
        )

        assertEquals(CustomDesignDeleteResult.Success, coordinator.delete(designId))
        assertEquals(listOf("read", "database", "storage:png"), events)
        assertFalse(localFile.exists())
    }

    @Test
    fun missingRowRetriesAllStorageFormatsAndLocalCleanup() = runTest {
        val deletedExtensions = mutableListOf<String>()
        val coordinator = coordinator(
            read = { _, _ -> CustomDesignTemplateCloudReadResult.NotFound },
            storageDelete = { _, _, extension -> deletedExtensions += extension },
        )

        assertEquals(CustomDesignDeleteResult.Success, coordinator.delete(designId))
        assertEquals(listOf("png", "jpg", "webp"), deletedExtensions)
    }

    @Test
    fun authSwitchAfterDatabaseDeleteDoesNotDeleteStorage() = runTest {
        var authCalls = 0
        var storageDeletes = 0
        val coordinator = coordinator(
            currentUser = {
                authCalls += 1
                if (authCalls <= 2) ownerId else "b1000000-0000-0000-0000-000000000001"
            },
            storageDelete = { _, _, _ -> storageDeletes += 1 },
        )

        assertEquals(
            CustomDesignDeleteResult.Failed(CustomDesignDeleteFailure.AUTHORIZATION),
            coordinator.delete(designId),
        )
        assertEquals(0, storageDeletes)
    }

    @Test
    fun cancellationAfterDatabaseDeleteAttemptsCompensationAndRethrows() = runTest {
        val cancellation = CancellationException("cancelled")
        var storageDeletes = 0
        val root = temporaryRoot()
        val local = localPreserver(root)
        val localFile = local.customDesignPreservedFile(ownerId, designId, "png").apply {
            parentFile?.mkdirs()
            writeText("image")
        }
        val coordinator = coordinator(
            local = local,
            storageDelete = { _, _, _ ->
                storageDeletes += 1
                if (storageDeletes == 1) throw cancellation
            },
        )

        try {
            coordinator.delete(designId)
            throw AssertionError("Expected cancellation")
        } catch (thrown: CancellationException) {
            assertTrue(thrown === cancellation)
        }
        assertEquals(2, storageDeletes)
        assertFalse(localFile.exists())
    }

    private fun coordinator(
        currentUser: suspend () -> String? = { ownerId },
        local: LocalImagePreserver = localPreserver(temporaryRoot()),
        read: suspend (String, String) -> CustomDesignTemplateCloudReadResult =
            { _, _ -> CustomDesignTemplateCloudReadResult.Success(payload()) },
        databaseDelete: suspend (String, String) -> Unit = { _, _ -> },
        storageDelete: suspend (String, String, String) -> Unit = { _, _, _ -> },
    ) = CustomDesignDeleteCoordinator(
        currentUserId = currentUser,
        cloudDataSource = object : CustomDesignTemplateCloudDataSource {
            override suspend fun insert(
                payload: CustomDesignTemplateCloudPayload,
                expectedOwnerUserId: String,
            ): CustomDesignTemplateCloudInsertResult = error("delete test must not insert")

            override suspend fun readById(
                customDesignId: String,
                expectedOwnerUserId: String,
            ): CustomDesignTemplateCloudReadResult = read(customDesignId, expectedOwnerUserId)

            override suspend fun deleteById(
                customDesignId: String,
                expectedOwnerUserId: String,
            ): CustomDesignTemplateCloudDeleteResult {
                databaseDelete(customDesignId, expectedOwnerUserId)
                return CustomDesignTemplateCloudDeleteResult.Deleted
            }
        },
        storageUploader = object : CustomDesignStorageUploader {
            override suspend fun upload(
                expectedOwnerUserId: String,
                customDesignId: String,
                preparedFile: File,
                extension: String,
                mimeType: String,
            ): CustomDesignStorageUploadResult = error("delete test must not upload")

            override suspend fun delete(
                expectedOwnerUserId: String,
                customDesignId: String,
                extension: String,
            ): CustomDesignStorageDeleteResult {
                storageDelete(expectedOwnerUserId, customDesignId, extension)
                return CustomDesignStorageDeleteResult.Deleted
            }
        },
        localImagePreserver = local,
    )

    private fun localPreserver(root: File) = LocalImagePreserver(
        appPrivateRoot = root,
        sourceStreamOpener = ImageSourceStreamOpener { null },
        mimeTypeReader = ImageSourceMimeTypeReader { null },
    )

    private fun payload() = CustomDesignTemplateCloudPayload(
        id = designId,
        userId = ownerId,
        imagePath = "users/$ownerId/custom-designs/$designId/original.png",
        imageSha256 = "a".repeat(64),
        imageByteSize = 5,
        imageExtension = "png",
        imageMimeType = "image/png",
        sourceWidth = 1080,
        sourceHeight = 1350,
        labelsJson = buildJsonObject {
            put("teamName", "TEAM NAME")
            put("win", "WIN")
            put("totalKills", "ELIM.")
            put("positionPoints", "POS.")
            put("totalPoints", "TOTAL")
        },
        columnsJson = buildJsonObject {
            CustomDesignAnchorField.entries.forEachIndexed { index, field -> put(field.name, index * 100) }
        },
        rowsJson = buildJsonObject {
            (1..12).forEach { put(it.toString(), it * 100) }
        },
    )

    private fun temporaryRoot(): File = File.createTempFile("custom-design-delete-", "").apply {
        delete()
        mkdirs()
        deleteOnExit()
    }
}
