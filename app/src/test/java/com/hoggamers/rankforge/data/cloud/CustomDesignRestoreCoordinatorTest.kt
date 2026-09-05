package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors
import com.hoggamers.rankforge.presentation.screen.ImageSourceMimeTypeReader
import com.hoggamers.rankforge.presentation.screen.ImageSourceStreamOpener
import com.hoggamers.rankforge.presentation.screen.LocalImagePreserver
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDesignRestoreCoordinatorTest {
    private val ownerId = "a1000000-0000-0000-0000-000000000001"
    private val designId = "a2000000-0000-0000-0000-000000000001"

    @Test
    fun restoreVerifiesAndHydratesExactOwnerScopedDesignWithoutSortingColumns() = runTest {
        val events = mutableListOf<String>()
        val root = temporaryRoot()
        val bytes = byteArrayOf(1, 2, 3, 4)
        val payload = payload(bytes)
        val result = coordinator(
            root = root,
            events = events,
            payload = payload,
            bytes = bytes,
        ).restore(designId)

        val design = (result as CustomDesignRestoreResult.Success).design
        assertEquals(listOf("auth", "read", "auth", "download", "auth", "auth"), events)
        val imageUri = URI(design.localImageReference)
        assertEquals("file", imageUri.scheme)
        val restoredFile = File(imageUri)
        assertEquals(
            File(root, "custom-designs/users/$ownerId/$designId/original.png"),
            restoredFile,
        )
        assertArrayEquals(bytes, restoredFile.readBytes())
        assertEquals(payload.sourceWidth, design.sourceWidth)
        assertEquals(payload.sourceHeight, design.sourceHeight)
        assertEquals(" TEAM NAME ", design.labels.teamName)
        assertEquals(900f, design.geometry.columnX[CustomDesignAnchorField.TEAM_NAME])
        assertEquals(100f, design.geometry.columnX[CustomDesignAnchorField.WIN])
        assertEquals((1..12).associateWith { it * 100f }, design.geometry.rowY)
        assertEquals(
            mapOf(
                CustomDesignAnchorField.TEAM_NAME to "#112233",
                CustomDesignAnchorField.WIN to "#223344",
                CustomDesignAnchorField.TOTAL_KILLS to "#334455",
                CustomDesignAnchorField.POSITION_POINTS to "#445566",
                CustomDesignAnchorField.TOTAL_POINTS to "#556677",
            ),
            design.textColors.asMap(),
        )
    }

    @Test
    fun hashMismatchDoesNotWriteLocally() = runTest {
        val root = temporaryRoot()
        val result = coordinator(
            root = root,
            payload = payload(byteArrayOf(1, 2, 3, 4)),
            bytes = byteArrayOf(1, 2, 3, 5),
        ).restore(designId)

        assertEquals(
            CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.INTEGRITY_MISMATCH),
            result,
        )
        assertFalse(File(root, "custom-designs").exists())
    }

    @Test
    fun sizeMismatchDoesNotWriteLocally() = runTest {
        val root = temporaryRoot()
        val result = coordinator(
            root = root,
            payload = payload(byteArrayOf(1, 2, 3, 4)).copy(imageByteSize = 5),
            bytes = byteArrayOf(1, 2, 3, 4),
        ).restore(designId)

        assertEquals(
            CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.INTEGRITY_MISMATCH),
            result,
        )
        assertFalse(File(root, "custom-designs").exists())
    }

    @Test
    fun ownerSwitchAfterReadPreventsDownload() = runTest {
        var authCalls = 0
        var downloads = 0
        val result = coordinator(
            currentUserId = {
                authCalls += 1
                if (authCalls == 1) ownerId else "b1000000-0000-0000-0000-000000000001"
            },
            download = { downloads += 1; byteArrayOf(1) },
        ).restore(designId)

        assertEquals(CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.AUTHORIZATION), result)
        assertEquals(0, downloads)
    }

    @Test
    fun ownerSwitchAfterDownloadPreventsLocalWrite() = runTest {
        var authCalls = 0
        val root = temporaryRoot()
        val result = coordinator(
            root = root,
            currentUserId = {
                authCalls += 1
                if (authCalls <= 2) ownerId else "b1000000-0000-0000-0000-000000000001"
            },
        ).restore(designId)

        assertEquals(CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.AUTHORIZATION), result)
        assertFalse(File(root, "custom-designs").exists())
    }

    @Test
    fun ownerSwitchAfterLocalWriteDeletesRestoredFile() = runTest {
        var authCalls = 0
        val root = temporaryRoot()
        val result = coordinator(
            root = root,
            currentUserId = {
                authCalls += 1
                if (authCalls <= 3) ownerId else "b1000000-0000-0000-0000-000000000001"
            },
        ).restore(designId)

        assertEquals(CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.AUTHORIZATION), result)
        assertFalse(File(root, "custom-designs/users/$ownerId/$designId/original.png").exists())
    }

    @Test
    fun cancellationAfterLocalWriteCleansFileAndPropagates() = runTest {
        var authCalls = 0
        val root = temporaryRoot()
        val cancellation = CancellationException("cancelled")
        try {
            coordinator(
                root = root,
                currentUserId = {
                    authCalls += 1
                    if (authCalls == 4) throw cancellation
                    ownerId
                },
            ).restore(designId)
            throw AssertionError("Expected cancellation")
        } catch (thrown: CancellationException) {
            assertTrue(thrown === cancellation)
        }
        assertFalse(File(root, "custom-designs/users/$ownerId/$designId/original.png").exists())
    }

    @Test
    fun invalidPayloadIsRejectedBeforeDownload() = runTest {
        var downloads = 0
        val invalid = payload(byteArrayOf(1, 2, 3, 4)).copy(imageSha256 = "A".repeat(64))
        val result = coordinator(payload = invalid, download = { downloads += 1; byteArrayOf(1) })
            .restore(designId)

        assertEquals(CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.VALIDATION), result)
        assertEquals(0, downloads)
    }

    @Test
    fun validatorRejectsExtraLabelsAndRowsButAcceptsCrossedColumns() {
        val valid = payload(byteArrayOf(1, 2, 3, 4))
        assertTrue(CustomDesignTemplateValidator.validate(valid, designId, ownerId) != null)
        assertTrue(
            CustomDesignTemplateValidator.validate(
                valid.copy(labelsJson = valid.labelsJson.toMutableMap().let { map ->
                    buildJsonObject {
                        map.forEach { (key, value) -> put(key, value) }
                        put("extra", "no")
                    }
                }),
                designId,
                ownerId,
            ) == null,
        )
        assertTrue(
            CustomDesignTemplateValidator.validate(
                valid.copy(rowsJson = buildJsonObject {
                    (1..12).forEach { rank -> put(rank.toString(), if (rank == 2) 100 else rank * 100) }
                }),
                designId,
                ownerId,
            ) == null,
        )
    }

    @Test
    fun legacyNullColorsUseBlackAndInvalidOrIncompleteColorsAreRejected() {
        val valid = payload(byteArrayOf(1, 2, 3, 4))
        assertEquals(
            CustomDesignColumnTextColors.allBlack(),
            CustomDesignTemplateValidator.validate(
                valid.copy(textColorsJson = null),
                designId,
                ownerId,
            )?.textColors,
        )
        assertTrue(
            CustomDesignTemplateValidator.validate(
                valid.copy(textColorsJson = buildJsonObject {
                    put("TEAM_NAME", "#11223")
                    put("WIN", "#223344")
                    put("TOTAL_KILLS", "#334455")
                    put("POSITION_POINTS", "#445566")
                    put("TOTAL_POINTS", "#556677")
                }),
                designId,
                ownerId,
            ) == null,
        )
        assertTrue(
            CustomDesignTemplateValidator.validate(
                valid.copy(textColorsJson = buildJsonObject {
                    put("TEAM_NAME", "#112233")
                    put("WIN", "#223344")
                    put("TOTAL_KILLS", "#334455")
                    put("POSITION_POINTS", "#445566")
                    put("TOTAL_POINTS", "#556677")
                    put("EXTRA", "#000000")
                }),
                designId,
                ownerId,
            ) == null,
        )
        assertTrue(
            CustomDesignTemplateValidator.validate(
                valid.copy(textColorsJson = buildJsonObject {
                    put("TEAM_NAME", "#112233")
                    put("WIN", "#223344")
                }),
                designId,
                ownerId,
            ) == null,
        )
    }

    private fun coordinator(
        root: File = temporaryRoot(),
        events: MutableList<String> = mutableListOf(),
        currentUserId: suspend () -> String? = {
            events += "auth"
            ownerId
        },
        payload: CustomDesignTemplateCloudPayload = payload(byteArrayOf(1, 2, 3, 4)),
        bytes: ByteArray = byteArrayOf(1, 2, 3, 4),
        download: suspend () -> ByteArray = {
            events += "download"
            bytes
        },
    ) = CustomDesignRestoreCoordinator(
        currentUserId = currentUserId,
        cloudDataSource = object : CustomDesignTemplateCloudDataSource {
            override suspend fun insert(
                payload: CustomDesignTemplateCloudPayload,
                expectedOwnerUserId: String,
            ): CustomDesignTemplateCloudInsertResult =
                error("restore test must not insert")

            override suspend fun readById(
                customDesignId: String,
                expectedOwnerUserId: String,
            ): CustomDesignTemplateCloudReadResult {
                events += "read"
                return CustomDesignTemplateCloudReadResult.Success(payload)
            }

            override suspend fun deleteById(
                customDesignId: String,
                expectedOwnerUserId: String,
            ): CustomDesignTemplateCloudDeleteResult =
                error("restore test must not delete")
        },
        storageDownloader = object : AuthenticatedScreenshotStorageDownloader {
            override suspend fun download(bucket: String, objectPath: String): ByteArray = download()

            override suspend fun download(
                expectedOwnerUserId: String,
                bucket: String,
                objectPath: String,
            ): ByteArray = download()
        },
        localImagePreserver = LocalImagePreserver(
            appPrivateRoot = root,
            sourceStreamOpener = ImageSourceStreamOpener { null },
            mimeTypeReader = ImageSourceMimeTypeReader { null },
        ),
    )

    private fun payload(bytes: ByteArray) = CustomDesignTemplateCloudPayload(
        id = designId,
        userId = ownerId,
        imagePath = "users/$ownerId/custom-designs/$designId/original.png",
        imageSha256 = bytes.sha256(),
        imageByteSize = bytes.size.toLong(),
        imageExtension = "png",
        imageMimeType = "image/png",
        sourceWidth = 1080,
        sourceHeight = 1350,
        labelsJson = buildJsonObject {
            put("teamName", " TEAM NAME ")
            put("win", "WIN")
            put("totalKills", "ELIM.")
            put("positionPoints", "POS.")
            put("totalPoints", "TOTAL")
        },
        columnsJson = buildJsonObject {
            put("TEAM_NAME", 900)
            put("WIN", 100)
            put("TOTAL_KILLS", 700)
            put("POSITION_POINTS", 300)
            put("TOTAL_POINTS", 500)
        },
        rowsJson = buildJsonObject {
            (1..12).forEach { put(it.toString(), it * 100) }
        },
        textColorsJson = buildJsonObject {
            put("TEAM_NAME", "#112233")
            put("WIN", "#223344")
            put("TOTAL_KILLS", "#334455")
            put("POSITION_POINTS", "#445566")
            put("TOTAL_POINTS", "#556677")
        },
    )

    private fun temporaryRoot(): File = File.createTempFile("custom-design-restore-", "").apply {
        delete()
        mkdirs()
        deleteOnExit()
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
