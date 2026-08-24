package com.hoggamers.rankforge.data.ocr.preprocessing

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.local.RosterScreenshotAssociationSaveResult
import com.hoggamers.rankforge.data.local.RosterScreenshotMetadataEntity
import com.hoggamers.rankforge.data.local.RosterScreenshotMetadataRepository
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrLocalRelativePath
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparationFailure
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparationResult
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrScreenshotSource
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrSourceProviderResult
import com.hoggamers.rankforge.presentation.screen.RosterScreenshotLocalImageStore
import com.hoggamers.rankforge.presentation.screen.RosterScreenshotLocalImageStoreResult
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidRosterOcrPanelPreparerTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun blankTournamentIdDoesNotReadMetadataRepository() = runBlocking {
        val repository = FakeMetadataRepository(metadata = metadataSet())

        val result = RoomRosterOcrSourceProvider(repository).load(" ", "owner-a")

        assertEquals(RosterOcrSourceProviderResult.InvalidTournamentContext, result)
        assertEquals(0, repository.reads)
    }

    @Test
    fun blankOwnerIdDoesNotReadMetadataRepository() = runBlocking {
        val repository = FakeMetadataRepository(metadata = metadataSet())

        val result = RoomRosterOcrSourceProvider(repository).load(TOURNAMENT_ID, " ")

        assertEquals(RosterOcrSourceProviderResult.InvalidTournamentContext, result)
        assertEquals(0, repository.reads)
    }

    @Test
    fun foreignOwnerReturnsNoOcrSourceAndDoesNotExposePath() = runBlocking {
        val repository = FakeMetadataRepository(
            ownerMetadata = mapOf("owner-a" to emptyList()),
        )

        val result = RoomRosterOcrSourceProvider(repository).load(TOURNAMENT_ID, "owner-a")

        assertEquals(RosterOcrSourceProviderResult.IncompleteScreenshotSet, result)
        assertEquals(1, repository.reads)
        assertEquals("owner-a", repository.lastOwner)
    }

    @Test
    fun unorderedMetadataMapsToOrderedSourcesAndPreservesFields() = runBlocking {
        val metadata = listOf(
            metadata(index = 3, path = "private/roster/three.jpg", width = 303, height = 203, crop = Crop(0.13, 0.23, 0.83, 0.93)),
            metadata(index = 1, path = "private/roster/one.jpg", width = 301, height = 201, crop = Crop(0.11, 0.21, 0.81, 0.91)),
            metadata(index = 2, path = "private/roster/two.jpg", width = 302, height = 202, crop = Crop(0.12, 0.22, 0.82, 0.92)),
        )

        val result = RoomRosterOcrSourceProvider(FakeMetadataRepository(metadata)).load(TOURNAMENT_ID, "owner-a")
        val sources = (result as RosterOcrSourceProviderResult.Loaded).sources

        assertEquals(listOf(1, 2, 3), sources.map { it.rosterScreenshotIndex })
        assertEquals(
            listOf(
                RosterScreenshotPosition.ONE,
                RosterScreenshotPosition.TWO,
                RosterScreenshotPosition.THREE,
            ),
            sources.map { it.screenshotPosition },
        )
        assertEquals(
            listOf("private/roster/one.jpg", "private/roster/two.jpg", "private/roster/three.jpg"),
            sources.map { it.localRelativePath.value },
        )
        assertEquals(listOf(301, 302, 303), sources.map { it.sourceWidth })
        assertEquals(listOf(201, 202, 203), sources.map { it.sourceHeight })
        assertEquals(listOf(0.11, 0.12, 0.13), sources.map { it.cropLeft })
        assertEquals(TOURNAMENT_ID, sources.first().tournamentId)
    }

    @Test
    fun incompleteMetadataSetIsRejected() = runBlocking {
        val result = RoomRosterOcrSourceProvider(
            FakeMetadataRepository(metadata = listOf(metadata(1), metadata(2))),
        ).load(TOURNAMENT_ID, "owner-a")

        assertEquals(RosterOcrSourceProviderResult.IncompleteScreenshotSet, result)
    }

    @Test
    fun duplicateMetadataIndexIsRejected() = runBlocking {
        val result = RoomRosterOcrSourceProvider(
            FakeMetadataRepository(metadata = listOf(metadata(1), metadata(1), metadata(3))),
        ).load(TOURNAMENT_ID, "owner-a")

        assertEquals(
            RosterOcrSourceProviderResult.DuplicateScreenshotPositions(listOf(1)),
            result,
        )
    }

    @Test
    fun unsupportedMetadataIndexIsRejected() = runBlocking {
        val result = RoomRosterOcrSourceProvider(
            FakeMetadataRepository(metadata = listOf(metadata(1), metadata(2), metadata(4))),
        ).load(TOURNAMENT_ID, "owner-a")

        assertEquals(
            RosterOcrSourceProviderResult.UnsupportedScreenshotPosition(4),
            result,
        )
    }

    @Test
    fun missingCropMetadataReportsExactIndex() = runBlocking {
        val result = RoomRosterOcrSourceProvider(
            FakeMetadataRepository(
                metadata = listOf(
                    metadata(1),
                    metadata(2, crop = Crop(null, 0.0, 1.0, 1.0)),
                    metadata(3),
                ),
            ),
        ).load(TOURNAMENT_ID, "owner-a")

        assertEquals(RosterOcrSourceProviderResult.MissingCropMetadata(2), result)
    }

    @Test
    fun metadataRepositoryFailureIsControlled() = runBlocking {
        val result = RoomRosterOcrSourceProvider(
            FakeMetadataRepository(failure = IllegalStateException("private repository failure")),
        ).load(TOURNAMENT_ID, "owner-a")

        assertEquals(RosterOcrSourceProviderResult.LoadingFailure, result)
    }

    @Test
    fun metadataRepositoryCancellationPropagates() = runBlocking {
        try {
            RoomRosterOcrSourceProvider(FakeMetadataRepository(cancel = true)).load(TOURNAMENT_ID, "owner-a")
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            Unit
        }
    }

    @Test
    fun missingLocalOriginalIsControlled() = runBlocking {
        val result = preparer(FakeImageStore(emptyMap())).prepare(source())

        assertEquals(
            RosterOcrPanelPreparationFailure.MISSING_LOCAL_ORIGINAL,
            result.failure(),
        )
    }

    @Test
    fun unreadableAndNonImageFilesAreControlled() = runBlocking {
        val unreadable = cacheFile("unreadable", byteArrayOf(1, 2, 3, 4))
        val nonImage = cacheFile("non-image", "not an image".encodeToByteArray())
        try {
            assertEquals(
                RosterOcrPanelPreparationFailure.UNREADABLE_OR_DECODE_FAILURE,
                preparer(FakeImageStore(mapOf(PATH to unreadable.toUriString())))
                    .prepare(source()).failure(),
            )
            assertEquals(
                RosterOcrPanelPreparationFailure.UNREADABLE_OR_DECODE_FAILURE,
                preparer(FakeImageStore(mapOf(PATH to nonImage.toUriString())))
                    .prepare(source()).failure(),
            )
        } finally {
            unreadable.delete()
            nonImage.delete()
        }
    }

    @Test
    fun invalidNormalizedCropsAreRejected() = runBlocking {
        val invalidSources = listOf(
            source(cropLeft = Double.NaN),
            source(cropLeft = -0.1),
            source(cropRight = 1.1),
            source(cropLeft = 0.8, cropRight = 0.2),
            source(cropTop = 0.8, cropBottom = 0.2),
            source(cropRight = 0.05),
            source(cropBottom = 0.05),
        )

        invalidSources.forEach { invalid ->
            val result = preparer(FakeImageStore(emptyMap())).prepare(invalid)
            assertEquals(RosterOcrPanelPreparationFailure.INVALID_CROP, result.failure())
        }
    }

    @Test
    fun metadataAndDecodedDimensionsMustMatch() = runBlocking {
        val file = syntheticPng(20, 10)
        try {
            val result = preparer(FakeImageStore(mapOf(PATH to file.toUriString())))
                .prepare(source(sourceWidth = 21, sourceHeight = 10))

            assertEquals(RosterOcrPanelPreparationFailure.UNSAFE_DIMENSIONS, result.failure())
        } finally {
            file.delete()
        }
    }

    @Test
    fun oversizedMetadataIsRejectedBeforeMatchingDecodeAllocation() = runBlocking {
        val file = syntheticPng(20, 10)
        try {
            val result = preparer(FakeImageStore(mapOf(PATH to file.toUriString())))
                .prepare(source(sourceWidth = 8_193, sourceHeight = 10))

            assertEquals(RosterOcrPanelPreparationFailure.UNSAFE_DIMENSIONS, result.failure())
        } finally {
            file.delete()
        }
    }

    @Test
    fun validPartialCropProducesPreparedAndroidImageAndExpectedDimensions() = runBlocking {
        val file = syntheticPng(100, 80)
        try {
            val result = preparer(FakeImageStore(mapOf(PATH to file.toUriString())))
                .prepare(
                    source(
                        sourceWidth = 100,
                        sourceHeight = 80,
                        cropLeft = 0.25,
                        cropTop = 0.125,
                        cropRight = 0.75,
                        cropBottom = 0.625,
                    ),
                )
            val panel = result.panel()
            val image = panel.croppedPanelImage
            val bitmap = (image as AndroidBitmapOcrImage).bitmap

            assertFalse(bitmap.isRecycled)
            assertEquals(50, image.width)
            assertEquals(40, image.height)
            assertEquals(RosterScreenshotPosition.TWO, panel.croppedPanelInput.screenshotPosition)
            assertTrue(panel.croppedPanelInput.isPreparedRosterCrop)
            panel.release()
            assertTrue(bitmap.isRecycled)
            panel.release()
            assertTrue(bitmap.isRecycled)
        } finally {
            file.delete()
        }
    }

    @Test
    fun fullImageCropKeepsBitmapUntilReleaseAndRepeatedReleaseIsSafe() = runBlocking {
        val file = syntheticPng(20, 10)
        try {
            val panel = preparer(FakeImageStore(mapOf(PATH to file.toUriString())))
                .prepare(source(sourceWidth = 20, sourceHeight = 10)).panel()
            val bitmap = (panel.croppedPanelImage as AndroidBitmapOcrImage).bitmap

            assertFalse(bitmap.isRecycled)
            panel.release()
            assertTrue(bitmap.isRecycled)
            panel.release()
            assertTrue(bitmap.isRecycled)
        } finally {
            file.delete()
        }
    }

    @Test
    fun secondPassReadFailureIsUnreadableDecodeFailure() = runBlocking {
        val file = syntheticPng(20, 10)
        try {
            val preparer = preparer(FakeImageStore(mapOf(PATH to file.toUriString())))
            var openCount = 0
            preparer.inputStreamOpener = RosterOcrInputStreamOpener {
                openCount++
                if (openCount == 1) file.inputStream()
                else throw IOException("synthetic second-pass read failure")
            }

            val result = preparer.prepare(source())

            assertEquals(
                RosterOcrPanelPreparationFailure.UNREADABLE_OR_DECODE_FAILURE,
                result.failure(),
            )
            assertTrue(result is RosterOcrPanelPreparationResult.Failed)
            assertEquals(2, openCount)
        } finally {
            file.delete()
        }
    }

    @Test
    fun cancellationBeforePreparationPropagates() = runBlocking {
        val preparer = preparer(FakeImageStore(emptyMap()))
        val deferred = async(start = CoroutineStart.LAZY) { preparer.prepare(source()) }
        deferred.cancel()
        try {
            deferred.await()
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            Unit
        }
    }

    private fun preparer(store: FakeImageStore): AndroidRosterOcrPanelPreparer =
        AndroidRosterOcrPanelPreparer(context, store)

    private fun source(
        path: String = PATH,
        sourceWidth: Int = 20,
        sourceHeight: Int = 10,
        cropLeft: Double = 0.0,
        cropTop: Double = 0.0,
        cropRight: Double = 1.0,
        cropBottom: Double = 1.0,
    ) = RosterOcrScreenshotSource(
        tournamentId = TOURNAMENT_ID,
        rosterScreenshotIndex = 2,
        screenshotPosition = RosterScreenshotPosition.TWO,
        localRelativePath = RosterOcrLocalRelativePath(path),
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        cropLeft = cropLeft,
        cropTop = cropTop,
        cropRight = cropRight,
        cropBottom = cropBottom,
    )

    private fun metadataSet(): List<RosterScreenshotMetadataEntity> = (1..3).map(::metadata)

    private fun metadata(
        index: Int,
        tournamentId: String = TOURNAMENT_ID,
        path: String = "private-$index.jpg",
        width: Int = 301 + index,
        height: Int = 201 + index,
        crop: Crop = Crop(0.1, 0.2, 0.9, 0.95),
    ) = RosterScreenshotMetadataEntity(
        tournamentId = tournamentId,
        rosterScreenshotIndex = index,
        localRelativePath = path,
        mimeType = "image/png",
        width = width,
        height = height,
        sha256 = "synthetic-$index",
        validationStatus = "VALID",
        cropLeft = crop.left,
        cropTop = crop.top,
        cropRight = crop.right,
        cropBottom = crop.bottom,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun cacheFile(prefix: String, bytes: ByteArray): File =
        File.createTempFile("roster-ocr-$prefix-", ".bin", context.cacheDir).also { it.writeBytes(bytes) }

    private fun syntheticPng(width: Int, height: Int): File {
        val file = File(context.cacheDir, "roster-ocr-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private fun File.toUriString(): String = toURI().toString()

    private fun RosterOcrPanelPreparationResult.failure(): RosterOcrPanelPreparationFailure =
        (this as? RosterOcrPanelPreparationResult.Failed)?.failure
            ?: error("Expected a preparation failure")

    private fun RosterOcrPanelPreparationResult.panel() =
        (this as? RosterOcrPanelPreparationResult.Prepared)?.panel
            ?: error("Expected a prepared panel")

    private class FakeMetadataRepository(
        private val metadata: List<RosterScreenshotMetadataEntity> = emptyList(),
        private val failure: Throwable? = null,
        private val cancel: Boolean = false,
        private val ownerMetadata: Map<String, List<RosterScreenshotMetadataEntity>> = emptyMap(),
    ) : RosterScreenshotMetadataRepository {
        var reads = 0
        var lastOwner: String? = null

        override fun observeByTournamentId(tournamentId: String): Flow<List<RosterScreenshotMetadataEntity>> = flow {
            reads++
            if (cancel) throw CancellationException()
            failure?.let { throw it }
            emit(metadata)
        }

        override fun observeByTournamentIdAndOwner(
            tournamentId: String,
            ownerUserId: String,
        ): Flow<List<RosterScreenshotMetadataEntity>> = flow {
            reads++
            lastOwner = ownerUserId
            if (cancel) throw CancellationException()
            failure?.let { throw it }
            emit(ownerMetadata[ownerUserId] ?: metadata)
        }

        override suspend fun existsByTournamentIdAndOwner(tournamentId: String, ownerUserId: String): Boolean = true

        override suspend fun readByTournamentAndIndexAndOwner(
            tournamentId: String,
            index: Int,
            ownerUserId: String,
        ): RosterScreenshotMetadataEntity? = metadata.firstOrNull { it.rosterScreenshotIndex == index }

        override suspend fun findDuplicateFingerprintAndOwner(
            tournamentId: String,
            sha256: String,
            index: Int,
            ownerUserId: String,
        ): RosterScreenshotMetadataEntity? = metadata.firstOrNull {
            it.sha256 == sha256 && it.rosterScreenshotIndex != index
        }

        override suspend fun saveOrReplace(metadata: RosterScreenshotMetadataEntity) =
            RosterScreenshotAssociationSaveResult.Saved

        override suspend fun saveOrReplaceByOwner(
            metadata: RosterScreenshotMetadataEntity,
            ownerUserId: String,
        ) = RosterScreenshotAssociationSaveResult.Saved

        override suspend fun deleteByTournamentAndIndex(tournamentId: String, index: Int) = Unit

        override suspend fun deleteByTournamentAndIndexAndOwner(
            tournamentId: String,
            index: Int,
            ownerUserId: String,
        ) = com.hoggamers.rankforge.data.local.RosterScreenshotAssociationDeleteResult.Deleted
    }

    private class FakeImageStore(
        private val uriByPath: Map<String, String?>,
    ) : RosterScreenshotLocalImageStore {
        override fun displayUriOrNull(localRelativePath: String): String? = uriByPath[localRelativePath]

        override suspend fun preserve(
            tournamentId: String,
            rosterScreenshotIndex: Int,
            selectedUri: String,
        ): RosterScreenshotLocalImageStoreResult = RosterScreenshotLocalImageStoreResult.Failed

        override suspend fun cleanup(tournamentId: String, rosterScreenshotIndex: Int) = Unit
    }

    private data class Crop(
        val left: Double?,
        val top: Double?,
        val right: Double?,
        val bottom: Double?,
    )

    private companion object {
        const val TOURNAMENT_ID = "synthetic-tournament"
        const val PATH = "private-roster-original.png"
    }
}
