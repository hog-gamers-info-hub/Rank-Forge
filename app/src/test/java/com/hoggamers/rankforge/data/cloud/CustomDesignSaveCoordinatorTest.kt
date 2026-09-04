package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrLabels
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDesignSaveCoordinatorTest {
    private val ownerId = "a1000000-0000-0000-0000-000000000001"

    @Test
    fun successCapturesOwnerUploadsThenRechecksBeforeInsert() = runTest {
        val events = mutableListOf<String>()
        val captured = RecordingDependencies(events)
        val coordinator = captured.coordinator()

        val result = coordinator.save(validRequest())

        assertTrue(result is CustomDesignSaveResult.Success)
        assertEquals(listOf("auth", "prepare", "upload", "auth", "insert"), events)
        assertEquals(1, captured.inserted.size)
        assertEquals(ownerId, captured.inserted.single().userId)
        assertEquals(700.0, captured.inserted.single().columnsJson["TEAM_NAME"]?.toString()?.toDouble())
        assertEquals(200.0, captured.inserted.single().columnsJson["WIN"]?.toString()?.toDouble())
    }

    @Test
    fun databaseFailureAttemptsStorageCompensationAndDoesNotSucceed() = runTest {
        val dependencies = RecordingDependencies(mutableListOf(), insertFailure = true)

        val result = dependencies.coordinator().save(validRequest())

        assertEquals(CustomDesignSaveResult.Failed(CustomDesignSaveFailure.DATABASE_INSERT), result)
        assertEquals(1, dependencies.deleted.size)
    }

    @Test
    fun uploadFailurePreventsDatabaseInsert() = runTest {
        val dependencies = RecordingDependencies(mutableListOf(), uploadFailure = true)

        assertEquals(
            CustomDesignSaveResult.Failed(CustomDesignSaveFailure.STORAGE_UPLOAD),
            dependencies.coordinator().save(validRequest()),
        )
        assertTrue(dependencies.inserted.isEmpty())
        assertEquals(0, dependencies.deleted.size)
    }

    @Test
    fun cancellationAfterUploadAttemptsStorageCleanupAndRethrows() = runTest {
        val dependencies = RecordingDependencies(mutableListOf(), cancelOnInsert = true)
        var cancellation: CancellationException? = null

        try {
            dependencies.coordinator().save(validRequest())
        } catch (thrown: CancellationException) {
            cancellation = thrown
        }

        assertTrue(cancellation != null)
        assertEquals(1, dependencies.deleted.size)
        assertTrue(dependencies.inserted.isEmpty())
        assertFalse(checkNotNull(dependencies.preparedFile).exists())
    }

    @Test
    fun authSwitchPreventsDatabaseInsertAndAttemptsOwnerBoundCleanup() = runTest {
        var authCalls = 0
        val dependencies = RecordingDependencies(mutableListOf())
        val coordinator = CustomDesignSaveCoordinator(
            currentUserId = {
                authCalls += 1
                if (authCalls == 1) ownerId else "b1000000-0000-0000-0000-000000000001"
            },
            imagePreparer = dependencies.preparer,
            storageUploader = dependencies.uploader,
            cloudDataSource = dependencies.dataSource,
        )

        val result = coordinator.save(validRequest())

        assertEquals(CustomDesignSaveResult.Failed(CustomDesignSaveFailure.AUTHORIZATION), result)
        assertTrue(dependencies.inserted.isEmpty())
        assertEquals(1, dependencies.deleted.size)
    }

    @Test
    fun invalidGeometryRejectsBeforePreparation() = runTest {
        val dependencies = RecordingDependencies(mutableListOf())
        val request = validRequest().copy(
            effectiveGridGeometry = validGeometry().copy(
                rowY = validGeometry().rowY + (12 to 1100f),
            ),
        )

        assertEquals(
            CustomDesignSaveResult.Failed(CustomDesignSaveFailure.VALIDATION),
            dependencies.coordinator().save(request),
        )
        assertEquals(0, dependencies.prepared)
    }

    private fun validRequest() = CustomDesignSaveRequest(
        imageReference = "content://custom-design",
        draftSourceWidth = 1080,
        draftSourceHeight = 1350,
        currentSourceWidth = 1080,
        currentSourceHeight = 1350,
        labels = CustomDesignOcrLabels("TEAM NAME", "WIN", "ELIM.", "POS.", "TOTAL"),
        effectiveGridGeometry = validGeometry(),
    )

    private fun validGeometry() = CustomDesignEffectiveGridGeometry(
        sourceWidth = 1080,
        sourceHeight = 1350,
        columnX = linkedMapOf(
            CustomDesignAnchorField.TEAM_NAME to 700f,
            CustomDesignAnchorField.WIN to 200f,
            CustomDesignAnchorField.TOTAL_KILLS to 600f,
            CustomDesignAnchorField.POSITION_POINTS to 400f,
            CustomDesignAnchorField.TOTAL_POINTS to 900f,
        ),
        rowY = (1..12).associateWith { it * 100f },
    )

    private class RecordingDependencies(
        private val events: MutableList<String>,
        private val insertFailure: Boolean = false,
        private val uploadFailure: Boolean = false,
        private val cancelOnInsert: Boolean = false,
    ) {
        private val ownerId = "a1000000-0000-0000-0000-000000000001"
        var prepared = 0
        var preparedFile: File? = null
        val inserted = mutableListOf<CustomDesignTemplateCloudPayload>()
        val deleted = mutableListOf<Triple<String, String, String>>()
        val preparer = CustomDesignImagePreparer {
            events += "prepare"
            prepared += 1
            preparedFile = File.createTempFile("custom-design-coordinator-", ".tmp").apply {
                writeText("image")
            }
            PreparedCustomDesignImage(
                file = checkNotNull(preparedFile),
                sha256 = "a".repeat(64),
                byteSize = 5,
                mimeType = "image/png",
                extension = "png",
            )
        }
        val uploader = object : CustomDesignStorageUploader {
            override suspend fun upload(
                expectedOwnerUserId: String,
                customDesignId: String,
                preparedFile: File,
                extension: String,
                mimeType: String,
            ): CustomDesignStorageUploadResult {
                events += "upload"
                if (uploadFailure) {
                    return CustomDesignStorageUploadResult.Failed(CustomDesignStorageFailure.UPLOAD_FAILED)
                }
                return CustomDesignStorageUploadResult.Uploaded(
                    SupabaseCustomDesignStorageUploader.objectPath(expectedOwnerUserId, customDesignId, extension),
                )
            }

            override suspend fun delete(
                expectedOwnerUserId: String,
                customDesignId: String,
                extension: String,
            ): CustomDesignStorageDeleteResult {
                deleted += Triple(expectedOwnerUserId, customDesignId, extension)
                return CustomDesignStorageDeleteResult.Deleted
            }
        }
        val dataSource = object : CustomDesignTemplateCloudDataSource {
            override suspend fun insert(
                payload: CustomDesignTemplateCloudPayload,
                expectedOwnerUserId: String,
            ): CustomDesignTemplateCloudInsertResult {
                events += "insert"
                if (cancelOnInsert) throw CancellationException("cancelled")
                if (insertFailure) error("insert failed")
                inserted += payload
                return CustomDesignTemplateCloudInsertResult.Inserted
            }

            override suspend fun readById(
                customDesignId: String,
                expectedOwnerUserId: String,
            ): CustomDesignTemplateCloudReadResult = CustomDesignTemplateCloudReadResult.NotFound

            override suspend fun deleteById(
                customDesignId: String,
                expectedOwnerUserId: String,
            ): CustomDesignTemplateCloudDeleteResult = CustomDesignTemplateCloudDeleteResult.Deleted
        }

        fun coordinator() = CustomDesignSaveCoordinator(
            currentUserId = {
                events += "auth"
                ownerId
            },
            imagePreparer = preparer,
            storageUploader = uploader,
            cloudDataSource = dataSource,
        )
    }
}
